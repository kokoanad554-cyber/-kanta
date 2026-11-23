package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val LocalExpressiveAnimations = staticCompositionLocalOf { true }
val LocalHapticEnabled = staticCompositionLocalOf { true }

/**
 * Material 3 Expressive scalloped/cookie silhouette inspired by tactile key popups & toolbar actions.
 */
class ScallopedCookieShape(
    private val lobes: Int = 10,
    private val depthFraction: Float = 0.095f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val baseRadius = minOf(cx, cy)
        val steps = lobes * 12
        val path = Path()
        for (i in 0..steps) {
            val theta = (i.toDouble() / steps.toDouble()) * 2.0 * PI
            val wave = cos(theta * lobes)
            val r = baseRadius * (1f - depthFraction + depthFraction * wave.toFloat())
            val x = cx + r * cos(theta).toFloat()
            val y = cy + r * sin(theta).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/**
 * Tactile 3D Expressive Surface inspired by Material 3 Expressive pill/squircle keys:
 * - Physical solid 3D base lip underneath
 * - Corner radius morphs on press (pill <-> squircle)
 * - Springy scale & vertical press displacement
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Tactile3DSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    restingCornerRadius: Dp = 22.dp,
    pressedCornerRadius: Dp = 12.dp,
    bevelDepth: Dp = 4.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val expressive = LocalExpressiveAnimations.current
    val hapticEnabled = LocalHapticEnabled.current
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val animCorner by animateDpAsState(
        targetValue = if (expressive && isPressed) pressedCornerRadius else restingCornerRadius,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tactile_corner"
    )
    val animScale by animateFloatAsState(
        targetValue = if (expressive && isPressed) 0.965f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "tactile_scale"
    )
    val pressOffsetPx by animateFloatAsState(
        targetValue = if (expressive && isPressed) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "tactile_offset"
    )

    val shape = RoundedCornerShape(animCorner)
    val baseBevelColor = remember(containerColor) {
        lerp(containerColor, Color.Black, 0.36f)
    }
    val topHighlightColor = remember(containerColor) {
        lerp(containerColor, Color.White, 0.14f)
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = animScale
                scaleY = animScale
            }
            .clip(shape)
            .background(baseBevelColor)
            .padding(bottom = bevelDepth)
    ) {
        Surface(
            shape = shape,
            color = containerColor,
            contentColor = contentColor,
            border = BorderStroke(1.dp, topHighlightColor),
            modifier = Modifier
                .graphicsLayer {
                    translationY = pressOffsetPx * (bevelDepth.toPx() * 0.72f)
                }
                .clip(shape)
                .combinedClickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = androidx.compose.material3.ripple(),
                    onClick = {
                        if (hapticEnabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        onClick()
                    },
                    onLongClick = onLongClick?.let { longAction ->
                        {
                            if (hapticEnabled) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            longAction()
                        }
                    }
                )
        ) {
            Box(contentAlignment = Alignment.Center, content = content)
        }
    }
}
