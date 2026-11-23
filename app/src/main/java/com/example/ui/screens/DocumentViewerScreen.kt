package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.FileRepository
import com.example.localization.LocalAppStrings
import com.example.model.FileCategory
import com.example.model.FileItem
import com.example.ui.components.ExpressiveIconButton
import com.example.ui.components.ExpressiveWavyProgressIndicator
import com.example.ui.components.LocalIsTvMode
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ReadexProFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun DocumentViewerScreen(
    item: FileItem,
    fileRepository: FileRepository,
    fontScale: Float,
    onClose: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val isTvMode = LocalIsTvMode.current
    BackHandler { onClose() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(
                    horizontal = if (isTvMode) 28.dp else 12.dp,
                    vertical = if (isTvMode) 20.dp else 8.dp
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (item.category) {
                FileCategory.PDF -> PdfDocumentView(
                    item = item,
                    fileRepository = fileRepository,
                    onClose = onClose,
                    onShowMessage = onShowMessage
                )
                FileCategory.IMAGE -> ImageDocumentView(
                    item = item,
                    fileRepository = fileRepository,
                    onClose = onClose
                )
                else -> TextDocumentView(
                    item = item,
                    fileRepository = fileRepository,
                    fontScale = fontScale,
                    context = context,
                    onClose = onClose,
                    onShowMessage = onShowMessage
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.TextDocumentView(
    item: FileItem,
    fileRepository: FileRepository,
    fontScale: Float,
    context: Context,
    onClose: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    val appDir = LocalLayoutDirection.current
    var isLoading by remember { mutableStateOf(true) }
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var fullText by remember { mutableStateOf("") }
    var isTruncated by remember { mutableStateOf(false) }
    var showLineNumbers by remember { mutableStateOf(item.category == FileCategory.CODE) }
    var forceLtr by remember { mutableStateOf(item.category == FileCategory.CODE || appDir == LayoutDirection.Ltr) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(item.id) {
        isLoading = true
        fileRepository.readTextDocument(item).fold(
            onSuccess = { (content, truncated) ->
                fullText = content
                lines = content.split('\n')
                isTruncated = truncated
                isLoading = false
            },
            onFailure = { err ->
                isLoading = false
                onShowMessage(err.message ?: strings.unknownLabel)
                onClose()
            }
        )
    }

    val displayedLines = remember(lines, searchQuery) {
        if (searchQuery.isBlank()) {
            lines.mapIndexed { idx, line -> idx + 1 to line }
        } else {
            lines.mapIndexedNotNull { idx, line ->
                if (line.contains(searchQuery, ignoreCase = true)) (idx + 1 to line) else null
            }
        }
    }

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
                    onClick = onClose,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    size = 48.dp,
                    modifier = Modifier.testTag("doc_viewer_back")
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
                        text = "${strings.formatBytes(item.sizeBytes)} • ${strings.docLinesCount(lines.size)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row {
                IconButton(onClick = { showSearch = !showSearch }) {
                    Icon(Icons.Rounded.Search, contentDescription = strings.search)
                }
                IconButton(onClick = { showLineNumbers = !showLineNumbers }) {
                    Icon(
                        Icons.Rounded.FormatListNumbered,
                        contentDescription = null,
                        tint = if (showLineNumbers) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = { forceLtr = !forceLtr }) {
                    Icon(Icons.Rounded.SwapHoriz, contentDescription = null)
                }
                IconButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(item.name, fullText))
                    onShowMessage(strings.docTextCopied)
                }) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = strings.copy)
                }
            }
        }
    }

    AnimatedVisibility(visible = showSearch) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(strings.docFilterLinesHint) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (isTruncated) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer
        ) {
            Text(
                text = strings.docLargeTruncated,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
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
        } else {
            val textFont = if (item.category == FileCategory.CODE) JetBrainsMonoFontFamily else ReadexProFontFamily
            val docListState = rememberLazyListState()
            val docScope = rememberCoroutineScope()
            CompositionLocalProvider(
                LocalLayoutDirection provides if (forceLtr) LayoutDirection.Ltr else LayoutDirection.Rtl
            ) {
                LazyColumn(
                    state = docListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .focusable()
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionDown, Key.PageDown -> {
                                        docScope.launch { docListState.animateScrollBy(260f) }
                                        true
                                    }
                                    Key.DirectionUp, Key.PageUp -> {
                                        docScope.launch { docListState.animateScrollBy(-260f) }
                                        true
                                    }
                                    else -> false
                                }
                            } else {
                                false
                            }
                        },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(displayedLines, key = { _, pair -> pair.first }) { _, (lineNumber, text) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (showLineNumbers) {
                                Text(
                                    text = "$lineNumber",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = (12f * fontScale).sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = text.ifEmpty { " " },
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = textFont,
                                    fontSize = (14.5f * fontScale).sp,
                                    lineHeight = (22f * fontScale).sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.PdfDocumentView(
    item: FileItem,
    fileRepository: FileRepository,
    onClose: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    var pages by remember { mutableStateOf<List<ImageBitmap>>(emptyList()) }
    var totalPages by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(item.id) {
        isLoading = true
        withContext(Dispatchers.IO) {
            runCatching {
                val pfd: ParcelFileDescriptor = if (item.safUri != null) {
                    val tempPdf = File(context.cacheDir, "view_${System.currentTimeMillis()}.pdf")
                    context.contentResolver.openInputStream(item.safUri)?.use { inp ->
                        FileOutputStream(tempPdf).use { out -> inp.copyTo(out) }
                    }
                    ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY)
                } else {
                    ParcelFileDescriptor.open(File(item.path), ParcelFileDescriptor.MODE_READ_ONLY)
                }

                pfd.use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        totalPages = renderer.pageCount
                        val limit = minOf(renderer.pageCount, 40)
                        val rendered = mutableListOf<ImageBitmap>()
                        for (i in 0 until limit) {
                            renderer.openPage(i).use { page ->
                                val width = 1080
                                val height = ((page.height.toFloat() / page.width.toFloat()) * width).toInt().coerceAtLeast(1)
                                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                val canvas = android.graphics.Canvas(bmp)
                                canvas.drawColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                rendered += bmp.asImageBitmap()
                            }
                        }
                        rendered
                    }
                }
            }
        }.fold(
            onSuccess = { list ->
                pages = list
                isLoading = false
            },
            onFailure = {
                isLoading = false
                onShowMessage(strings.unknownLabel)
                fileRepository.openExternally(item)
                onClose()
            }
        )
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
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
                    onClick = onClose,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    size = 48.dp
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = strings.back, modifier = Modifier.size(22.dp))
                }
                Column {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = strings.docPdfPages(totalPages),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = { fileRepository.openExternally(item) }) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = strings.docOpenExternal)
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
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
        } else {
            val pdfListState = rememberLazyListState()
            val pdfScope = rememberCoroutineScope()
            LazyColumn(
                state = pdfListState,
                modifier = Modifier
                    .fillMaxSize()
                    .focusable()
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.key) {
                                Key.DirectionDown, Key.PageDown -> {
                                    pdfScope.launch { pdfListState.animateScrollBy(360f) }
                                    true
                                }
                                Key.DirectionUp, Key.PageUp -> {
                                    pdfScope.launch { pdfListState.animateScrollBy(-360f) }
                                    true
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    },
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(pages) { index, pageBitmap ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        tonalElevation = 2.dp
                    ) {
                        Column {
                            Image(
                                bitmap = pageBitmap,
                                contentDescription = strings.docPdfPageOf(index + 1, totalPages),
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = strings.docPdfPageOf(index + 1, totalPages),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ImageDocumentView(
    item: FileItem,
    fileRepository: FileRepository,
    onClose: () -> Unit
) {
    val strings = LocalAppStrings.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val modelData: Any = item.safUri ?: File(item.path)

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
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
                    onClick = onClose,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    size = 48.dp
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = strings.back, modifier = Modifier.size(22.dp))
                }
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { fileRepository.openExternally(item) }) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = strings.docOpenExternal)
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset = if (scale > 1f) offset + pan else Offset.Zero
                }
            }
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = modelData,
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )
        }
    }
}
