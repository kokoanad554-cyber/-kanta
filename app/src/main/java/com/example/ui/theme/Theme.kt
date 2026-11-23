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
import com.example.localization.LocalAppStrings
import com.example.localization.stringsForLanguage
import com.example.model.AppColorPalette
import com.example.model.AppLanguage
import com.example.model.FontScaleOption
import com.example.model.ListDensity
import com.example.model.ThemeMode
import com.example.ui.components.LocalExpressiveAnimations
import com.example.ui.components.LocalHapticEnabled

val LocalFontScale = staticCompositionLocalOf { 1.0f }
val LocalListDensity = staticCompositionLocalOf { ListDensity.COMFORTABLE }

val KantaShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

@Composable
fun KantaFilesTheme(
    language: AppLanguage = AppLanguage.ARABIC,
    themeMode: ThemeMode = ThemeMode.DARK,
    dynamicColor: Boolean = true,
    colorPalette: AppColorPalette = AppColorPalette.DESERT_GOLD,
    fontScaleOption: FontScaleOption = FontScaleOption.MEDIUM,
    listDensity: ListDensity = ListDensity.COMFORTABLE,
    expressiveAnimations: Boolean = true,
    hapticFeedback: Boolean = true,
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

    val layoutDirection = if (language == AppLanguage.ARABIC) LayoutDirection.Rtl else LayoutDirection.Ltr
    val strings = stringsForLanguage(language)
    val typography = buildAppTypography(fontScaleOption.scale)

    CompositionLocalProvider(
        LocalLayoutDirection provides layoutDirection,
        LocalAppStrings provides strings,
        LocalFontScale provides fontScaleOption.scale,
        LocalListDensity provides listDensity,
        LocalExpressiveAnimations provides expressiveAnimations,
        LocalHapticEnabled provides hapticFeedback
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = KantaShapes,
            content = content
        )
    }
}
