package com.example.ui.components

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val LocalExpressiveAnimations = staticCompositionLocalOf { true }
val LocalHapticEnabled = staticCompositionLocalOf { true }
val LocalIsTvMode = staticCompositionLocalOf { false }
val LocalUseSideNavRail = staticCompositionLocalOf { false }

fun isTelevisionContext(context: Context): Boolean = runCatching {
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    val typeMatch = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    val configMatch = (context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
        Configuration.UI_MODE_TYPE_TELEVISION
    typeMatch || configMatch
}.getOrDefault(false)

/**
 * Material 3 Expressive scalloped/cookie silhouette inspired by the M3 Expressive keyboard popups.
 */
class ScallopedCookieShape(
    private val lobes: Int = 10,
    private val depthFraction: Float = 0.085f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val baseRadius = minOf(cx, cy)
        if (baseRadius <= 0f) {
            return Outline.Rectangle(Rect.Zero)
        }
        val steps = lobes * 10
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

val CookieShape10: Shape = ScallopedCookieShape(lobes = 10, depthFraction = 0.08f)

/**
 * Clean 2D Material 3 Expressive interactive surface:
 * - Pure flat 2D Material 3 surface (no heavy 3D bevels or clipping artifacts)
 * - Keyboard-style pill <-> squircle corner morphing on press & TV D-pad focus
 * - High-contrast M3 focus ring & spring scale lift for Android TV remote navigation
 * - Smooth spring scale bounce + optional subtle haptic feedback
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpressiveSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    restingCornerRadius: Dp = 22.dp,
    pressedCornerRadius: Dp = 11.dp,
    focusedCornerRadius: Dp = 15.dp,
    tonalElevation: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val expressive = LocalExpressiveAnimations.current
    val hapticEnabled = LocalHapticEnabled.current
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()

    val targetCorner = when {
        expressive && isPressed -> pressedCornerRadius
        expressive && isFocused -> focusedCornerRadius
        else -> restingCornerRadius
    }
    val animCorner by animateDpAsState(
        targetValue = targetCorner,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "m3e_corner"
    )
    val targetScale = when {
        expressive && isPressed -> 0.95f
        expressive && isFocused -> 1.04f
        else -> 1f
    }
    val animScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "m3e_scale"
    )
    val focusBorderColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(durationMillis = 120),
        label = "m3e_focus_border"
    )

    val shape = RoundedCornerShape(animCorner.coerceAtLeast(0.dp))

    Surface(
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        border = if (isFocused) BorderStroke(2.5.dp, focusBorderColor) else null,
        modifier = modifier
            .graphicsLayer {
                scaleX = animScale
                scaleY = animScale
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

/**
 * Clean 2D circular-to-squircle Material 3 Expressive icon button (inspired by M3 Expressive keyboard keys).
 * Supports both touch and Android TV D-pad focus highlight with >= 48.dp interactive target.
 */
@Composable
fun ExpressiveIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 48.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable BoxScope.() -> Unit
) {
    ExpressiveSurface(
        onClick = onClick,
        enabled = enabled,
        containerColor = containerColor,
        contentColor = contentColor,
        restingCornerRadius = size / 2f,
        pressedCornerRadius = 12.dp,
        focusedCornerRadius = 15.dp,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(size),
        content = content
    )
}

/**
 * Material 3 Expressive Wavy Progress Indicator (similar to Google Play's squiggly progress bar).
 * Smoothly undulates up-and-down along a sine wave with rounded caps.
 *
 * - Pass [progress] in `0f..1f` for determinate mode (e.g., file copy, storage bar).
 * - Pass `progress = null` for smooth indeterminate gliding wave mode (e.g., Smart Scanner).
 */
@Composable
fun ExpressiveWavyProgressIndicator(
    modifier: Modifier = Modifier,
    progress: (() -> Float)? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    strokeWidth: Dp = 4.5.dp,
    wavelength: Dp = 28.dp,
    waveAmplitude: Dp = 3.5.dp,
    animateWhenDeterminate: Boolean = true
) {
    val expressive = LocalExpressiveAnimations.current
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl

    val shouldAnimateWave = expressive && (progress == null || animateWhenDeterminate)
    val phaseShift: Float
    val indeterminateSweep: Float

    if (shouldAnimateWave) {
        val infiniteTransition = rememberInfiniteTransition(label = "m3e_wavy_progress")
        val animatedPhase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wave_phase"
        )
        phaseShift = animatedPhase
        if (progress == null) {
            val animatedSweep by infiniteTransition.animateFloat(
                initialValue = -0.35f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1450, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "indeterminate_sweep"
            )
            indeterminateSweep = animatedSweep
        } else {
            indeterminateSweep = 0f
        }
    } else {
        phaseShift = 0f
        indeterminateSweep = 0f
    }

    val reusablePath = remember { Path() }
    val heightDp = (waveAmplitude * 2f) + strokeWidth + 6.dp

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp)
    ) {
        val strokePx = strokeWidth.toPx()
        val ampPx = if (expressive) waveAmplitude.toPx() else 0f
        val wavelengthPx = wavelength.toPx().coerceAtLeast(8f)
        val centerY = size.height / 2f
        val pad = strokePx / 2f + 2f
        val usableWidth = (size.width - pad * 2f).coerceAtLeast(1f)

        val rawFrac = progress?.invoke()?.coerceIn(0f, 1f)
        val startFrac: Float
        val endFrac: Float

        if (rawFrac == null) {
            // Indeterminate traveling wave segment
            val segLen = 0.42f
            startFrac = indeterminateSweep.coerceIn(0f, 1f)
            endFrac = (indeterminateSweep + segLen).coerceIn(0f, 1f)
        } else {
            startFrac = 0f
            endFrac = rawFrac
        }

        // Convert logical 0..1 to physical X respecting RTL
        fun fracToX(f: Float): Float {
            return if (isRtl) size.width - pad - f * usableWidth else pad + f * usableWidth
        }

        // Draw flat background track
        if (rawFrac != null) {
            val activeEndX = fracToX(endFrac)
            val fullEndX = fracToX(1f)
            val trackStart = minOf(activeEndX, fullEndX)
            val trackEnd = maxOf(activeEndX, fullEndX)
            if (trackEnd - trackStart > strokePx) {
                drawLine(
                    color = trackColor,
                    start = Offset(trackStart, centerY),
                    end = Offset(trackEnd, centerY),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round
                )
            }
            // Expressive M3 stop indicator dot at end of track
            drawCircle(
                color = color,
                radius = strokePx * 0.45f,
                center = Offset(fullEndX, centerY)
            )
        } else {
            drawLine(
                color = trackColor,
                start = Offset(pad, centerY),
                end = Offset(size.width - pad, centerY),
                strokeWidth = strokePx,
                cap = StrokeCap.Round
            )
        }

        // Draw wavy active segment
        val xA = fracToX(startFrac)
        val xB = fracToX(endFrac)
        val segLeft = minOf(xA, xB)
        val segRight = maxOf(xA, xB)

        if (segRight - segLeft > 1.5f) {
            reusablePath.reset()
            val phase = if (expressive && (rawFrac == null || animateWhenDeterminate)) {
                if (isRtl) phaseShift else -phaseShift
            } else {
                0f
            }
            val stepPx = 3f
            var x = segLeft
            var first = true
            while (x <= segRight) {
                val y = centerY + ampPx * sin((x / wavelengthPx) * 2.0 * PI + phase).toFloat()
                if (first) {
                    reusablePath.moveTo(x, y)
                    first = false
                } else {
                    reusablePath.lineTo(x, y)
                }
                x += stepPx
            }
            val lastY = centerY + ampPx * sin((segRight / wavelengthPx) * 2.0 * PI + phase).toFloat()
            reusablePath.lineTo(segRight, lastY)

            drawPath(
                path = reusablePath,
                color = color,
                style = Stroke(
                    width = strokePx,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}
