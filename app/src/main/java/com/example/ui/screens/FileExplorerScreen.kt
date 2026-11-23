package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.localization.LocalAppStrings
import com.example.model.ClipboardAction
import com.example.model.FileCategory
import com.example.model.FileItem
import com.example.model.ListDensity
import com.example.model.SortOption
import com.example.model.StorageRootType
import com.example.ui.components.CompressZipDialog
import com.example.ui.components.CookieShape10
import com.example.ui.components.CreateFileDialog
import com.example.ui.components.CreateFolderDialog
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.ExpressiveIconButton
import com.example.ui.components.ExpressiveSurface
import com.example.ui.components.ExpressiveWavyProgressIndicator
import com.example.ui.components.FileCategoryBadge
import com.example.ui.components.LocalExpressiveAnimations
import com.example.ui.components.LocalHapticEnabled
import com.example.ui.components.LocalIsTvMode
import com.example.ui.components.LocalUseSideNavRail
import com.example.ui.components.RenameDialog
import com.example.ui.components.TopEdgeFadeScrim
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalListDensity
import com.example.ui.viewmodel.ExplorerUiState
import com.example.ui.viewmodel.FileExplorerViewModel
import com.example.util.FormatUtils

@Composable
fun FileExplorerScreen(
    uiState: ExplorerUiState,
    viewModel: FileExplorerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val strings = LocalAppStrings.current
    val expressive = LocalExpressiveAnimations.current
    val density = LocalListDensity.current
    val isTvMode = LocalIsTvMode.current
    val useSideRail = LocalUseSideNavRail.current
    val layoutDir = LocalLayoutDirection.current
    val safeInsets = WindowInsets.safeDrawing.only(
        if (useSideRail) {
            WindowInsetsSides.Top + WindowInsetsSides.End + WindowInsetsSides.Bottom
        } else {
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
        }
    ).asPaddingValues()

    var showSearch by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showFabMenu by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileItem?>(null) }
    var deleteTargets by remember { mutableStateOf<List<FileItem>?>(null) }
    var compressTargets by remember { mutableStateOf<List<FileItem>?>(null) }

    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.addSafTreeUri(uri)
        }
    }

    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshPermissionAndRoots()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissionAndRoots()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = uiState.isSelectionMode || uiState.breadcrumbs.size > 1) {
        viewModel.navigateUp()
    }

    val animatedStorageFraction by animateFloatAsState(
        targetValue = uiState.storageStats.usedFraction,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "storage_fraction"
    )

    val fabRotation by animateFloatAsState(
        targetValue = if (expressive && showFabMenu) 45f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "fab_rotation"
    )

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Pure 2D Material 3 Expressive Pinned Header Card (never clipped under notch/status bar or lost on scroll)
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = safeInsets.calculateStartPadding(layoutDir) + (if (isTvMode) 22.dp else 16.dp),
                        end = safeInsets.calculateEndPadding(layoutDir) + (if (isTvMode) 28.dp else 16.dp),
                        top = safeInsets.calculateTopPadding() + (if (isTvMode) 20.dp else 10.dp),
                        bottom = 6.dp
                    )
            ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (uiState.breadcrumbs.size > 1 || uiState.isSelectionMode) {
                                    ExpressiveIconButton(
                                        onClick = { viewModel.navigateUp() },
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        size = 48.dp,
                                        modifier = Modifier.testTag("explorer_back_button")
                                    ) {
                                        Icon(
                                            imageVector = if (uiState.isSelectionMode) {
                                                Icons.Rounded.Close
                                            } else {
                                                Icons.AutoMirrored.Rounded.ArrowBack
                                            },
                                            contentDescription = strings.back,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    AnimatedContent(
                                        targetState = if (uiState.isSelectionMode) {
                                            strings.selectedCount(uiState.selectedIds.size)
                                        } else {
                                            val lastTitle = uiState.breadcrumbs.lastOrNull()?.title
                                            if (uiState.breadcrumbs.size <= 1) strings.appTitle else (lastTitle ?: strings.appTitle)
                                        },
                                        transitionSpec = {
                                            (fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith fadeOut()
                                        },
                                        label = "header_title"
                                    ) { titleText ->
                                        Text(
                                            text = titleText,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = strings.itemCountText(uiState.displayedItems.size),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ExpressiveIconButton(
                                    onClick = {
                                        showSearch = !showSearch
                                        if (!showSearch) viewModel.updateSearchQuery("")
                                    },
                                    containerColor = if (showSearch) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    contentColor = if (showSearch) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    size = 48.dp,
                                    modifier = Modifier.testTag("toggle_search_button")
                                ) {
                                    Icon(
                                        Icons.Rounded.Search,
                                        contentDescription = strings.search,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Box {
                                    ExpressiveIconButton(
                                        onClick = { showSortMenu = true },
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        size = 48.dp,
                                        modifier = Modifier.testTag("sort_menu_button")
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Rounded.Sort,
                                            contentDescription = strings.sort,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showSortMenu,
                                        onDismissRequest = { showSortMenu = false },
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        SortOption.entries.forEach { option ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = strings.sortLabel(option),
                                                        fontWeight = if (uiState.sortOption == option) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (uiState.sortOption == option) {
                                                            MaterialTheme.colorScheme.primary
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurface
                                                        }
                                                    )
                                                },
                                                onClick = {
                                                    viewModel.updateSortOption(option)
                                                    showSortMenu = false
                                                }
                                            )
                                        }
                                    }
                                }

                                ExpressiveIconButton(
                                    onClick = { viewModel.refreshCurrentDirectory() },
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    size = 48.dp
                                ) {
                                    Icon(
                                        Icons.Rounded.Refresh,
                                        contentDescription = strings.refresh,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = showSearch,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = { viewModel.updateSearchQuery(it) },
                                placeholder = { Text(strings.searchInFolder) },
                                singleLine = true,
                                trailingIcon = {
                                    if (uiState.searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                            Icon(Icons.Rounded.Close, contentDescription = strings.close)
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Expressive Breadcrumbs Pills (2D pill <-> squircle morphing)
                        if (uiState.breadcrumbs.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                itemsIndexed(uiState.breadcrumbs) { idx, segment ->
                                    val isLast = idx == uiState.breadcrumbs.lastIndex
                                    val label = if (idx == 0 && segment.safUri == null && segment.path != "/") {
                                        strings.rootInternal
                                    } else {
                                        segment.title
                                    }
                                    ExpressiveSurface(
                                        onClick = { viewModel.navigateToBreadcrumb(segment) },
                                        containerColor = if (isLast) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        },
                                        contentColor = if (isLast) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        restingCornerRadius = 18.dp,
                                        pressedCornerRadius = 10.dp
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            if (idx == 0) {
                                                Icon(
                                                    Icons.Rounded.Home,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    }
                                    if (!isLast) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 360.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = safeInsets.calculateStartPadding(layoutDir) + (if (isTvMode) 22.dp else 16.dp),
                        end = safeInsets.calculateEndPadding(layoutDir) + (if (isTvMode) 28.dp else 16.dp),
                        top = 6.dp,
                        bottom = safeInsets.calculateBottomPadding() + (if (useSideRail) 36.dp else 148.dp)
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(if (density == ListDensity.COMPACT) 8.dp else 10.dp)
                ) {

            // Permission Request Banner
            if (!uiState.hasStoragePermission) {
                item(key = "permission_banner", span = { GridItemSpan(maxLineSpan) }) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = strings.permissionRequiredTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text(
                                text = strings.permissionRequiredDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                            val launchedAppSettings = runCatching {
                                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                                    data = Uri.parse("package:${context.packageName}")
                                                }
                                                context.startActivity(intent)
                                            }.isSuccess
                                            if (!launchedAppSettings) {
                                                val launchedGeneralSettings = runCatching {
                                                    context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                                                }.isSuccess
                                                if (!launchedGeneralSettings) {
                                                    runCatching { safLauncher.launch(null) }
                                                }
                                            }
                                        } else {
                                            runCatching {
                                                legacyPermissionLauncher.launch(
                                                    arrayOf(
                                                        Manifest.permission.READ_EXTERNAL_STORAGE,
                                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                                    )
                                                )
                                            }.onFailure {
                                                runCatching { safLauncher.launch(null) }
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("grant_storage_permission_button")
                                ) {
                                    Text(strings.grantPermission)
                                }
                                OutlinedButton(
                                    onClick = { runCatching { safLauncher.launch(null) } },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                ) {
                                    Text(strings.chooseSafFolder)
                                }
                            }
                        }
                    }
                }
            }

            // Storage Stats (with Google Play-style wavy progress bar) & Quick Roots Strip
            item(key = "storage_strip", span = { GridItemSpan(maxLineSpan) }) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (uiState.storageStats.totalBytes > 0L) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Storage,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = strings.storageSpace,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = strings.storageAvailableOf(
                                        strings.formatBytes(uiState.storageStats.freeBytes),
                                        strings.formatBytes(uiState.storageStats.totalBytes)
                                    ),
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            ExpressiveWavyProgressIndicator(
                                progress = { animatedStorageFraction },
                                animateWhenDeterminate = false,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(uiState.storageRoots, key = { it.id }) { root ->
                                val isCurrent = uiState.currentPath == root.path
                                val rootLabel = when (root.type) {
                                    StorageRootType.INTERNAL -> strings.rootInternal
                                    StorageRootType.DOWNLOADS -> strings.rootDownloads
                                    StorageRootType.DOCUMENTS -> strings.rootDocuments
                                    StorageRootType.EXTERNAL_USB -> "${strings.rootExternalUsb}: ${root.title}"
                                    StorageRootType.SYSTEM -> if (root.path == "/") strings.rootSlash else strings.rootSystem
                                    StorageRootType.SAF_TREE -> root.title
                                }
                                ExpressiveSurface(
                                    onClick = { viewModel.navigateToRoot(root) },
                                    containerColor = if (isCurrent) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    contentColor = if (isCurrent) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    restingCornerRadius = 20.dp,
                                    pressedCornerRadius = 11.dp
                                ) {
                                    Text(
                                        text = rootLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                                    )
                                }
                            }
                            item(key = "add_saf_pill") {
                                ExpressiveSurface(
                                    onClick = { runCatching { safLauncher.launch(null) } },
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    restingCornerRadius = 20.dp,
                                    pressedCornerRadius = 11.dp,
                                    modifier = Modifier.testTag("add_saf_folder_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.FolderSpecial,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = strings.addSafFolder,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Selection Action Bar
            if (uiState.isSelectionMode) {
                item(key = "selection_bar", span = { GridItemSpan(maxLineSpan) }) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            item {
                                TextButton(onClick = { viewModel.selectAll() }) {
                                    Icon(Icons.Rounded.SelectAll, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.selectAll)
                                }
                            }
                            item {
                                TextButton(onClick = {
                                    viewModel.copyToClipboard(uiState.selectedItems, ClipboardAction.COPY)
                                }) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.copy)
                                }
                            }
                            item {
                                TextButton(onClick = {
                                    viewModel.copyToClipboard(uiState.selectedItems, ClipboardAction.CUT)
                                }) {
                                    Icon(Icons.Rounded.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.cut)
                                }
                            }
                            item {
                                TextButton(onClick = {
                                    viewModel.requestMoveToFolder(uiState.selectedItems)
                                }) {
                                    Icon(Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.move)
                                }
                            }
                            item {
                                TextButton(onClick = { compressTargets = uiState.selectedItems }) {
                                    Icon(Icons.Rounded.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.compressZip)
                                }
                            }
                            if (uiState.selectedItems.size == 1) {
                                item {
                                    TextButton(onClick = { renameTarget = uiState.selectedItems.first() }) {
                                        Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(strings.rename)
                                    }
                                }
                            }
                            item {
                                TextButton(onClick = { deleteTargets = uiState.selectedItems }) {
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

            // Active Clipboard Bar
            if (uiState.clipboard != null) {
                item(key = "clipboard_bar", span = { GridItemSpan(maxLineSpan) }) {
                    val clip = uiState.clipboard
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (clip.action == ClipboardAction.COPY) strings.readyToCopy else strings.readyToMove,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = strings.itemCountText(clip.items.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { viewModel.clearClipboard() }) {
                                    Text(strings.cancel)
                                }
                                ExpressiveSurface(
                                    onClick = { viewModel.pasteClipboardHere() },
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    restingCornerRadius = 20.dp,
                                    pressedCornerRadius = 11.dp,
                                    modifier = Modifier.testTag("paste_here_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(strings.pasteHere, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Loading / Error / Empty / File Items
            if (uiState.isLoading) {
                item(key = "explorer_loading", span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ExpressiveWavyProgressIndicator(
                            progress = null,
                            modifier = Modifier.widthIn(max = 220.dp)
                        )
                    }
                }
            } else if (uiState.errorMessage != null) {
                item(key = "explorer_error", span = { GridItemSpan(maxLineSpan) }) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.FolderOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = uiState.errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            OutlinedButton(onClick = { viewModel.navigateUp() }) {
                                Text(strings.goBack)
                            }
                        }
                    }
                }
            } else if (uiState.displayedItems.isEmpty()) {
                item(key = "explorer_empty", span = { GridItemSpan(maxLineSpan) }) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 44.dp, horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Rounded.FolderOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = strings.emptyFolder,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = strings.emptyFolderHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(uiState.displayedItems, key = { it.id }) { item ->
                    FileItemRow(
                        item = item,
                        isSelected = item.id in uiState.selectedIds,
                        isCompact = density == ListDensity.COMPACT,
                        onClick = { viewModel.onItemClick(item) },
                        onLongClick = { viewModel.toggleSelection(item) },
                        onCopy = { viewModel.copyToClipboard(listOf(item), ClipboardAction.COPY) },
                        onCut = { viewModel.copyToClipboard(listOf(item), ClipboardAction.CUT) },
                        onMove = { viewModel.requestMoveToFolder(listOf(item)) },
                        onRename = { renameTarget = item },
                        onCompress = { compressTargets = listOf(item) },
                        onExtract = { viewModel.requestExtractZip(item) },
                        onDetails = { viewModel.showDetails(item) },
                        onDelete = { deleteTargets = listOf(item) }
                    )
                }
            }
            }

                TopEdgeFadeScrim(
                    includeStatusBarInset = false,
                    scrimHeight = 14.dp,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }

        // Pure 2D Material 3 Expressive Floating Actions with Scalloped Cookie Main FAB
        val fabInteractionSource = remember { MutableInteractionSource() }
        val isFabFocused by fabInteractionSource.collectIsFocusedAsState()
        val fabFocusScale by animateFloatAsState(
            targetValue = if (expressive && isFabFocused) 1.09f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "fab_focus_scale"
        )

        Column(
            modifier = Modifier
                .align(if (useSideRail) Alignment.BottomEnd else Alignment.BottomStart)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                )
                .padding(
                    start = if (useSideRail) 0.dp else 20.dp,
                    end = if (useSideRail) (if (isTvMode) 32.dp else 20.dp) else 0.dp,
                    bottom = if (useSideRail) (if (isTvMode) 28.dp else 22.dp) else 94.dp
                ),
            horizontalAlignment = if (useSideRail) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AnimatedVisibility(
                visible = showFabMenu,
                enter = fadeIn() + scaleIn(
                    initialScale = 0.85f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                ),
                exit = fadeOut() + scaleOut(targetScale = 0.85f)
            ) {
                Column(
                    horizontalAlignment = if (useSideRail) Alignment.End else Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ExpressiveSurface(
                        onClick = {
                            showFabMenu = false
                            showCreateFolderDialog = true
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        restingCornerRadius = 22.dp,
                        pressedCornerRadius = 12.dp,
                        tonalElevation = 4.dp,
                        modifier = Modifier.testTag("fab_new_folder")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.CreateNewFolder, contentDescription = strings.newFolder)
                            Text(strings.newFolder, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }

                    ExpressiveSurface(
                        onClick = {
                            showFabMenu = false
                            showCreateFileDialog = true
                        },
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        restingCornerRadius = 22.dp,
                        pressedCornerRadius = 12.dp,
                        tonalElevation = 4.dp,
                        modifier = Modifier.testTag("fab_new_file")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.NoteAdd, contentDescription = strings.newFile)
                            Text(strings.newFile, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Surface(
                onClick = { showFabMenu = !showFabMenu },
                interactionSource = fabInteractionSource,
                shape = CookieShape10,
                color = if (isFabFocused) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                contentColor = if (isFabFocused) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
                border = if (isFabFocused) BorderStroke(2.5.dp, MaterialTheme.colorScheme.onPrimary) else null,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .size(62.dp)
                    .graphicsLayer {
                        scaleX = fabFocusScale
                        scaleY = fabFocusScale
                    }
                    .testTag("explorer_fab_main")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = strings.createItem,
                        modifier = Modifier
                            .size(28.dp)
                            .graphicsLayer { rotationZ = fabRotation }
                    )
                }
            }
        }
    }

    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { name ->
                showCreateFolderDialog = false
                viewModel.createFolder(name)
            }
        )
    }

    if (showCreateFileDialog) {
        CreateFileDialog(
            onDismiss = { showCreateFileDialog = false },
            onConfirm = { name, openAfter ->
                showCreateFileDialog = false
                viewModel.createFile(name, openAfter)
            }
        )
    }

    renameTarget?.let { target ->
        RenameDialog(
            currentName = target.name,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                renameTarget = null
                viewModel.renameItem(target, newName)
            }
        )
    }

    deleteTargets?.let { targets ->
        DeleteConfirmDialog(
            count = targets.size,
            singleName = targets.singleOrNull()?.name,
            onDismiss = { deleteTargets = null },
            onConfirm = {
                deleteTargets = null
                viewModel.deleteItems(targets)
            }
        )
    }

    compressTargets?.let { targets ->
        val suggested = if (targets.size == 1) {
            targets.first().name.substringBeforeLast('.')
        } else {
            "archive_${System.currentTimeMillis() / 1000}"
        }
        CompressZipDialog(
            suggestedName = suggested,
            onDismiss = { compressTargets = null },
            onConfirm = { archiveName ->
                compressTargets = null
                viewModel.compressItems(targets, archiveName)
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileItemRow(
    item: FileItem,
    isSelected: Boolean,
    isCompact: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onCompress: () -> Unit,
    onExtract: () -> Unit,
    onDetails: () -> Unit,
    onDelete: () -> Unit
) {
    val strings = LocalAppStrings.current
    val expressive = LocalExpressiveAnimations.current
    val hapticEnabled = LocalHapticEnabled.current
    val haptics = LocalHapticFeedback.current
    var menuExpanded by remember { mutableStateOf(false) }

    val cardInteractionSource = remember { MutableInteractionSource() }
    val isFocused by cardInteractionSource.collectIsFocusedAsState()

    val focusScale by animateFloatAsState(
        targetValue = if (expressive && isFocused) 1.02f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "file_row_focus_scale"
    )
    val focusBorderColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(durationMillis = 120),
        label = "file_row_focus_border"
    )

    val rowShape = RoundedCornerShape(if (isCompact) 14.dp else 18.dp)

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
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyUp &&
                    (keyEvent.key == Key.Menu || keyEvent.key == Key.ButtonY)
                ) {
                    menuExpanded = true
                    true
                } else {
                    false
                }
            }
            .combinedClickable(
                interactionSource = cardInteractionSource,
                indication = androidx.compose.material3.ripple(),
                onClick = onClick,
                onLongClick = {
                    if (hapticEnabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    onLongClick()
                }
            )
            .testTag("file_item_${item.name}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 12.dp,
                    vertical = if (isCompact) 7.dp else 10.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                FileCategoryBadge(
                    category = item.category,
                    size = if (isCompact) 40.dp else 46.dp
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val primaryMeta = if (item.isDirectory) {
                    item.childCount?.let { strings.itemCountText(it) } ?: strings.folder
                } else {
                    strings.formatBytes(item.sizeBytes)
                }
                Text(
                    text = "$primaryMeta • ${FormatUtils.formatDate(item.lastModifiedMillis, strings.unknownLabel)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box {
                ExpressiveIconButton(
                    onClick = { menuExpanded = true },
                    size = 48.dp,
                    containerColor = Color.Transparent,
                    contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                ) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = strings.detailsAndOpen,
                        modifier = Modifier.size(22.dp)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    DropdownMenuItem(
                        text = { Text(strings.detailsAndOpen) },
                        leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDetails()
                        }
                    )
                    if (item.category == FileCategory.ARCHIVE) {
                        DropdownMenuItem(
                            text = { Text(strings.extractArchive) },
                            leadingIcon = { Icon(Icons.Rounded.Unarchive, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onExtract()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(strings.copy) },
                        leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onCopy()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(strings.cut) },
                        leadingIcon = { Icon(Icons.Rounded.ContentCut, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onCut()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(strings.moveTo) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onMove()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(strings.compressZip) },
                        leadingIcon = { Icon(Icons.Rounded.Archive, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onCompress()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(strings.rename) },
                        leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(strings.delete, color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
