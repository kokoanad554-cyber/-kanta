package com.example.ui.components

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Html
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import com.example.model.ApkMetadata
import com.example.model.ArchiveEntryInfo
import com.example.model.FileItem
import com.example.model.FileOperationProgress
import com.example.model.SortOption
import com.example.model.StorageRoot
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.util.FormatUtils
import kotlinx.coroutines.launch

@Composable
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(24.dp),
        icon = { Icon(Icons.Rounded.CreateNewFolder, contentDescription = null) },
        title = { Text("إنشاء مجلد جديد") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("اسم المجلد") },
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
                Text("إنشاء")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateFileDialog(
    onDismiss: () -> Unit,
    onConfirm: (fileName: String, openAfter: Boolean) -> Unit
) {
    var baseName by remember { mutableStateOf("") }
    var selectedExt by remember { mutableStateOf(".txt") }
    val presets = listOf(".txt", ".html", ".json", ".md", ".xml", "")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(24.dp),
        icon = { Icon(Icons.Rounded.InsertDriveFile, contentDescription = null) },
        title = { Text("إنشاء ملف جديد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = baseName,
                    onValueChange = { baseName = it },
                    label = { Text("اسم الملف") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_file_input")
                )
                Text(
                    text = "الامتداد السريع",
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
                            label = { Text(if (ext.isEmpty()) "بدون إضافة" else ext) }
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
                Text("إنشاء")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun RenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(currentName) { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(24.dp),
        title = { Text("إعادة التسمية") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("الاسم الجديد") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                enabled = name.isNotBlank() && name.trim() != currentName
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(24.dp),
        icon = {
            Icon(
                Icons.Rounded.DeleteOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("تأكيد الحذف") },
        text = {
            Text(
                if (count == 1 && singleName != null) {
                    "هل تريد حذف \"$singleName\" نهائياً؟ لا يمكن التراجع عن هذا الإجراء."
                } else {
                    "هل تريد حذف ${FormatUtils.formatCountItems(count)} نهائياً؟ لا يمكن التراجع عن هذا الإجراء."
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
                Text("حذف")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun CompressZipDialog(
    suggestedName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(suggestedName) { mutableStateOf(suggestedName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(24.dp),
        icon = { Icon(Icons.Rounded.Archive, contentDescription = null) },
        title = { Text("ضغط إلى أرشيف ZIP") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("اسم الأرشيف") },
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
                Text("بدء الضغط")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun FileOperationProgressDialog(
    progress: FileOperationProgress,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(24.dp),
        title = { Text(progress.opType.titleAr) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = progress.currentFileName.ifEmpty { "جاري المعالجة..." },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (progress.isIndeterminate) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(
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
                                text = "${FormatUtils.formatBytes(progress.processedBytes)} / ${FormatUtils.formatBytes(progress.totalBytes)}",
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
                Text("إلغاء العملية", color = MaterialTheme.colorScheme.error)
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
                        text = fileCategoryVisual(item.category).labelAr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            DetailRow("المسار", item.path, mono = true)
            if (!item.isDirectory) {
                DetailRow("الحجم", FormatUtils.formatBytes(item.sizeBytes), mono = true)
            } else if (item.childCount != null) {
                DetailRow("المحتوى", FormatUtils.formatCountItems(item.childCount))
            }
            DetailRow("آخر تعديل", FormatUtils.formatDate(item.lastModifiedMillis), mono = true)
            DetailRow(
                "الصلاحيات",
                buildString {
                    append(if (item.canRead) "قابل للقراءة" else "غير مقروء")
                    append(" • ")
                    append(if (item.canWrite) "قابل للتعديل" else "للقراءة فقط")
                }
            )
            DetailRow("الحالة", if (item.isHidden) "ملف مخفي" else "ظاهر")

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
                        Text("عرض كنص")
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
                        Text("محرر HTML")
                    }
                }
                Button(
                    onClick = {
                        onDismiss()
                        onOpenExternal(item)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("فتح بتطبيق خارجي")
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
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("جاري تحليل حزمة APK واستخراج البيانات...")
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(68.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (metadata.icon != null) {
                                Image(
                                    bitmap = metadata.icon,
                                    contentDescription = metadata.appName,
                                    modifier = Modifier
                                        .size(56.dp)
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
                        label = "الإصدار",
                        value = "${metadata.versionName} (${metadata.versionCode})",
                        modifier = Modifier.weight(1f)
                    )
                    ApkStatPill(
                        label = "الحجم",
                        value = FormatUtils.formatBytes(metadata.fileSizeBytes),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (metadata.minSdk != null || metadata.targetSdk != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ApkStatPill(
                            label = "الحد الأدنى (Min SDK)",
                            value = metadata.minSdk?.toString() ?: "-",
                            modifier = Modifier.weight(1f)
                        )
                        ApkStatPill(
                            label = "الهدف (Target SDK)",
                            value = metadata.targetSdk?.toString() ?: "-",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("إغلاق")
                    }
                    Button(
                        onClick = { onInstall(metadata) },
                        modifier = Modifier
                            .weight(1.4f)
                            .heightIn(min = 48.dp)
                            .testTag("apk_install_button")
                    ) {
                        Icon(Icons.Rounded.InstallMobile, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("تثبيت التطبيق")
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
                        text = "${FormatUtils.formatBytes(zipItem.sizeBytes)} • ${FormatUtils.formatCountItems(entries.size)}",
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
                    CircularProgressIndicator()
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
                            Text("الأرشيف فارغ")
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
                                        imageVector = if (entry.isDirectory) Icons.Rounded.Folder else Icons.Rounded.InsertDriveFile,
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
                                            text = FormatUtils.formatBytes(entry.sizeBytes),
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
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Text("إغلاق")
                }
                Button(
                    onClick = { onExtractClick(zipItem) },
                    modifier = Modifier
                        .weight(1.4f)
                        .heightIn(min = 48.dp)
                        .testTag("extract_zip_button")
                ) {
                    Icon(Icons.Rounded.Unarchive, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("استخراج إلى مسار...")
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
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "رجوع")
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Row {
                    IconButton(onClick = { showNewFolderDialog = true }) {
                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = "مجلد جديد")
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "إغلاق")
                    }
                }
            }

            // Quick roots row
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(storageRoots) { root ->
                    FilterChip(
                        selected = currentPath == root.path,
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
                        label = { Text(root.title) }
                    )
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
                    Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else if (folders.isEmpty()) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "لا توجد مجلدات فرعية",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.padding(6.dp)) {
                        items(folders, key = { it.id }) { folder ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
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

            Button(
                onClick = { onConfirm(currentPath, currentSafUri) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
            ) {
                Text(confirmLabel)
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
