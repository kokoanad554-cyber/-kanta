package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Html
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.FileCategory

data class CategoryVisual(
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color,
    val labelAr: String
)

@Composable
fun fileCategoryVisual(category: FileCategory): CategoryVisual {
    val cs = MaterialTheme.colorScheme
    return when (category) {
        FileCategory.FOLDER -> CategoryVisual(
            icon = Icons.Rounded.Folder,
            containerColor = cs.primaryContainer,
            contentColor = cs.onPrimaryContainer,
            labelAr = "مجلد"
        )
        FileCategory.APK -> CategoryVisual(
            icon = Icons.Rounded.Android,
            containerColor = cs.tertiaryContainer,
            contentColor = cs.onTertiaryContainer,
            labelAr = "حزمة تطبيق APK"
        )
        FileCategory.ARCHIVE -> CategoryVisual(
            icon = Icons.Rounded.FolderZip,
            containerColor = cs.secondaryContainer,
            contentColor = cs.onSecondaryContainer,
            labelAr = "ملف مضغوط"
        )
        FileCategory.HTML -> CategoryVisual(
            icon = Icons.Rounded.Html,
            containerColor = cs.primaryContainer,
            contentColor = cs.onPrimaryContainer,
            labelAr = "صفحة HTML"
        )
        FileCategory.TEXT -> CategoryVisual(
            icon = Icons.Rounded.Description,
            containerColor = cs.surfaceContainerHighest,
            contentColor = cs.onSurface,
            labelAr = "مستند نصي"
        )
        FileCategory.CODE -> CategoryVisual(
            icon = Icons.Rounded.Code,
            containerColor = cs.tertiaryContainer,
            contentColor = cs.onTertiaryContainer,
            labelAr = "ملف برمجي"
        )
        FileCategory.PDF -> CategoryVisual(
            icon = Icons.Rounded.PictureAsPdf,
            containerColor = cs.errorContainer,
            contentColor = cs.onErrorContainer,
            labelAr = "مستند PDF"
        )
        FileCategory.IMAGE -> CategoryVisual(
            icon = Icons.Rounded.Image,
            containerColor = cs.secondaryContainer,
            contentColor = cs.onSecondaryContainer,
            labelAr = "صورة"
        )
        FileCategory.AUDIO -> CategoryVisual(
            icon = Icons.Rounded.AudioFile,
            containerColor = cs.tertiaryContainer,
            contentColor = cs.onTertiaryContainer,
            labelAr = "ملف صوتي"
        )
        FileCategory.VIDEO -> CategoryVisual(
            icon = Icons.Rounded.Movie,
            containerColor = cs.primaryContainer,
            contentColor = cs.onPrimaryContainer,
            labelAr = "فيديو"
        )
        FileCategory.OTHER -> CategoryVisual(
            icon = Icons.Rounded.InsertDriveFile,
            containerColor = cs.surfaceContainerHigh,
            contentColor = cs.onSurfaceVariant,
            labelAr = "ملف"
        )
    }
}

@Composable
fun FileCategoryBadge(
    category: FileCategory,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp
) {
    val visual = fileCategoryVisual(category)
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(14.dp),
        color = visual.containerColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = visual.icon,
                contentDescription = visual.labelAr,
                tint = visual.contentColor,
                modifier = Modifier.size(size * 0.54f)
            )
        }
    }
}
