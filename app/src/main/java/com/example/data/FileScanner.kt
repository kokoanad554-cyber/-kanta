package com.example.data

import com.example.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import kotlin.coroutines.coroutineContext

data class ScanUpdate(
    val scannedFilesCount: Int,
    val currentDirectory: String,
    val matchedItems: List<FileItem>,
    val isFinished: Boolean
)

class FileScanner {

    fun scanByExtensions(
        rootPaths: List<String>,
        extensions: Set<String>,
        includeHidden: Boolean
    ): Flow<ScanUpdate> = flow {
        val normalizedExts = extensions.map { it.trim().lowercase().removePrefix(".") }.filter { it.isNotEmpty() }.toSet()
        val matches = mutableListOf<FileItem>()
        val visitedCanonical = HashSet<String>()
        var scannedCount = 0
        var lastEmitTime = 0L

        val stack = ArrayDeque<File>()
        rootPaths.forEach { path ->
            val f = File(path)
            if (f.exists() && f.isDirectory) stack.addLast(f)
        }

        while (stack.isNotEmpty()) {
            coroutineContext.ensureActive()
            val dir = stack.removeLast()
            val canonical = runCatching { dir.canonicalPath }.getOrNull() ?: continue
            if (!visitedCanonical.add(canonical)) continue

            // Avoid virtual kernel proc/sys loops if root path was ever scanned
            if (canonical == "/proc" || canonical == "/sys" || canonical == "/dev") continue

            val children = dir.listFiles() ?: continue
            for (child in children) {
                coroutineContext.ensureActive()
                val name = child.name
                if (!includeHidden && (child.isHidden || name.startsWith("."))) continue

                if (child.isDirectory) {
                    stack.addLast(child)
                } else {
                    scannedCount++
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (normalizedExts.isEmpty() || ext in normalizedExts) {
                        val (classifiedExt, category) = FileRepository.classifyFile(name, false)
                        matches += FileItem(
                            id = child.absolutePath,
                            name = name,
                            path = child.absolutePath,
                            isDirectory = false,
                            sizeBytes = child.length(),
                            lastModifiedMillis = child.lastModified(),
                            isHidden = child.isHidden || name.startsWith("."),
                            canRead = child.canRead(),
                            canWrite = child.canWrite(),
                            extension = classifiedExt,
                            category = category
                        )
                    }
                }
            }

            val now = System.currentTimeMillis()
            if (now - lastEmitTime > 180L) {
                lastEmitTime = now
                emit(
                    ScanUpdate(
                        scannedFilesCount = scannedCount,
                        currentDirectory = dir.name.ifEmpty { dir.absolutePath },
                        matchedItems = matches.toList(),
                        isFinished = false
                    )
                )
            }
        }

        emit(
            ScanUpdate(
                scannedFilesCount = scannedCount,
                currentDirectory = "اكتمل الفحص",
                matchedItems = matches.sortedByDescending { it.lastModifiedMillis },
                isFinished = true
            )
        )
    }.flowOn(Dispatchers.IO)
}
