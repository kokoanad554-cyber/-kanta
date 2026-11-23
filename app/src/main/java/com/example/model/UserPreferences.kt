package com.example.model

enum class ThemeMode(val labelAr: String) {
    DARK("داكن"),
    LIGHT("فاتح"),
    SYSTEM("تلقائي حسب النظام")
}

enum class AppColorPalette(val labelAr: String) {
    DESERT_GOLD("رملي دافئ"),
    DEEP_INDIGO("أزرق ليلي"),
    EMERALD_SLATE("زمردي داكن"),
    CRIMSON_OBSIDIAN("قرمزي فاخر")
}

enum class FontScaleOption(val labelAr: String, val scale: Float) {
    SMALL("صغير", 0.90f),
    MEDIUM("متوسط", 1.00f),
    LARGE("كبير", 1.12f),
    EXTRA_LARGE("كبير جداً", 1.24f)
}

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val useDynamicColors: Boolean = true,
    val colorPalette: AppColorPalette = AppColorPalette.DESERT_GOLD,
    val fontScale: FontScaleOption = FontScaleOption.MEDIUM,
    val showHiddenFiles: Boolean = false,
    val showSystemPaths: Boolean = true,
    val scannerIncludeHidden: Boolean = false,
    val scannerConfirmBatchDelete: Boolean = true,
    val safTreeUris: List<String> = emptyList(),
    val creatorHandle: String = "ak.911"
)
