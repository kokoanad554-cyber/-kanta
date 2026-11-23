package com.example.ui.screens

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.ManageSearch
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.FileRepository
import com.example.localization.LocalAppStrings
import com.example.model.FileItem
import com.example.model.StorageRoot
import com.example.ui.components.CookieShape10
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.ExpressiveIconButton
import com.example.ui.components.ExpressiveSurface
import com.example.ui.components.ExpressiveWavyProgressIndicator
import com.example.ui.components.FileCategoryBadge
import com.example.ui.components.FileOperationProgressDialog
import com.example.ui.components.FolderPickerSheet
import com.example.ui.components.LocalExpressiveAnimations
import com.example.ui.components.LocalIsTvMode
import com.example.ui.components.LocalUseSideNavRail
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.ScanScope
import com.example.ui.viewmodel.ScannerUiState
import com.example.ui.viewmodel.ScannerViewModel
import com.example.util.FormatUtils

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun SmartScannerScreen(
    uiState: ScannerUiState,
    scannerViewModel: ScannerViewModel,
    confirmBatchDelete: Boolean,
    showHiddenFiles: Boolean,
    fileRepository: FileRepository,
    storageRoots: List<StorageRoot>,
    onOpenFile: (FileItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val expressive = LocalExpressiveAnimations.current
    val isTvMode = LocalIsTvMode.current
    val useSideRail = LocalUseSideNavRail.current
    var pendingDeleteItems by remember { mutableStateOf<List<FileItem>?>(null) }
    var pendingMoveItems by remember { mutableStateOf<List<FileItem>?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = if (isTvMode) 22.dp else 16.dp,
            end = if (isTvMode) 28.dp else 16.dp,
            top = if (isTvMode) 16.dp else 8.dp,
            bottom = if (useSideRail) 36.dp else 148.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.statusBarsPadding())
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = CookieShape10,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.ManageSearch,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = strings.scannerTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = strings.scannerSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = strings.scannerChooseExtensions,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        scannerViewModel.presetExtensions.forEach { ext ->
                            val selected = ext in uiState.selectedPresetExtensions
                            ExpressiveSurface(
                                onClick = { scannerViewModel.togglePresetExtension(ext) },
                                containerColor = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                contentColor = if (selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                restingCornerRadius = 18.dp,
                                pressedCornerRadius = 10.dp,
                                modifier = Modifier.testTag("scanner_chip_$ext")
                            ) {
                                Text(
                                    text = ext.uppercase(),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = uiState.customExtensionInput,
                        onValueChange = { scannerViewModel.updateCustomExtension(it) },
                        label = { Text(strings.scannerCustomExtHint) },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("scanner_custom_ext_input")
                    )

                    Text(
                        text = strings.scannerScopeLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ScanScope.entries.forEach { scope ->
                            val selected = uiState.scanScope == scope
                            ExpressiveSurface(
                                onClick = { scannerViewModel.setScanScope(scope) },
                                containerColor = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                contentColor = if (selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                restingCornerRadius = 18.dp,
                                pressedCornerRadius = 10.dp
                            ) {
                                Text(
                                    text = strings.scopeLabel(scope),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }

                    if (uiState.isScanning) {
                        ExpressiveSurface(
                            onClick = { scannerViewModel.stopScan() },
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                            restingCornerRadius = 25.dp,
                            pressedCornerRadius = 12.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("scanner_stop_button")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 50.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Stop, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(strings.scannerStop, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        ExpressiveSurface(
                            onClick = { scannerViewModel.startScan() },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            restingCornerRadius = 25.dp,
                            pressedCornerRadius = 12.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("scanner_start_button")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 50.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(strings.scannerStart, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Live progress / summary card
        if (uiState.hasScannedOnce) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.animateItem()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (uiState.isScanning) strings.scannerInProgress else strings.scannerSummary,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = strings.scannerFoundAndScanned(uiState.results.size, uiState.scannedFilesCount),
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (uiState.isScanning) {
                            ExpressiveWavyProgressIndicator(
                                progress = null,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = uiState.currentDirectoryLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Batch Actions Toolbar
        if (uiState.isSelectionMode) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { scannerViewModel.clearSelection() }) {
                                Icon(Icons.Rounded.Close, contentDescription = strings.cancel)
                            }
                            Text(
                                text = strings.selectedCount(uiState.selectedIds.size),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row {
                            TextButton(onClick = { scannerViewModel.selectAll() }) {
                                Icon(Icons.Rounded.SelectAll, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(strings.selectAll)
                            }
                            TextButton(onClick = { pendingMoveItems = uiState.selectedItems }) {
                                Icon(Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(strings.move)
                            }
                            TextButton(
                                onClick = {
                                    val targets = uiState.selectedItems
                                    if (confirmBatchDelete) {
                                        pendingDeleteItems = targets
                                    } else {
                                        scannerViewModel.deleteItems(targets)
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(strings.delete, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        if (uiState.hasScannedOnce && !uiState.isScanning && uiState.results.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = strings.scannerNoMatches,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = strings.scannerNoMatchesHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(uiState.results, key = { it.id }) { item ->
                val isSelected = item.id in uiState.selectedIds
                val rowInteractionSource = remember { MutableInteractionSource() }
                val isFocused by rowInteractionSource.collectIsFocusedAsState()
                val focusScale by animateFloatAsState(
                    targetValue = if (expressive && isFocused) 1.02f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "scan_row_scale"
                )
                val focusBorderColor by animateColorAsState(
                    targetValue = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                    animationSpec = tween(120),
                    label = "scan_row_border"
                )
                val rowShape = RoundedCornerShape(18.dp)
                Surface(
                    shape = rowShape,
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                        isFocused -> MaterialTheme.colorScheme.surfaceContainerHighest
                        else -> MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    border = if (isFocused) BorderStroke(2.5.dp, focusBorderColor) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = focusScale
                            scaleY = focusScale
                        }
                        .clip(rowShape)
                        .combinedClickable(
                            interactionSource = rowInteractionSource,
                            indication = androidx.compose.material3.ripple(),
                            onClick = {
                                if (uiState.isSelectionMode) {
                                    scannerViewModel.toggleSelection(item)
                                } else {
                                    onOpenFile(item)
                                }
                            },
                            onLongClick = { scannerViewModel.toggleSelection(item) }
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            FileCategoryBadge(category = item.category, size = 44.dp)
                            if (isSelected) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.path.substringBeforeLast('/'),
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${strings.formatBytes(item.sizeBytes)} • ${FormatUtils.formatDate(item.lastModifiedMillis, strings.unknownLabel)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            ExpressiveIconButton(
                                onClick = { pendingMoveItems = listOf(item) },
                                size = 48.dp,
                                containerColor = Color.Transparent
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.DriveFileMove,
                                    contentDescription = strings.move
                                )
                            }
                            ExpressiveIconButton(
                                onClick = { pendingDeleteItems = listOf(item) },
                                size = 48.dp,
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.error
                            ) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = strings.delete
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDeleteItems?.let { targets ->
        DeleteConfirmDialog(
            count = targets.size,
            singleName = targets.singleOrNull()?.name,
            onDismiss = { pendingDeleteItems = null },
            onConfirm = {
                pendingDeleteItems = null
                scannerViewModel.deleteItems(targets)
            }
        )
    }

    pendingMoveItems?.let { targets ->
        FolderPickerSheet(
            title = strings.chooseMoveFolder,
            confirmLabel = strings.moveTo,
            fileRepository = fileRepository,
            storageRoots = storageRoots,
            initialPath = fileRepository.getInternalRootPath(),
            initialSafUri = null,
            showHidden = showHiddenFiles,
            onDismiss = { pendingMoveItems = null },
            onConfirm = { destPath: String, destSafUri: Uri? ->
                pendingMoveItems = null
                scannerViewModel.moveItems(targets, destPath, destSafUri)
            }
        )
    }

    uiState.operationProgress?.let { prog ->
        FileOperationProgressDialog(
            progress = prog,
            onCancel = { }
        )
    }
}
