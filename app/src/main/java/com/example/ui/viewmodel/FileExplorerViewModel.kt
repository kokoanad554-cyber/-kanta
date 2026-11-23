package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ApkInspector
import com.example.data.ArchiveManager
import com.example.data.FileProTools
import com.example.data.FileRepository
import com.example.data.PreferencesRepository
import com.example.localization.AppStrings
import com.example.localization.stringsForLanguage
import com.example.model.ApkMetadata
import com.example.model.ArchiveBrowseState
import com.example.model.ArchiveEntryInfo
import com.example.model.BatchRenameRule
import com.example.model.BreadcrumbSegment
import com.example.model.ClipboardAction
import com.example.model.ClipboardState
import com.example.model.CompressionLevel
import com.example.model.FavoriteFolder
import com.example.model.FileCategory
import com.example.model.FileItem
import com.example.model.FileOpType
import com.example.model.FileOperationProgress
import com.example.model.SortOption
import com.example.model.StorageRoot
import com.example.model.StorageStats
import com.example.model.UserPreferences
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ActiveViewer {
    data class HtmlEditor(val item: FileItem) : ActiveViewer
    data class DocumentViewer(val item: FileItem, val hostArchive: FileItem? = null) : ActiveViewer
}

data class FolderPickerRequest(
    val title: String,
    val confirmLabel: String,
    val onConfirm: (destPath: String, destSafUri: Uri?) -> Unit
)

data class ExplorerUiState(
    val hasStoragePermission: Boolean = false,
    val storageStats: StorageStats = StorageStats(0L, 0L),
    val storageRoots: List<StorageRoot> = emptyList(),
    val currentPath: String = "",
    val currentSafUri: Uri? = null,
    val archiveBrowse: ArchiveBrowseState? = null,
    val navDirection: Int = 1,
    val breadcrumbs: List<BreadcrumbSegment> = emptyList(),
    val rawItems: List<FileItem> = emptyList(),
    val displayedItems: List<FileItem> = emptyList(),
    val searchQuery: String = "",
    val sortOption: SortOption = SortOption.NAME_ASC,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedIds: Set<String> = emptySet(),
    val clipboard: ClipboardState? = null,
    val operationProgress: FileOperationProgress? = null,
    val apkPreview: ApkMetadata? = null,
    val isApkLoading: Boolean = false,
    val zipPreviewItem: FileItem? = null,
    val zipPreviewEntries: List<ArchiveEntryInfo> = emptyList(),
    val isZipLoading: Boolean = false,
    val detailsItem: FileItem? = null,
    val folderPicker: FolderPickerRequest? = null,
    val activeViewer: ActiveViewer? = null,
    val snackbarMessage: String? = null
) {
    val isSelectionMode: Boolean get() = selectedIds.isNotEmpty()
    val isInsideArchive: Boolean get() = archiveBrowse != null
    val selectedItems: List<FileItem> get() = rawItems.filter { it.id in selectedIds }
}

class FileExplorerViewModel(application: Application) : AndroidViewModel(application) {

    val fileRepository = FileRepository(application.applicationContext)
    val archiveManager = ArchiveManager(application.applicationContext)
    val apkInspector = ApkInspector(application.applicationContext)
    private val preferencesRepository = PreferencesRepository(application.applicationContext)

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    private fun currentStrings(): AppStrings = stringsForLanguage(preferences.value.language)

