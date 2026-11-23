package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.localization.LocalAppStrings
import com.example.ui.components.ApkPreviewSheet
import com.example.ui.components.AppDestination
import com.example.ui.components.BottomEdgeFadeScrim
import com.example.ui.components.FileDetailsSheet
import com.example.ui.components.FileOperationProgressDialog
import com.example.ui.components.FloatingCapsuleNavBar
import com.example.ui.components.FolderPickerSheet
import com.example.ui.components.LocalIsTvMode
import com.example.ui.components.LocalUseSideNavRail
import com.example.ui.components.TopEdgeFadeScrim
import com.example.ui.components.TvSideCapsuleNavRail
import com.example.ui.components.ZipPreviewSheet
import com.example.ui.components.isTelevisionContext
import com.example.ui.screens.DocumentViewerScreen
import com.example.ui.screens.FileExplorerScreen
import com.example.ui.screens.HtmlEditorScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SmartScannerScreen
import com.example.ui.theme.KantaFilesTheme
import com.example.ui.viewmodel.ActiveViewer
import com.example.ui.viewmodel.FileExplorerViewModel
import com.example.ui.viewmodel.ScannerViewModel
import com.example.ui.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { enableEdgeToEdge() }
        setContent {
            KantaFilesRootApp()
        }
    }
}

