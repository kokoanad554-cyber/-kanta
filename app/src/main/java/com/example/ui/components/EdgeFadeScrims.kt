package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun TopEdgeFadeScrim(
    modifier: Modifier = Modifier,
    includeStatusBarInset: Boolean = true,
    scrimHeight: Dp = 26.dp
) {
    val bg = MaterialTheme.colorScheme.background
    val topInset = if (includeStatusBarInset) {
        WindowInsets.safeDrawing.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding()
    } else {
        0.dp
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(topInset + scrimHeight)
            .background(
                Brush.verticalGradient(
                    0.0f to bg,
                    0.55f to bg.copy(alpha = 0.78f),
                    1.0f to Color.Transparent
                )
            )
    )
}

@Composable
fun BottomEdgeFadeScrim(modifier: Modifier = Modifier) {
    val bg = MaterialTheme.colorScheme.background
    val bottomInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(bottomInset + 104.dp)
            .background(
                Brush.verticalGradient(
                    0.0f to Color.Transparent,
                    0.45f to bg.copy(alpha = 0.78f),
                    0.85f to bg,
                    1.0f to bg
                )
            )
    )
}
