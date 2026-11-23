package com.example.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.model.ArchiveEntryInfo
import com.example.model.ArchiveTestResult
import com.example.model.CompressionLevel
import com.example.model.FileCategory
import com.example.model.FileItem
import com.example.model.FileOpType
import com.example.model.FileOperationProgress
import com.example.model.SortOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext

class ArchiveManager(private val context: Context) {

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
        const val ARCHIVE_ENTRY_PREFIX = "zipentry://"

        fun listArchiveDirectory(
            entries: List<ArchiveEntryInfo>,
            innerPath: String,
            showHidden: Boolean,
            sortOption: SortOption
        ): List<FileItem> {
            val cleanPrefix = innerPath.trim('/').let { if (it.isEmpty()) "" else "$it/" }
            val folderMap = LinkedHashMap<String, ArchiveEntryInfo?>()
            val fileList = mutableListOf<FileItem>()

            for (entry in entries) {
                val norm = entry.fullEntryPath.replace('\\', '/').trimStart('/')
                if (norm.isEmpty()) continue
                if (cleanPrefix.isNotEmpty() && !norm.startsWith(cleanPrefix)) continue
                val remainder = norm.removePrefix(cleanPrefix).trimEnd('/')
                if (remainder.isEmpty()) continue

                val slashIdx = remainder.indexOf('/')
                if (slashIdx >= 0 || entry.isDirectory) {
                    val dirName = if (slashIdx >= 0) remainder.substring(0, slashIdx) else remainder
                    if (!showHidden && dirName.startsWith(".")) continue
                    if (!folderMap.containsKey(dirName)) {
                        folderMap[dirName] = if (slashIdx < 0) entry else null
                    }
                } else {
                    if (!showHidden && remainder.startsWith(".")) continue
                    val fullEntry = "$cleanPrefix$remainder"
                    val (ext, cat) = FileRepository.classifyFile(remainder, false)
                    fileList += FileItem(
                        id = "$ARCHIVE_ENTRY_PREFIX$fullEntry",
                        name = remainder,
                        path = fullEntry,
                        isDirectory = false,
                        sizeBytes = entry.sizeBytes,
                        lastModifiedMillis = 0L,
                        isHidden = remainder.startsWith("."),
                        canRead = true,
                        canWrite = false,
                        extension = ext,
                        category = cat,
                        safUri = null,
                        childCount = null
                    )
                }
            }

            val folderItems = folderMap.keys.map { dirName ->
                val fullDir = "$cleanPrefix$dirName"
                FileItem(
                    id = "$ARCHIVE_ENTRY_PREFIX$fullDir/",
                    name = dirName,
                    path = fullDir,
                    isDirectory = true,
                    sizeBytes = 0L,
                    lastModifiedMillis = 0L,
                    isHidden = dirName.startsWith("."),
                    canRead = true,
                    canWrite = false,
                    extension = "",
                    category = FileCategory.FOLDER,
                    safUri = null,
                    childCount = null
                )
            }

            return sortArchiveItems(folderItems + fileList, sortOption)
        }

