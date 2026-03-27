package org.pysh.janus.ui

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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

    val whitelist = remember(whitelistVersion) {
        whitelistManager?.getWhitelist() ?: emptySet()
    }

    val baseList = remember(allApps, mediaApps, whitelist) {
        val mediaPackages = mediaApps.mapTo(HashSet()) { it.packageName }
        allApps.map { app ->
            app.copy(isMediaApp = app.packageName in mediaPackages)
        }.sortedByDescending { it.packageName in whitelist }
    }

    val filteredApps = remember(baseList, searchQuery) {
        if (searchQuery.isBlank()) baseList
        else baseList.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

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
            modifier = Modifier
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
                        val cachedIcon = remember(app.packageName) { app.icon }
                        AppListItem(
                            appName = app.appName,
                            packageName = app.packageName,
                            icon = cachedIcon,
                            isMediaApp = app.isMediaApp,
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
    icon: Drawable,
    isMediaApp: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(drawable = icon, size = 48, contentDescription = appName)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = appName,
                style = MiuixTheme.textStyles.headline2,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
            )
            Text(
                text = packageName,
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

@Composable
internal fun MediaTag() {
    Box(
        modifier = Modifier
            .background(
                color = MiuixTheme.colorScheme.primary,
                shape = RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(
            text = stringResource(R.string.media_tag),
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
internal fun AppIcon(drawable: Drawable, size: Int = 40, contentDescription: String) {
    val density = LocalContext.current.resources.displayMetrics.density
    val sizePx = (size * density).toInt()
    val bitmap = remember(drawable) {
        drawable.toBitmap(width = sizePx, height = sizePx).asImageBitmap()
    }
    Image(
        painter = BitmapPainter(bitmap),
        contentDescription = contentDescription,
        modifier = Modifier.size(size.dp),
    )
}
