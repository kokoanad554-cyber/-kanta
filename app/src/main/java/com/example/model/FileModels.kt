package com.example.model

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap

enum class FileCategory {
    FOLDER,
    APK,
    ARCHIVE,
    HTML,
    TEXT,
    CODE,
    PDF,
    IMAGE,
    AUDIO,
    VIDEO,
    OTHER
}

enum class SortOption(val labelAr: String) {
    NAME_ASC("الاسم (أ - ي)"),
    NAME_DESC("الاسم (ي - أ)"),
    SIZE_DESC("الحجم (الأكبر)"),
    DATE_DESC("التاريخ (الأحدث)"),
    TYPE("النوع")
}

data class FileItem(
    val id: String,
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val isHidden: Boolean,
    val canRead: Boolean,
    val canWrite: Boolean,
    val extension: String,
    val category: FileCategory,
    val safUri: Uri? = null,
    val childCount: Int? = null
) {
    val isSaf: Boolean get() = safUri != null
}

enum class StorageRootType {
    INTERNAL,
    DOWNLOADS,
    DOCUMENTS,
    EXTERNAL_USB,
    SYSTEM,
    SAF_TREE
}

data class StorageRoot(
    val id: String,
    val title: String,
    val path: String,
    val type: StorageRootType,
    val safUri: Uri? = null
)

data class BreadcrumbSegment(
    val title: String,
    val path: String,
    val safUri: Uri? = null
)

enum class ClipboardAction {
    COPY,
    CUT
}

data class ClipboardState(
    val items: List<FileItem>,
    val action: ClipboardAction
)

enum class FileOpType(val titleAr: String) {
    COPY("جاري نسخ الملفات"),
    MOVE("جاري نقل الملفات"),
    DELETE("جاري الحذف"),
    COMPRESS("جاري ضغط الأرشيف"),
    EXTRACT("جاري فك الضغط")
}

data class FileOperationProgress(
    val opType: FileOpType,
    val currentFileName: String,
    val processedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val processedCount: Int = 0,
    val totalCount: Int = 0,
    val isIndeterminate: Boolean = false
) {
    val progressFraction: Float
        get() = when {
            totalBytes > 0L -> (processedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            totalCount > 0 -> (processedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
            else -> 0f
        }
}

data class ApkMetadata(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int?,
    val targetSdk: Int?,
    val fileSizeBytes: Long,
    val icon: ImageBitmap?,
    val fileItem: FileItem,
    val resolvedLocalPath: String?
)

data class ArchiveEntryInfo(
    val name: String,
    val sizeBytes: Long,
    val compressedSizeBytes: Long,
    val isDirectory: Boolean
)

data class StorageStats(
    val totalBytes: Long,
    val freeBytes: Long
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0L)
    val usedFraction: Float
        get() = if (totalBytes > 0L) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}
