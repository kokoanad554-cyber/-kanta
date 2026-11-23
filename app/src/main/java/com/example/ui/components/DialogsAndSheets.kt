package com.example.ui.components

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Html
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.FileRepository
import com.example.localization.LocalAppStrings
import com.example.model.ApkMetadata
import com.example.model.ArchiveEntryInfo
import com.example.model.FileItem
import com.example.model.FileOperationProgress
import com.example.model.SortOption
import com.example.model.StorageRoot
import com.example.model.StorageRootType
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.util.FormatUtils
import kotlinx.coroutines.launch

@Composable
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(26.dp),
        icon = { Icon(Icons.Rounded.CreateNewFolder, contentDescription = null) },
        title = { Text(strings.createFolderTitle) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(strings.folderNameLabel) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("create_folder_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("create_folder_confirm")
            ) {
                Text(strings.create)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateFileDialog(
    onDismiss: () -> Unit,
    onConfirm: (fileName: String, openAfter: Boolean) -> Unit
) {
    val strings = LocalAppStrings.current
    var baseName by remember { mutableStateOf("") }
    var selectedExt by remember { mutableStateOf(".txt") }
    val presets = listOf(".txt", ".html", ".json", ".md", ".xml", "")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(26.dp),
        icon = { Icon(Icons.AutoMirrored.Rounded.InsertDriveFile, contentDescription = null) },
        title = { Text(strings.createFileTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = baseName,
                    onValueChange = { baseName = it },
                    label = { Text(strings.fileNameLabel) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_file_input")
                )
                Text(
                    text = strings.quickExtension,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { ext ->
                        FilterChip(
                            selected = selectedExt == ext,
                            onClick = { selectedExt = ext },
                            label = { Text(if (ext.isEmpty()) strings.noExtension else ext) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = baseName.trim()
                    if (trimmed.isNotEmpty()) {
                        val finalName = if (selectedExt.isNotEmpty() && !trimmed.contains('.')) {
                            "$trimmed$selectedExt"
                        } else trimmed
                        onConfirm(finalName, selectedExt == ".html" || selectedExt == ".txt")
                    }
                },
                enabled = baseName.isNotBlank(),
                modifier = Modifier.testTag("create_file_confirm")
            ) {
                Text(strings.create)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}

@Composable
fun RenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    var name by remember(currentName) { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(26.dp),
        title = { Text(strings.renameTitle) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(strings.newNameLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                enabled = name.isNotBlank() && name.trim() != currentName
            ) {
                Text(strings.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}

@Composable
fun DeleteConfirmDialog(
    count: Int,
    singleName: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val strings = LocalAppStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(26.dp),
        icon = {
            Icon(
                Icons.Rounded.DeleteOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text(strings.confirmDeleteTitle) },
        text = {
            Text(
                if (count == 1 && singleName != null) {
                    strings.confirmDeleteSingle(singleName)
                } else {
                    strings.confirmDeleteMulti(strings.itemCountText(count))
                }
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.testTag("confirm_delete_button")
            ) {
                Text(strings.delete)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}

@Composable
fun CompressZipDialog(
    suggestedName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    var name by remember(suggestedName) { mutableStateOf(suggestedName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(26.dp),
        icon = { Icon(Icons.Rounded.Archive, contentDescription = null) },
        title = { Text(strings.compressDialogTitle) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(strings.archiveNameLabel) },
                suffix = { Text(".zip") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text(strings.startCompress)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}

@Composable
fun FileOperationProgressDialog(
    progress: FileOperationProgress,
    onCancel: () -> Unit
) {
    val strings = LocalAppStrings.current
    AlertDialog(
        onDismissRequest = {},
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(26.dp),
        title = { Text(strings.opTitle(progress.opType)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = progress.currentFileName.ifEmpty { strings.processing },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (progress.isIndeterminate) {
                    ExpressiveWavyProgressIndicator(
                        progress = null,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    ExpressiveWavyProgressIndicator(
                        progress = { progress.progressFraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val percent = (progress.progressFraction * 100).toInt()
                        Text(
                            text = "$percent%",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = JetBrainsMonoFontFamily
                        )
                        if (progress.totalBytes > 0L) {
                            Text(
                                text = "${strings.formatBytes(progress.processedBytes)} / ${strings.formatBytes(progress.totalBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (progress.totalCount > 0) {
                            Text(
                                text = "${progress.processedCount} / ${progress.totalCount}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFontFamily
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) {
                Text(strings.cancelOperation, color = MaterialTheme.colorScheme.error)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileDetailsSheet(
    item: FileItem,
    onDismiss: () -> Unit,
    onOpenAsText: (FileItem) -> Unit,
    onOpenInHtmlEditor: (FileItem) -> Unit,
    onOpenExternal: (FileItem) -> Unit
) {
    val strings = LocalAppStrings.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                FileCategoryBadge(category = item.category, size = 52.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = strings.categoryLabel(item.category),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            DetailRow(strings.pathLabel, item.path, mono = true)
            if (!item.isDirectory) {
                DetailRow(strings.sizeLabel, strings.formatBytes(item.sizeBytes), mono = true)
            } else if (item.childCount != null) {
                DetailRow(strings.contentLabel, strings.itemCountText(item.childCount))
            }
            DetailRow(strings.modifiedLabel, FormatUtils.formatDate(item.lastModifiedMillis, strings.unknownLabel), mono = true)
            DetailRow(
                strings.permissionsLabel,
                buildString {
                    append(if (item.canRead) strings.readable else strings.unreadable)
                    append(" • ")
                    append(if (item.canWrite) strings.writable else strings.readOnly)
                }
            )
            DetailRow(strings.statusLabel, if (item.isHidden) strings.hiddenFile else strings.visibleFile)

            if (!item.isDirectory) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenAsText(item)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.openAsText)
                    }
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenInHtmlEditor(item)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Html, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.htmlEditor)
                    }
                }
                ExpressiveSurface(
                    onClick = {
                        onDismiss()
                        onOpenExternal(item)
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    restingCornerRadius = 24.dp,
                    pressedCornerRadius = 12.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(strings.openExternal, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, mono: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = value,
                style = if (mono) {
                    MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily)
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkPreviewSheet(
    metadata: ApkMetadata?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onInstall: (ApkMetadata) -> Unit
) {
    val strings = LocalAppStrings.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (isLoading || metadata == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ExpressiveWavyProgressIndicator(
                            progress = null,
                            modifier = Modifier.widthIn(max = 200.dp)
                        )
                        Text(strings.analyzingApk)
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(68.dp),
                        shape = ScallopedCookieShape(lobes = 10, depthFraction = 0.08f),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (metadata.icon != null) {
                                Image(
                                    bitmap = metadata.icon,
                                    contentDescription = metadata.appName,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Android,
                                    contentDescription = metadata.appName,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = metadata.appName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = metadata.packageName,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ApkStatPill(
                        label = strings.versionLabel,
                        value = "${metadata.versionName} (${metadata.versionCode})",
                        modifier = Modifier.weight(1f)
                    )
                    ApkStatPill(
                        label = strings.sizeLabel,
                        value = strings.formatBytes(metadata.fileSizeBytes),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (metadata.minSdk != null || metadata.targetSdk != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ApkStatPill(
                            label = strings.minSdkLabel,
                            value = metadata.minSdk?.toString() ?: "-",
                            modifier = Modifier.weight(1f)
                        )
                        ApkStatPill(
                            label = strings.targetSdkLabel,
                            value = metadata.targetSdk?.toString() ?: "-",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text(strings.close)
                    }
                    ExpressiveSurface(
                        onClick = { onInstall(metadata) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        restingCornerRadius = 24.dp,
                        pressedCornerRadius = 12.dp,
                        modifier = Modifier
                            .weight(1.4f)
                            .testTag("apk_install_button")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp)
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.InstallMobile, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(strings.installApp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApkStatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZipPreviewSheet(
    zipItem: FileItem,
    entries: List<ArchiveEntryInfo>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onExtractClick: (FileItem) -> Unit
) {
    val strings = LocalAppStrings.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FileCategoryBadge(category = zipItem.category, size = 48.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = zipItem.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${strings.formatBytes(zipItem.sizeBytes)} • ${strings.itemCountText(entries.size)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ExpressiveWavyProgressIndicator(
                        progress = null,
                        modifier = Modifier.widthIn(max = 200.dp)
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                ) {
                    if (entries.isEmpty()) {
                        Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(strings.archiveEmpty)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(entries.take(150)) { entry ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = if (entry.isDirectory) Icons.Rounded.Folder else Icons.AutoMirrored.Rounded.InsertDriveFile,
                                        contentDescription = null,
                                        tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = entry.name,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!entry.isDirectory && entry.sizeBytes > 0L) {
                                        Text(
                                            text = strings.formatBytes(entry.sizeBytes),
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Text(strings.close)
                }
                ExpressiveSurface(
                    onClick = { onExtractClick(zipItem) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    restingCornerRadius = 24.dp,
                    pressedCornerRadius = 12.dp,
                    modifier = Modifier
                        .weight(1.4f)
                        .testTag("extract_zip_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Unarchive, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(strings.extractToPath, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerSheet(
    title: String,
    confirmLabel: String,
    fileRepository: FileRepository,
    storageRoots: List<StorageRoot>,
    initialPath: String,
    initialSafUri: Uri?,
    showHidden: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (destPath: String, destSafUri: Uri?) -> Unit
) {
    val strings = LocalAppStrings.current
    var currentPath by remember { mutableStateOf(initialPath) }
    var currentSafUri by remember { mutableStateOf(initialSafUri) }
    val safStack = remember { mutableStateListOf<Pair<String, Uri>>() }
    var folders by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        isLoading = true
        val res = fileRepository.listDirectory(currentPath, currentSafUri, showHidden, SortOption.NAME_ASC)
        folders = res.getOrDefault(emptyList()).filter { it.isDirectory }
        isLoading = false
    }

    LaunchedEffect(currentPath, currentSafUri) {
        reload()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (currentSafUri != null) {
                                if (safStack.size > 1) {
                                    safStack.removeAt(safStack.lastIndex)
                                    val prev = safStack.last()
                                    currentPath = prev.first
                                    currentSafUri = prev.second
                                } else {
                                    safStack.clear()
                                    currentPath = fileRepository.getInternalRootPath()
                                    currentSafUri = null
                                }
                            } else {
                                val parent = java.io.File(currentPath).parentFile
                                if (parent != null && currentPath != "/") {
                                    currentPath = parent.absolutePath
                                }
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = strings.back)
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Row {
                    IconButton(onClick = { showNewFolderDialog = true }) {
                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = strings.newFolder)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = strings.close)
                    }
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(storageRoots) { root ->
                    val isRootSelected = currentPath == root.path
                    val rootLabel = when (root.type) {
                        StorageRootType.INTERNAL -> strings.rootInternal
                        StorageRootType.DOWNLOADS -> strings.rootDownloads
                        StorageRootType.DOCUMENTS -> strings.rootDocuments
                        StorageRootType.EXTERNAL_USB -> "${strings.rootExternalUsb}: ${root.title}"
                        StorageRootType.SYSTEM -> if (root.path == "/") strings.rootSlash else strings.rootSystem
                        StorageRootType.SAF_TREE -> root.title
                    }
                    ExpressiveSurface(
                        onClick = {
                            if (root.safUri != null) {
                                safStack.clear()
                                safStack.add(root.path to root.safUri)
                                currentPath = root.path
                                currentSafUri = root.safUri
                            } else {
                                safStack.clear()
                                currentPath = root.path
                                currentSafUri = null
                            }
                        },
                        containerColor = if (isRootSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        contentColor = if (isRootSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        restingCornerRadius = 18.dp,
                        pressedCornerRadius = 10.dp
                    ) {
                        Text(
                            text = rootLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isRootSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Text(
                    text = currentPath,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            ) {
                if (isLoading) {
                    Box(contentAlignment = Alignment.Center) {
                        ExpressiveWavyProgressIndicator(
                            progress = null,
                            modifier = Modifier.widthIn(max = 180.dp)
                        )
                    }
                } else if (folders.isEmpty()) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            strings.noSubfolders,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.padding(6.dp)) {
                        items(folders, key = { it.id }) { folder ->
                            val rowInteraction = remember { MutableInteractionSource() }
                            val isRowFocused by rowInteraction.collectIsFocusedAsState()
                            val rowBorderColor by animateColorAsState(
                                targetValue = if (isRowFocused) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                                animationSpec = tween(120),
                                label = "picker_row_border"
                            )
                            val rowShape = RoundedCornerShape(12.dp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(rowShape)
                                    .then(
                                        if (isRowFocused) {
                                            Modifier.border(BorderStroke(2.dp, rowBorderColor), rowShape)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable(
                                        interactionSource = rowInteraction,
                                        indication = androidx.compose.material3.ripple()
                                    ) {
                                        if (folder.safUri != null) {
                                            safStack.add(folder.path to folder.safUri)
                                            currentPath = folder.path
                                            currentSafUri = folder.safUri
                                        } else {
                                            currentPath = folder.path
                                            currentSafUri = null
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = folder.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            ExpressiveSurface(
                onClick = { onConfirm(currentPath, currentSafUri) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                restingCornerRadius = 24.dp,
                pressedCornerRadius = 12.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(confirmLabel, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showNewFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showNewFolderDialog = false },
            onConfirm = { folderName ->
                showNewFolderDialog = false
                scope.launch {
                    fileRepository.createFolder(currentPath, currentSafUri, folderName)
                    reload()
                }
            }
        )
    }
}
