package org.pysh.janus.ui

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pysh.janus.R
import org.pysh.janus.data.MediaAppInfo
import org.pysh.janus.data.MediaAppScanner
import org.pysh.janus.data.WhitelistManager
import org.pysh.janus.service.JanusBackgroundService
import org.pysh.janus.util.DisplayUtils
import org.pysh.janus.core.util.RootUtils
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.All
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlin.math.abs

sealed interface Screen : NavKey {
    data object Main : Screen

    data object Wallpaper : Screen

    data object Themes : Screen

    data class AppFeature(
        val packageName: String,
    ) : Screen

    data class CardDetail(
        val slot: Int,
    ) : Screen

    data object Cards : Screen

    data object SystemCards : Screen

    data object Other : Screen

    data object Casting : Screen
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

        navJob =
            coroutineScope.launch {
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
): MainPagerState =
    remember(pagerState, coroutineScope) {
        MainPagerState(pagerState, coroutineScope)
    }

@Composable
fun MainScreen(isModuleActive: Boolean) {
    val controller =
        remember {
            ThemeController(colorSchemeMode = ColorSchemeMode.System)
        }

    MiuixTheme(controller = controller) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val whitelistManager = remember { WhitelistManager(context) }

        val backStack = remember { mutableStateListOf<NavKey>(Screen.Main) }

        var hasRoot by remember { mutableStateOf<Boolean?>(null) }
        var currentDpi by remember { mutableStateOf<Int?>(null) }
        var whitelistVersion by remember { mutableIntStateOf(0) }
        var cardsVersion by remember { mutableIntStateOf(0) }

        // Bump whitelistVersion whenever the app returns to foreground so that
        // AppsPage re-reads whitelist state. Covers the case where the user
        // toggled the whitelist from AppFeaturePage, pressed Home instead of
        // Back, then returned to AppsPage — without this the row ordering
        // would stay stale until the next pull-to-refresh.
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    whitelistVersion++
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        // App list cached at MainScreen level — survives NavDisplay entry recreation
        val scanner = remember { MediaAppScanner(context) }
        var mediaApps by remember { mutableStateOf(emptyList<MediaAppInfo>()) }
        var allApps by remember { mutableStateOf(emptyList<MediaAppInfo>()) }
        var appsLoading by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            hasRoot = withContext(Dispatchers.IO) { RootUtils.hasRoot() }
            currentDpi = withContext(Dispatchers.IO) { DisplayUtils.getRearDpi() }
            withContext(Dispatchers.IO) {
                mediaApps = scanner.scanMediaApps()
                allApps = scanner.scanAllApps()
            }
            // 自动迁移旧版存储布局（需 Root）
            if (hasRoot == true) {
                withContext(Dispatchers.IO) {
                    org.pysh.janus.data.JanusMigration
                        .migrateIfNeeded()
                }
            }
            // 同步已有配置到 LSPosed RemotePreferences，供 Hook 端读取
            withContext(Dispatchers.IO) { whitelistManager.syncAllFlags() }
            // 恢复常亮服务
            if (whitelistManager.isKeepAliveEnabled() && !JanusBackgroundService.isKeepAliveActive) {
                JanusBackgroundService.startKeepAlive(context, whitelistManager.getKeepAliveInterval())
            }
        }

        // 自动激活：模块和 Root 都就绪时，标记已激活
        LaunchedEffect(isModuleActive, hasRoot) {
            if (isModuleActive && hasRoot == true && !whitelistManager.isActivated()) {
                whitelistManager.setActivated()
            }
        }

