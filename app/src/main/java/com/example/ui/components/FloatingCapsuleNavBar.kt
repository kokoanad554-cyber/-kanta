package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.automirrored.rounded.ManageSearch
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.localization.AppStrings
import com.example.localization.LocalAppStrings

enum class AppDestination(
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    FILES(
        route = "files",
        selectedIcon = Icons.Rounded.Folder,
        unselectedIcon = Icons.Outlined.Folder,
        testTag = "nav_tab_files"
    ),
    SCANNER(
        route = "scanner",
        selectedIcon = Icons.AutoMirrored.Rounded.ManageSearch,
        unselectedIcon = Icons.AutoMirrored.Outlined.ManageSearch,
        testTag = "nav_tab_scanner"
    ),
    SETTINGS(
        route = "settings",
        selectedIcon = Icons.Rounded.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_tab_settings"
    );

    fun title(strings: AppStrings): String = when (this) {
        FILES -> strings.tabFiles
        SCANNER -> strings.tabScanner
        SETTINGS -> strings.tabSettings
    }
}

@Composable
fun FloatingCapsuleNavBar(
    currentRoute: String?,
    onNavigate: (AppDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val expressive = LocalExpressiveAnimations.current
    val hapticEnabled = LocalHapticEnabled.current
    val haptics = LocalHapticFeedback.current

    val capsuleColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            color = capsuleColor,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppDestination.entries.forEach { destination ->
                    val selected = currentRoute == destination.route
                    val title = destination.title(strings)
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val isFocused by interactionSource.collectIsFocusedAsState()

                    val pillColor by animateColorAsState(
                        targetValue = when {
                            selected -> MaterialTheme.colorScheme.primaryContainer
                            isFocused -> MaterialTheme.colorScheme.surfaceContainerHigh
                            else -> Color.Transparent
                        },
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "nav_pill_color"
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (selected || isFocused) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "nav_content_color"
                    )
                    val pillRadius by animateDpAsState(
                        targetValue = when {
                            expressive && isPressed -> 14.dp
                            expressive && isFocused -> 18.dp
                            selected -> 26.dp
                            else -> 22.dp
                        },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "nav_pill_radius"
                    )
                    val scale by animateFloatAsState(
                        targetValue = when {
                            expressive && isPressed -> 0.93f
                            expressive && isFocused -> 1.05f
                            else -> 1f
                        },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "nav_item_scale"
                    )

                    val itemShape = RoundedCornerShape(pillRadius.coerceAtLeast(0.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .clip(itemShape)
                            .background(pillColor)
                            .then(
                                if (isFocused) {
                                    Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, itemShape)
                                } else {
                                    Modifier
                                }
                            )
                            .clickable(
                                interactionSource = interactionSource,
                                indication = androidx.compose.material3.ripple()
                            ) {
                                if (hapticEnabled) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                onNavigate(destination)
                            }
                            .testTag(destination.testTag)
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = title,
                                tint = contentColor,
                                modifier = Modifier.size(23.dp)
                            )
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Android TV & Wide-Screen Floating Side Capsule Navigation Rail:
 * Preserves the exact Material 3 Expressive 2D v1.12 capsule aesthetics and spring morphing,
 * placed on the start edge with overscan-safe padding and D-pad focus highlights.
 */
@Composable
fun TvSideCapsuleNavRail(
    currentRoute: String?,
    onNavigate: (AppDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val expressive = LocalExpressiveAnimations.current
    val hapticEnabled = LocalHapticEnabled.current
    val haptics = LocalHapticFeedback.current
    val isTv = LocalIsTvMode.current

    val capsuleColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Box(
        modifier = modifier
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(
                start = if (isTv) 28.dp else 16.dp,
                top = if (isTv) 24.dp else 14.dp,
                bottom = if (isTv) 24.dp else 14.dp,
                end = 4.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.width(106.dp),
            shape = RoundedCornerShape(32.dp),
            color = capsuleColor,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CookieShape10,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Folder,
                            contentDescription = strings.appTitle,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.padding(vertical = 2.dp))

                AppDestination.entries.forEach { destination ->
                    val selected = currentRoute == destination.route
                    val title = destination.title(strings)
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val isFocused by interactionSource.collectIsFocusedAsState()

                    val pillColor by animateColorAsState(
                        targetValue = when {
                            selected -> MaterialTheme.colorScheme.primaryContainer
                            isFocused -> MaterialTheme.colorScheme.surfaceContainerHigh
                            else -> Color.Transparent
                        },
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "tv_rail_color"
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (selected || isFocused) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "tv_rail_content_color"
                    )
                    val pillRadius by animateDpAsState(
                        targetValue = when {
                            expressive && isPressed -> 13.dp
                            expressive && isFocused -> 17.dp
                            selected -> 24.dp
                            else -> 20.dp
                        },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "tv_rail_radius"
                    )
                    val scale by animateFloatAsState(
                        targetValue = when {
                            expressive && isPressed -> 0.94f
                            expressive && isFocused -> 1.06f
                            else -> 1f
                        },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "tv_rail_scale"
                    )

                    val itemShape = RoundedCornerShape(pillRadius.coerceAtLeast(0.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 66.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .clip(itemShape)
                            .background(pillColor)
                            .then(
                                if (isFocused) {
                                    Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, itemShape)
                                } else {
                                    Modifier
                                }
                            )
                            .clickable(
                                interactionSource = interactionSource,
                                indication = androidx.compose.material3.ripple()
                            ) {
                                if (hapticEnabled) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                onNavigate(destination)
                            }
                            .testTag(destination.testTag)
                            .padding(horizontal = 6.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = title,
                                tint = contentColor,
                                modifier = Modifier.size(25.dp)
                            )
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (selected || isFocused) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = contentColor,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
