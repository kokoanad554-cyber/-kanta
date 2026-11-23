package com.example.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.example.model.FileCategory
import com.example.model.FileItem
import com.example.model.FileOpType
import com.example.model.FileOperationProgress
import com.example.model.SortOption
import com.example.model.StorageRoot
import com.example.model.StorageRootType
import com.example.model.StorageStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.coroutineContext

class FileRepository(private val context: Context) {

    companion object {
        private const val STREAM_BUFFER_SIZE = 64 * 1024

        fun classifyFile(name: String, isDirectory: Boolean): Pair<String, FileCategory> {
            if (isDirectory) return "" to FileCategory.FOLDER
            val ext = name.substringAfterLast('.', "").lowercase()
            val category = when (ext) {
                "apk" -> FileCategory.APK
                "zip", "jar" -> FileCategory.ARCHIVE
                "html", "htm", "xhtml" -> FileCategory.HTML
                "txt", "md", "markdown", "log", "csv", "ini", "conf", "cfg", "properties", "yml", "yaml" -> FileCategory.TEXT
                "kt", "kts", "java", "xml", "json", "js", "ts", "css", "py", "c", "cpp", "h", "hpp", "sh", "sql", "gradle" -> FileCategory.CODE
                "pdf" -> FileCategory.PDF
                "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "svg" -> FileCategory.IMAGE
                "mp3", "wav", "ogg", "flac", "aac", "m4a", "opus" -> FileCategory.AUDIO
                "mp4", "mkv", "webm", "avi", "mov", "3gp" -> FileCategory.VIDEO
                else -> FileCategory.OTHER
            }
            return ext to category
        }

        fun guessMimeType(name: String): String {
            val ext = name.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "apk" -> "application/vnd.android.package-archive"
                "html", "htm" -> "text/html"
                "txt", "log", "md", "kt", "java", "json", "xml", "css", "js" -> "text/plain"
                "pdf" -> "application/pdf"
                "zip" -> "application/zip"
                else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
            }
        }
    }

    fun hasStoragePermission(): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }.getOrDefault(false)

    fun getInternalRootPath(): String = runCatching {
        Environment.getExternalStorageDirectory()?.absolutePath
    }.getOrNull() ?: "/storage/emulated/0"

    suspend fun getStorageStats(): StorageStats = withContext(Dispatchers.IO) {
        runCatching {
            val stat = StatFs(getInternalRootPath())
            val total = stat.totalBytes
            val free = stat.availableBytes
            StorageStats(totalBytes = total, freeBytes = free)
        }.getOrDefault(StorageStats(0L, 0L))
    }

    suspend fun getStorageRoots(safUris: List<String>, showSystemPaths: Boolean): List<StorageRoot> =
        withContext(Dispatchers.IO) {
            val roots = mutableListOf<StorageRoot>()
            val internalPath = getInternalRootPath()
            roots += StorageRoot(
                id = "internal",
                title = "الذاكرة الداخلية",
                path = internalPath,
                type = StorageRootType.INTERNAL
            )

            runCatching {
                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloads != null && downloads.exists()) {
                    roots += StorageRoot(
                        id = "downloads",
                        title = "التنزيلات",
                        path = downloads.absolutePath,
                        type = StorageRootType.DOWNLOADS
                    )
                }
            }

            runCatching {
                val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                if (docs != null && docs.exists()) {
                    roots += StorageRoot(
                        id = "documents",
                        title = "المستندات",
                        path = docs.absolutePath,
                        type = StorageRootType.DOCUMENTS
                    )
                }
            }

            // Detect removable USB drives / external SD cards (essential on Android TV)
            runCatching {
                val addedPaths = HashSet<String>().apply { add(internalPath) }
                val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
                if (storageManager != null) {
                    storageManager.storageVolumes.forEachIndexed { idx, volume ->
                        if (volume.isRemovable && volume.state == Environment.MEDIA_MOUNTED) {
                            val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                volume.directory
                            } else {
                                null
                            }
                            if (dir != null && dir.exists() && dir.canRead() && addedPaths.add(dir.absolutePath)) {
                                roots += StorageRoot(
                                    id = "usb_vol_$idx",
                                    title = volume.getDescription(context)?.takeIf { it.isNotBlank() } ?: dir.name,
                                    path = dir.absolutePath,
                                    type = StorageRootType.EXTERNAL_USB
                                )
                            }
                        }
                    }
                }
                // Fallback mount scan for older TV firmware under /storage/*
                val storageDir = File("/storage")
                storageDir.listFiles()?.forEachIndexed { idx, entry ->
                    if (entry.isDirectory &&
                        entry.name != "emulated" &&
                        entry.name != "self" &&
                        entry.canRead() &&
                        addedPaths.add(entry.absolutePath)
                    ) {
                        roots += StorageRoot(
                            id = "usb_mnt_$idx",
                            title = entry.name,
                            path = entry.absolutePath,
                            type = StorageRootType.EXTERNAL_USB
                        )
                    }
                }
            }

            if (showSystemPaths) {
                roots += StorageRoot(
                    id = "system",
                    title = "مسارات النظام",
                    path = "/system",
                    type = StorageRootType.SYSTEM
                )
                roots += StorageRoot(
                    id = "root",
                    title = "جذر النظام (/)",
                    path = "/",
                    type = StorageRootType.SYSTEM
                )
            }

            safUris.forEachIndexed { index, uriStr ->
                runCatching {
                    val uri = Uri.parse(uriStr)
                    val doc = DocumentFile.fromTreeUri(context, uri)
                    if (doc != null && doc.exists()) {
                        roots += StorageRoot(
                            id = "saf_$index",
                            title = doc.name ?: "مجلد خارجي",
                            path = uriStr,
                            type = StorageRootType.SAF_TREE,
                            safUri = uri
                        )
                    }
                }
            }
            roots
        }

    suspend fun listDirectory(
        path: String,
        safUri: Uri?,
        showHidden: Boolean,
        sortOption: SortOption
    ): Result<List<FileItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val rawItems = if (safUri != null) {
                val doc = DocumentFile.fromTreeUri(context, safUri)
                    ?: DocumentFile.fromSingleUri(context, safUri)
                    ?: throw IllegalStateException("تعذر الوصول إلى مجلد SAF")
                val children = doc.listFiles()
                children.mapNotNull { child ->
                    coroutineContext.ensureActive()
                    val name = child.name ?: return@mapNotNull null
                    if (!showHidden && name.startsWith(".")) return@mapNotNull null
                    val isDir = child.isDirectory
                    val (ext, cat) = classifyFile(name, isDir)
                    FileItem(
                        id = child.uri.toString(),
                        name = name,
                        path = child.uri.toString(),
                        isDirectory = isDir,
                        sizeBytes = if (isDir) 0L else child.length(),
                        lastModifiedMillis = child.lastModified(),
                        isHidden = name.startsWith("."),
                        canRead = child.canRead(),
                        canWrite = child.canWrite(),
                        extension = ext,
                        category = cat,
                        safUri = child.uri,
                        childCount = null
                    )
                }
            } else {
                val dir = File(path)
                if (!dir.exists() || !dir.isDirectory) {
                    throw IllegalStateException("المسار غير موجود أو ليس مجلداً")
                }
                val children = dir.listFiles()
                    ?: throw IllegalStateException("لا توجد صلاحية لقراءة محتوى هذا المسار")
                children.mapNotNull { file ->
                    coroutineContext.ensureActive()
                    val name = file.name
                    if (!showHidden && (file.isHidden || name.startsWith("."))) return@mapNotNull null
                    val isDir = file.isDirectory
                    val (ext, cat) = classifyFile(name, isDir)
                    FileItem(
                        id = file.absolutePath,
                        name = name,
                        path = file.absolutePath,
                        isDirectory = isDir,
                        sizeBytes = if (isDir) 0L else file.length(),
                        lastModifiedMillis = file.lastModified(),
                        isHidden = file.isHidden || name.startsWith("."),
                        canRead = file.canRead(),
                        canWrite = file.canWrite(),
                        extension = ext,
                        category = cat,
                        safUri = null,
                        childCount = null
                    )
                }
            }
            sortItems(rawItems, sortOption)
        }
    }

    suspend fun resolveSubfolderCounts(
        items: List<FileItem>,
        maxFolders: Int = 35
    ): Map<String, Int> = withContext(Dispatchers.IO) {
        val result = HashMap<String, Int>()
        var processed = 0
        for (item in items) {
            coroutineContext.ensureActive()
            if (!item.isDirectory || item.safUri != null) continue
            if (processed >= maxFolders) break
            processed++
            val count = countDirectoryChildrenFast(File(item.path))
            if (count != null) {
                result[item.id] = count
            }
        }
        result
    }

    private fun countDirectoryChildrenFast(dir: File): Int? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                var count = 0
                java.nio.file.Files.newDirectoryStream(dir.toPath()).use { stream ->
                    for (ignored in stream) {
                        count++
                        if (count >= 1000) break
                    }
                }
                count
            } else {
                dir.list()?.size
            }
        }.getOrNull()
    }

    fun sortItems(items: List<FileItem>, sortOption: SortOption): List<FileItem> {
        val comparator = when (sortOption) {
            SortOption.NAME_ASC -> compareBy<FileItem> { !it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortOption.NAME_DESC -> compareBy<FileItem> { !it.isDirectory }.thenByDescending(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortOption.SIZE_DESC -> compareBy<FileItem> { !it.isDirectory }.thenByDescending { it.sizeBytes }
            SortOption.DATE_DESC -> compareBy<FileItem> { !it.isDirectory }.thenByDescending { it.lastModifiedMillis }
            SortOption.TYPE -> compareBy<FileItem> { !it.isDirectory }.thenBy { it.extension }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        }
        return items.sortedWith(comparator)
    }

    suspend fun createFolder(parentPath: String, parentSafUri: Uri?, folderName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val clean = folderName.trim()
                require(clean.isNotEmpty() && !clean.contains('/')) { "اسم المجلد غير صالح" }
                if (parentSafUri != null) {
                    val parentDoc = DocumentFile.fromTreeUri(context, parentSafUri)
                        ?: throw IllegalStateException("تعذر الوصول للمجلد")
                    if (parentDoc.findFile(clean) != null) throw IllegalStateException("يوجد عنصر بنفس الاسم")
                    parentDoc.createDirectory(clean) ?: throw IllegalStateException("فشل إنشاء المجلد")
                } else {
                    val newDir = File(parentPath, clean)
                    if (newDir.exists()) throw IllegalStateException("يوجد عنصر بنفس الاسم")
                    if (!newDir.mkdirs()) throw IllegalStateException("تعذر إنشاء المجلد (تأكد من الصلاحيات)")
                }
                Unit
            }
        }

    suspend fun createFile(parentPath: String, parentSafUri: Uri?, fileName: String): Result<FileItem> =
        withContext(Dispatchers.IO) {
            runCatching {
                val clean = fileName.trim()
                require(clean.isNotEmpty() && !clean.contains('/')) { "اسم الملف غير صالح" }
                if (parentSafUri != null) {
                    val parentDoc = DocumentFile.fromTreeUri(context, parentSafUri)
                        ?: throw IllegalStateException("تعذر الوصول للمجلد")
                    if (parentDoc.findFile(clean) != null) throw IllegalStateException("يوجد ملف بنفس الاسم")
                    val mime = guessMimeType(clean)
                    val created = parentDoc.createFile(mime, clean)
                        ?: throw IllegalStateException("فشل إنشاء الملف")
                    val (ext, cat) = classifyFile(clean, false)
                    FileItem(
                        id = created.uri.toString(),
                        name = clean,
                        path = created.uri.toString(),
                        isDirectory = false,
                        sizeBytes = 0L,
                        lastModifiedMillis = created.lastModified(),
                        isHidden = clean.startsWith("."),
                        canRead = true,
                        canWrite = true,
                        extension = ext,
                        category = cat,
                        safUri = created.uri
                    )
                } else {
                    val file = File(parentPath, clean)
                    if (file.exists()) throw IllegalStateException("يوجد ملف بنفس الاسم")
                    if (!file.createNewFile()) throw IllegalStateException("تعذر إنشاء الملف")
                    if (clean.endsWith(".html", ignoreCase = true) || clean.endsWith(".htm", ignoreCase = true)) {
                        file.writeText(
                            "<!DOCTYPE html>\n<html lang=\"ar\" dir=\"rtl\">\n<head>\n  <meta charset=\"UTF-8\">\n  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n  <title>صفحة جديدة</title>\n</head>\n<body>\n  <h1>مرحباً بك</h1>\n</body>\n</html>\n"
                        )
                    }
                    val (ext, cat) = classifyFile(clean, false)
                    FileItem(
                        id = file.absolutePath,
                        name = clean,
                        path = file.absolutePath,
                        isDirectory = false,
                        sizeBytes = file.length(),
                        lastModifiedMillis = file.lastModified(),
                        isHidden = file.isHidden || clean.startsWith("."),
                        canRead = file.canRead(),
                        canWrite = file.canWrite(),
                        extension = ext,
                        category = cat
                    )
                }
            }
        }

    suspend fun renameItem(item: FileItem, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = newName.trim()
            require(clean.isNotEmpty() && !clean.contains('/')) { "الاسم غير صالح" }
            if (item.safUri != null) {
                val doc = DocumentFile.fromSingleUri(context, item.safUri)
                    ?: throw IllegalStateException("تعذر الوصول للعنصر")
                if (!doc.renameTo(clean)) throw IllegalStateException("فشل إعادة التسمية")
            } else {
                val src = File(item.path)
                val dest = File(src.parentFile, clean)
                if (dest.exists()) throw IllegalStateException("يوجد عنصر بنفس الاسم")
                if (!src.renameTo(dest)) throw IllegalStateException("تعذر إعادة التسمية")
            }
            Unit
        }
    }

    suspend fun deleteItems(
        items: List<FileItem>,
        onProgress: (FileOperationProgress) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val total = items.size.coerceAtLeast(1)
            items.forEachIndexed { index, item ->
                coroutineContext.ensureActive()
                onProgress(
                    FileOperationProgress(
                        opType = FileOpType.DELETE,
                        currentFileName = item.name,
                        processedCount = index,
                        totalCount = total
                    )
                )
                if (item.safUri != null) {
                    val doc = DocumentFile.fromTreeUri(context, item.safUri)
                        ?: DocumentFile.fromSingleUri(context, item.safUri)
                    if (doc == null || !doc.delete()) {
                        throw IllegalStateException("تعذر حذف ${item.name}")
                    }
                } else {
                    val file = File(item.path)
                    if (!file.deleteRecursively()) {
                        throw IllegalStateException("تعذر حذف ${item.name}")
                    }
                }
            }
            onProgress(
                FileOperationProgress(
                    opType = FileOpType.DELETE,
                    currentFileName = "اكتمل الحذف",
                    processedCount = total,
                    totalCount = total
                )
            )
        }
    }

    suspend fun copyOrMoveItems(
        items: List<FileItem>,
        destPath: String,
        destSafUri: Uri?,
        isMove: Boolean,
        onProgress: (FileOperationProgress) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val opType = if (isMove) FileOpType.MOVE else FileOpType.COPY
            val totalBytes = calculateTotalBytes(items)
            var processedBytes = 0L
            val totalItems = items.size

            items.forEachIndexed { idx, item ->
                coroutineContext.ensureActive()
                if (destSafUri == null && item.safUri == null) {
                    val srcFile = File(item.path)
                    val destDir = File(destPath)
                    if (isMove && srcFile.parentFile?.absolutePath == destDir.absolutePath) {
                        return@forEachIndexed
                    }
                    if (srcFile.isDirectory && destDir.canonicalPath.startsWith(srcFile.canonicalPath + File.separator)) {
                        throw IllegalStateException("لا يمكن نسخ المجلد إلى داخل نفسه")
                    }
                    val targetFile = resolveNonCollidingFile(destDir, item.name)
                    if (isMove && srcFile.renameTo(targetFile)) {
                        processedBytes += item.sizeBytes
                        onProgress(
                            FileOperationProgress(
                                opType = opType,
                                currentFileName = item.name,
                                processedBytes = processedBytes,
                                totalBytes = totalBytes,
                                processedCount = idx + 1,
                                totalCount = totalItems
                            )
                        )
                    } else {
                        copyFileRecursive(srcFile, targetFile) { delta, currentName ->
                            processedBytes += delta
                            onProgress(
                                FileOperationProgress(
                                    opType = opType,
                                    currentFileName = currentName,
                                    processedBytes = processedBytes,
                                    totalBytes = totalBytes,
                                    processedCount = idx,
                                    totalCount = totalItems
                                )
                            )
                        }
                        if (isMove) {
                            srcFile.deleteRecursively()
                        }
                    }
                } else {
                    copyMixedItem(item, destPath, destSafUri) { delta, currentName ->
                        processedBytes += delta
                        onProgress(
                            FileOperationProgress(
                                opType = opType,
                                currentFileName = currentName,
                                processedBytes = processedBytes,
                                totalBytes = totalBytes,
                                processedCount = idx,
                                totalCount = totalItems
                            )
                        )
                    }
                    if (isMove) {
                        if (item.safUri != null) {
                            DocumentFile.fromSingleUri(context, item.safUri)?.delete()
                        } else {
                            File(item.path).deleteRecursively()
                        }
                    }
                }
            }
            Unit
        }
    }

    private fun calculateTotalBytes(items: List<FileItem>): Long {
        var total = 0L
        for (item in items) {
            if (item.safUri == null) {
                val f = File(item.path)
                total += if (f.isDirectory) dirSize(f) else f.length()
            } else {
                total += item.sizeBytes
            }
        }
        return total.coerceAtLeast(1L)
    }

    private fun dirSize(dir: File): Long {
        var sum = 0L
        dir.listFiles()?.forEach { child ->
            sum += if (child.isDirectory) dirSize(child) else child.length()
        }
        return sum
    }

    private fun resolveNonCollidingFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var counter = 1
        while (candidate.exists()) {
            val newName = if (ext.isNotEmpty() && name.contains('.')) {
                "${base}_($counter).$ext"
            } else {
                "${name}_($counter)"
            }
            candidate = File(dir, newName)
            counter++
        }
        return candidate
    }

    private suspend fun copyFileRecursive(
        src: File,
        dest: File,
        onBytes: (Long, String) -> Unit
    ) {
        coroutineContext.ensureActive()
        if (src.isDirectory) {
            if (!dest.exists()) dest.mkdirs()
            src.listFiles()?.forEach { child ->
                copyFileRecursive(child, File(dest, child.name), onBytes)
            }
        } else {
            dest.parentFile?.mkdirs()
            FileInputStream(src).use { input ->
                FileOutputStream(dest).use { output ->
                    streamCopy(input, output, src.name, onBytes)
                }
            }
        }
    }

    private suspend fun copyMixedItem(
        item: FileItem,
        destPath: String,
        destSafUri: Uri?,
        onBytes: (Long, String) -> Unit
    ) {
        coroutineContext.ensureActive()
        if (item.isDirectory) {
            if (destSafUri != null) {
                val destDoc = DocumentFile.fromTreeUri(context, destSafUri) ?: return
                val createdDir = destDoc.findFile(item.name) ?: destDoc.createDirectory(item.name) ?: return
                val subItems = listDirectory(item.path, item.safUri, true, SortOption.NAME_ASC).getOrDefault(emptyList())
                for (child in subItems) {
                    copyMixedItem(child, createdDir.uri.toString(), createdDir.uri, onBytes)
                }
            } else {
                val targetDir = resolveNonCollidingFile(File(destPath), item.name)
                targetDir.mkdirs()
                val subItems = listDirectory(item.path, item.safUri, true, SortOption.NAME_ASC).getOrDefault(emptyList())
                for (child in subItems) {
                    copyMixedItem(child, targetDir.absolutePath, null, onBytes)
                }
            }
        } else {
            val input: InputStream = if (item.safUri != null) {
                context.contentResolver.openInputStream(item.safUri)
                    ?: throw IllegalStateException("تعذر فتح ${item.name}")
            } else {
                FileInputStream(File(item.path))
            }

            val output: OutputStream = if (destSafUri != null) {
                val destDoc = DocumentFile.fromTreeUri(context, destSafUri)
                    ?: throw IllegalStateException("تعذر الوصول إلى المجلد الهدف")
                val targetDoc = destDoc.createFile(guessMimeType(item.name), item.name)
                    ?: throw IllegalStateException("تعذر إنشاء ${item.name}")
                context.contentResolver.openOutputStream(targetDoc.uri)
                    ?: throw IllegalStateException("تعذر الكتابة إلى ${item.name}")
            } else {
                val target = resolveNonCollidingFile(File(destPath), item.name)
                FileOutputStream(target)
            }

            input.use { inp ->
                output.use { out ->
                    streamCopy(inp, out, item.name, onBytes)
                }
            }
        }
    }

    private suspend fun streamCopy(
        input: InputStream,
        output: OutputStream,
        fileName: String,
        onBytes: (Long, String) -> Unit
    ) {
        val buffer = ByteArray(STREAM_BUFFER_SIZE)
        var bytesRead: Int
        var pendingReport = 0L
        while (input.read(buffer).also { bytesRead = it } != -1) {
            coroutineContext.ensureActive()
            output.write(buffer, 0, bytesRead)
            pendingReport += bytesRead
            if (pendingReport >= 256 * 1024) {
                onBytes(pendingReport, fileName)
                pendingReport = 0L
            }
        }
        if (pendingReport > 0L) {
            onBytes(pendingReport, fileName)
        }
    }

    suspend fun readTextDocument(item: FileItem, maxChars: Int = 500_000): Result<Pair<String, Boolean>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val input = if (item.safUri != null) {
                    context.contentResolver.openInputStream(item.safUri)
                        ?: throw IllegalStateException("تعذر فتح الملف")
                } else {
                    FileInputStream(File(item.path))
                }
                input.bufferedReader(Charsets.UTF_8).use { reader ->
                    val buffer = CharArray(8192)
                    val sb = StringBuilder()
                    var truncated = false
                    while (true) {
                        coroutineContext.ensureActive()
                        val remaining = maxChars - sb.length
                        if (remaining <= 0) {
                            if (reader.read() != -1) truncated = true
                            break
                        }
                        val readCount = reader.read(buffer, 0, minOf(buffer.size, remaining))
                        if (readCount == -1) break
                        sb.append(buffer, 0, readCount)
                    }
                    sb.toString() to truncated
                }
            }
        }

    suspend fun writeTextSafely(item: FileItem, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (item.safUri != null) {
                context.contentResolver.openOutputStream(item.safUri, "wt")?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                    out.flush()
                } ?: throw IllegalStateException("تعذر فتح الملف للحفظ")
            } else {
                val target = File(item.path)
                val parent = target.parentFile
                val temp = File(parent, ".${target.name}.tmp_${System.currentTimeMillis()}")
                temp.writeText(content, Charsets.UTF_8)
                if (target.exists() && !target.delete()) {
                    // Fallback direct overwrite if rename cannot replace
                    target.writeText(content, Charsets.UTF_8)
                    temp.delete()
                } else if (!temp.renameTo(target)) {
                    target.writeText(content, Charsets.UTF_8)
                    temp.delete()
                }
            }
            Unit
        }
    }

    fun getFileProviderUri(item: FileItem): Uri {
        return item.safUri ?: FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            File(item.path)
        )
    }

    fun openExternally(item: FileItem): Result<Unit> = runCatching {
        val uri = getFileProviderUri(item)
        val mime = guessMimeType(item.name)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "فتح باستخدام").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
