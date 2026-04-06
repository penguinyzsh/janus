package org.pysh.janus.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import org.pysh.janus.core.util.RootUtils
import org.pysh.janus.data.ThemeManager
import org.pysh.janus.data.WhitelistManager
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Grid of imported rear-screen themes. Tapping a theme opens an action dialog
 * with Apply / Deactivate / Delete. Reorder via long-press drag.
 */
@Composable
fun ThemesPage(onBack: () -> Unit) {
    val isInPreview = LocalInspectionMode.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val themeManager = remember { if (!isInPreview) ThemeManager(context) else null }
    val whitelistManager = remember { if (!isInPreview) WhitelistManager(context) else null }

    var themes by remember { mutableStateOf(themeManager?.getThemes() ?: emptyList()) }
    var activeId by remember { mutableStateOf(themeManager?.getActiveThemeId()) }
    var isBusy by remember { mutableStateOf(false) }
    var actionTargetId by remember { mutableStateOf<String?>(null) }
    var deleteTargetId by remember { mutableStateOf<String?>(null) }
    var renameTargetId by remember { mutableStateOf<String?>(null) }
    var renameInput by remember { mutableStateOf("") }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && themeManager != null) {
                    themes = themeManager.getThemes()
                    activeId = themeManager.getActiveThemeId()
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
            isBusy = true
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
                isBusy = false
            }
        }

    val lazyGridState = rememberLazyGridState()
    val reorderableState =
        rememberReorderableLazyGridState(lazyGridState) { from, to ->
            val fromId = from.key as? String ?: return@rememberReorderableLazyGridState
            val toId = to.key as? String ?: return@rememberReorderableLazyGridState
            val mutable = themes.toMutableList()
            val fromIdx = mutable.indexOfFirst { it.id == fromId }
            val toIdx = mutable.indexOfFirst { it.id == toId }
            if (fromIdx != -1 && toIdx != -1) {
                mutable.add(toIdx, mutable.removeAt(fromIdx))
                themes = mutable
            }
        }

    val scrollBehavior = MiuixScrollBehavior()
    val title = stringResource(R.string.themes_title)

    Scaffold(
        topBar = {
            TopAppBar(
                title = title,
                largeTitle = title,
                scrollBehavior = scrollBehavior,
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
                            if (!isBusy) importLauncher.launch(arrayOf("*/*"))
                        },
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Add,
                            contentDescription = stringResource(R.string.theme_import),
                        )
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = lazyGridState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(top = paddingValues.calculateTopPadding())
                    .padding(horizontal = 4.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            items(themes, key = { it.id }) { theme ->
                ReorderableItem(reorderableState, key = theme.id) { isDragging ->
                    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "drag_elev")
                    val isActive = theme.id == activeId

                    val cardModifier =
                        Modifier
                            .padding(8.dp)
                            .aspectRatio(0.8f)
                            .shadow(elevation, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .background(MiuixTheme.colorScheme.surface)
                            .let {
                                if (isActive) {
                                    it.border(2.dp, MiuixTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                                } else {
                                    it
                                }
                            }.longPressDraggableHandle(
                                onDragStopped = {
                                    val reordered = themes.map { it.id }
                                    scope.launch(Dispatchers.IO) {
                                        themeManager?.reorderThemes(reordered)
                                    }
                                },
                            ).clickable(enabled = !isBusy) {
                                actionTargetId = theme.id
                            }

                    Box(modifier = cardModifier) {
                        val thumbFile = themeManager?.thumbnailFile(theme.id)
                        val bitmap =
                            remember(thumbFile, theme.hasThumbnail) {
                                if (theme.hasThumbnail && thumbFile?.exists() == true) {
                                    try {
                                        BitmapFactory.decodeFile(thumbFile.absolutePath)?.asImageBitmap()
                                    } catch (_: Exception) {
                                        null
                                    }
                                } else {
                                    null
                                }
                            }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = theme.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(MiuixTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = theme.displayName.take(2),
                                    fontSize = 20.sp,
                                    color = MiuixTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }

                        if (isActive) {
                            Box(
                                modifier =
                                    Modifier
                                        .align(Alignment.TopStart)
                                        .padding(8.dp)
                                        .background(MiuixTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.theme_active_badge),
                                    color = MiuixTheme.colorScheme.onPrimary,
                                    style = MiuixTheme.textStyles.body2,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Action dialog for a tapped theme
        val actionTarget = themes.find { it.id == actionTargetId }
        SuperDialog(
            show = actionTarget != null,
            title = actionTarget?.displayName ?: "",
            onDismissRequest = { actionTargetId = null },
        ) {
            val target = actionTarget ?: return@SuperDialog
            val targetIsActive = target.id == activeId
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (targetIsActive) {
                    TextButton(
                        text = stringResource(R.string.theme_deactivate),
                        onClick = {
                            actionTargetId = null
                            if (themeManager == null) return@TextButton
                            isBusy = true
                            scope.launch {
                                withContext(Dispatchers.IO) { themeManager.deactivate() }
                                if (whitelistManager?.isThemeAutoRestart() != false) {
                                    withContext(Dispatchers.IO) { RootUtils.restartBackScreen() }
                                }
                                activeId = null
                                Toast
                                    .makeText(
                                        context,
                                        context.getString(R.string.theme_deactivated),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                isBusy = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    )
                } else {
                    TextButton(
                        text = stringResource(R.string.theme_apply),
                        onClick = {
                            actionTargetId = null
                            if (themeManager == null) return@TextButton
                            isBusy = true
                            scope.launch {
                                val ok =
                                    withContext(Dispatchers.IO) { themeManager.applyTheme(target.id) }
                                if (ok) {
                                    activeId = target.id
                                    if (whitelistManager?.isThemeAutoRestart() != false) {
                                        withContext(Dispatchers.IO) { RootUtils.restartBackScreen() }
                                    }
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(R.string.theme_apply_success),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                } else {
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(R.string.theme_apply_failed),
                                            Toast.LENGTH_LONG,
                                        ).show()
                                }
                                isBusy = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    )
                }
                TextButton(
                    text = stringResource(R.string.wp_rename),
                    onClick = {
                        renameInput = target.displayName
                        renameTargetId = target.id
                        actionTargetId = null
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                TextButton(
                    text = stringResource(R.string.theme_delete),
                    onClick = {
                        deleteTargetId = target.id
                        actionTargetId = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Delete confirmation
        val deleteTarget = themes.find { it.id == deleteTargetId }
        SuperDialog(
            show = deleteTarget != null,
            title = stringResource(R.string.theme_delete_confirm_title),
            summary =
                deleteTarget?.let {
                    stringResource(R.string.theme_delete_confirm_message, it.displayName)
                } ?: "",
            onDismissRequest = { deleteTargetId = null },
        ) {
            val target = deleteTarget ?: return@SuperDialog
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { deleteTargetId = null },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.theme_delete),
                    onClick = {
                        deleteTargetId = null
                        if (themeManager == null) return@TextButton
                        // Must remember whether the deleted theme was the active one
                        // BEFORE the delete runs — after delete, activeId is cleared,
                        // so we can't infer it retroactively.
                        val wasActive = target.id == activeId
                        scope.launch {
                            val ok =
                                try {
                                    withContext(Dispatchers.IO) {
                                        themeManager.deleteTheme(target.id)
                                    }
                                } catch (_: Throwable) {
                                    false
                                }
                            if (ok) {
                                themes = themeManager.getThemes()
                                activeId = themeManager.getActiveThemeId()
                                // Deleting the active theme cleared the pointer file,
                                // but subscreencenter still has the old MRC loaded in
                                // memory. Restart it so the wallpaper reverts to the
                                // next layer (custom wallpaper / system default).
                                // Honors the same auto-restart toggle as apply/deactivate.
                                if (wasActive && whitelistManager?.isThemeAutoRestart() != false) {
                                    withContext(Dispatchers.IO) { RootUtils.restartBackScreen() }
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }

        // Rename dialog
        SuperDialog(
            show = renameTargetId != null,
            title = stringResource(R.string.theme_rename_title),
            onDismissRequest = { renameTargetId = null },
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                TextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        text = stringResource(R.string.cancel),
                        onClick = { renameTargetId = null },
                    )
                    TextButton(
                        text = stringResource(R.string.confirm),
                        onClick = {
                            val targetId = renameTargetId ?: return@TextButton
                            if (renameInput.isNotBlank()) {
                                themeManager?.renameTheme(targetId, renameInput.trim())
                                themes = themeManager?.getThemes() ?: emptyList()
                            }
                            renameTargetId = null
                        },
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