        val isReady = isModuleActive && hasRoot == true

        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                ),
        ) { key ->
            when (key) {
                Screen.Main ->
                    NavEntry(key) {
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
                                        icon = MiuixIcons.All,
                                        label = stringResource(R.string.nav_home),
                                    )
                                    NavigationBarItem(
                                        selected = mainPagerState.selectedPage == 1,
                                        onClick = { if (isReady) mainPagerState.animateToPage(1) },
                                        icon = MiuixIcons.GridView,
                                        label = stringResource(R.string.nav_apps),
                                        enabled = isReady,
                                    )
                                    NavigationBarItem(
                                        selected = mainPagerState.selectedPage == 2,
                                        onClick = { if (isReady) mainPagerState.animateToPage(2) },
                                        icon = MiuixIcons.Tune,
                                        label = stringResource(R.string.nav_features),
                                        enabled = isReady,
                                    )
                                    NavigationBarItem(
                                        selected = mainPagerState.selectedPage == 3,
                                        onClick = { mainPagerState.animateToPage(3) },
                                        icon = MiuixIcons.Info,
                                        label = stringResource(R.string.about),
                                    )
                                }
                            },
                            contentWindowInsets = WindowInsets.systemBars,
                        ) { paddingValues ->
                            // When a secondary screen (AppFeature, Wallpaper, About) is
                            // pushed on top, the pager is still composed underneath.
                            // Hide ALL pager content from the accessibility tree so
                            // services like Xiaomi AI Engine don't traverse 100+ items
                            // on every UI change in the overlay screen.
                            val isMainCovered = backStack.size > 1
                            HorizontalPager(
                                state = pagerState,
                                beyondViewportPageCount = 3,
                                userScrollEnabled = false,
                            ) { page ->
                                val hideFromA11y = isMainCovered || page != pagerState.currentPage
                                val pageModifier =
                                    if (hideFromA11y) {
                                        Modifier.clearAndSetSemantics {}
                                    } else {
                                        Modifier
                                    }
                                Box(modifier = pageModifier) {
                                    when (page) {
                                        0 ->
                                            HomePage(
                                                bottomPadding = paddingValues.calculateBottomPadding(),
                                                isModuleActive = isModuleActive,
                                                hasRoot = hasRoot,
                                            )
                                        1 ->
                                            AppsPage(
                                                bottomPadding = paddingValues.calculateBottomPadding(),
                                                whitelistVersion = whitelistVersion,
                                                allApps = allApps,
                                                mediaApps = mediaApps,
                                                isRefreshing = appsLoading,
                                                onRefresh = {
                                                    appsLoading = true
                                                    scope.launch {
                                                        withContext(Dispatchers.IO) {
                                                            mediaApps = scanner.scanMediaApps()
                                                            allApps = scanner.scanAllApps()
                                                        }
                                                        appsLoading = false
                                                    }
                                                },
                                                onAppClick = { app -> backStack.add(Screen.AppFeature(app.packageName)) },
                                            )
                                        2 ->
                                            FeaturesPage(
                                                bottomPadding = paddingValues.calculateBottomPadding(),
                                                onWallpaperClick = { backStack.add(Screen.Wallpaper) },
                                                onCastingClick = { backStack.add(Screen.Casting) },
                                                onThemesClick = { backStack.add(Screen.Themes) },
                                                onCardsClick = { backStack.add(Screen.Cards) },
                                            )
                                        3 ->
                                            SettingsPage(
                                                bottomPadding = paddingValues.calculateBottomPadding(),
                                                onOtherClick = { backStack.add(Screen.Other) },
                                            )
                                    }
                                }
                            }
                        }
                    }
                Screen.Other ->
                    NavEntry(key) {
                        OtherPage(onBack = { backStack.removeLastOrNull() })
                    }
                Screen.Wallpaper ->
                    NavEntry(key) {
                        WallpaperPage(onBack = { backStack.removeLastOrNull() })
                    }
                Screen.Themes ->
                    NavEntry(key) {
                        ThemesPage(
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                Screen.Casting ->
                    NavEntry(key) {
                        CastingPage(
                            currentDpi = currentDpi,
                            onDpiChanged = { currentDpi = it },
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                Screen.Cards ->
                    NavEntry(key) {
                        CardsPage(
                            bottomPadding = 0.dp,
                            cardsVersion = cardsVersion,
                            onBack = { backStack.removeLastOrNull() },
                            onCardClick = { slot -> backStack.add(Screen.CardDetail(slot)) },
                            onSystemCardsClick = { backStack.add(Screen.SystemCards) },
                            onCardsChanged = { cardsVersion++ },
                        )
                    }
                Screen.SystemCards ->
                    NavEntry(key) {
                        SystemCardsPage(
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                is Screen.CardDetail ->
                    NavEntry(key) {
                        CardDetailPage(
                            slot = key.slot,
                            onBack = {
                                backStack.removeLastOrNull()
                                cardsVersion++
                            },
                        )
                    }
                is Screen.AppFeature ->
                    NavEntry(key) {
                        AppFeaturePage(
                            packageName = key.packageName,
                            onBack = {
                                backStack.removeLastOrNull()
                                whitelistVersion++
                            },
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
