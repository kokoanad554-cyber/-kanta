package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.localization.LocalAppStrings
import com.example.model.FileCategory

data class CategoryVisual(
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color,
    val label: String
)

@Composable
fun fileCategoryVisual(category: FileCategory): CategoryVisual {
    val cs = MaterialTheme.colorScheme
    val strings = LocalAppStrings.current
    val label = strings.categoryLabel(category)
    return when (category) {
        FileCategory.FOLDER -> CategoryVisual(Icons.Rounded.Folder, cs.primaryContainer, cs.onPrimaryContainer, label)
        FileCategory.APK -> CategoryVisual(Icons.Rounded.Android, cs.tertiaryContainer, cs.onTertiaryContainer, label)
        FileCategory.ARCHIVE -> CategoryVisual(Icons.Rounded.FolderZip, cs.secondaryContainer, cs.onSecondaryContainer, label)
        FileCategory.HTML -> CategoryVisual(Icons.Rounded.Html, cs.primaryContainer, cs.onPrimaryContainer, label)
        FileCategory.TEXT -> CategoryVisual(Icons.Rounded.Description, cs.surfaceContainerHighest, cs.onSurface, label)
        FileCategory.CODE -> CategoryVisual(Icons.Rounded.Code, cs.tertiaryContainer, cs.onTertiaryContainer, label)
        FileCategory.PDF -> CategoryVisual(Icons.Rounded.PictureAsPdf, cs.errorContainer, cs.onErrorContainer, label)
        FileCategory.IMAGE -> CategoryVisual(Icons.Rounded.Image, cs.secondaryContainer, cs.onSecondaryContainer, label)
        FileCategory.AUDIO -> CategoryVisual(Icons.Rounded.AudioFile, cs.tertiaryContainer, cs.onTertiaryContainer, label)
        FileCategory.VIDEO -> CategoryVisual(Icons.Rounded.Movie, cs.primaryContainer, cs.onPrimaryContainer, label)
        FileCategory.OTHER -> CategoryVisual(Icons.Rounded.InsertDriveFile, cs.surfaceContainerHigh, cs.onSurfaceVariant, label)
    }
}

@Composable
fun FileCategoryBadge(
    category: FileCategory,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp
) {
    val visual = fileCategoryVisual(category)
    val badgeShape = if (category == FileCategory.APK || category == FileCategory.ARCHIVE) {
        ScallopedCookieShape(lobes = 10, depthFraction = 0.08f)
    } else {
        RoundedCornerShape(15.dp)
    }
    val bevelColor = remember(visual.containerColor) { lerp(visual.containerColor, Color.Black, 0.34f) }
    val rimColor = remember(visual.containerColor) { lerp(visual.containerColor, Color.White, 0.16f) }

    Box(
        modifier = modifier
            .size(size)
            .clip(badgeShape)
            .background(bevelColor)
            .padding(bottom = 2.5.dp)
    ) {
        Surface(
            modifier = Modifier.size(size),
            shape = badgeShape,
            color = visual.containerColor,
            border = BorderStroke(1.dp, rimColor)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = visual.icon,
                    contentDescription = visual.label,
                    tint = visual.contentColor,
                    modifier = Modifier.size(size * 0.52f)
                )
            }
        }
    }
}
