package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ApkInspector
import com.example.data.ArchiveManager
import com.example.data.FileRepository
import com.example.data.PreferencesRepository
import com.example.localization.AppStrings
import com.example.localization.stringsForLanguage
import com.example.model.ApkMetadata
import com.example.model.ArchiveEntryInfo
import com.example.model.BreadcrumbSegment
import com.example.model.ClipboardAction
import com.example.model.ClipboardState
import com.example.model.FileCategory
import com.example.model.FileItem
import com.example.model.FileOpType
import com.example.model.FileOperationProgress
import com.example.model.SortOption
import com.example.model.StorageRoot
import com.example.model.StorageStats
import com.example.model.UserPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

sealed interface ActiveViewer {
    data class HtmlEditor(val item: FileItem) : ActiveViewer
    data class DocumentViewer(val item: FileItem) : ActiveViewer
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
    val selectedItems: List<FileItem> get() = rawItems.filter { it.id in selectedIds }
}

class FileExplorerViewModel(application: Application) : AndroidViewModel(application) {

    val fileRepository = FileRepository(application.applicationContext)
    private val archiveManager = ArchiveManager(application.applicationContext)
    val apkInspector = ApkInspector(application.applicationContext)
    private val preferencesRepository = PreferencesRepository(application.applicationContext)

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    private fun currentStrings(): AppStrings = stringsForLanguage(preferences.value.language)

    private val _uiState = MutableStateFlow(
        ExplorerUiState(
            hasStoragePermission = fileRepository.hasStoragePermission(),
            currentPath = fileRepository.getInternalRootPath()
        )
    )
    val uiState: StateFlow<ExplorerUiState> = _uiState.asStateFlow()

    private val safNavStack = mutableListOf<BreadcrumbSegment>()
    private var activeOperationJob: Job? = null

    init {
        refreshPermissionAndRoots()
        viewModelScope.launch {
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
                    _uiState.update { it.copy(sortOption = prefs.defaultSortOption) }
                    loadDirectory(fileRepository.getInternalRootPath(), null)
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
        val prefs = preferences.value
        val hasPerm = fileRepository.hasStoragePermission()
        val stats = fileRepository.getStorageStats()
        val roots = fileRepository.getStorageRoots(prefs.safTreeUris, prefs.showSystemPaths)
        _uiState.update {
            it.copy(
                hasStoragePermission = hasPerm,
                storageStats = stats,
                storageRoots = roots
            )
        }
        if (hasPerm) {
            refreshCurrentDirectory()
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
            navigateToSafRoot(root.safUri, root.title)
        } else {
            safNavStack.clear()
            loadDirectory(root.path, null)
        }
    }

    private fun navigateToSafRoot(uri: Uri, title: String = currentStrings().safFolderFallback) {
        safNavStack.clear()
        safNavStack += BreadcrumbSegment(title = title, path = uri.toString(), safUri = uri)
        loadDirectory(uri.toString(), uri)
    }

    fun openFolder(item: FileItem) {
        if (!item.isDirectory) return
        if (item.safUri != null) {
            safNavStack += BreadcrumbSegment(title = item.name, path = item.safUri.toString(), safUri = item.safUri)
            loadDirectory(item.safUri.toString(), item.safUri)
        } else {
            safNavStack.clear()
            loadDirectory(item.path, null)
        }
    }

    fun navigateToBreadcrumb(segment: BreadcrumbSegment) {
        if (segment.safUri != null) {
            val idx = safNavStack.indexOfFirst { it.safUri == segment.safUri }
            if (idx >= 0) {
                while (safNavStack.size > idx + 1) safNavStack.removeAt(safNavStack.lastIndex)
            }
            loadDirectory(segment.path, segment.safUri)
        } else {
            safNavStack.clear()
            loadDirectory(segment.path, null)
        }
    }

    fun navigateUp(): Boolean {
        val state = _uiState.value
        if (state.isSelectionMode) {
            clearSelection()
            return true
        }
        if (state.currentSafUri != null) {
            return if (safNavStack.size > 1) {
                safNavStack.removeAt(safNavStack.lastIndex)
                val parent = safNavStack.last()
                loadDirectory(parent.path, parent.safUri)
                true
            } else {
                safNavStack.clear()
                loadDirectory(fileRepository.getInternalRootPath(), null)
                true
            }
        }

        val currentFile = File(state.currentPath)
        val internalRoot = fileRepository.getInternalRootPath()
        if (state.currentPath == "/" || state.currentPath == internalRoot) {
            return false
        }
        val parent = currentFile.parentFile ?: return false
        loadDirectory(parent.absolutePath, null)
        return true
    }

    fun refreshCurrentDirectory() {
        val state = _uiState.value
        loadDirectory(state.currentPath, state.currentSafUri)
    }

    fun loadDirectory(path: String, safUri: Uri?) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
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
            result.fold(
                onSuccess = { items ->
                    val crumbs = if (safUri != null) {
                        safNavStack.toList()
                    } else {
                        buildPathBreadcrumbs(path)
                    }
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
                            storageStats = fileRepository.getStorageStats()
                        )
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
        when (item.category) {
            FileCategory.APK -> inspectApk(item)
            FileCategory.ARCHIVE -> inspectZip(item)
            FileCategory.HTML -> _uiState.update { it.copy(activeViewer = ActiveViewer.HtmlEditor(item)) }
            FileCategory.TEXT, FileCategory.CODE, FileCategory.PDF, FileCategory.IMAGE -> {
                _uiState.update { it.copy(activeViewer = ActiveViewer.DocumentViewer(item)) }
            }
            else -> {
                fileRepository.openExternally(item).onFailure {
                    // Fallback to text viewer if small or show message
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
        _uiState.update { it.copy(activeViewer = ActiveViewer.DocumentViewer(item)) }
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
        val s = currentStrings()
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

    fun compressItems(items: List<FileItem>, archiveName: String) {
        if (items.isEmpty()) return
        val state = _uiState.value
        runOperation {
            archiveManager.compressToZip(
                sources = items,
                destDirPath = state.currentPath,
                destSafUri = state.currentSafUri,
                archiveName = archiveName
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
            apkInspector.openInstallPermissionSettings()
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
