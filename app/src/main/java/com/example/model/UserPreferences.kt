package com.example.model

enum class AppLanguage(val code: String, val nativeName: String) {
    ARABIC("ar", "العربية"),
    ENGLISH("en", "English"),
    CHINESE("zh", "中文")
}

enum class ThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

enum class AppColorPalette {
    DESERT_GOLD,
    DEEP_INDIGO,
    EMERALD_SLATE,
    CRIMSON_OBSIDIAN
}

enum class FontScaleOption(val scale: Float) {
    SMALL(0.90f),
    MEDIUM(1.00f),
    LARGE(1.12f),
    EXTRA_LARGE(1.24f)
}

enum class ListDensity {
    COMFORTABLE,
    COMPACT
}

data class UserPreferences(
    val language: AppLanguage = AppLanguage.ARABIC,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val useDynamicColors: Boolean = true,
    val colorPalette: AppColorPalette = AppColorPalette.DESERT_GOLD,
    val fontScale: FontScaleOption = FontScaleOption.MEDIUM,
    val listDensity: ListDensity = ListDensity.COMFORTABLE,
    val expressiveAnimations: Boolean = true,
    val hapticFeedback: Boolean = true,
    val showHiddenFiles: Boolean = false,
    val showSystemPaths: Boolean = true,
    val scannerIncludeHidden: Boolean = false,
    val scannerConfirmBatchDelete: Boolean = true,
    val defaultSortOption: SortOption = SortOption.NAME_ASC,
    val safTreeUris: List<String> = emptyList(),
    val creatorHandle: String = "ak.v911"
)
