package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val ReadexProFontFamily = FontFamily(
    Font(R.font.readex_pro, FontWeight.Normal),
    Font(R.font.readex_pro, FontWeight.Medium),
    Font(R.font.readex_pro, FontWeight.SemiBold),
    Font(R.font.readex_pro, FontWeight.Bold)
)

val JetBrainsMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium)
)

fun buildAppTypography(scaleFactor: Float = 1.0f): Typography {
    fun s(base: Float) = (base * scaleFactor).sp
    return Typography(
        displaySmall = TextStyle(
            fontFamily = ReadexProFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = s(32f),
            lineHeight = s(40f)
        ),
        headlineMedium = TextStyle(
            fontFamily = ReadexProFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = s(24f),
            lineHeight = s(32f)
        ),
        headlineSmall = TextStyle(
            fontFamily = ReadexProFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = s(20f),
            lineHeight = s(28f)
        ),
        titleLarge = TextStyle(
            fontFamily = ReadexProFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = s(19f),
            lineHeight = s(26f)
        ),
        titleMedium = TextStyle(
            fontFamily = ReadexProFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = s(16f),
            lineHeight = s(23f)
        ),
        titleSmall = TextStyle(
            fontFamily = ReadexProFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = s(14f),
            lineHeight = s(20f)
        ),
        bodyLarge = TextStyle(
            fontFamily = ReadexProFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = s(15.5f),
            lineHeight = s(24f)
        ),
        bodyMedium = TextStyle(
            fontFamily = ReadexProFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = s(14f),
            lineHeight = s(20f)
        ),
        bodySmall = TextStyle(
            fontFamily = ReadexProFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = s(12f),
            lineHeight = s(17f)
        ),
        labelLarge = TextStyle(
            fontFamily = ReadexProFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = s(14f),
            lineHeight = s(20f)
        ),
        labelMedium = TextStyle(
            fontFamily = ReadexProFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = s(12f),
            lineHeight = s(16f)
        ),
        labelSmall = TextStyle(
            fontFamily = ReadexProFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = s(11f),
            lineHeight = s(15f)
        )
    )
}
