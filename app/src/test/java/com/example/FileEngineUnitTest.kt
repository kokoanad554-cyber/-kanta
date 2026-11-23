package com.example

import com.example.data.ArchiveManager
import com.example.data.FileProTools
import com.example.data.FileRepository
import com.example.data.FileScanner
import com.example.localization.stringsForLanguage
import com.example.model.AppColorPalette
import com.example.model.AppLanguage
import com.example.model.ArchiveEntryInfo
import com.example.model.BatchRenameRule
import com.example.model.CompressionLevel
import com.example.model.FavoriteFolder
import com.example.model.FileCategory
import com.example.model.FileItem
import com.example.model.FileOpType
import com.example.model.FontScaleOption
import com.example.model.ListDensity
import com.example.model.SortOption
import com.example.model.StorageRootType
import com.example.model.UserPreferences
import com.example.ui.viewmodel.ScanScope
import com.example.util.FormatUtils
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class FileEngineUnitTest {

    @Test
    fun `classifyFile identifies all major extensions accurately`() {
        assertEquals("apk" to FileCategory.APK, FileRepository.classifyFile("app.APK", false))
        assertEquals("html" to FileCategory.HTML, FileRepository.classifyFile("index.html", false))
        assertEquals("htm" to FileCategory.HTML, FileRepository.classifyFile("page.HTM", false))
        assertEquals("zip" to FileCategory.ARCHIVE, FileRepository.classifyFile("bundle.zip", false))
        assertEquals("jar" to FileCategory.ARCHIVE, FileRepository.classifyFile("lib.jar", false))
        assertEquals("pdf" to FileCategory.PDF, FileRepository.classifyFile("book.pdf", false))
        assertEquals("txt" to FileCategory.TEXT, FileRepository.classifyFile("notes.txt", false))
        assertEquals("kt" to FileCategory.CODE, FileRepository.classifyFile("Main.kt", false))
        assertEquals("json" to FileCategory.CODE, FileRepository.classifyFile("config.json", false))
        assertEquals("jpg" to FileCategory.IMAGE, FileRepository.classifyFile("photo.jpg", false))
        assertEquals("mp3" to FileCategory.AUDIO, FileRepository.classifyFile("track.mp3", false))
        assertEquals("mp4" to FileCategory.VIDEO, FileRepository.classifyFile("clip.mp4", false))
        assertEquals("bin" to FileCategory.OTHER, FileRepository.classifyFile("data.bin", false))
        assertEquals("" to FileCategory.FOLDER, FileRepository.classifyFile("docs.zip", true))
    }

    @Test
    fun `guessMimeType returns valid mime types for key formats`() {
        assertEquals("application/vnd.android.package-archive", FileRepository.guessMimeType("release.apk"))
        assertEquals("text/html", FileRepository.guessMimeType("index.html"))
        assertEquals("text/plain", FileRepository.guessMimeType("readme.txt"))
        assertEquals("application/pdf", FileRepository.guessMimeType("doc.pdf"))
        assertEquals("application/zip", FileRepository.guessMimeType("pack.zip"))
    }

    @Test
    fun `formatBytes and formatDate handle zero and positive values cleanly`() {
        assertEquals("0 ب", FormatUtils.formatBytes(0L))
        assertEquals("512 ب", FormatUtils.formatBytes(512L))
        assertEquals("1.0 ك.ب", FormatUtils.formatBytes(1024L))
        assertEquals("2.0 م.ب", FormatUtils.formatBytes(2L * 1024 * 1024))
        assertEquals("غير معروف", FormatUtils.formatDate(0L))
        assertEquals("Unknown", FormatUtils.formatDate(-5L, "Unknown"))
        assertTrue(FormatUtils.formatDate(1_700_000_000_000L).contains("/"))
    }

    @Test
    fun `formatDate is thread-safe under concurrent multi-thread formatting`() {
        val pool = Executors.newFixedThreadPool(8)
        try {
            val timestamp = 1_700_000_000_000L
            val expected = FormatUtils.formatDate(timestamp)
            val tasks = (1..200).map { idx ->
                Callable {
                    val res = FormatUtils.formatDate(timestamp + idx * 1_000L)
                    res.isNotBlank() && res.contains("/") && res.length == expected.length
                }
            }
            val futures = pool.invokeAll(tasks)
            assertTrue(futures.all { it.get(5, TimeUnit.SECONDS) })
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `trilingual AppStrings covers all enums without emojis or empty labels`() {
        for (lang in AppLanguage.entries) {
            val s = stringsForLanguage(lang)
            val coreStrings = listOf(
                s.appTitle,
                s.tabFiles,
                s.tabScanner,
                s.tabSettings,
                s.creatorSectionTitle,
                s.creatorSubtitle,
                s.creatorHandleLabel,
                s.settingsExpressiveTitle,
                s.scannerStart,
                s.scannerStop,
                s.createFolderTitle,
                s.createFileTitle,
                s.renameTitle,
                s.confirmDeleteTitle,
                s.rootExternalUsb,
                s.testerBadgeLabel,
                s.browseAsFolder,
                s.testArchive,
                s.batchRename,
                s.calculateHashes,
                s.favoriteAdd,
                s.openCodeEditor,
                s.extractSelected
            )
            coreStrings.forEach { text ->
                assertTrue("Empty string in $lang", text.isNotBlank())
                assertFalse("Emoji/surrogate found in $lang: $text", text.any { Character.isSurrogate(it) })
            }
            SortOption.entries.forEach { assertTrue(s.sortLabel(it).isNotBlank()) }
            FileCategory.entries.forEach { assertTrue(s.categoryLabel(it).isNotBlank()) }
            FileOpType.entries.forEach { assertTrue(s.opTitle(it).isNotBlank()) }
            ScanScope.entries.forEach { assertTrue(s.scopeLabel(it).isNotBlank()) }
            AppColorPalette.entries.forEach { assertTrue(s.paletteLabel(it).isNotBlank()) }
            FontScaleOption.entries.forEach { assertTrue(s.fontScaleLabel(it).isNotBlank()) }
            CompressionLevel.entries.forEach { assertTrue(s.compressionLabel(it).isNotBlank()) }
            assertTrue(StorageRootType.entries.contains(StorageRootType.EXTERNAL_USB))
        }

        val ar = stringsForLanguage(AppLanguage.ARABIC)
        val en = stringsForLanguage(AppLanguage.ENGLISH)
        val zh = stringsForLanguage(AppLanguage.CHINESE)

        assertEquals("Kanta ملفات", ar.appTitle)
        assertEquals("Kanta Files", en.appTitle)
        assertEquals("Kanta 文件", zh.appTitle)

        assertEquals("1.0 ك.ب", ar.formatBytes(1024L))
        assertEquals("1.0 KB", en.formatBytes(1024L))
        assertEquals("1.0 KB", zh.formatBytes(1024L))
    }

    @Test
    fun `default user preferences use ak_v911 creator handle, amged_203 tester, and expressive 2D defaults`() {
        val defaults = UserPreferences()
        assertEquals("ak.v911", defaults.creatorHandle)
        assertEquals("amged_203", defaults.testerHandle)
        assertEquals(AppLanguage.ARABIC, defaults.language)
        assertEquals(ListDensity.COMFORTABLE, defaults.listDensity)
        assertTrue(defaults.expressiveAnimations)
        assertTrue(defaults.hapticFeedback)
    }

    @Test
    fun `directory-first sorting works across all sort options`() {
        val items = listOf(
            FileItem("1", "b_notes.txt", "/b_notes.txt", false, 500, 100, false, true, true, "txt", FileCategory.TEXT),
            FileItem("2", "a_archive.zip", "/a_archive.zip", false, 2000, 300, false, true, true, "zip", FileCategory.ARCHIVE),
            FileItem("3", "z_folder", "/z_folder", true, 0, 50, false, true, true, "", FileCategory.FOLDER)
        )

        fun sortBy(option: SortOption): List<FileItem> {
            val comparator = when (option) {
                SortOption.NAME_ASC -> compareBy<FileItem> { !it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                SortOption.NAME_DESC -> compareBy<FileItem> { !it.isDirectory }.thenByDescending(String.CASE_INSENSITIVE_ORDER) { it.name }
                SortOption.SIZE_DESC -> compareBy<FileItem> { !it.isDirectory }.thenByDescending { it.sizeBytes }
                SortOption.DATE_DESC -> compareBy<FileItem> { !it.isDirectory }.thenByDescending { it.lastModifiedMillis }
                SortOption.TYPE -> compareBy<FileItem> { !it.isDirectory }.thenBy { it.extension }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            }
            return items.sortedWith(comparator)
        }

        SortOption.entries.forEach { opt ->
            val sorted = sortBy(opt)
            assertTrue("Folder must remain first for $opt", sorted.first().isDirectory)
        }
        assertEquals("a_archive.zip", sortBy(SortOption.NAME_ASC)[1].name)
        assertEquals("b_notes.txt", sortBy(SortOption.NAME_DESC)[1].name)
        assertEquals("a_archive.zip", sortBy(SortOption.SIZE_DESC)[1].name)
        assertEquals("a_archive.zip", sortBy(SortOption.DATE_DESC)[1].name)
        assertEquals("b_notes.txt", sortBy(SortOption.TYPE)[1].name)
    }

    @Test
    fun `fileScanner finds multi-extension matches and respects hidden toggle`() = runTest {
        val sandbox = Files.createTempDirectory("kanta_scan_test").toFile()
        try {
            val sub = File(sandbox, "web").apply { mkdirs() }
            File(sub, "page.html").writeText("<h1>Hi</h1>")
            File(sub, "notes.txt").writeText("hello")
            File(sub, "image.png").writeText("png")
            val hiddenDir = File(sandbox, ".hidden").apply { mkdirs() }
            File(hiddenDir, "secret.HTML").writeText("<p>secret</p>")

            val scanner = FileScanner()
            val visibleOnly = scanner.scanByExtensions(
                rootPaths = listOf(sandbox.absolutePath),
                extensions = setOf("html", "txt"),
                includeHidden = false
            ).last()
            assertEquals(2, visibleOnly.matchedItems.size)
            assertTrue(visibleOnly.matchedItems.any { it.name == "page.html" })
            assertTrue(visibleOnly.matchedItems.any { it.name == "notes.txt" })

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
    fun `zip creation and roundtrip extraction preserve entries and block traversal`() {
        val sandbox = Files.createTempDirectory("kanta_zip_test").toFile()
        try {
            val zipFile = File(sandbox, "test.zip")
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                zos.putNextEntry(ZipEntry("folder/hello.txt"))
                zos.write("Kanta 2D Expressive".toByteArray())
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("../../evil.txt"))
                zos.write("blocked".toByteArray())
                zos.closeEntry()
            }
            assertTrue(zipFile.exists() && zipFile.length() > 0L)

            val outDir = File(sandbox, "out").apply { mkdirs() }
            val canonicalOut = outDir.canonicalPath
            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val dest = File(outDir, entry.name)
                    if (dest.canonicalPath.startsWith(canonicalOut + File.separator)) {
                        dest.parentFile?.mkdirs()
                        FileOutputStream(dest).use { zis.copyTo(it) }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            assertEquals("Kanta 2D Expressive", File(outDir, "folder/hello.txt").readText())
            assertFalse(File(sandbox, "evil.txt").exists())
        } finally {
            sandbox.deleteRecursively()
        }
    }

    @Test
    fun `batch rename planner produces conflict-free sequential and extension renames`() {
        val items = listOf(
            FileItem("1", "photo A.jpg", "/photo A.jpg", false, 100, 0, false, true, true, "jpg", FileCategory.IMAGE),
            FileItem("2", "photo B.jpg", "/photo B.jpg", false, 100, 0, false, true, true, "jpg", FileCategory.IMAGE),
            FileItem("3", "photo B.png", "/photo B.png", false, 100, 0, false, true, true, "png", FileCategory.IMAGE)
        )
        val rule = BatchRenameRule(
            findText = "photo",
            replaceText = "img",
            prefix = "v_",
            useNumbering = true,
            startNumber = 1,
            changeExtension = true,
            newExtension = "webp"
        )
        val plan = FileProTools.buildBatchRenamePlan(items, rule)
        assertEquals(3, plan.size)
        assertEquals("v_img A_01.webp", plan[0].second)
        assertEquals("v_img B_02.webp", plan[1].second)
        assertEquals("v_img B_03.webp", plan[2].second)
    }

    @Test
    fun `hash calculator and folder analyzer compute exact digests and recursive sizes`() = runTest {
        val sandbox = Files.createTempDirectory("kanta_pro_tools").toFile()
        try {
            val nested = File(sandbox, "sub").apply { mkdirs() }
            val f1 = File(sandbox, "hello.txt").apply { writeText("abc") }
            File(nested, "data.bin").writeBytes(ByteArray(128))

            val hashes = FileProTools.calculateHashesFromFile(f1).getOrThrow()
            assertEquals("900150983cd24fb0d6963f7d28e17f72", hashes.md5)
            assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", hashes.sha1)
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hashes.sha256)

            val stats = FileProTools.calculateLocalFolderStats(sandbox)
            assertEquals(131L, stats.totalBytes)
            assertEquals(2, stats.fileCount)
            assertEquals(1, stats.folderCount)
        } finally {
            sandbox.deleteRecursively()
        }
    }

    @Test
    fun `virtual archive listing groups folders and sorts entries cleanly`() {
        val entries = listOf(
            ArchiveEntryInfo("assets/logo.png", 400, 200, false, 1L, "assets/logo.png"),
            ArchiveEntryInfo("assets/fonts/mono.ttf", 800, 300, false, 2L, "assets/fonts/mono.ttf"),
            ArchiveEntryInfo("AndroidManifest.xml", 1200, 500, false, 3L, "AndroidManifest.xml")
        )
        val rootItems = ArchiveManager.listArchiveDirectory(entries, "", false, SortOption.NAME_ASC)
        assertEquals(2, rootItems.size)
        assertTrue(rootItems[0].isDirectory)
        assertEquals("assets", rootItems[0].name)
        assertEquals("AndroidManifest.xml", rootItems[1].name)

        val assetsItems = ArchiveManager.listArchiveDirectory(entries, "assets", false, SortOption.NAME_ASC)
        assertEquals(2, assetsItems.size)
        assertTrue(assetsItems[0].isDirectory)
        assertEquals("fonts", assetsItems[0].name)
        assertEquals("logo.png", assetsItems[1].name)

        val fav = FavoriteFolder("Downloads", "/storage/emulated/0/Download", null)
        assertEquals(fav, FavoriteFolder.decode(fav.encode()))
    }
}
