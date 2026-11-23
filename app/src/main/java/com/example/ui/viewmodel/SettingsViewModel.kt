package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.PreferencesRepository
import com.example.model.AppColorPalette
import com.example.model.AppLanguage
import com.example.model.FontScaleOption
import com.example.model.ListDensity
import com.example.model.SortOption
import com.example.model.ThemeMode
import com.example.model.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesRepository = PreferencesRepository(application.applicationContext)

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { preferencesRepository.setLanguage(language) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setUseDynamicColors(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setUseDynamicColors(enabled) }
    }

    fun setColorPalette(palette: AppColorPalette) {
        viewModelScope.launch { preferencesRepository.setColorPalette(palette) }
    }

    fun setFontScale(scale: FontScaleOption) {
        viewModelScope.launch { preferencesRepository.setFontScale(scale) }
    }

    fun setListDensity(density: ListDensity) {
        viewModelScope.launch { preferencesRepository.setListDensity(density) }
    }

    fun setExpressiveAnimations(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setExpressiveAnimations(enabled) }
    }

    fun setHapticFeedback(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setHapticFeedback(enabled) }
    }

    fun setShowHiddenFiles(show: Boolean) {
        viewModelScope.launch { preferencesRepository.setShowHiddenFiles(show) }
    }

    fun setShowSystemPaths(show: Boolean) {
        viewModelScope.launch { preferencesRepository.setShowSystemPaths(show) }
    }

    fun setScannerIncludeHidden(include: Boolean) {
        viewModelScope.launch { preferencesRepository.setScannerIncludeHidden(include) }
    }

    fun setScannerConfirmBatchDelete(confirm: Boolean) {
        viewModelScope.launch { preferencesRepository.setScannerConfirmBatchDelete(confirm) }
    }

    fun setDefaultSortOption(sortOption: SortOption) {
        viewModelScope.launch { preferencesRepository.setDefaultSortOption(sortOption) }
    }

    fun removeSafTreeUri(uriString: String) {
        viewModelScope.launch { preferencesRepository.removeSafTreeUri(uriString) }
    }

    fun setCreatorHandle(handle: String) {
        viewModelScope.launch { preferencesRepository.setCreatorHandle(handle) }
    }

    fun clearTempCache(onCompleted: (Long) -> Unit) {
        viewModelScope.launch {
            val freed = withContext(Dispatchers.IO) {
                val cacheDir = getApplication<Application>().cacheDir
                var total = 0L
                cacheDir.listFiles()?.forEach { f ->
                    if (f.name.startsWith("inspect_") || f.name.startsWith("view_")) {
                        total += f.length()
                        f.deleteRecursively()
                    }
                }
                total
            }
            onCompleted(freed)
        }
    }
}
