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
import com.example.model.AppLanguage
import com.example.model.FontScaleOption
import com.example.model.ListDensity
import com.example.model.SortOption
import com.example.model.ThemeMode
import com.example.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kanta_files_prefs")

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("app_language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_DYNAMIC_COLORS = booleanPreferencesKey("use_dynamic_colors")
        val COLOR_PALETTE = stringPreferencesKey("color_palette")
        val FONT_SCALE = stringPreferencesKey("font_scale")
        val LIST_DENSITY = stringPreferencesKey("list_density")
        val EXPRESSIVE_ANIMATIONS = booleanPreferencesKey("expressive_animations")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val SHOW_HIDDEN_FILES = booleanPreferencesKey("show_hidden_files")
        val SHOW_SYSTEM_PATHS = booleanPreferencesKey("show_system_paths")
        val SCANNER_INCLUDE_HIDDEN = booleanPreferencesKey("scanner_include_hidden")
        val SCANNER_CONFIRM_DELETE = booleanPreferencesKey("scanner_confirm_delete")
        val DEFAULT_SORT = stringPreferencesKey("default_sort")
        val SAF_TREE_URIS = stringSetPreferencesKey("saf_tree_uris")
        val CREATOR_HANDLE = stringPreferencesKey("creator_handle")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val rawHandle = prefs[Keys.CREATOR_HANDLE]?.takeIf { it.isNotBlank() }
        val resolvedHandle = when {
            rawHandle == null || rawHandle == "ak.911" -> "ak.v911"
            else -> rawHandle
        }
        UserPreferences(
            language = prefs[Keys.LANGUAGE]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                ?: AppLanguage.ARABIC,
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.DARK,
            useDynamicColors = prefs[Keys.USE_DYNAMIC_COLORS] ?: true,
            colorPalette = prefs[Keys.COLOR_PALETTE]?.let { runCatching { AppColorPalette.valueOf(it) }.getOrNull() }
                ?: AppColorPalette.DESERT_GOLD,
            fontScale = prefs[Keys.FONT_SCALE]?.let { runCatching { FontScaleOption.valueOf(it) }.getOrNull() }
                ?: FontScaleOption.MEDIUM,
            listDensity = prefs[Keys.LIST_DENSITY]?.let { runCatching { ListDensity.valueOf(it) }.getOrNull() }
                ?: ListDensity.COMFORTABLE,
            expressiveAnimations = prefs[Keys.EXPRESSIVE_ANIMATIONS] ?: true,
            hapticFeedback = prefs[Keys.HAPTIC_FEEDBACK] ?: true,
            showHiddenFiles = prefs[Keys.SHOW_HIDDEN_FILES] ?: false,
            showSystemPaths = prefs[Keys.SHOW_SYSTEM_PATHS] ?: true,
            scannerIncludeHidden = prefs[Keys.SCANNER_INCLUDE_HIDDEN] ?: false,
            scannerConfirmBatchDelete = prefs[Keys.SCANNER_CONFIRM_DELETE] ?: true,
            defaultSortOption = prefs[Keys.DEFAULT_SORT]?.let { runCatching { SortOption.valueOf(it) }.getOrNull() }
                ?: SortOption.NAME_ASC,
            safTreeUris = prefs[Keys.SAF_TREE_URIS]?.toList()?.sorted() ?: emptyList(),
            creatorHandle = resolvedHandle
        )
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.name }
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

    suspend fun setListDensity(density: ListDensity) {
        context.dataStore.edit { it[Keys.LIST_DENSITY] = density.name }
    }

    suspend fun setExpressiveAnimations(enabled: Boolean) {
        context.dataStore.edit { it[Keys.EXPRESSIVE_ANIMATIONS] = enabled }
    }

    suspend fun setHapticFeedback(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTIC_FEEDBACK] = enabled }
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

    suspend fun setDefaultSortOption(sortOption: SortOption) {
        context.dataStore.edit { it[Keys.DEFAULT_SORT] = sortOption.name }
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
        val cleaned = handle.trim().removePrefix("@").ifBlank { "ak.v911" }
        context.dataStore.edit { it[Keys.CREATOR_HANDLE] = cleaned }
    }
}
