package com.example.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.FindReplace
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.FileRepository
import com.example.model.FileItem
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlEditorScreen(
    item: FileItem,
    fileRepository: FileRepository,
    fontScale: Float,
    onClose: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var originalContent by remember { mutableStateOf("") }
    var codeValue by remember { mutableStateOf(TextFieldValue("")) }
    var isPreviewMode by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var activeMatchIndex by remember { mutableIntStateOf(0) }
    var showUnsavedDialog by remember { mutableStateOf(false) }

    val hasUnsavedChanges = codeValue.text != originalContent

    LaunchedEffect(item.id) {
        isLoading = true
        fileRepository.readTextDocument(item).fold(
            onSuccess = { (content, truncated) ->
                originalContent = content
                codeValue = TextFieldValue(content)
                isLoading = false
                if (truncated) {
                    onShowMessage("الملف كبير جداً، تم تحميل الجزء الأول فقط حفاظاً على الأداء")
                }
            },
            onFailure = { err ->
                isLoading = false
                onShowMessage(err.message ?: "تعذر قراءة ملف HTML")
                onClose()
            }
        )
    }

    val matchIndices = remember(codeValue.text, searchQuery) {
        if (searchQuery.isEmpty()) {
            emptyList()
        } else {
            val matches = mutableListOf<Int>()
            var idx = codeValue.text.indexOf(searchQuery, ignoreCase = true)
            while (idx >= 0) {
                matches += idx
                idx = codeValue.text.indexOf(searchQuery, idx + maxOf(1, searchQuery.length), ignoreCase = true)
            }
            matches
        }
    }

    fun jumpToMatch(index: Int) {
        if (matchIndices.isEmpty()) return
        val safeIdx = ((index % matchIndices.size) + matchIndices.size) % matchIndices.size
        activeMatchIndex = safeIdx
        val start = matchIndices[safeIdx]
        val end = (start + searchQuery.length).coerceAtMost(codeValue.text.length)
        codeValue = codeValue.copy(selection = TextRange(start, end))
    }

    fun saveDocument() {
        if (isSaving) return
        isSaving = true
        scope.launch {
            fileRepository.writeTextSafely(item, codeValue.text).fold(
                onSuccess = {
                    originalContent = codeValue.text
                    isSaving = false
                    onShowMessage("تم حفظ الملف بأمان")
                },
                onFailure = { err ->
                    isSaving = false
                    onShowMessage(err.message ?: "فشل حفظ الملف")
                }
            )
        }
    }

    BackHandler {
        if (hasUnsavedChanges) {
            showUnsavedDialog = true
        } else {
            onClose()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Editor Toolbar
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = {
                                if (hasUnsavedChanges) showUnsavedDialog = true else onClose()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("html_editor_back")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "رجوع")
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = when {
                                    isPreviewMode -> "معاينة حية للصفحة"
                                    hasUnsavedChanges -> "تعديلات غير محفوظة"
                                    else -> "محرر HTML"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (hasUnsavedChanges) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isPreviewMode) {
                            IconButton(
                                onClick = { showSearchBar = !showSearchBar },
                                modifier = Modifier.testTag("html_editor_search_toggle")
                            ) {
                                Icon(Icons.Rounded.Search, contentDescription = "بحث في الكود")
                            }
                        }
                        IconButton(
                            onClick = { isPreviewMode = !isPreviewMode },
                            modifier = Modifier.testTag("html_editor_preview_toggle")
                        ) {
                            Icon(
                                imageVector = if (isPreviewMode) Icons.Rounded.Code else Icons.Rounded.Visibility,
                                contentDescription = if (isPreviewMode) "العودة للكود" else "معاينة حية",
                                tint = if (isPreviewMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = { saveDocument() },
                            enabled = !isSaving && !isLoading,
                            modifier = Modifier.testTag("html_editor_save")
                        ) {
                            Icon(
                                Icons.Rounded.Save,
                                contentDescription = "حفظ",
                                tint = if (hasUnsavedChanges) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }

            // Collapsible Find & Replace Bar
            AnimatedVisibility(visible = showSearchBar && !isPreviewMode) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = {
                                    searchQuery = it
                                    activeMatchIndex = 0
                                },
                                placeholder = { Text("بحث في الكود...") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (matchIndices.isEmpty()) "0/0" else "${activeMatchIndex + 1}/${matchIndices.size}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily)
                            )
                            IconButton(onClick = { jumpToMatch(activeMatchIndex - 1) }) {
                                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "السابق")
                            }
                            IconButton(onClick = { jumpToMatch(activeMatchIndex + 1) }) {
                                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "التالي")
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = replaceQuery,
                                onValueChange = { replaceQuery = it },
                                placeholder = { Text("استبدال بـ...") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    if (searchQuery.isNotEmpty() && matchIndices.isNotEmpty()) {
                                        val updated = codeValue.text.replace(searchQuery, replaceQuery, ignoreCase = true)
                                        codeValue = TextFieldValue(updated)
                                    }
                                }
                            ) {
                                Icon(Icons.Rounded.FindReplace, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("الكل")
                            }
                        }
                    }
                }
            }

            // Main Editor / Preview Surface
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (isPreviewMode) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                webViewClient = WebViewClient()
                            }
                        },
                        update = { webView ->
                            webView.loadDataWithBaseURL(
                                null,
                                codeValue.text,
                                "text/html",
                                "UTF-8",
                                null
                            )
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("html_live_webview")
                    )
                } else {
                    val vScroll = rememberScrollState()
                    val hScroll = rememberScrollState()
                    val lines = remember(codeValue.text) { codeValue.text.split('\n') }
                    val codeStyle = TextStyle(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = (13.5f * fontScale).sp,
                        lineHeight = (21f * fontScale).sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(vScroll)
                                .padding(vertical = 12.dp)
                        ) {
                            // Line numbers gutter
                            Column(
                                modifier = Modifier
                                    .widthIn(min = 44.dp)
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                    .padding(horizontal = 8.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                lines.forEachIndexed { idx, _ ->
                                    Text(
                                        text = "${idx + 1}",
                                        style = codeStyle.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.End
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Code editing area
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(hScroll)
                                    .padding(end = 16.dp)
                            ) {
                                BasicTextField(
                                    value = codeValue,
                                    onValueChange = { codeValue = it },
                                    textStyle = codeStyle,
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .widthIn(min = 340.dp)
                                        .testTag("html_code_textfield")
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(24.dp),
            title = { Text("تعديلات غير محفوظة") },
            text = { Text("هل تريد حفظ التعديلات على ${item.name} قبل الخروج؟") },
            confirmButton = {
                Button(onClick = {
                    showUnsavedDialog = false
                    scope.launch {
                        fileRepository.writeTextSafely(item, codeValue.text)
                        onShowMessage("تم حفظ الملف")
                        onClose()
                    }
                }) {
                    Text("حفظ وخروج")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showUnsavedDialog = false
                        onClose()
                    }) {
                        Text("تجاهل", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { showUnsavedDialog = false }) {
                        Text("إلغاء")
                    }
                }
            }
        )
    }
}
