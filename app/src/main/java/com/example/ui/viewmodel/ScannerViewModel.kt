package com.example.ui.viewmodel

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FileRepository
import com.example.data.FileScanner
import com.example.data.PreferencesRepository
import com.example.localization.AppStrings
import com.example.localization.stringsForLanguage
import com.example.model.FileItem
import com.example.model.FileOperationProgress
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ScanScope {
    ALL_INTERNAL,
    DOWNLOADS,
    DOCUMENTS
}

data class ScannerUiState(
    val selectedPresetExtensions: Set<String> = setOf("apk"),
    val customExtensionInput: String = "",
    val scanScope: ScanScope = ScanScope.ALL_INTERNAL,
    val isScanning: Boolean = false,
    val hasScannedOnce: Boolean = false,
    val scannedFilesCount: Int = 0,
    val currentDirectoryLabel: String = "",
    val results: List<FileItem> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val operationProgress: FileOperationProgress? = null,
    val snackbarMessage: String? = null
) {
    val isSelectionMode: Boolean get() = selectedIds.isNotEmpty()
    val selectedItems: List<FileItem> get() = results.filter { it.id in selectedIds }
}

class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val fileRepository = FileRepository(application.applicationContext)
    private val preferencesRepository = PreferencesRepository(application.applicationContext)
    private val fileScanner = FileScanner()

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    private val safeExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        _uiState.update {
            it.copy(
                isScanning = false,
                operationProgress = null,
                snackbarMessage = throwable.message
            )
        }
    }

    val presetExtensions = listOf("html", "txt", "apk", "zip", "pdf", "json", "xml", "log", "mp3", "mp4")

    private suspend fun currentStrings(): AppStrings =
        stringsForLanguage(preferencesRepository.preferencesFlow.first().language)

    fun togglePresetExtension(ext: String) {
        _uiState.update { state ->
            val updated = if (ext in state.selectedPresetExtensions) {
                state.selectedPresetExtensions - ext
            } else {
                state.selectedPresetExtensions + ext
            }
            state.copy(selectedPresetExtensions = updated)
        }
    }

    fun updateCustomExtension(input: String) {
        _uiState.update { it.copy(customExtensionInput = input) }
    }

    fun setScanScope(scope: ScanScope) {
        _uiState.update { it.copy(scanScope = scope) }
    }

    fun startScan() {
        val state = _uiState.value
        val customExts = state.customExtensionInput
            .split(',', ' ', '،')
            .map { it.trim().lowercase().removePrefix(".") }
            .filter { it.isNotEmpty() }
            .toSet()

        val targetExtensions = state.selectedPresetExtensions + customExts
        if (targetExtensions.isEmpty()) {
            viewModelScope.launch(safeExceptionHandler) {
                val s = currentStrings()
                _uiState.update { it.copy(snackbarMessage = s.scannerSelectOneExt) }
            }
            return
        }

        scanJob?.cancel()
        scanJob = viewModelScope.launch(safeExceptionHandler) {
            val prefs = preferencesRepository.preferencesFlow.first()
            val s = stringsForLanguage(prefs.language)
            val rootPath = withContext(Dispatchers.IO) {
                runCatching {
                    when (state.scanScope) {
                        ScanScope.ALL_INTERNAL -> fileRepository.getInternalRootPath()
                        ScanScope.DOWNLOADS -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
                        ScanScope.DOCUMENTS -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath
                    }
                }.getOrElse { fileRepository.getInternalRootPath() }
            }
            _uiState.update {
                it.copy(
                    isScanning = true,
                    hasScannedOnce = true,
                    scannedFilesCount = 0,
                    results = emptyList(),
                    selectedIds = emptySet(),
                    currentDirectoryLabel = rootPath
                )
            }

            fileScanner.scanByExtensions(
                rootPaths = listOf(rootPath),
                extensions = targetExtensions,
                includeHidden = prefs.scannerIncludeHidden
            ).catch { err ->
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        snackbarMessage = err.message ?: s.scannerError
                    )
                }
            }.collect { update ->
                _uiState.update {
                    it.copy(
                        scannedFilesCount = update.scannedFilesCount,
                        currentDirectoryLabel = update.currentDirectory,
                        results = update.matchedItems,
                        isScanning = !update.isFinished
                    )
                }
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        viewModelScope.launch {
            val s = currentStrings()
            _uiState.update { it.copy(isScanning = false, currentDirectoryLabel = s.scannerStoppedLabel) }
        }
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
            state.copy(selectedIds = state.results.map { it.id }.toSet())
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    fun deleteItems(items: List<FileItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            val s = currentStrings()
            fileRepository.deleteItems(items) { progress ->
                _uiState.update { it.copy(operationProgress = progress) }
            }.fold(
                onSuccess = {
                    val deletedIds = items.map { it.id }.toSet()
                    _uiState.update { state ->
                        state.copy(
                            operationProgress = null,
                            results = state.results.filterNot { it.id in deletedIds },
                            selectedIds = state.selectedIds - deletedIds,
                            snackbarMessage = s.scannerDeletedCount(items.size)
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            operationProgress = null,
                            snackbarMessage = err.message ?: s.errDeleteFailed
                        )
                    }
                }
            )
        }
    }

    fun moveItems(items: List<FileItem>, destPath: String, destSafUri: android.net.Uri?) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            val s = currentStrings()
            fileRepository.copyOrMoveItems(
                items = items,
                destPath = destPath,
                destSafUri = destSafUri,
                isMove = true
            ) { progress ->
                _uiState.update { it.copy(operationProgress = progress) }
            }.fold(
                onSuccess = {
                    val movedIds = items.map { it.id }.toSet()
                    _uiState.update { state ->
                        state.copy(
                            operationProgress = null,
                            results = state.results.filterNot { it.id in movedIds },
                            selectedIds = state.selectedIds - movedIds,
                            snackbarMessage = s.scannerMovedCount(items.size)
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            operationProgress = null,
                            snackbarMessage = err.message ?: s.errMoveFailed
                        )
                    }
                }
            )
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
