package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.model.AppColorPalette
import com.example.model.FontScaleOption
import com.example.model.ThemeMode

val LocalFontScale = staticCompositionLocalOf { 1.0f }

val KantaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun KantaFilesTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    dynamicColor: Boolean = true,
    colorPalette: AppColorPalette = AppColorPalette.DESERT_GOLD,
    fontScaleOption: FontScaleOption = FontScaleOption.MEDIUM,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDarkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }

    val context = LocalContext.current
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        paletteColorScheme(colorPalette, useDarkTheme)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !useDarkTheme
                    isAppearanceLightNavigationBars = !useDarkTheme
                }
            }
        }
    }

    val typography = buildAppTypography(fontScaleOption.scale)

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalFontScale provides fontScaleOption.scale
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = KantaShapes,
            content = content
        )
    }
}
