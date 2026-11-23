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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material3.Icon
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
import com.example.localization.LocalAppStrings
import com.example.model.FileCategory
import com.example.model.FileItem
import com.example.ui.components.ExpressiveIconButton
import com.example.ui.components.ExpressiveWavyProgressIndicator
import com.example.ui.components.LocalIsTvMode
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
    val strings = LocalAppStrings.current
    val isTvMode = LocalIsTvMode.current
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

    val isHtmlFile = remember(item) {
        item.category == FileCategory.HTML ||
            item.extension.equals("html", ignoreCase = true) ||
            item.extension.equals("htm", ignoreCase = true) ||
            item.extension.equals("xhtml", ignoreCase = true)
    }

    val hasUnsavedChanges = codeValue.text != originalContent

    LaunchedEffect(item.id) {
        isLoading = true
        fileRepository.readTextDocument(item).fold(
            onSuccess = { (content, truncated) ->
                originalContent = content
                codeValue = TextFieldValue(content)
                isLoading = false
                if (truncated) {
                    onShowMessage(strings.docLargeTruncated)
                }
            },
            onFailure = { err ->
                isLoading = false
                onShowMessage(err.message ?: strings.unknownLabel)
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
                    onShowMessage(strings.save)
                },
                onFailure = { err ->
                    isSaving = false
                    onShowMessage(err.message ?: strings.unknownLabel)
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
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .padding(
                    horizontal = if (isTvMode) 28.dp else 12.dp,
                    vertical = if (isTvMode) 20.dp else 8.dp
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        ExpressiveIconButton(
                            onClick = {
                                if (hasUnsavedChanges) showUnsavedDialog = true else onClose()
                            },
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            size = 48.dp,
                            modifier = Modifier.testTag("html_editor_back")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = strings.back, modifier = Modifier.size(22.dp))
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
                                    isPreviewMode -> strings.htmlLivePreviewMode
                                    hasUnsavedChanges -> strings.htmlUnsavedChanges
                                    isHtmlFile -> strings.htmlEditorMode
                                    else -> strings.openCodeEditor
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

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (!isPreviewMode) {
                            ExpressiveIconButton(
                                onClick = { showSearchBar = !showSearchBar },
                                containerColor = if (showSearchBar) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                contentColor = if (showSearchBar) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                size = 48.dp,
                                modifier = Modifier.testTag("html_editor_search_toggle")
                            ) {
                                Icon(Icons.Rounded.Search, contentDescription = strings.search, modifier = Modifier.size(22.dp))
                            }
                        }
                        if (isHtmlFile) {
                            ExpressiveIconButton(
                                onClick = { isPreviewMode = !isPreviewMode },
                                containerColor = if (isPreviewMode) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                contentColor = if (isPreviewMode) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                size = 48.dp,
                                modifier = Modifier.testTag("html_editor_preview_toggle")
                            ) {
                                Icon(
                                    imageVector = if (isPreviewMode) Icons.Rounded.Code else Icons.Rounded.Visibility,
                                    contentDescription = strings.htmlLivePreviewMode,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        ExpressiveIconButton(
                            onClick = { saveDocument() },
                            enabled = !isSaving && !isLoading,
                            containerColor = if (hasUnsavedChanges) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            contentColor = if (hasUnsavedChanges) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            size = 48.dp,
                            modifier = Modifier.testTag("html_editor_save")
                        ) {
                            Icon(Icons.Rounded.Save, contentDescription = strings.save, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }

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
                                placeholder = { Text(strings.htmlSearchCodeHint) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (matchIndices.isEmpty()) "0/0" else "${activeMatchIndex + 1}/${matchIndices.size}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily)
                            )
                            ExpressiveIconButton(
                                onClick = { jumpToMatch(activeMatchIndex - 1) },
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                size = 48.dp
                            ) {
                                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = strings.search)
                            }
                            ExpressiveIconButton(
                                onClick = { jumpToMatch(activeMatchIndex + 1) },
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                size = 48.dp
                            ) {
                                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = strings.search)
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = replaceQuery,
                                onValueChange = { replaceQuery = it },
                                placeholder = { Text(strings.htmlReplaceWithHint) },
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
                                Text(strings.htmlReplaceAll)
                            }
                        }
                    }
                }
            }

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
                        ExpressiveWavyProgressIndicator(
                            progress = null,
                            modifier = Modifier.widthIn(max = 220.dp)
                        )
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
            title = { Text(strings.htmlUnsavedDialogTitle) },
            text = { Text(strings.htmlUnsavedDialogBody(item.name)) },
            confirmButton = {
                Button(onClick = {
                    showUnsavedDialog = false
                    scope.launch {
                        fileRepository.writeTextSafely(item, codeValue.text)
                        onShowMessage(strings.save)
                        onClose()
                    }
                }) {
                    Text(strings.htmlSaveAndExit)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showUnsavedDialog = false
                        onClose()
                    }) {
                        Text(strings.htmlDiscard, color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { showUnsavedDialog = false }) {
                        Text(strings.cancel)
                    }
                }
            }
        )
    }
}