@Composable
fun KantaFilesRootApp(
    explorerViewModel: FileExplorerViewModel = viewModel(),
    scannerViewModel: ScannerViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val isTvDevice = remember(context) { isTelevisionContext(context) }

    val prefs by settingsViewModel.preferences.collectAsStateWithLifecycle()
    val explorerState by explorerViewModel.uiState.collectAsStateWithLifecycle()
    val scannerState by scannerViewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    LaunchedEffect(explorerState.snackbarMessage) {
        val msg = explorerState.snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            explorerViewModel.clearSnackbar()
        }
    }

    LaunchedEffect(scannerState.snackbarMessage) {
        val msg = scannerState.snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            scannerViewModel.clearSnackbar()
        }
    }

    KantaFilesTheme(
        language = prefs.language,
        themeMode = prefs.themeMode,
        dynamicColor = prefs.useDynamicColors,
        colorPalette = prefs.colorPalette,
        fontScaleOption = prefs.fontScale,
        listDensity = prefs.listDensity,
        expressiveAnimations = prefs.expressiveAnimations,
        hapticFeedback = prefs.hapticFeedback
    ) {
        val strings = LocalAppStrings.current
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useSideNavRail = isTvDevice || (maxWidth >= 680.dp && maxWidth > maxHeight)

            CompositionLocalProvider(
                LocalIsTvMode provides isTvDevice,
                LocalUseSideNavRail provides useSideNavRail
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        snackbarHost = {
                            SnackbarHost(
                                hostState = snackbarHostState,
                                modifier = Modifier
                                    .navigationBarsPadding()
                                    .padding(
                                        bottom = if (useSideNavRail) 28.dp else 92.dp,
                                        start = 16.dp,
                                        end = 16.dp
                                    )
                            )
                        }
                    ) { _ ->
                        Row(modifier = Modifier.fillMaxSize()) {
                            if (useSideNavRail) {
                                TvSideCapsuleNavRail(
                                    currentRoute = currentRoute,
                                    onNavigate = { destination ->
                                        if (currentRoute != destination.route) {
                                            navController.navigate(destination.route) {
                                                popUpTo(AppDestination.FILES.route) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    }
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                val layoutDir = LocalLayoutDirection.current
                                val dirSign = if (layoutDir == LayoutDirection.Rtl) -1 else 1
                                NavHost(
                                    navController = navController,
                                    startDestination = AppDestination.FILES.route,
                                    enterTransition = {
                                        val fromIdx = routeIndex(initialState.destination.route)
                                        val toIdx = routeIndex(targetState.destination.route)
                                        val slideSign = if (toIdx >= fromIdx) dirSign else -dirSign
                                        if (prefs.expressiveAnimations) {
                                            slideInHorizontally(
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                                    stiffness = Spring.StiffnessMediumLow
                                                ),
                                                initialOffsetX = { width -> (width * 0.22f * slideSign).toInt() }
                                            ) + scaleIn(
                                                initialScale = 0.94f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                                    stiffness = Spring.StiffnessMedium
                                                )
                                            ) + fadeIn(tween(220))
                                        } else {
                                            fadeIn(tween(120))
                                        }
                                    },
                                    exitTransition = {
                                        val fromIdx = routeIndex(initialState.destination.route)
                                        val toIdx = routeIndex(targetState.destination.route)
                                        val slideSign = if (toIdx >= fromIdx) dirSign else -dirSign
                                        if (prefs.expressiveAnimations) {
                                            slideOutHorizontally(
                                                animationSpec = tween(200),
                                                targetOffsetX = { width -> (-width * 0.16f * slideSign).toInt() }
                                            ) + scaleOut(
                                                targetScale = 0.96f,
                                                animationSpec = tween(200)
                                            ) + fadeOut(tween(160))
                                        } else {
                                            fadeOut(tween(110))
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    composable(AppDestination.FILES.route) {
                                        FileExplorerScreen(
                                            uiState = explorerState,
                                            viewModel = explorerViewModel
                                        )
                                    }
                                    composable(AppDestination.SCANNER.route) {
                                        SmartScannerScreen(
                                            uiState = scannerState,
                                            scannerViewModel = scannerViewModel,
                                            confirmBatchDelete = prefs.scannerConfirmBatchDelete,
                                            showHiddenFiles = prefs.showHiddenFiles,
                                            fileRepository = explorerViewModel.fileRepository,
                                            storageRoots = explorerState.storageRoots,
                                            onOpenFile = { item -> explorerViewModel.openFileItem(item) }
                                        )
                                    }
                                    composable(AppDestination.SETTINGS.route) {
                                        SettingsScreen(
                                            preferences = prefs,
                                            viewModel = settingsViewModel,
                                            onShowMessage = { explorerViewModel.showSnackbar(it) }
                                        )
                                    }
                                }

                                // Soft gradient edge fades (Files screen draws its own top scrim under its pinned bar)
                                if (currentRoute != AppDestination.FILES.route) {
                                    TopEdgeFadeScrim(modifier = Modifier.align(Alignment.TopCenter))
                                }
                                if (!useSideNavRail) {
                                    BottomEdgeFadeScrim(modifier = Modifier.align(Alignment.BottomCenter))
                                    FloatingCapsuleNavBar(
                                        currentRoute = currentRoute,
                                        onNavigate = { destination ->
                                            if (currentRoute != destination.route) {
                                                navController.navigate(destination.route) {
                                                    popUpTo(AppDestination.FILES.route) { saveState = true }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        },
                                        modifier = Modifier.align(Alignment.BottomCenter)
                                    )
                                }
                            }
                        }
                    }

            // Fullscreen Editors & Viewers Overlay with spring scale/fade
            AnimatedVisibility(
                visible = explorerState.activeViewer != null,
                enter = fadeIn() + scaleIn(
                    initialScale = 0.94f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
                ),
                exit = fadeOut() + scaleOut(targetScale = 0.95f)
            ) {
                when (val viewer = explorerState.activeViewer) {
                    is ActiveViewer.HtmlEditor -> {
                        HtmlEditorScreen(
                            item = viewer.item,
                            fileRepository = explorerViewModel.fileRepository,
                            fontScale = prefs.fontScale.scale,
                            onClose = { explorerViewModel.closeActiveViewer() },
                            onShowMessage = { explorerViewModel.showSnackbar(it) }
                        )
                    }
                    is ActiveViewer.DocumentViewer -> {
                        DocumentViewerScreen(
                            item = viewer.item,
                            fileRepository = explorerViewModel.fileRepository,
                            fontScale = prefs.fontScale.scale,
                            hostArchive = viewer.hostArchive,
                            archiveManager = explorerViewModel.archiveManager,
                            onClose = { explorerViewModel.closeActiveViewer() },
                            onShowMessage = { explorerViewModel.showSnackbar(it) }
                        )
                    }
                    null -> Unit
                }
            }

            // Modals & Bottom Sheets
            explorerState.operationProgress?.let { progress ->
                FileOperationProgressDialog(
                    progress = progress,
                    onCancel = { explorerViewModel.cancelActiveOperation() }
                )
            }

            if (explorerState.isApkLoading || explorerState.apkPreview != null) {
                ApkPreviewSheet(
                    metadata = explorerState.apkPreview,
                    isLoading = explorerState.isApkLoading,
                    onDismiss = { explorerViewModel.dismissApkPreview() },
                    onBrowseContents = { explorerViewModel.enterArchiveBrowse(it) },
                    onInstall = { meta -> explorerViewModel.installApk(meta) }
                )
            }

            explorerState.zipPreviewItem?.let { zipItem ->
                ZipPreviewSheet(
                    zipItem = zipItem,
                    entries = explorerState.zipPreviewEntries,
                    isLoading = explorerState.isZipLoading,
                    onDismiss = { explorerViewModel.dismissZipPreview() },
                    onBrowseContents = { explorerViewModel.enterArchiveBrowse(it) },
                    onTestIntegrity = { explorerViewModel.testArchiveIntegrity(it) },
                    onExtractClick = { explorerViewModel.requestExtractZip(it) }
                )
            }

            explorerState.detailsItem?.let { detailItem ->
                FileDetailsSheet(
                    item = detailItem,
                    onDismiss = { explorerViewModel.dismissDetails() },
                    onOpenAsText = { explorerViewModel.openAsText(it) },
                    onOpenInHtmlEditor = { explorerViewModel.openInHtmlEditor(it) },
                    onOpenExternal = {
                        explorerViewModel.fileRepository.openExternally(it).onFailure {
                            explorerViewModel.showSnackbar(strings.unknownLabel)
                        }
                    }
                )
            }

            explorerState.folderPicker?.let { request ->
                FolderPickerSheet(
                    title = request.title,
                    confirmLabel = request.confirmLabel,
                    fileRepository = explorerViewModel.fileRepository,
                    storageRoots = explorerState.storageRoots,
                    initialPath = explorerState.currentPath,
                    initialSafUri = explorerState.currentSafUri,
                    showHidden = prefs.showHiddenFiles,
                    onDismiss = { explorerViewModel.dismissFolderPicker() },
                    onConfirm = request.onConfirm
                )
            }
                }
            }
        }
    }
}

private fun routeIndex(route: String?): Int =
    AppDestination.entries.indexOfFirst { it.route == route }.takeIf { it >= 0 } ?: 0
