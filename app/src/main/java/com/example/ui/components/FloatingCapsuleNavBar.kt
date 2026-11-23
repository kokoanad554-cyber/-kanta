package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.ManageSearch
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class AppDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    FILES(
        route = "files",
        label = "الملفات",
        selectedIcon = Icons.Rounded.Folder,
        unselectedIcon = Icons.Outlined.Folder,
        testTag = "nav_tab_files"
    ),
    SCANNER(
        route = "scanner",
        label = "الفاحص الذكي",
        selectedIcon = Icons.Rounded.ManageSearch,
        unselectedIcon = Icons.Outlined.ManageSearch,
        testTag = "nav_tab_scanner"
    ),
    SETTINGS(
        route = "settings",
        label = "الإعدادات",
        selectedIcon = Icons.Rounded.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_tab_settings"
    )
}

@Composable
fun FloatingCapsuleNavBar(
    currentRoute: String?,
    onNavigate: (AppDestination) -> Unit,
    modifier: Modifier = Modifier
) {
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
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 6.dp,
            shadowElevation = 10.dp
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
                    val pillColor by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "nav_pill_color"
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "nav_content_color"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(pillColor)
                            .clickable { onNavigate(destination) }
                            .testTag(destination.testTag)
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = destination.label,
                                tint = contentColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = destination.label,
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
