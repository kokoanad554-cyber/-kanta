package com.example.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.example.model.BatchRenameRule
import com.example.model.FileHashResult
import com.example.model.FileItem
import com.example.model.FolderStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

object FileProTools {

    private const val STREAM_BUFFER_SIZE = 64 * 1024

    suspend fun calculateHashes(context: Context, item: FileItem): Result<FileHashResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val input: InputStream = if (item.safUri != null) {
                    context.contentResolver.openInputStream(item.safUri)
                        ?: throw IllegalStateException("Cannot open file stream")
                } else {
                    FileInputStream(File(item.path))
                }
                input.use { stream ->
                    calculateHashesFromStream(stream)
                }
            }
        }

    suspend fun calculateHashesFromFile(file: File): Result<FileHashResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                FileInputStream(file).use { stream ->
                    calculateHashesFromStream(stream)
                }
            }
        }

    suspend fun calculateHashesFromStream(stream: InputStream): FileHashResult {
        val md5 = MessageDigest.getInstance("MD5")
        val sha1 = MessageDigest.getInstance("SHA-1")
        val sha256 = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(STREAM_BUFFER_SIZE)
        var read: Int
        while (stream.read(buffer).also { read = it } != -1) {
            coroutineContext.ensureActive()
            md5.update(buffer, 0, read)
            sha1.update(buffer, 0, read)
            sha256.update(buffer, 0, read)
        }
        return FileHashResult(
            md5 = md5.digest().toHex(),
            sha1 = sha1.digest().toHex(),
            sha256 = sha256.digest().toHex()
        )
    }

    suspend fun calculateFolderStats(context: Context, item: FileItem): Result<FolderStats> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (item.safUri != null) {
                    val doc = DocumentFile.fromTreeUri(context, item.safUri)
                        ?: DocumentFile.fromSingleUri(context, item.safUri)
                        ?: throw IllegalStateException("Cannot access folder")
                    walkSafStats(doc)
                } else {
                    walkLocalStats(File(item.path))
                }
            }
        }

    suspend fun calculateLocalFolderStats(dir: File): FolderStats =
        withContext(Dispatchers.IO) {
            walkLocalStats(dir)
        }

    private suspend fun walkLocalStats(dir: File): FolderStats {
        coroutineContext.ensureActive()
        if (!dir.exists()) return FolderStats(0L, 0, 0)
        if (!dir.isDirectory) return FolderStats(dir.length(), 1, 0)
        var bytes = 0L
        var files = 0
        var folders = 0
        val children = dir.listFiles() ?: return FolderStats(0L, 0, 0)
        for (child in children) {
            coroutineContext.ensureActive()
            if (child.isDirectory) {
                folders++
                val sub = walkLocalStats(child)
                bytes += sub.totalBytes
                files += sub.fileCount
                folders += sub.folderCount
            } else {
                files++
                bytes += child.length()
            }
        }
        return FolderStats(bytes, files, folders)
    }

    private suspend fun walkSafStats(doc: DocumentFile): FolderStats {
        coroutineContext.ensureActive()
        if (!doc.isDirectory) return FolderStats(doc.length(), 1, 0)
        var bytes = 0L
        var files = 0
        var folders = 0
        for (child in doc.listFiles()) {
            coroutineContext.ensureActive()
            if (child.isDirectory) {
                folders++
                val sub = walkSafStats(child)
                bytes += sub.totalBytes
                files += sub.fileCount
                folders += sub.folderCount
            } else {
                files++
                bytes += child.length()
            }
        }
        return FolderStats(bytes, files, folders)
    }

    /**
     * Pure deterministic batch rename planner with conflict-free collision numbering.
     */
    fun buildBatchRenamePlan(
        items: List<FileItem>,
        rule: BatchRenameRule
    ): List<Pair<FileItem, String>> {
        val usedNames = HashSet<String>()
        return items.mapIndexed { index, item ->
            val rawName = item.name
            val dotIdx = if (item.isDirectory) -1 else rawName.lastIndexOf('.')
            val origBase = if (dotIdx > 0) rawName.substring(0, dotIdx) else rawName
            val origExt = if (dotIdx > 0) rawName.substring(dotIdx + 1) else ""

            var base = origBase
            if (rule.findText.isNotEmpty()) {
                base = base.replace(rule.findText, rule.replaceText, ignoreCase = false)
            }
            if (rule.prefix.isNotEmpty()) {
                base = "${rule.prefix}$base"
            }
            if (rule.suffix.isNotEmpty()) {
                base = "$base${rule.suffix}"
            }
            if (rule.useNumbering) {
                val numStr = (rule.startNumber + index).toString().padStart(2, '0')
                base = if (base.isBlank()) numStr else "${base}_$numStr"
            }
            if (base.isBlank()) {
                base = origBase.ifBlank { "item_${index + 1}" }
            }

            val finalExt = if (!item.isDirectory && rule.changeExtension) {
                rule.newExtension.trim().removePrefix(".")
            } else {
                origExt
            }

            var candidate = if (finalExt.isNotEmpty()) "$base.$finalExt" else base
            var counter = 2
            while (!usedNames.add(candidate.lowercase())) {
                candidate = if (finalExt.isNotEmpty()) "${base}_$counter.$finalExt" else "${base}_$counter"
                counter++
            }
            item to candidate
        }
    }

    /**
     * Builds an Android 11+ DocumentsContract initial URI pointing directly to Android/data or Android/obb
     * for OpenDocumentTree().
     */
    fun buildAndroidSafInitialUri(subFolder: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return runCatching {
            val clean = subFolder.trim('/')
            DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                "primary:$clean"
            )
        }.getOrNull()
    }

    private fun ByteArray.toHex(): String {
        val hex = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xFF
            if (v < 16) hex.append('0')
            hex.append(Integer.toHexString(v))
        }
        return hex.toString()
    }
}
