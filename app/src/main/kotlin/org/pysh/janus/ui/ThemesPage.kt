package org.pysh.janus.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pysh.janus.R
import org.pysh.janus.data.ThemeManager
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Top-level page listing imported rear-screen themes.
 *
 * Responsibilities:
 *  - SAF import picker (`.mrc` / `.mtz` / `.zip`)
 *  - List rendering with thumbnail, name, author, active badge
 *  - Drag-and-drop reorder via sh.calvin.reorderable
 *  - Navigation to [ThemeDetailPage] on tap (handled by caller)
 *
 * The page is stateless with respect to the MainScreen nav stack — it
 * receives [onBack] and [onThemeClick] and owns only ephemeral import
 * progress state.
 */
@Composable
fun ThemesPage(
    onBack: () -> Unit,
    onThemeClick: (themeId: String) -> Unit,
) {
    val isInPreview = LocalInspectionMode.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val themeManager = remember { if (!isInPreview) ThemeManager(context) else null }

    var themes by remember { mutableStateOf(themeManager?.getThemes() ?: emptyList()) }
    var activeId by remember { mutableStateOf(themeManager?.getActiveThemeId()) }
    var isImporting by remember { mutableStateOf(false) }

    // Refresh when the hosting lifecycle resumes — this covers returning
    // from ThemeDetailPage (after delete/apply/deactivate) as well as coming
    // back to the app from the background.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && themeManager != null) {
                themes = themeManager.getThemes()
                activeId = themeManager.getActiveThemeId()
                android.util.Log.i(
                    "Janus-ThemeManager",
                    "ThemesPage refreshed: ${themes.size} themes, active=$activeId",
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val importLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            if (uri == null || themeManager == null) return@rememberLauncherForActivityResult
            isImporting = true
            scope.launch {
                val result =
                    withContext(Dispatchers.IO) {
                        val fileName = queryDisplayName(context, uri) ?: "theme"
                        themeManager.importTheme(uri, fileName)
                    }
                when (result) {
                    is ThemeManager.ImportResult.Success -> {
                        themes = themeManager.getThemes()
                        Toast.makeText(
                            context,
                            context.getString(R.string.theme_import_success, result.entry.displayName),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    is ThemeManager.ImportResult.Failure -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.theme_import_failed, result.reason),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
                isImporting = false
            }
        }

    val lazyListState = rememberLazyListState()
    val reorderableState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
            val fromId = from.key as? String ?: return@rememberReorderableLazyListState
            val toId = to.key as? String ?: return@rememberReorderableLazyListState
            val mutable = themes.toMutableList()
            val fromIdx = mutable.indexOfFirst { it.id == fromId }
            val toIdx = mutable.indexOfFirst { it.id == toId }
            if (fromIdx != -1 && toIdx != -1) {
                mutable.add(toIdx, mutable.removeAt(fromIdx))
                themes = mutable
                // Persist order asynchronously; order is decorative, no need to await.
                themeManager?.let {
                    scope.launch(Dispatchers.IO) {
                        it.reorderThemes(mutable.map { entry -> entry.id })
                    }
                }
            }
        }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.themes_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            importLauncher.launch(
                                arrayOf("*/*"), // SAF does not expose .mrc mime; wildcard + let parser validate.
                            )
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.theme_import),
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { paddingValues ->
        if (themes.isEmpty()) {
            ThemesEmptyState(paddingValues, isImporting)
        } else {
            LazyColumn(
                state = lazyListState,
                contentPadding =
                    PaddingValues(
                        top = paddingValues.calculateTopPadding() + 8.dp,
                        bottom = paddingValues.calculateBottomPadding() + 16.dp,
                        start = 16.dp,
                        end = 16.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(themes, key = { it.id }) { theme ->
                    ReorderableItem(reorderableState, key = theme.id) {
                        ThemeRow(
                            theme = theme,
                            isActive = theme.id == activeId,
                            thumbnailFile = themeManager?.thumbnailFile(theme.id),
                            onClick = { onThemeClick(theme.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemesEmptyState(
    paddingValues: PaddingValues,
    isImporting: Boolean,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(paddingValues),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text =
                    stringResource(
                        if (isImporting) R.string.theme_importing else R.string.themes_empty_title,
                    ),
                fontSize = 16.sp,
                color = MiuixTheme.colorScheme.onBackground,
            )
            if (!isImporting) {
                Text(
                    text = stringResource(R.string.themes_empty_hint),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
        }
    }
}

@Composable
private fun ThemeRow(
    theme: ThemeManager.ThemeEntry,
    isActive: Boolean,
    thumbnailFile: java.io.File?,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onClick() },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Thumbnail
            Box(
                modifier =
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MiuixTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                val bitmap =
                    remember(thumbnailFile, theme.hasThumbnail) {
                        if (theme.hasThumbnail && thumbnailFile?.exists() == true) {
                            try {
                                BitmapFactory.decodeFile(thumbnailFile.absolutePath)
                            } catch (_: Exception) {
                                null
                            }
                        } else {
                            null
                        }
                    }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = theme.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = theme.displayName.take(2),
                        fontSize = 18.sp,
                        color = MiuixTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = theme.displayName,
                        fontSize = 16.sp,
                        color = MiuixTheme.colorScheme.onBackground,
                    )
                    if (isActive) {
                        Box(
                            modifier =
                                Modifier
                                    .padding(start = 8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MiuixTheme.colorScheme.primary)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.theme_active_badge),
                                fontSize = 10.sp,
                                color = Color.White,
                            )
                        }
                    }
                }
                val subtitle =
                    buildString {
                        if (!theme.author.isNullOrBlank()) append(theme.author)
                        if (theme.fileSize > 0) {
                            if (isNotEmpty()) append(" · ")
                            append(formatSize(theme.fileSize))
                        }
                        if (theme.isExtractedFromMtz) {
                            if (isNotEmpty()) append(" · ")
                            append("MTZ")
                        }
                    }
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
            }
        }
    }
}

private fun queryDisplayName(
    context: android.content.Context,
    uri: Uri,
): String? =
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    } catch (_: Exception) {
        null
    }

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val units = arrayOf("B", "KB", "MB", "GB")
    var size = bytes.toDouble()
    var unit = 0
    while (size >= 1024 && unit < units.lastIndex) {
        size /= 1024
        unit++
    }
    return "%.1f %s".format(size, units[unit])
}