    private val safeExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessage = throwable.message ?: currentStrings().errOpenFolder
            )
        }
    }

    private val _uiState = MutableStateFlow(
        ExplorerUiState(
            hasStoragePermission = false,
            currentPath = "",
            isLoading = true
        )
    )
    val uiState: StateFlow<ExplorerUiState> = _uiState.asStateFlow()

    private val safNavStack = mutableListOf<BreadcrumbSegment>()
    private var activeOperationJob: Job? = null
    private var loadDirJob: Job? = null
    private var childCountJob: Job? = null
    private var refreshRootsJob: Job? = null
    private var hasCompletedInitialLoad = false

    init {
        viewModelScope.launch(safeExceptionHandler) {
            var initialized = false
            var lastShowHidden = preferences.value.showHiddenFiles
            var lastSort = preferences.value.defaultSortOption
            var lastShowSystem = preferences.value.showSystemPaths
            var lastSafUris = preferences.value.safTreeUris

            preferencesRepository.preferencesFlow.collect { prefs ->
                val rootsChanged = !initialized ||
                    prefs.showSystemPaths != lastShowSystem ||
                    prefs.safTreeUris != lastSafUris
                val sortChanged = prefs.defaultSortOption != lastSort
                val hiddenChanged = prefs.showHiddenFiles != lastShowHidden

                if (rootsChanged) {
                    val roots = fileRepository.getStorageRoots(prefs.safTreeUris, prefs.showSystemPaths)
                    _uiState.update { it.copy(storageRoots = roots) }
                }

                if (!initialized) {
                    initialized = true
                    val (hasPerm, internalRoot, stats) = withContext(Dispatchers.IO) {
                        Triple(
                            fileRepository.hasStoragePermission(),
                            fileRepository.getInternalRootPath(),
                            fileRepository.getStorageStats()
                        )
                    }
                    _uiState.update {
                        it.copy(
                            hasStoragePermission = hasPerm,
                            currentPath = internalRoot,
                            storageStats = stats,
                            sortOption = prefs.defaultSortOption
                        )
                    }
                    loadDirectory(internalRoot, null)
                } else if (hiddenChanged) {
                    _uiState.update { it.copy(sortOption = prefs.defaultSortOption) }
                    refreshCurrentDirectory()
                } else if (sortChanged) {
                    updateSortOption(prefs.defaultSortOption)
                }

                lastShowHidden = prefs.showHiddenFiles
                lastSort = prefs.defaultSortOption
                lastShowSystem = prefs.showSystemPaths
                lastSafUris = prefs.safTreeUris
            }
        }
    }

    fun refreshPermissionAndRoots() {
        refreshRootsJob?.cancel()
        refreshRootsJob = viewModelScope.launch(safeExceptionHandler) {
            val prefs = preferences.value
            val prevPerm = _uiState.value.hasStoragePermission
            val (hasPerm, stats, roots) = withContext(Dispatchers.IO) {
                Triple(
                    fileRepository.hasStoragePermission(),
                    fileRepository.getStorageStats(),
                    fileRepository.getStorageRoots(prefs.safTreeUris, prefs.showSystemPaths)
                )
            }
            _uiState.update {
                it.copy(
                    hasStoragePermission = hasPerm,
                    storageStats = stats,
                    storageRoots = roots
                )
            }
            if (!prevPerm && hasPerm && hasCompletedInitialLoad) {
                refreshCurrentDirectory()
            }
        }
    }

    fun addSafTreeUri(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                getApplication<Application>().contentResolver.takePersistableUriPermission(uri, flags)
            }
            preferencesRepository.addSafTreeUri(uri.toString())
            navigateToSafRoot(uri)
        }
    }

    fun navigateToRoot(root: StorageRoot) {
        if (root.safUri != null) {
            navigateToSafRoot(root.safUri, root.title, navDirection = -1)
        } else {
            safNavStack.clear()
            loadDirectory(root.path, null, navDirection = -1)
        }
    }

    fun openFavorite(fav: FavoriteFolder) {
        val safUri = fav.safUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (safUri != null) {
            navigateToSafRoot(safUri, fav.title, navDirection = 1)
        } else {
            safNavStack.clear()
            loadDirectory(fav.path, null, navDirection = 1)
        }
    }

    fun toggleFavoriteCurrentFolder() {
        val st = _uiState.value
        if (st.archiveBrowse != null || st.currentPath.isEmpty()) return
        val title = st.breadcrumbs.lastOrNull()?.title?.takeIf { it.isNotBlank() }
            ?: File(st.currentPath).name.ifBlank { currentStrings().rootInternal }
        val fav = FavoriteFolder(
            title = title,
            path = st.currentPath,
            safUri = st.currentSafUri?.toString()
        )
        viewModelScope.launch {
            val added = preferencesRepository.toggleFavoriteFolder(fav)
            showSnackbar(if (added) currentStrings().msgFavoriteAdded else currentStrings().msgFavoriteRemoved)
        }
    }

    private fun navigateToSafRoot(uri: Uri, title: String = currentStrings().safFolderFallback, navDirection: Int = 1) {
        safNavStack.clear()
        safNavStack += BreadcrumbSegment(title = title, path = uri.toString(), safUri = uri)
        loadDirectory(uri.toString(), uri, navDirection = navDirection)
    }

    fun openFolder(item: FileItem) {
        if (!item.isDirectory) return
        val archive = _uiState.value.archiveBrowse
        if (archive != null) {
            navigateArchiveFolder(item.path, navDirection = 1)
            return
        }
        if (item.safUri != null) {
            safNavStack += BreadcrumbSegment(title = item.name, path = item.safUri.toString(), safUri = item.safUri)
            loadDirectory(item.safUri.toString(), item.safUri, navDirection = 1)
        } else {
            safNavStack.clear()
            loadDirectory(item.path, null, navDirection = 1)
        }
    }

    fun navigateToBreadcrumb(segment: BreadcrumbSegment) {
        val archive = _uiState.value.archiveBrowse
        if (archive != null && segment.path.startsWith("zipcrumb://")) {
            val inner = segment.path.removePrefix("zipcrumb://")
            navigateArchiveFolder(inner, navDirection = -1)
            return
        }
        if (segment.safUri != null) {
            val idx = safNavStack.indexOfFirst { it.safUri == segment.safUri }
            if (idx >= 0) {
                while (safNavStack.size > idx + 1) safNavStack.removeAt(safNavStack.lastIndex)
            }
            loadDirectory(segment.path, segment.safUri, navDirection = -1)
        } else {
            safNavStack.clear()
            loadDirectory(segment.path, null, navDirection = -1)
        }
    }

    fun navigateUp(): Boolean {
        val state = _uiState.value
        if (state.isSelectionMode) {
            clearSelection()
            return true
        }
        val archive = state.archiveBrowse
        if (archive != null) {
            val cleanInner = archive.innerPath.trim('/')
            if (cleanInner.isNotEmpty()) {
                val parentInner = if (cleanInner.contains('/')) cleanInner.substringBeforeLast('/') else ""
                navigateArchiveFolder(parentInner, navDirection = -1)
            } else {
                exitArchiveBrowse()
            }
            return true
        }
        if (state.currentSafUri != null) {
            return if (safNavStack.size > 1) {
                safNavStack.removeAt(safNavStack.lastIndex)
                val parent = safNavStack.last()
                loadDirectory(parent.path, parent.safUri, navDirection = -1)
                true
            } else {
                safNavStack.clear()
                loadDirectory(fileRepository.getInternalRootPath(), null, navDirection = -1)
                true
            }
        }

        val currentFile = File(state.currentPath)
        val internalRoot = fileRepository.getInternalRootPath()
        if (state.currentPath == "/" || state.currentPath == internalRoot) {
            return false
        }
        val parent = currentFile.parentFile ?: return false
        loadDirectory(parent.absolutePath, null, navDirection = -1)
        return true
    }

    fun enterArchiveBrowse(archiveItem: FileItem) {
        dismissZipPreview()
        dismissApkPreview()
        viewModelScope.launch(safeExceptionHandler) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, selectedIds = emptySet()) }
            archiveManager.inspectZip(archiveItem).fold(
                onSuccess = { entries ->
                    val newArchiveState = ArchiveBrowseState(
                        archiveItem = archiveItem,
                        innerPath = "",
                        allEntries = entries
                    )
                    applyArchiveBrowseState(newArchiveState, navDirection = 1)
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false) }
                    showSnackbar(err.message ?: currentStrings().errInspectZipFailed)
                }
            )
        }
    }

    private fun navigateArchiveFolder(innerPath: String, navDirection: Int) {
        val current = _uiState.value.archiveBrowse ?: return
        applyArchiveBrowseState(current.copy(innerPath = innerPath.trim('/')), navDirection = navDirection)
    }

    private fun exitArchiveBrowse() {
        val state = _uiState.value
        _uiState.update { it.copy(archiveBrowse = null, navDirection = -1, selectedIds = emptySet()) }
        loadDirectory(state.currentPath, state.currentSafUri, navDirection = -1)
    }

    private fun applyArchiveBrowseState(archiveState: ArchiveBrowseState, navDirection: Int) {
        val prefs = preferences.value
        val state = _uiState.value
        val items = ArchiveManager.listArchiveDirectory(
            entries = archiveState.allEntries,
            innerPath = archiveState.innerPath,
            showHidden = prefs.showHiddenFiles,
            sortOption = state.sortOption
        )
        val crumbs = buildArchiveBreadcrumbs(archiveState.archiveItem, archiveState.innerPath)
        _uiState.update {
            it.copy(
                archiveBrowse = archiveState,
                navDirection = navDirection,
                breadcrumbs = crumbs,
                rawItems = items,
                displayedItems = filterItems(items, it.searchQuery),
                isLoading = false,
                errorMessage = null,
                selectedIds = emptySet()
            )
        }
    }

    private fun buildArchiveBreadcrumbs(archiveItem: FileItem, innerPath: String): List<BreadcrumbSegment> {
        val list = mutableListOf(BreadcrumbSegment(archiveItem.name, "zipcrumb://"))
        val clean = innerPath.trim('/')
        if (clean.isNotEmpty()) {
            var acc = ""
            clean.split('/').forEach { seg ->
                acc = if (acc.isEmpty()) seg else "$acc/$seg"
                list += BreadcrumbSegment(seg, "zipcrumb://$acc")
            }
        }
        return list
    }

    fun refreshCurrentDirectory() {
        val state = _uiState.value
        val archive = state.archiveBrowse
        if (archive != null) {
            viewModelScope.launch(safeExceptionHandler) {
                archiveManager.inspectZip(archive.archiveItem).onSuccess { entries ->
                    applyArchiveBrowseState(archive.copy(allEntries = entries), navDirection = 0)
                }
            }
            return
        }
        loadDirectory(state.currentPath, state.currentSafUri, navDirection = 0)
    }

    fun loadDirectory(path: String, safUri: Uri?, navDirection: Int = 1) {
        if (path.isEmpty() && safUri == null) return
        loadDirJob?.cancel()
        childCountJob?.cancel()
        loadDirJob = viewModelScope.launch(safeExceptionHandler) {
            _uiState.update {
                it.copy(
                    archiveBrowse = null,
                    navDirection = navDirection,
                    isLoading = true,
                    errorMessage = null,
                    selectedIds = emptySet()
                )
            }
            val prefs = preferences.value
            val result = fileRepository.listDirectory(
                path = path,
                safUri = safUri,
                showHidden = prefs.showHiddenFiles,
                sortOption = _uiState.value.sortOption
            )
            hasCompletedInitialLoad = true
            result.fold(
                onSuccess = { items ->
                    val crumbs = if (safUri != null) {
                        safNavStack.toList()
                    } else {
                        buildPathBreadcrumbs(path)
                    }
                    val stats = fileRepository.getStorageStats()
                    _uiState.update { state ->
                        val filtered = filterItems(items, state.searchQuery)
                        state.copy(
                            currentPath = path,
                            currentSafUri = safUri,
                            breadcrumbs = crumbs,
                            rawItems = items,
                            displayedItems = filtered,
                            isLoading = false,
                            errorMessage = null,
                            storageStats = stats
                        )
                    }
                    // Populate subfolder item counts lazily off the main thread
                    if (safUri == null && items.any { it.isDirectory }) {
                        childCountJob = viewModelScope.launch(safeExceptionHandler) {
                            val counts = fileRepository.resolveSubfolderCounts(items)
                            if (counts.isNotEmpty()) {
                                _uiState.update { st ->
                                    if (st.currentPath != path) return@update st
                                    val updatedRaw = st.rawItems.map { item ->
                                        val c = counts[item.id]
                                        if (c != null) item.copy(childCount = c) else item
                                    }
                                    st.copy(
                                        rawItems = updatedRaw,
                                        displayedItems = filterItems(updatedRaw, st.searchQuery)
                                    )
                                }
                            }
                        }
                    }
                },
                onFailure = { err ->
                    val crumbs = if (safUri != null) safNavStack.toList() else buildPathBreadcrumbs(path)
                    _uiState.update {
                        it.copy(
                            currentPath = path,
                            currentSafUri = safUri,
                            breadcrumbs = crumbs,
                            rawItems = emptyList(),
                            displayedItems = emptyList(),
                            isLoading = false,
                            errorMessage = err.message ?: currentStrings().errOpenFolder
                        )
                    }
                }
            )
        }
    }

    private fun buildPathBreadcrumbs(path: String): List<BreadcrumbSegment> {
        val internalRoot = fileRepository.getInternalRootPath()
        if (path == internalRoot || path.startsWith("$internalRoot/")) {
            val list = mutableListOf(BreadcrumbSegment(currentStrings().rootInternal, internalRoot))
            val rel = path.removePrefix(internalRoot).trim('/')
            if (rel.isNotEmpty()) {
                var acc = internalRoot
                rel.split('/').forEach { seg ->
                    acc = "$acc/$seg"
                    list += BreadcrumbSegment(seg, acc)
                }
            }
            return list
        }
        val list = mutableListOf(BreadcrumbSegment("/", "/"))
        val parts = path.trim('/').split('/').filter { it.isNotEmpty() }
        var acc = ""
        parts.forEach { part ->
            acc = "$acc/$part"
            list += BreadcrumbSegment(part, acc)
        }
        return list
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                displayedItems = filterItems(state.rawItems, query)
            )
        }
    }

    fun updateSortOption(option: SortOption) {
        _uiState.update { state ->
            val sorted = fileRepository.sortItems(state.rawItems, option)
            state.copy(
                sortOption = option,
                rawItems = sorted,
                displayedItems = filterItems(sorted, state.searchQuery)
            )
        }
    }

    private fun filterItems(items: List<FileItem>, query: String): List<FileItem> {
        val q = query.trim()
        if (q.isEmpty()) return items
        return items.filter { it.name.contains(q, ignoreCase = true) }
    }

    fun toggleSelection(item: FileItem) {
        _uiState.update { state ->
            val updated = if (item.id in state.selectedIds) {
                state.selectedIds - item.id
            } else {
                state.selectedIds + item.id
            }
            state.copy(selectedIds = updated)
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            state.copy(selectedIds = state.displayedItems.map { it.id }.toSet())
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    fun onItemClick(item: FileItem) {
        if (_uiState.value.isSelectionMode) {
            toggleSelection(item)
            return
        }
        if (item.isDirectory) {
            openFolder(item)
            return
        }
        openFileItem(item)
    }

    fun openFileItem(item: FileItem) {
        val archive = _uiState.value.archiveBrowse
        if (archive != null) {
            val previewableMedia = (item.category == FileCategory.IMAGE || item.category == FileCategory.PDF) &&
                item.sizeBytes in 1..10_000_000L
            if (item.category == FileCategory.TEXT || item.category == FileCategory.CODE ||
                item.category == FileCategory.HTML || previewableMedia || item.sizeBytes in 1..512_000L
            ) {
                _uiState.update { it.copy(activeViewer = ActiveViewer.DocumentViewer(item, hostArchive = archive.archiveItem)) }
            } else {
                requestExtractSelectedFromArchive(listOf(item))
            }
            return
        }
        when (item.category) {
            FileCategory.APK -> inspectApk(item)
            FileCategory.ARCHIVE -> inspectZip(item)
            FileCategory.HTML -> _uiState.update { it.copy(activeViewer = ActiveViewer.HtmlEditor(item)) }
            FileCategory.TEXT, FileCategory.CODE, FileCategory.PDF, FileCategory.IMAGE -> {
                _uiState.update { it.copy(activeViewer = ActiveViewer.DocumentViewer(item)) }
            }
            else -> {
                fileRepository.openExternally(item).onFailure {
                    if (item.sizeBytes in 1..2_000_000) {
                        _uiState.update { s -> s.copy(activeViewer = ActiveViewer.DocumentViewer(item)) }
                    } else {
                        showSnackbar(currentStrings().errNoAppForFile)
                    }
                }
            }
        }
    }

    fun openAsText(item: FileItem) {
        val host = _uiState.value.archiveBrowse?.archiveItem
        _uiState.update { it.copy(activeViewer = ActiveViewer.DocumentViewer(item, hostArchive = host)) }
    }

    fun openInHtmlEditor(item: FileItem) {
        _uiState.update { it.copy(activeViewer = ActiveViewer.HtmlEditor(item)) }
    }

    fun closeActiveViewer() {
        _uiState.update { it.copy(activeViewer = null) }
        refreshCurrentDirectory()
    }

    fun createFolder(name: String) {
        val state = _uiState.value
        val s = currentStrings()
        viewModelScope.launch {
            fileRepository.createFolder(state.currentPath, state.currentSafUri, name).fold(
                onSuccess = {
                    showSnackbar(s.msgFolderCreated)
                    refreshCurrentDirectory()
                },
                onFailure = { showSnackbar(it.message ?: s.errCreateFolder) }
            )
        }
    }

    fun createFile(name: String, openAfterCreate: Boolean = false) {
        val state = _uiState.value
        val s = currentStrings()
        viewModelScope.launch {
            fileRepository.createFile(state.currentPath, state.currentSafUri, name).fold(
                onSuccess = { created ->
                    showSnackbar(s.msgFileCreated)
                    refreshCurrentDirectory()
                    if (openAfterCreate) {
                        openFileItem(created)
                    }
                },
                onFailure = { showSnackbar(it.message ?: s.errCreateFile) }
            )
        }
    }

    fun renameItem(item: FileItem, newName: String) {
        val archive = _uiState.value.archiveBrowse
        val s = currentStrings()
        if (archive != null) {
            if (!archive.isEditableZip) return
            viewModelScope.launch {
                archiveManager.renameEntryInZip(archive.archiveItem, item, newName).fold(
                    onSuccess = {
                        clearSelection()
                        showSnackbar(s.msgArchiveUpdated)
                        refreshCurrentDirectory()
                    },
                    onFailure = { showSnackbar(it.message ?: s.errRename) }
                )
            }
            return
        }
        viewModelScope.launch {
            fileRepository.renameItem(item, newName).fold(
                onSuccess = {
                    clearSelection()
                    showSnackbar(s.msgRenamed)
                    refreshCurrentDirectory()
                },
                onFailure = { showSnackbar(it.message ?: s.errRename) }
            )
        }
    }

    fun batchRenameItems(items: List<FileItem>, rule: BatchRenameRule) {
        if (items.isEmpty()) return
        val s = currentStrings()
        val plan = FileProTools.buildBatchRenamePlan(items, rule)
        runOperation {
            var renamedCount = 0
            plan.forEachIndexed { idx, (item, newName) ->
                if (item.name != newName) {
                    _uiState.update {
                        it.copy(
                            operationProgress = FileOperationProgress(
                                opType = FileOpType.MOVE,
                                currentFileName = newName,
                                processedCount = idx + 1,
                                totalCount = plan.size
                            )
                        )
                    }
                    fileRepository.renameItem(item, newName).onSuccess { renamedCount++ }
                }
            }
            _uiState.update { it.copy(operationProgress = null, selectedIds = emptySet()) }
            showSnackbar(s.msgBatchRenamed(renamedCount))
            refreshCurrentDirectory()
        }
    }

    fun copyToClipboard(items: List<FileItem>, action: ClipboardAction) {
        if (items.isEmpty()) return
        val s = currentStrings()
        _uiState.update {
            it.copy(
                clipboard = ClipboardState(items = items, action = action),
                selectedIds = emptySet(),
                snackbarMessage = if (action == ClipboardAction.COPY) {
                    s.msgCopiedToClipboard(items.size)
                } else {
                    s.msgCutToClipboard(items.size)
                }
            )
        }
    }

    fun clearClipboard() {
        _uiState.update { it.copy(clipboard = null) }
    }

    fun pasteClipboardHere() {
        val state = _uiState.value
        val clip = state.clipboard ?: return
        val archive = state.archiveBrowse
        if (archive != null) {
            if (!archive.isEditableZip) {
                showSnackbar(currentStrings().errOperationFailed)
                return
            }
            val externalItems = clip.items.filter { !it.id.startsWith(ArchiveManager.ARCHIVE_ENTRY_PREFIX) }
            if (externalItems.isEmpty()) {
                clearClipboard()
                return
            }
            runOperation {
                _uiState.update {
                    it.copy(
                        operationProgress = FileOperationProgress(
                            opType = FileOpType.COMPRESS,
                            currentFileName = externalItems.first().name,
                            isIndeterminate = true
                        )
                    )
                }
                archiveManager.addItemsToZip(
                    zipItem = archive.archiveItem,
                    sources = externalItems,
                    targetInnerDir = archive.innerPath
                ) { progress ->
                    _uiState.update { it.copy(operationProgress = progress) }
                }.fold(
                    onSuccess = {
                        _uiState.update { it.copy(clipboard = null, operationProgress = null) }
                        showSnackbar(currentStrings().msgArchiveUpdated)
                        refreshCurrentDirectory()
                    },
                    onFailure = { err ->
                        _uiState.update { it.copy(operationProgress = null) }
                        showSnackbar(err.message ?: currentStrings().errCompressFailed)
                    }
                )
            }
            return
        }

        val isMove = clip.action == ClipboardAction.CUT
        runOperation {
            _uiState.update {
                it.copy(
                    operationProgress = FileOperationProgress(
                        opType = if (isMove) FileOpType.MOVE else FileOpType.COPY,
                        currentFileName = clip.items.firstOrNull()?.name ?: "",
                        isIndeterminate = true
                    )
                )
            }
            fileRepository.copyOrMoveItems(
                items = clip.items,
                destPath = state.currentPath,
                destSafUri = state.currentSafUri,
                isMove = isMove
            ) { progress ->
                _uiState.update { it.copy(operationProgress = progress) }
            }.fold(
                onSuccess = {
                    _uiState.update { it.copy(clipboard = null, operationProgress = null) }
                    showSnackbar(if (isMove) currentStrings().msgMovedSuccess else currentStrings().msgCopiedSuccess)
                    refreshCurrentDirectory()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(operationProgress = null) }
                    showSnackbar(err.message ?: currentStrings().errOperationFailed)
                }
            )
        }
    }

    fun requestMoveToFolder(items: List<FileItem>) {
        if (items.isEmpty()) return
        val s = currentStrings()
        _uiState.update {
            it.copy(
                folderPicker = FolderPickerRequest(
                    title = s.chooseMoveFolder,
                    confirmLabel = s.moveHere,
                    onConfirm = { destPath, destSafUri ->
                        _uiState.update { st -> st.copy(folderPicker = null, selectedIds = emptySet()) }
                        runOperation {
                            fileRepository.copyOrMoveItems(
                                items = items,
                                destPath = destPath,
                                destSafUri = destSafUri,
                                isMove = true
                            ) { progress ->
                                _uiState.update { st -> st.copy(operationProgress = progress) }
                            }.fold(
                                onSuccess = {
                                    _uiState.update { st -> st.copy(operationProgress = null) }
                                    showSnackbar(currentStrings().msgMovedSuccess)
                                    refreshCurrentDirectory()
                                },
                                onFailure = { err ->
                                    _uiState.update { st -> st.copy(operationProgress = null) }
                                    showSnackbar(err.message ?: currentStrings().errMoveFailed)
                                }
                            )
                        }
                    }
                )
            )
        }
    }

    fun deleteItems(items: List<FileItem>) {
        if (items.isEmpty()) return
        val archive = _uiState.value.archiveBrowse
        if (archive != null) {
            if (!archive.isEditableZip) {
                showSnackbar(currentStrings().errDeleteFailed)
                return
            }
            runOperation {
                archiveManager.deleteEntriesFromZip(archive.archiveItem, items) { progress ->
                    _uiState.update { it.copy(operationProgress = progress) }
                }.fold(
                    onSuccess = {
                        _uiState.update { it.copy(operationProgress = null, selectedIds = emptySet()) }
                        showSnackbar(currentStrings().msgArchiveUpdated)
                        refreshCurrentDirectory()
                    },
                    onFailure = { err ->
                        _uiState.update { it.copy(operationProgress = null) }
                        showSnackbar(err.message ?: currentStrings().errDeleteFailed)
                    }
                )
            }
            return
        }
        runOperation {
            fileRepository.deleteItems(items) { progress ->
                _uiState.update { it.copy(operationProgress = progress) }
            }.fold(
                onSuccess = {
                    _uiState.update { it.copy(operationProgress = null, selectedIds = emptySet()) }
                    showSnackbar(currentStrings().msgDeletedSuccess)
                    refreshCurrentDirectory()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(operationProgress = null) }
                    showSnackbar(err.message ?: currentStrings().errDeleteFailed)
                }
            )
        }
    }

    fun compressItems(
        items: List<FileItem>,
        archiveName: String,
        level: CompressionLevel = CompressionLevel.NORMAL
    ) {
        if (items.isEmpty()) return
        val state = _uiState.value
        runOperation {
            archiveManager.compressToZip(
                sources = items,
                destDirPath = state.currentPath,
                destSafUri = state.currentSafUri,
                archiveName = archiveName,
                level = level
            ) { progress ->
                _uiState.update { it.copy(operationProgress = progress) }
            }.fold(
                onSuccess = {
                    _uiState.update { it.copy(operationProgress = null, selectedIds = emptySet()) }
                    showSnackbar(currentStrings().msgCompressedSuccess)
                    refreshCurrentDirectory()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(operationProgress = null) }
                    showSnackbar(err.message ?: currentStrings().errCompressFailed)
                }
            )
        }
    }

    fun testArchiveIntegrity(zipItem: FileItem) {
        dismissZipPreview()
        runOperation {
            archiveManager.testArchiveIntegrity(zipItem) { progress ->
                _uiState.update { it.copy(operationProgress = progress) }
            }.fold(
                onSuccess = { res ->
                    _uiState.update { it.copy(operationProgress = null) }
                    showSnackbar(currentStrings().msgArchiveVerified(res.verifiedEntries))
                },
                onFailure = { err ->
                    _uiState.update { it.copy(operationProgress = null) }
                    showSnackbar(err.message ?: currentStrings().errInspectZipFailed)
                }
            )
        }
    }

    fun requestExtractSelectedFromArchive(selectedVirtualItems: List<FileItem>) {
        val archiveItem = _uiState.value.archiveBrowse?.archiveItem ?: return
        if (selectedVirtualItems.isEmpty()) return
        val s = currentStrings()
        _uiState.update {
            it.copy(
                folderPicker = FolderPickerRequest(
                    title = s.chooseExtractFolder,
                    confirmLabel = s.extractHere,
                    onConfirm = { destPath, destSafUri ->
                        _uiState.update { st -> st.copy(folderPicker = null, selectedIds = emptySet()) }
                        runOperation {
                            archiveManager.extractEntries(
                                zipItem = archiveItem,
                                selectedVirtualItems = selectedVirtualItems,
                                destDirPath = destPath,
                                destSafUri = destSafUri
                            ) { progress ->
                                _uiState.update { st -> st.copy(operationProgress = progress) }
                            }.fold(
                                onSuccess = {
                                    _uiState.update { st -> st.copy(operationProgress = null) }
                                    showSnackbar(currentStrings().msgExtractedSuccess)
                                },
                                onFailure = { err ->
                                    _uiState.update { st -> st.copy(operationProgress = null) }
                                    showSnackbar(err.message ?: currentStrings().errExtractFailed)
                                }
                            )
                        }
                    }
                )
            )
        }
    }

    fun inspectZip(item: FileItem) {
        _uiState.update { it.copy(zipPreviewItem = item, isZipLoading = true, zipPreviewEntries = emptyList()) }
        viewModelScope.launch {
            archiveManager.inspectZip(item).fold(
                onSuccess = { entries ->
                    _uiState.update { it.copy(isZipLoading = false, zipPreviewEntries = entries) }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isZipLoading = false, zipPreviewItem = null) }
                    showSnackbar(err.message ?: currentStrings().errInspectZipFailed)
                }
            )
        }
    }

    fun dismissZipPreview() {
        _uiState.update { it.copy(zipPreviewItem = null, zipPreviewEntries = emptyList()) }
    }

    fun requestExtractZip(zipItem: FileItem) {
        dismissZipPreview()
        val s = currentStrings()
        _uiState.update {
            it.copy(
                folderPicker = FolderPickerRequest(
                    title = s.chooseExtractFolder,
                    confirmLabel = s.extractHere,
                    onConfirm = { destPath, destSafUri ->
                        _uiState.update { st -> st.copy(folderPicker = null) }
                        runOperation {
                            archiveManager.extractZip(zipItem, destPath, destSafUri) { progress ->
                                _uiState.update { st -> st.copy(operationProgress = progress) }
                            }.fold(
                                onSuccess = {
                                    _uiState.update { st -> st.copy(operationProgress = null) }
                                    showSnackbar(currentStrings().msgExtractedSuccess)
                                    refreshCurrentDirectory()
                                },
                                onFailure = { err ->
                                    _uiState.update { st -> st.copy(operationProgress = null) }
                                    showSnackbar(err.message ?: currentStrings().errExtractFailed)
                                }
                            )
                        }
                    }
                )
            )
        }
    }

    fun dismissFolderPicker() {
        _uiState.update { it.copy(folderPicker = null) }
    }

    fun inspectApk(item: FileItem) {
        _uiState.update { it.copy(isApkLoading = true, apkPreview = null) }
        viewModelScope.launch {
            apkInspector.inspectApk(item).fold(
                onSuccess = { meta ->
                    _uiState.update { it.copy(isApkLoading = false, apkPreview = meta) }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isApkLoading = false) }
                    showSnackbar(err.message ?: currentStrings().errInspectApkFailed)
                }
            )
        }
    }

    fun dismissApkPreview() {
        _uiState.update { it.copy(apkPreview = null) }
    }

    fun installApk(metadata: ApkMetadata) {
        if (!apkInspector.canRequestPackageInstalls()) {
            showSnackbar(currentStrings().msgAllowApkInstall)
            apkInspector.openInstallPermissionSettings().onFailure {
                showSnackbar(currentStrings().errLaunchInstaller)
            }
            return
        }
        apkInspector.launchInstaller(metadata).onFailure {
            showSnackbar(currentStrings().errLaunchInstaller)
        }
    }

    fun showDetails(item: FileItem) {
        _uiState.update { it.copy(detailsItem = item) }
    }

    fun dismissDetails() {
        _uiState.update { it.copy(detailsItem = null) }
    }

    fun cancelActiveOperation() {
        activeOperationJob?.cancel()
        activeOperationJob = null
        _uiState.update { it.copy(operationProgress = null, snackbarMessage = currentStrings().msgOperationCancelled) }
        refreshCurrentDirectory()
    }

    private fun runOperation(block: suspend () -> Unit) {
        activeOperationJob?.cancel()
        activeOperationJob = viewModelScope.launch {
            block()
        }
    }

    fun showSnackbar(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
