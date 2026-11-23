package com.example.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.model.ArchiveEntryInfo
import com.example.model.FileItem
import com.example.model.FileOpType
import com.example.model.FileOperationProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext

class ArchiveManager(private val context: Context) {

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
    }

    suspend fun compressToZip(
        sources: List<FileItem>,
        destDirPath: String,
        destSafUri: Uri?,
        archiveName: String,
        onProgress: (FileOperationProgress) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanName = archiveName.trim().let {
                if (it.endsWith(".zip", ignoreCase = true)) it else "$it.zip"
            }

            val totalBytes = sources.sumOf {
                if (it.safUri == null) calculateFileTreeSize(File(it.path)) else it.sizeBytes
            }.coerceAtLeast(1L)
            var processedBytes = 0L

            val outStream = if (destSafUri != null) {
                val destDoc = DocumentFile.fromTreeUri(context, destSafUri)
                    ?: throw IllegalStateException("تعذر الوصول للمجلد الهدف")
                val zipDoc = destDoc.createFile("application/zip", cleanName)
                    ?: throw IllegalStateException("فشل إنشاء ملف الأرشيف")
                context.contentResolver.openOutputStream(zipDoc.uri)
                    ?: throw IllegalStateException("تعذر الكتابة إلى الأرشيف")
            } else {
                val outFile = File(destDirPath, cleanName)
                FileOutputStream(outFile)
            }

            ZipOutputStream(BufferedOutputStream(outStream, BUFFER_SIZE)).use { zos ->
                for (item in sources) {
                    coroutineContext.ensureActive()
                    if (item.safUri == null) {
                        val file = File(item.path)
                        zipLocalFile(file, file.name, zos) { delta, name ->
                            processedBytes += delta
                            onProgress(
                                FileOperationProgress(
                                    opType = FileOpType.COMPRESS,
                                    currentFileName = name,
                                    processedBytes = processedBytes,
                                    totalBytes = totalBytes
                                )
                            )
                        }
                    } else {
                        val doc = DocumentFile.fromTreeUri(context, item.safUri)
                            ?: DocumentFile.fromSingleUri(context, item.safUri)
                        if (doc != null) {
                            zipDocumentFile(doc, doc.name ?: item.name, zos) { delta, name ->
                                processedBytes += delta
                                onProgress(
                                    FileOperationProgress(
                                        opType = FileOpType.COMPRESS,
                                        currentFileName = name,
                                        processedBytes = processedBytes,
                                        totalBytes = totalBytes
                                    )
                                )
                            }
                        }
                    }
                }
            }
            Unit
        }
    }

    private fun calculateFileTreeSize(file: File): Long {
        if (!file.exists()) return 0L
        if (!file.isDirectory) return file.length()
        return file.listFiles()?.sumOf { calculateFileTreeSize(it) } ?: 0L
    }

    private suspend fun zipLocalFile(
        file: File,
        entryName: String,
        zos: ZipOutputStream,
        onBytes: (Long, String) -> Unit
    ) {
        coroutineContext.ensureActive()
        if (file.isDirectory) {
            val dirEntry = if (entryName.endsWith("/")) entryName else "$entryName/"
            zos.putNextEntry(ZipEntry(dirEntry))
            zos.closeEntry()
            file.listFiles()?.forEach { child ->
                zipLocalFile(child, "$dirEntry${child.name}", zos, onBytes)
            }
        } else {
            val entry = ZipEntry(entryName)
            entry.time = file.lastModified()
            zos.putNextEntry(entry)
            FileInputStream(file).use { fis ->
                streamToZip(fis, zos, file.name, onBytes)
            }
            zos.closeEntry()
        }
    }

    private suspend fun zipDocumentFile(
        doc: DocumentFile,
        entryName: String,
        zos: ZipOutputStream,
        onBytes: (Long, String) -> Unit
    ) {
        coroutineContext.ensureActive()
        if (doc.isDirectory) {
            val dirEntry = if (entryName.endsWith("/")) entryName else "$entryName/"
            zos.putNextEntry(ZipEntry(dirEntry))
            zos.closeEntry()
            doc.listFiles().forEach { child ->
                val childName = child.name ?: return@forEach
                zipDocumentFile(child, "$dirEntry$childName", zos, onBytes)
            }
        } else {
            zos.putNextEntry(ZipEntry(entryName))
            context.contentResolver.openInputStream(doc.uri)?.use { input ->
                streamToZip(input, zos, doc.name ?: entryName, onBytes)
            }
            zos.closeEntry()
        }
    }

    private suspend fun streamToZip(
        input: InputStream,
        zos: ZipOutputStream,
        name: String,
        onBytes: (Long, String) -> Unit
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var read: Int
        var pending = 0L
        while (input.read(buffer).also { read = it } != -1) {
            coroutineContext.ensureActive()
            zos.write(buffer, 0, read)
            pending += read
            if (pending >= 256 * 1024) {
                onBytes(pending, name)
                pending = 0L
            }
        }
        if (pending > 0L) onBytes(pending, name)
    }

    suspend fun inspectZip(zipItem: FileItem): Result<List<ArchiveEntryInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val entries = mutableListOf<ArchiveEntryInfo>()
            val inputStream = openZipInput(zipItem)
            ZipInputStream(BufferedInputStream(inputStream, BUFFER_SIZE)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    coroutineContext.ensureActive()
                    entries += ArchiveEntryInfo(
                        name = entry.name,
                        sizeBytes = entry.size.coerceAtLeast(0L),
                        compressedSizeBytes = entry.compressedSize.coerceAtLeast(0L),
                        isDirectory = entry.isDirectory
                    )
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            entries
        }
    }

    suspend fun extractZip(
        zipItem: FileItem,
        destDirPath: String,
        destSafUri: Uri?,
        onProgress: (FileOperationProgress) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val totalHint = zipItem.sizeBytes.coerceAtLeast(1L)
            var count = 0
            val inputStream = openZipInput(zipItem)

            if (destSafUri != null) {
                val rootDoc = DocumentFile.fromTreeUri(context, destSafUri)
                    ?: throw IllegalStateException("تعذر الوصول لمجلد الاستخراج")
                ZipInputStream(BufferedInputStream(inputStream, BUFFER_SIZE)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        coroutineContext.ensureActive()
                        val sanitized = sanitizeRelativePath(entry.name)
                        if (sanitized != null) {
                            count++
                            onProgress(
                                FileOperationProgress(
                                    opType = FileOpType.EXTRACT,
                                    currentFileName = sanitized,
                                    processedCount = count,
                                    totalCount = 0,
                                    isIndeterminate = true
                                )
                            )
                            if (entry.isDirectory) {
                                ensureSafDirectories(rootDoc, sanitized)
                            } else {
                                val parts = sanitized.split('/').filter { it.isNotEmpty() }
                                val fileName = parts.lastOrNull()
                                if (fileName != null) {
                                    val parentDoc = if (parts.size > 1) {
                                        ensureSafDirectories(rootDoc, parts.dropLast(1).joinToString("/"))
                                    } else {
                                        rootDoc
                                    }
                                    val mime = FileRepository.guessMimeType(fileName)
                                    parentDoc.findFile(fileName)?.delete()
                                    val outDoc = parentDoc.createFile(mime, fileName)
                                    if (outDoc != null) {
                                        context.contentResolver.openOutputStream(outDoc.uri)?.use { out ->
                                            copyZipEntryStream(zis, out)
                                        }
                                    }
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            } else {
                val destDir = File(destDirPath)
                if (!destDir.exists()) destDir.mkdirs()
                val canonicalDest = destDir.canonicalPath
                var extractedBytes = 0L

                ZipInputStream(BufferedInputStream(inputStream, BUFFER_SIZE)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        coroutineContext.ensureActive()
                        val target = File(destDir, entry.name)
                        val canonicalTarget = target.canonicalPath
                        if (!canonicalTarget.startsWith(canonicalDest + File.separator) && canonicalTarget != canonicalDest) {
                            throw SecurityException("مسار غير آمن داخل الأرشيف: ${entry.name}")
                        }
                        count++
                        if (entry.isDirectory) {
                            target.mkdirs()
                        } else {
                            target.parentFile?.mkdirs()
                            FileOutputStream(target).use { fos ->
                                val written = copyZipEntryStream(zis, fos)
                                extractedBytes += written
                            }
                        }
                        onProgress(
                            FileOperationProgress(
                                opType = FileOpType.EXTRACT,
                                currentFileName = entry.name.substringAfterLast('/'),
                                processedBytes = extractedBytes.coerceAtMost(totalHint * 2),
                                totalBytes = totalHint,
                                processedCount = count,
                                isIndeterminate = extractedBytes > totalHint
                            )
                        )
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
            Unit
        }
    }

    private fun openZipInput(zipItem: FileItem): InputStream {
        return if (zipItem.safUri != null) {
            context.contentResolver.openInputStream(zipItem.safUri)
                ?: throw IllegalStateException("تعذر فتح ملف الأرشيف")
        } else {
            FileInputStream(File(zipItem.path))
        }
    }

    private suspend fun copyZipEntryStream(zis: ZipInputStream, out: java.io.OutputStream): Long {
        val buffer = ByteArray(BUFFER_SIZE)
        var read: Int
        var total = 0L
        while (zis.read(buffer).also { read = it } != -1) {
            coroutineContext.ensureActive()
            out.write(buffer, 0, read)
            total += read
        }
        return total
    }

    private fun sanitizeRelativePath(raw: String): String? {
        val normalized = raw.replace('\\', '/').trim('/')
        if (normalized.isEmpty()) return null
        val segments = normalized.split('/').filter { it.isNotEmpty() }
        if (segments.any { it == ".." || it == "." }) return null
        return segments.joinToString("/")
    }

    private fun ensureSafDirectories(root: DocumentFile, relPath: String): DocumentFile {
        var current = root
        val parts = relPath.split('/').filter { it.isNotEmpty() }
        for (part in parts) {
            current = current.findFile(part)?.takeIf { it.isDirectory }
                ?: current.createDirectory(part)
                ?: throw IllegalStateException("فشل إنشاء مجلد $part")
        }
        return current
    }
}
