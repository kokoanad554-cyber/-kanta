package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
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
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.ClipboardAction
import com.example.model.FileCategory
import com.example.model.FileItem
import com.example.model.SortOption
import com.example.ui.components.CompressZipDialog
import com.example.ui.components.CreateFileDialog
import com.example.ui.components.CreateFolderDialog
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.FileCategoryBadge
import com.example.ui.components.RenameDialog
import com.example.ui.theme.JetBrainsMonoFontFamily
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

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 148.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top bar with comfortable edge separation
            item {
                Spacer(modifier = Modifier.statusBarsPadding())
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (uiState.breadcrumbs.size > 1 || uiState.isSelectionMode) {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        IconButton(
                                            onClick = { viewModel.navigateUp() },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .testTag("explorer_back_button")
                                        ) {
                                            Icon(
                                                imageVector = if (uiState.isSelectionMode) {
                                                    Icons.Rounded.Close
                                                } else {
                                                    Icons.AutoMirrored.Rounded.ArrowBack
                                                },
                                                contentDescription = "رجوع"
                                            )
                                        }
                                    }
                                }
                                Column {
                                    Text(
                                        text = if (uiState.isSelectionMode) {
                                            "تم تحديد ${uiState.selectedIds.size}"
                                        } else {
                                            uiState.breadcrumbs.lastOrNull()?.title ?: "Kanta ملفات"
                                        },
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = FormatUtils.formatCountItems(uiState.displayedItems.size),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        showSearch = !showSearch
                                        if (!showSearch) viewModel.updateSearchQuery("")
                                    },
                                    modifier = Modifier.testTag("toggle_search_button")
                                ) {
                                    Icon(Icons.Rounded.Search, contentDescription = "بحث")
                                }
                                Box {
                                    IconButton(
                                        onClick = { showSortMenu = true },
                                        modifier = Modifier.testTag("sort_menu_button")
                                    ) {
                                        Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "ترتيب")
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
                                                        text = option.labelAr,
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
                                IconButton(
                                    onClick = { safLauncher.launch(null) },
                                    modifier = Modifier.testTag("add_saf_folder_button")
                                ) {
                                    Icon(Icons.Rounded.FolderSpecial, contentDescription = "إضافة مجلد SAF")
                                }
                                IconButton(onClick = { viewModel.refreshCurrentDirectory() }) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = "تحديث")
                                }
                            }
                        }

                        AnimatedVisibility(visible = showSearch) {
                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = { viewModel.updateSearchQuery(it) },
                                placeholder = { Text("ابحث في هذا المجلد...") },
                                singleLine = true,
                                trailingIcon = {
                                    if (uiState.searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                            Icon(Icons.Rounded.Close, contentDescription = "مسح")
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Breadcrumbs Row
                        if (uiState.breadcrumbs.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                itemsIndexed(uiState.breadcrumbs) { idx, segment ->
                                    val isLast = idx == uiState.breadcrumbs.lastIndex
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isLast) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        },
                                        modifier = Modifier
                                            .heightIn(min = 36.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { viewModel.navigateToBreadcrumb(segment) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (idx == 0) {
                                                Icon(
                                                    Icons.Rounded.Home,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = if (isLast) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    }
                                                )
                                            }
                                            Text(
                                                text = segment.title,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isLast) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                }
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
            }

            // Permission Request Banner if not granted
            if (!uiState.hasStoragePermission) {
                item {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
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
                                    text = "صلاحية إدارة كل الملفات مطلوبة",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text(
                                text = "للوصول إلى جميع ملفات الذاكرة الداخلية ومجلدات النظام، امنح صلاحية الوصول الكامل للملفات أو اختر مجلداً عبر SAF.",
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
                                            runCatching {
                                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                                    data = Uri.parse("package:${context.packageName}")
                                                }
                                                context.startActivity(intent)
                                            }.onFailure {
                                                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                                            }
                                        } else {
                                            legacyPermissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.READ_EXTERNAL_STORAGE,
                                                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                                                )
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("grant_storage_permission_button")
                                ) {
                                    Text("منح الصلاحية")
                                }
                                OutlinedButton(
                                    onClick = { safLauncher.launch(null) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                ) {
                                    Text("اختيار مجلد SAF")
                                }
                            }
                        }
                    }
                }
            }

            // Storage Capacity & Roots Strip
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
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
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "مساحة التخزين",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = "متاح ${FormatUtils.formatBytes(uiState.storageStats.freeBytes)} من ${FormatUtils.formatBytes(uiState.storageStats.totalBytes)}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            LinearProgressIndicator(
                                progress = { uiState.storageStats.usedFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(uiState.storageRoots, key = { it.id }) { root ->
                                val isCurrent = uiState.currentPath == root.path
                                FilterChip(
                                    selected = isCurrent,
                                    onClick = { viewModel.navigateToRoot(root) },
                                    label = { Text(root.title) }
                                )
                            }
                        }
                    }
                }
            }

            // Selection Action Bar
            if (uiState.isSelectionMode) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
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
                                    Text("تحديد الكل")
                                }
                            }
                            item {
                                TextButton(onClick = {
                                    viewModel.copyToClipboard(uiState.selectedItems, ClipboardAction.COPY)
                                }) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("نسخ")
                                }
                            }
                            item {
                                TextButton(onClick = {
                                    viewModel.copyToClipboard(uiState.selectedItems, ClipboardAction.CUT)
                                }) {
                                    Icon(Icons.Rounded.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("قص")
                                }
                            }
                            item {
                                TextButton(onClick = {
                                    viewModel.requestMoveToFolder(uiState.selectedItems)
                                }) {
                                    Icon(Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("نقل")
                                }
                            }
                            item {
                                TextButton(onClick = {
                                    compressTargets = uiState.selectedItems
                                }) {
                                    Icon(Icons.Rounded.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("ضغط ZIP")
                                }
                            }
                            if (uiState.selectedItems.size == 1) {
                                item {
                                    TextButton(onClick = {
                                        renameTarget = uiState.selectedItems.first()
                                    }) {
                                        Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("تسمية")
                                    }
                                }
                            }
                            item {
                                TextButton(onClick = {
                                    deleteTargets = uiState.selectedItems
                                }) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("حذف", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            // Active Clipboard Bar
            if (uiState.clipboard != null) {
                item {
                    val clip = uiState.clipboard
                    Surface(
                        shape = RoundedCornerShape(20.dp),
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
                                    text = if (clip.action == ClipboardAction.COPY) "جاهز للنسخ" else "جاهز للنقل",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = FormatUtils.formatCountItems(clip.items.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { viewModel.clearClipboard() }) {
                                    Text("إلغاء")
                                }
                                Button(
                                    onClick = { viewModel.pasteClipboardHere() },
                                    modifier = Modifier.testTag("paste_here_button")
                                ) {
                                    Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("لصق هنا")
                                }
                            }
                        }
                    }
                }
            }

            // Loading / Error / Empty / File Items
            if (uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (uiState.errorMessage != null) {
                item {
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
                                Text("العودة للخلف")
                            }
                        }
                    }
                }
            } else if (uiState.displayedItems.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
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
                                text = "المجلد فارغ",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "يمكنك إنشاء مجلد أو ملف جديد باستخدام زر الإضافة بالأسفل.",
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

        // Floating Add Actions positioned comfortably above the Floating Capsule Nav Bar
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 20.dp, bottom = 94.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AnimatedVisibility(
                visible = showFabMenu,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmallFloatingActionButton(
                        onClick = {
                            showFabMenu = false
                            showCreateFolderDialog = true
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.testTag("fab_new_folder")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.CreateNewFolder, contentDescription = "مجلد جديد")
                            Text("مجلد جديد", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    SmallFloatingActionButton(
                        onClick = {
                            showFabMenu = false
                            showCreateFileDialog = true
                        },
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.testTag("fab_new_file")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.NoteAdd, contentDescription = "ملف جديد")
                            Text("ملف جديد", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = { showFabMenu = !showFabMenu },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.testTag("explorer_fab_main")
            ) {
                Icon(
                    imageVector = if (showFabMenu) Icons.Rounded.Close else Icons.Rounded.Add,
                    contentDescription = "إنشاء عنصر"
                )
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
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        tonalElevation = if (isSelected) 4.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("file_item_${item.name}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                FileCategoryBadge(category = item.category, size = 46.dp)
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "محدد",
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
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val primaryMeta = if (item.isDirectory) {
                        item.childCount?.let { FormatUtils.formatCountItems(it) } ?: "مجلد"
                    } else {
                        FormatUtils.formatBytes(item.sizeBytes)
                    }
                    Text(
                        text = "$primaryMeta • ${FormatUtils.formatDate(item.lastModifiedMillis)}",
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
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = "خيارات",
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    DropdownMenuItem(
                        text = { Text("الخصائص والفتح") },
                        leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDetails()
                        }
                    )
                    if (item.category == FileCategory.ARCHIVE) {
                        DropdownMenuItem(
                            text = { Text("استخراج الأرشيف") },
                            leadingIcon = { Icon(Icons.Rounded.Unarchive, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onExtract()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("نسخ") },
                        leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onCopy()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("قص") },
                        leadingIcon = { Icon(Icons.Rounded.ContentCut, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onCut()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("نقل إلى...") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onMove()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("ضغط إلى ZIP") },
                        leadingIcon = { Icon(Icons.Rounded.Archive, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onCompress()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("إعادة تسمية") },
                        leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("حذف", color = MaterialTheme.colorScheme.error) },
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