        private fun sortArchiveItems(items: List<FileItem>, sortOption: SortOption): List<FileItem> {
            val comparator = when (sortOption) {
                SortOption.NAME_ASC -> compareBy<FileItem> { !it.isDirectory }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                SortOption.NAME_DESC -> compareBy<FileItem> { !it.isDirectory }
                    .thenByDescending(String.CASE_INSENSITIVE_ORDER) { it.name }
                SortOption.SIZE_DESC -> compareBy<FileItem> { !it.isDirectory }
                    .thenByDescending { it.sizeBytes }
                SortOption.DATE_DESC -> compareBy<FileItem> { !it.isDirectory }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                SortOption.TYPE -> compareBy<FileItem> { !it.isDirectory }
                    .thenBy { it.extension }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            }
            return items.sortedWith(comparator)
        }
    }

    suspend fun compressToZip(
        sources: List<FileItem>,
        destDirPath: String,
        destSafUri: Uri?,
        archiveName: String,
        level: CompressionLevel = CompressionLevel.NORMAL,
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
                zos.setLevel(level.deflaterLevel)
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
                        isDirectory = entry.isDirectory,
                        crc = entry.crc,
                        fullEntryPath = entry.name.replace('\\', '/')
                    )
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            entries
        }
    }

    suspend fun readArchiveEntryText(
        zipItem: FileItem,
        entryPath: String,
        maxBytes: Int = 512 * 1024
    ): Result<Pair<String, Boolean>> = withContext(Dispatchers.IO) {
        runCatching {
            val target = entryPath.replace('\\', '/').trim('/')
            ZipInputStream(BufferedInputStream(openZipInput(zipItem), BUFFER_SIZE)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    coroutineContext.ensureActive()
                    val norm = entry.name.replace('\\', '/').trim('/')
                    if (!entry.isDirectory && norm == target) {
                        val buf = ByteArray(8192)
                        val out = java.io.ByteArrayOutputStream()
                        var total = 0
                        var truncated = false
                        var read: Int
                        while (zis.read(buf).also { read = it } != -1) {
                            if (total + read > maxBytes) {
                                val allow = (maxBytes - total).coerceAtLeast(0)
                                if (allow > 0) out.write(buf, 0, allow)
                                truncated = true
                                break
                            }
                            out.write(buf, 0, read)
                            total += read
                        }
                        return@runCatching out.toString(Charsets.UTF_8.name()) to truncated
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            throw IllegalStateException("Entry not found inside archive: $entryPath")
        }
    }

    suspend fun extractSingleEntryToCache(
        zipItem: FileItem,
        entryPath: String,
        fileName: String
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val target = entryPath.replace('\\', '/').trim('/')
            val safeName = fileName.replace(Regex("[/\\\\]"), "_").ifBlank { "entry.tmp" }
            val cacheOut = File(context.cacheDir, "zip_preview_${System.currentTimeMillis()}_$safeName")
            ZipInputStream(BufferedInputStream(openZipInput(zipItem), BUFFER_SIZE)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    coroutineContext.ensureActive()
                    val norm = entry.name.replace('\\', '/').trim('/')
                    if (!entry.isDirectory && norm == target) {
                        FileOutputStream(cacheOut).use { out ->
                            copyZipEntryStream(zis, out)
                        }
                        return@runCatching cacheOut
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            throw IllegalStateException("Entry not found inside archive: $entryPath")
        }
    }

    suspend fun extractZip(
        zipItem: FileItem,
        destDirPath: String,
        destSafUri: Uri?,
        onProgress: (FileOperationProgress) -> Unit
    ): Result<Unit> = extractEntries(zipItem, selectedVirtualItems = null, destDirPath = destDirPath, destSafUri = destSafUri, onProgress = onProgress)

    suspend fun extractEntries(
        zipItem: FileItem,
        selectedVirtualItems: List<FileItem>?,
        destDirPath: String,
        destSafUri: Uri?,
        onProgress: (FileOperationProgress) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val totalHint = zipItem.sizeBytes.coerceAtLeast(1L)
            var count = 0
            val inputStream = openZipInput(zipItem)

            fun matchesSelection(entryRawName: String): Boolean {
                if (selectedVirtualItems == null) return true
                val normEntry = sanitizeRelativePath(entryRawName) ?: return false
                return selectedVirtualItems.any { sel ->
                    val selPath = sel.path.replace('\\', '/').trim('/')
                    if (sel.isDirectory) {
                        normEntry == selPath || normEntry.startsWith("$selPath/")
                    } else {
                        normEntry == selPath
                    }
                }
            }

            if (destSafUri != null) {
                val rootDoc = DocumentFile.fromTreeUri(context, destSafUri)
                    ?: throw IllegalStateException("تعذر الوصول لمجلد الاستخراج")
                ZipInputStream(BufferedInputStream(inputStream, BUFFER_SIZE)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        coroutineContext.ensureActive()
                        val sanitized = sanitizeRelativePath(entry.name)
                        if (sanitized != null && matchesSelection(sanitized)) {
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
                        if (matchesSelection(entry.name)) {
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
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
            Unit
        }
    }

    suspend fun addItemsToZip(
        zipItem: FileItem,
        sources: List<FileItem>,
        targetInnerDir: String,
        onProgress: (FileOperationProgress) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanPrefix = targetInnerDir.trim('/').let { if (it.isEmpty()) "" else "$it/" }
            val newNames = sources.map { "$cleanPrefix${it.name}".trim('/') }.toSet()
            val tempZip = File(context.cacheDir, "kanta_edit_${System.currentTimeMillis()}.zip")
            try {
                ZipOutputStream(BufferedOutputStream(FileOutputStream(tempZip), BUFFER_SIZE)).use { zos ->
                    // Copy existing entries excluding any paths we are replacing
                    ZipInputStream(BufferedInputStream(openZipInput(zipItem), BUFFER_SIZE)).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            coroutineContext.ensureActive()
                            val norm = entry.name.replace('\\', '/').trim('/')
                            val replaced = newNames.any { n -> norm == n || norm.startsWith("$n/") }
                            if (!replaced) {
                                val copiedEntry = ZipEntry(entry.name).apply { time = entry.time }
                                zos.putNextEntry(copiedEntry)
                                if (!entry.isDirectory) {
                                    copyZipEntryStream(zis, zos)
                                }
                                zos.closeEntry()
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }

                    // Append new sources
                    for (src in sources) {
                        coroutineContext.ensureActive()
                        val entryPath = "$cleanPrefix${src.name}"
                        if (src.safUri == null) {
                            zipLocalFile(File(src.path), entryPath, zos) { _, name ->
                                onProgress(
                                    FileOperationProgress(
                                        opType = FileOpType.COMPRESS,
                                        currentFileName = name,
                                        isIndeterminate = true
                                    )
                                )
                            }
                        } else {
                            val doc = DocumentFile.fromTreeUri(context, src.safUri)
                                ?: DocumentFile.fromSingleUri(context, src.safUri)
                            if (doc != null) {
                                zipDocumentFile(doc, entryPath, zos) { _, name ->
                                    onProgress(
                                        FileOperationProgress(
                                            opType = FileOpType.COMPRESS,
                                            currentFileName = name,
                                            isIndeterminate = true
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                writeTempZipBackToTarget(tempZip, zipItem)
            } finally {
                tempZip.delete()
            }
        }
    }

    suspend fun deleteEntriesFromZip(
        zipItem: FileItem,
        targets: List<FileItem>,
        onProgress: (FileOperationProgress) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val tempZip = File(context.cacheDir, "kanta_del_${System.currentTimeMillis()}.zip")
            try {
                ZipOutputStream(BufferedOutputStream(FileOutputStream(tempZip), BUFFER_SIZE)).use { zos ->
                    ZipInputStream(BufferedInputStream(openZipInput(zipItem), BUFFER_SIZE)).use { zis ->
                        var entry = zis.nextEntry
                        var count = 0
                        while (entry != null) {
                            coroutineContext.ensureActive()
                            val norm = entry.name.replace('\\', '/').trim('/')
                            val shouldDelete = targets.any { t ->
                                val tPath = t.path.replace('\\', '/').trim('/')
                                if (t.isDirectory) {
                                    norm == tPath || norm.startsWith("$tPath/")
                                } else {
                                    norm == tPath
                                }
                            }
                            if (!shouldDelete) {
                                val copied = ZipEntry(entry.name).apply { time = entry.time }
                                zos.putNextEntry(copied)
                                if (!entry.isDirectory) {
                                    copyZipEntryStream(zis, zos)
                                }
                                zos.closeEntry()
                            } else {
                                count++
                                onProgress(
                                    FileOperationProgress(
                                        opType = FileOpType.DELETE,
                                        currentFileName = norm.substringAfterLast('/'),
                                        processedCount = count,
                                        isIndeterminate = true
                                    )
                                )
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }
                writeTempZipBackToTarget(tempZip, zipItem)
            } finally {
                tempZip.delete()
            }
        }
    }

    suspend fun renameEntryInZip(
        zipItem: FileItem,
        target: FileItem,
        newName: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanNew = newName.trim().replace('/', '_').replace('\\', '_')
            if (cleanNew.isEmpty()) throw IllegalArgumentException("Empty name")
            val oldPath = target.path.replace('\\', '/').trim('/')
            val parentPrefix = if (oldPath.contains('/')) "${oldPath.substringBeforeLast('/')}/" else ""
            val newPath = "$parentPrefix$cleanNew"
            val tempZip = File(context.cacheDir, "kanta_ren_${System.currentTimeMillis()}.zip")
            try {
                ZipOutputStream(BufferedOutputStream(FileOutputStream(tempZip), BUFFER_SIZE)).use { zos ->
                    ZipInputStream(BufferedInputStream(openZipInput(zipItem), BUFFER_SIZE)).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            coroutineContext.ensureActive()
                            val norm = entry.name.replace('\\', '/')
                            val normTrim = norm.trim('/')
                            val updatedName = when {
                                !target.isDirectory && normTrim == oldPath -> newPath
                                target.isDirectory && normTrim == oldPath -> "$newPath/"
                                target.isDirectory && normTrim.startsWith("$oldPath/") -> {
                                    val rest = normTrim.removePrefix("$oldPath/")
                                    if (entry.isDirectory) "$newPath/$rest/" else "$newPath/$rest"
                                }
                                else -> entry.name
                            }
                            val copied = ZipEntry(updatedName).apply { time = entry.time }
                            zos.putNextEntry(copied)
                            if (!entry.isDirectory) {
                                copyZipEntryStream(zis, zos)
                            }
                            zos.closeEntry()
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }
                writeTempZipBackToTarget(tempZip, zipItem)
            } finally {
                tempZip.delete()
            }
        }
    }

    suspend fun testArchiveIntegrity(
        zipItem: FileItem,
        onProgress: (FileOperationProgress) -> Unit
    ): Result<ArchiveTestResult> = withContext(Dispatchers.IO) {
        runCatching {
            var count = 0
            var totalBytes = 0L
            val buffer = ByteArray(BUFFER_SIZE)
            ZipInputStream(BufferedInputStream(openZipInput(zipItem), BUFFER_SIZE)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    coroutineContext.ensureActive()
                    val crc = CRC32()
                    var read: Int
                    while (zis.read(buffer).also { read = it } != -1) {
                        coroutineContext.ensureActive()
                        crc.update(buffer, 0, read)
                        totalBytes += read
                    }
                    if (!entry.isDirectory && entry.crc >= 0L && crc.value != entry.crc) {
                        throw IllegalStateException("CRC mismatch on ${entry.name}")
                    }
                    count++
                    onProgress(
                        FileOperationProgress(
                            opType = FileOpType.EXTRACT,
                            currentFileName = entry.name.substringAfterLast('/'),
                            processedCount = count,
                            isIndeterminate = true
                        )
                    )
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            ArchiveTestResult(verifiedEntries = count, totalUncompressedBytes = totalBytes)
        }
    }

    private fun writeTempZipBackToTarget(tempZip: File, zipItem: FileItem) {
        if (zipItem.safUri != null) {
            val out = context.contentResolver.openOutputStream(zipItem.safUri, "wt")
                ?: context.contentResolver.openOutputStream(zipItem.safUri)
                ?: throw IllegalStateException("Cannot write updated archive")
            out.use { dst ->
                FileInputStream(tempZip).use { src -> src.copyTo(dst, BUFFER_SIZE) }
            }
        } else {
            val targetFile = File(zipItem.path)
            FileOutputStream(targetFile, false).use { dst ->
                FileInputStream(tempZip).use { src -> src.copyTo(dst, BUFFER_SIZE) }
            }
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

    private fun openZipInput(zipItem: FileItem): InputStream {
        return if (zipItem.safUri != null) {
            context.contentResolver.openInputStream(zipItem.safUri)
                ?: throw IllegalStateException("تعذر فتح ملف الأرشيف")
        } else {
            FileInputStream(File(zipItem.path))
        }
    }

    private suspend fun copyZipEntryStream(zis: ZipInputStream, out: OutputStream): Long {
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
