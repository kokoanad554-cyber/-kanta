package com.example

import com.example.data.FileRepository
import com.example.data.FileScanner
import com.example.model.FileCategory
import com.example.util.FormatUtils
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FileEngineUnitTest {

    @Test
    fun `classifyFile identifies extensions accurately`() {
        assertEquals("apk" to FileCategory.APK, FileRepository.classifyFile("app.APK", false))
        assertEquals("html" to FileCategory.HTML, FileRepository.classifyFile("index.html", false))
        assertEquals("zip" to FileCategory.ARCHIVE, FileRepository.classifyFile("bundle.zip", false))
        assertEquals("pdf" to FileCategory.PDF, FileRepository.classifyFile("book.pdf", false))
        assertEquals("txt" to FileCategory.TEXT, FileRepository.classifyFile("notes.txt", false))
        assertEquals("" to FileCategory.FOLDER, FileRepository.classifyFile("docs", true))
    }

    @Test
    fun `formatBytes formats Arabic size units cleanly`() {
        assertEquals("0 ب", FormatUtils.formatBytes(0L))
        assertEquals("512 ب", FormatUtils.formatBytes(512L))
        assertEquals("1.0 ك.ب", FormatUtils.formatBytes(1024L))
        assertEquals("2.0 م.ب", FormatUtils.formatBytes(2L * 1024 * 1024))
    }

    @Test
    fun `fileScanner finds matching extensions and skips hidden by default`() = runTest {
        val sandbox = Files.createTempDirectory("kanta_scan_test").toFile()
        try {
            val sub = File(sandbox, "web").apply { mkdirs() }
            File(sub, "page.html").writeText("<h1>Hi</h1>")
            File(sub, "notes.txt").writeText("hello")
            val hiddenDir = File(sandbox, ".hidden").apply { mkdirs() }
            File(hiddenDir, "secret.html").writeText("<p>secret</p>")

            val scanner = FileScanner()
            val visibleOnly = scanner.scanByExtensions(
                rootPaths = listOf(sandbox.absolutePath),
                extensions = setOf("html"),
                includeHidden = false
            ).last()
            assertEquals(1, visibleOnly.matchedItems.size)
            assertEquals("page.html", visibleOnly.matchedItems.first().name)

            val withHidden = scanner.scanByExtensions(
                rootPaths = listOf(sandbox.absolutePath),
                extensions = setOf("html"),
                includeHidden = true
            ).last()
            assertEquals(2, withHidden.matchedItems.size)
        } finally {
            sandbox.deleteRecursively()
        }
    }

    @Test
    fun `zip creation produces readable archive`() {
        val sandbox = Files.createTempDirectory("kanta_zip_test").toFile()
        try {
            val zipFile = File(sandbox, "test.zip")
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                zos.putNextEntry(ZipEntry("hello.txt"))
                zos.write("Kanta".toByteArray())
                zos.closeEntry()
            }
            assertTrue(zipFile.exists() && zipFile.length() > 0L)
        } finally {
            sandbox.deleteRecursively()
        }
    }
}
