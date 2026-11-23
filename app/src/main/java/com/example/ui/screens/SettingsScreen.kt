package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderShared
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.ManageSearch
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AppColorPalette
import com.example.model.FontScaleOption
import com.example.model.ThemeMode
import com.example.model.UserPreferences
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.paletteColorScheme
import com.example.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    viewModel: SettingsViewModel,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showEditHandleDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 148.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.statusBarsPadding())
            Text(
                text = "الإعدادات والتخصيص",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        // Theme & Material 3 Colors
        item {
            SettingsCard(
                title = "المظهر وألوان Material 3",
                icon = Icons.Rounded.Palette
            ) {
                Text(
                    text = "وضع العرض",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = preferences.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(mode.labelAr) },
                            modifier = Modifier.testTag("theme_mode_${mode.name}")
                        )
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SettingsSwitchRow(
                        title = "ألوان Material You الديناميكية",
                        subtitle = "استخراج الألوان تلقائياً من خلفية جهازك",
                        checked = preferences.useDynamicColors,
                        onCheckedChange = { viewModel.setUseDynamicColors(it) },
                        testTag = "dynamic_color_switch"
                    )
                }

                Text(
                    text = "لوحة الألوان المخصصة",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppColorPalette.entries.forEach { palette ->
                        val previewScheme = paletteColorScheme(palette, darkTheme = true)
                        val isSelected = !preferences.useDynamicColors && preferences.colorPalette == palette
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    viewModel.setUseDynamicColors(false)
                                    viewModel.setColorPalette(palette)
                                }
                                .testTag("palette_${palette.name}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(previewScheme.primary)
                                        .border(
                                            width = 1.5.dp,
                                            color = Color.White.copy(alpha = 0.5f),
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = previewScheme.onPrimary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = palette.labelAr,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Typography & Font Size
        item {
            SettingsCard(
                title = "حجم الخط والقراءة",
                icon = Icons.Rounded.FormatSize
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FontScaleOption.entries.forEach { option ->
                        FilterChip(
                            selected = preferences.fontScale == option,
                            onClick = { viewModel.setFontScale(option) },
                            label = { Text(option.labelAr) },
                            modifier = Modifier.testTag("font_scale_${option.name}")
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = "معاينة الخط العربي الدائري المريح للقراءة داخل المستندات والقوائم.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    )
                }
            }
        }

        // File Visibility & Scanner Settings
        item {
            SettingsCard(
                title = "خيارات التصفح والفحص",
                icon = Icons.Rounded.Visibility
            ) {
                SettingsSwitchRow(
                    title = "إظهار الملفات والمجلدات المخفية",
                    subtitle = "عرض العناصر التي تبدأ بنقطة مثل .nomedia",
                    checked = preferences.showHiddenFiles,
                    onCheckedChange = { viewModel.setShowHiddenFiles(it) },
                    testTag = "show_hidden_switch"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsSwitchRow(
                    title = "إظهار مسارات النظام",
                    subtitle = "إتاحة تصفح مجلدات النظام /system و / للقراءة",
                    checked = preferences.showSystemPaths,
                    onCheckedChange = { viewModel.setShowSystemPaths(it) },
                    testTag = "show_system_switch"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsSwitchRow(
                    title = "تضمين الملفات المخفية في الفاحص الذكي",
                    subtitle = "البحث داخل المجلدات المخفية عند مسح الامتدادات",
                    checked = preferences.scannerIncludeHidden,
                    onCheckedChange = { viewModel.setScannerIncludeHidden(it) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsSwitchRow(
                    title = "تأكيد قبل الحذف الجماعي في الفاحص",
                    subtitle = "طلب نافذة تأكيد قبل حذف مجموعة ملفات دفعة واحدة",
                    checked = preferences.scannerConfirmBatchDelete,
                    onCheckedChange = { viewModel.setScannerConfirmBatchDelete(it) }
                )
            }
        }

        // Saved SAF Directories
        if (preferences.safTreeUris.isNotEmpty()) {
            item {
                SettingsCard(
                    title = "مجلدات SAF المرتبطة",
                    icon = Icons.Rounded.FolderShared
                ) {
                    preferences.safTreeUris.forEach { uriStr ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = Uri.parse(uriStr).lastPathSegment ?: uriStr,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.removeSafTreeUri(uriStr) }) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = "إزالة",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        // App Creator Card ("صانع التطبيق")
        item {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("creator_section_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "صانع التطبيق",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = { showEditHandleDialog = true }) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = "تعديل اسم الحساب",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.creator_avatar),
                            contentDescription = "صورة صانع التطبيق",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(84.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(22.dp)
                                )
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "تطوير وتصميم: Kanta",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "TikTok: @${preferences.creatorHandle}",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Text(
                                text = "جميع الحقوق محفوظة لتطبيق Kanta ملفات",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText("TikTok Handle", "@${preferences.creatorHandle}")
                                )
                                onShowMessage("تم نسخ الحساب @${preferences.creatorHandle}")
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("copy_creator_handle_button")
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("نسخ الحساب")
                        }

                        Button(
                            onClick = {
                                val url = "https://www.tiktok.com/@${preferences.creatorHandle}"
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                }.onFailure {
                                    onShowMessage("تعذر فتح رابط تيك توك")
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("open_creator_tiktok_button")
                        ) {
                            Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("فتح الحساب")
                        }
                    }
                }
            }
        }
    }

    if (showEditHandleDialog) {
        var handleInput by remember(preferences.creatorHandle) { mutableStateOf(preferences.creatorHandle) }
        AlertDialog(
            onDismissRequest = { showEditHandleDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(24.dp),
            title = { Text("حساب تيك توك") },
            text = {
                OutlinedTextField(
                    value = handleInput,
                    onValueChange = { handleInput = it },
                    label = { Text("اسم المستخدم") },
                    prefix = { Text("@") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setCreatorHandle(handleInput)
                        showEditHandleDialog = false
                    }
                ) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditHandleDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier
        )
    }
}
