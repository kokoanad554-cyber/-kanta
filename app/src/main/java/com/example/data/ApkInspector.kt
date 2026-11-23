package com.example.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.example.model.ApkMetadata
import com.example.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ApkInspector(private val context: Context) {

    suspend fun inspectApk(item: FileItem): Result<ApkMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            val localFile: File = if (item.safUri != null) {
                val cached = File(context.cacheDir, "inspect_${System.currentTimeMillis()}.apk")
                context.contentResolver.openInputStream(item.safUri)?.use { inp ->
                    FileOutputStream(cached).use { out -> inp.copyTo(out) }
                } ?: throw IllegalStateException("تعذر قراءة ملف APK")
                cached
            } else {
                File(item.path)
            }

            if (!localFile.exists() || !localFile.canRead()) {
                throw IllegalStateException("ملف APK غير متاح للقراءة")
            }

            val pm = context.packageManager
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(
                    localFile.absolutePath,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(localFile.absolutePath, PackageManager.GET_META_DATA)
            } ?: throw IllegalStateException("ملف APK تالف أو غير صالح")

            val appInfo = pkgInfo.applicationInfo
            if (appInfo != null) {
                appInfo.sourceDir = localFile.absolutePath
                appInfo.publicSourceDir = localFile.absolutePath
            }

            val appName = appInfo?.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: item.name
            val drawable = runCatching { appInfo?.loadIcon(pm) }.getOrNull()
            val iconBitmap = drawable?.toImageBitmap()

            val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                appInfo?.minSdkVersion?.takeIf { it > 0 }
            } else null
            val targetSdk = appInfo?.targetSdkVersion?.takeIf { it > 0 }

            ApkMetadata(
                appName = appName,
                packageName = pkgInfo.packageName ?: "غير معروف",
                versionName = pkgInfo.versionName ?: "1.0",
                versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo),
                minSdk = minSdk,
                targetSdk = targetSdk,
                fileSizeBytes = item.sizeBytes.takeIf { it > 0L } ?: localFile.length(),
                icon = iconBitmap,
                fileItem = item,
                resolvedLocalPath = localFile.absolutePath
            )
        }
    }

    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true
    }

    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun launchInstaller(metadata: ApkMetadata): Result<Unit> = runCatching {
        val uri: Uri = if (metadata.fileItem.safUri != null) {
            val cachedFile = metadata.resolvedLocalPath?.let { File(it) }
            if (cachedFile != null && cachedFile.exists()) {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cachedFile)
            } else {
                metadata.fileItem.safUri
            }
        } else {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                File(metadata.fileItem.path)
            )
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun Drawable.toImageBitmap(): ImageBitmap {
        if (this is BitmapDrawable && bitmap != null) {
            return bitmap.asImageBitmap()
        }
        val width = intrinsicWidth.takeIf { it > 0 } ?: 128
        val height = intrinsicHeight.takeIf { it > 0 } ?: 128
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bmp.asImageBitmap()
    }
}
