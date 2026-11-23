package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AppColorPalette
import com.example.model.FontScaleOption
import com.example.model.ThemeMode
import com.example.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kanta_files_prefs")

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_DYNAMIC_COLORS = booleanPreferencesKey("use_dynamic_colors")
        val COLOR_PALETTE = stringPreferencesKey("color_palette")
        val FONT_SCALE = stringPreferencesKey("font_scale")
        val SHOW_HIDDEN_FILES = booleanPreferencesKey("show_hidden_files")
        val SHOW_SYSTEM_PATHS = booleanPreferencesKey("show_system_paths")
        val SCANNER_INCLUDE_HIDDEN = booleanPreferencesKey("scanner_include_hidden")
        val SCANNER_CONFIRM_DELETE = booleanPreferencesKey("scanner_confirm_delete")
        val SAF_TREE_URIS = stringSetPreferencesKey("saf_tree_uris")
        val CREATOR_HANDLE = stringPreferencesKey("creator_handle")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.DARK,
            useDynamicColors = prefs[Keys.USE_DYNAMIC_COLORS] ?: true,
            colorPalette = prefs[Keys.COLOR_PALETTE]?.let { runCatching { AppColorPalette.valueOf(it) }.getOrNull() }
                ?: AppColorPalette.DESERT_GOLD,
            fontScale = prefs[Keys.FONT_SCALE]?.let { runCatching { FontScaleOption.valueOf(it) }.getOrNull() }
                ?: FontScaleOption.MEDIUM,
            showHiddenFiles = prefs[Keys.SHOW_HIDDEN_FILES] ?: false,
            showSystemPaths = prefs[Keys.SHOW_SYSTEM_PATHS] ?: true,
            scannerIncludeHidden = prefs[Keys.SCANNER_INCLUDE_HIDDEN] ?: false,
            scannerConfirmBatchDelete = prefs[Keys.SCANNER_CONFIRM_DELETE] ?: true,
            safTreeUris = prefs[Keys.SAF_TREE_URIS]?.toList()?.sorted() ?: emptyList(),
            creatorHandle = prefs[Keys.CREATOR_HANDLE]?.takeIf { it.isNotBlank() } ?: "ak.911"
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setUseDynamicColors(enabled: Boolean) {
        context.dataStore.edit { it[Keys.USE_DYNAMIC_COLORS] = enabled }
    }

    suspend fun setColorPalette(palette: AppColorPalette) {
        context.dataStore.edit { it[Keys.COLOR_PALETTE] = palette.name }
    }

    suspend fun setFontScale(scale: FontScaleOption) {
        context.dataStore.edit { it[Keys.FONT_SCALE] = scale.name }
    }

    suspend fun setShowHiddenFiles(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_HIDDEN_FILES] = show }
    }

    suspend fun setShowSystemPaths(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_SYSTEM_PATHS] = show }
    }

    suspend fun setScannerIncludeHidden(include: Boolean) {
        context.dataStore.edit { it[Keys.SCANNER_INCLUDE_HIDDEN] = include }
    }

    suspend fun setScannerConfirmBatchDelete(confirm: Boolean) {
        context.dataStore.edit { it[Keys.SCANNER_CONFIRM_DELETE] = confirm }
    }

    suspend fun addSafTreeUri(uriString: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.SAF_TREE_URIS] ?: emptySet()
            prefs[Keys.SAF_TREE_URIS] = current + uriString
        }
    }

    suspend fun removeSafTreeUri(uriString: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.SAF_TREE_URIS] ?: emptySet()
            prefs[Keys.SAF_TREE_URIS] = current - uriString
        }
    }

    suspend fun setCreatorHandle(handle: String) {
        val cleaned = handle.trim().removePrefix("@").ifBlank { "ak.911" }
        context.dataStore.edit { it[Keys.CREATOR_HANDLE] = cleaned }
    }
}
