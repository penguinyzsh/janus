package org.pysh.janus.ui

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.compose.ui.res.stringResource
import org.pysh.janus.R
import org.pysh.janus.data.WhitelistManager
import org.pysh.janus.service.ScreenKeepAliveService
import org.pysh.janus.util.DisplayUtils
import org.pysh.janus.util.RootUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pysh.janus.BuildConfig
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlin.math.abs

sealed interface Screen : NavKey {
    data object Activation : Screen
    data object Main : Screen
    data object About : Screen
    data class AppFeature(val packageName: String) : Screen
}

private class MainPagerState(
    val pagerState: PagerState,
    private val coroutineScope: CoroutineScope,
) {
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set

    private var isNavigating by mutableStateOf(false)
    private var navJob: Job? = null

    fun animateToPage(targetIndex: Int) {
        if (targetIndex == selectedPage) return

        navJob?.cancel()

        selectedPage = targetIndex
        isNavigating = true

        val distance = abs(targetIndex - pagerState.currentPage).coerceAtLeast(2)
        val duration = 100 * distance + 100
        val layoutInfo = pagerState.layoutInfo
        val pageSize = layoutInfo.pageSize + layoutInfo.pageSpacing
        val currentDistanceInPages =
            targetIndex - pagerState.currentPage - pagerState.currentPageOffsetFraction
        val scrollPixels = currentDistanceInPages * pageSize

        navJob = coroutineScope.launch {
            val myJob = coroutineContext.job
            try {
                pagerState.animateScrollBy(
                    value = scrollPixels,
                    animationSpec = tween(easing = EaseInOut, durationMillis = duration),
                )
            } finally {
                if (navJob == myJob) {
                    isNavigating = false
                    if (pagerState.currentPage != targetIndex) {
                        selectedPage = pagerState.currentPage
                    }
                }
            }
        }
    }

    fun syncPage() {
        if (!isNavigating && selectedPage != pagerState.currentPage) {
            selectedPage = pagerState.currentPage
        }
    }
}

@Composable
private fun rememberMainPagerState(
    pagerState: PagerState,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
): MainPagerState {
    return remember(pagerState, coroutineScope) {
        MainPagerState(pagerState, coroutineScope)
    }
}

@Composable
fun MainScreen(isModuleActive: Boolean) {
    val controller = remember {
        ThemeController(colorSchemeMode = ColorSchemeMode.System)
    }

    MiuixTheme(controller = controller) {
        val context = LocalContext.current
        val whitelistManager = remember { WhitelistManager(context) }

        val initialScreen = remember {
            if (whitelistManager.isActivated()) Screen.Main else Screen.Activation
        }
        val backStack = remember { mutableStateListOf<NavKey>(initialScreen) }

        var hasRoot by remember { mutableStateOf<Boolean?>(null) }
        var currentDpi by remember { mutableStateOf<Int?>(null) }
        var showUpdateDialog by remember {
            mutableStateOf(
                BuildConfig.DEBUG || whitelistManager.getLastSeenVersion() < BuildConfig.VERSION_CODE
            )
        }

        LaunchedEffect(Unit) {
            hasRoot = withContext(Dispatchers.IO) { RootUtils.hasRoot() }
            currentDpi = withContext(Dispatchers.IO) { DisplayUtils.getRearDpi() }
            // 恢复常亮服务
            if (whitelistManager.isKeepAliveEnabled() && !ScreenKeepAliveService.isRunning) {
                ScreenKeepAliveService.start(context, whitelistManager.getKeepAliveInterval())
            }
        }

        // 自动激活：模块和 Root 都就绪时，标记已激活并进入主界面
        LaunchedEffect(isModuleActive, hasRoot) {
            if (isModuleActive && hasRoot == true && !whitelistManager.isActivated()) {
                whitelistManager.setActivated()
                backStack.clear()
                backStack.add(Screen.Main)
            }
        }

        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
        ) { key ->
            when (key) {
                Screen.Activation -> NavEntry(key) {
                    ActivationPage(
                        isModuleActive = isModuleActive,
                        hasRoot = hasRoot == true,
                    )
                }
                Screen.Main -> NavEntry(key) {
                    val pagerState = rememberPagerState(pageCount = { 4 })
                    val mainPagerState = rememberMainPagerState(pagerState)

                    LaunchedEffect(pagerState.currentPage) {
                        mainPagerState.syncPage()
                    }

                    PagerBackHandler(mainPagerState, backStack)

                    Scaffold(
                        bottomBar = {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = mainPagerState.selectedPage == 0,
                                    onClick = { mainPagerState.animateToPage(0) },
                                    icon = MiuixIcons.Info,
                                    label = stringResource(R.string.nav_home),
                                )
                                NavigationBarItem(
                                    selected = mainPagerState.selectedPage == 1,
                                    onClick = { mainPagerState.animateToPage(1) },
                                    icon = MiuixIcons.GridView,
                                    label = stringResource(R.string.nav_apps),
                                )
                                NavigationBarItem(
                                    selected = mainPagerState.selectedPage == 2,
                                    onClick = { mainPagerState.animateToPage(2) },
                                    icon = MiuixIcons.Tune,
                                    label = stringResource(R.string.nav_features),
                                )
                                NavigationBarItem(
                                    selected = mainPagerState.selectedPage == 3,
                                    onClick = { mainPagerState.animateToPage(3) },
                                    icon = MiuixIcons.Settings,
                                    label = stringResource(R.string.nav_settings),
                                )
                            }
                        },
                        contentWindowInsets = WindowInsets.systemBars,
                    ) { paddingValues ->
                        HorizontalPager(
                            state = pagerState,
                            beyondViewportPageCount = 3,
                            userScrollEnabled = false,
                        ) { page ->
                            when (page) {
                                0 -> HomePage(
                                    bottomPadding = paddingValues.calculateBottomPadding(),
                                    isModuleActive = isModuleActive,
                                    hasRoot = hasRoot,
                                    showUpdateDialog = showUpdateDialog,
                                    onDismissUpdateDialog = {
                                        showUpdateDialog = false
                                        if (!BuildConfig.DEBUG) {
                                            whitelistManager.setLastSeenVersion(BuildConfig.VERSION_CODE)
                                        }
                                    },
                                )
                                1 -> AppsPage(
                                    bottomPadding = paddingValues.calculateBottomPadding(),
                                    onAppClick = { app -> backStack.add(Screen.AppFeature(app.packageName)) },
                                )
                                2 -> FeaturesPage(
                                    bottomPadding = paddingValues.calculateBottomPadding(),
                                    currentDpi = currentDpi,
                                    onDpiChanged = { currentDpi = it },
                                )
                                3 -> SettingsPage(
                                    bottomPadding = paddingValues.calculateBottomPadding(),
                                    onAboutClick = { backStack.add(Screen.About) },
                                )
                            }
                        }
                    }

                }
                Screen.About -> NavEntry(key) {
                    AboutPage(onBack = { backStack.removeLastOrNull() })
                }
                is Screen.AppFeature -> NavEntry(key) {
                    AppFeaturePage(
                        packageName = key.packageName,
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
                else -> NavEntry(key) {}
            }
        }

    }
}

@Composable
private fun PagerBackHandler(
    mainPagerState: MainPagerState,
    backStack: List<NavKey>,
) {
    val isEnabled by remember {
        derivedStateOf {
            backStack.size == 1 && mainPagerState.selectedPage != 0
        }
    }

    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)

    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = isEnabled,
        onBackCompleted = { mainPagerState.animateToPage(0) },
    )
}
