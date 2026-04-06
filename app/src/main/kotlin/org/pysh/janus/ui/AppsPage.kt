package org.pysh.janus.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import org.pysh.janus.R
import org.pysh.janus.data.MediaAppInfo
import org.pysh.janus.data.WhitelistManager
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.MiuixIcons.Basic
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Preview(showBackground = true)
@Composable
private fun AppsPagePreview() {
    MiuixTheme {
        AppsPage(
            bottomPadding = 0.dp,
            whitelistVersion = 0,
            allApps = emptyList(),
            mediaApps = emptyList(),
            isRefreshing = false,
            onRefresh = {},
            onAppClick = {},
        )
    }
}

@Composable
fun AppsPage(
    bottomPadding: Dp,
    whitelistVersion: Int,
    allApps: List<MediaAppInfo>,
    mediaApps: List<MediaAppInfo>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onAppClick: (MediaAppInfo) -> Unit,
) {
    val isInPreview = LocalInspectionMode.current
    val context = LocalContext.current
    val whitelistManager = remember { if (!isInPreview) WhitelistManager(context) else null }

    var searchQuery by remember { mutableStateOf("") }

    val whitelist =
        remember(whitelistVersion) {
            whitelistManager?.getWhitelist() ?: emptySet()
        }

    val baseList =
        remember(allApps, mediaApps, whitelist) {
            val mediaPackages = mediaApps.mapTo(HashSet()) { it.packageName }
            allApps
                .map { app ->
                    app.copy(isMediaApp = app.packageName in mediaPackages)
                }.sortedByDescending { it.packageName in whitelist }
        }

    val filteredApps =
        remember(baseList, searchQuery) {
            if (searchQuery.isBlank()) {
                baseList
            } else {
                baseList.filter {
                    it.appName.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
                }
            }
        }

    // Lazy icon cache keyed by package name — survives LazyColumn item
    // recycling, so scrolling back/forth no longer re-allocates bitmaps.
    // Invalidated whenever the underlying allApps list is replaced (e.g.
    // pull-to-refresh).
    val density = LocalDensity.current.density
    val iconSizePx = remember(density) { (48 * density).toInt() }
    val iconCache =
        remember(allApps) { HashMap<String, ImageBitmap>(allApps.size) }

    val highlightColor = MiuixTheme.colorScheme.primary

    val scrollBehavior = MiuixScrollBehavior()
    val title = stringResource(R.string.nav_apps)

    Scaffold(
        topBar = {
            TopAppBar(
                title = title,
                largeTitle = title,
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()),
        ) {
            SearchBar(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                inputField = {
                    InputField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onSearch = {},
                        expanded = false,
                        onExpandedChange = {},
                        label = stringResource(R.string.search_apps),
                    )
                },
                expanded = false,
                onExpandedChange = {},
            ) {}

            PullToRefresh(
                modifier = Modifier.weight(1f),
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                topAppBarScrollBehavior = scrollBehavior,
                refreshTexts = emptyList(),
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = bottomPadding),
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val iconBitmap =
                            iconCache.getOrPut(app.packageName) {
                                app.icon
                                    .toBitmap(width = iconSizePx, height = iconSizePx)
                                    .asImageBitmap()
                            }
                        AppListItem(
                            appName = app.appName,
                            packageName = app.packageName,
                            iconBitmap = iconBitmap,
                            isMediaApp = app.isMediaApp,
                            searchQuery = searchQuery,
                            highlightColor = highlightColor,
                            onClick = { onAppClick(app) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppListItem(
    appName: String,
    packageName: String,
    iconBitmap: ImageBitmap,
    isMediaApp: Boolean,
    searchQuery: String,
    highlightColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    val highlightedName =
        remember(appName, searchQuery, highlightColor) {
            highlightMatches(appName, searchQuery, highlightColor)
        }
    val highlightedPkg =
        remember(packageName, searchQuery, highlightColor) {
            highlightMatches(packageName, searchQuery, highlightColor)
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {}
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = BitmapPainter(iconBitmap),
            contentDescription = appName,
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = highlightedName,
                style = MiuixTheme.textStyles.headline2,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
            )
            Text(
                text = highlightedPkg,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            )
            if (isMediaApp) {
                Spacer(modifier = Modifier.height(4.dp))
                MediaTag()
            }
        }
        Icon(
            imageVector = MiuixIcons.Basic.ArrowRight,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceContainerVariant,
        )
    }
}

/**
 * Build an [AnnotatedString] where every case-insensitive occurrence of
 * [query] inside [text] is highlighted with [color]. Falls back to a plain
 * string when [query] is blank or not found, so empty searches stay free.
 */
private fun highlightMatches(
    text: String,
    query: String,
    color: androidx.compose.ui.graphics.Color,
): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    val lowerText = text.lowercase()
    val lowerQuery = query.lowercase()
    val firstIdx = lowerText.indexOf(lowerQuery)
    if (firstIdx < 0) return AnnotatedString(text)

    return buildAnnotatedString {
        var cursor = 0
        var idx = firstIdx
        while (idx >= 0) {
            if (idx > cursor) append(text.substring(cursor, idx))
            withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) {
                append(text.substring(idx, idx + query.length))
            }
            cursor = idx + query.length
            idx = lowerText.indexOf(lowerQuery, cursor)
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}

@Composable
internal fun MediaTag() {
    Box(
        modifier =
            Modifier
                .background(
                    color = MiuixTheme.colorScheme.primary,
                    shape = RoundedCornerShape(4.dp),
                ).padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(
            text = stringResource(R.string.media_tag),
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onPrimary,
        )
    }
}

