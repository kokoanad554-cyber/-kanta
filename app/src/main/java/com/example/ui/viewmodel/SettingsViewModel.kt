package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.PreferencesRepository
import com.example.model.AppColorPalette
import com.example.model.FontScaleOption
import com.example.model.ThemeMode
import com.example.model.UserPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesRepository = PreferencesRepository(application.applicationContext)

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

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

    fun removeSafTreeUri(uriString: String) {
        viewModelScope.launch { preferencesRepository.removeSafTreeUri(uriString) }
    }

    fun setCreatorHandle(handle: String) {
        viewModelScope.launch { preferencesRepository.setCreatorHandle(handle) }
    }
}
