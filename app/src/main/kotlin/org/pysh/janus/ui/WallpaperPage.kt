package org.pysh.janus.ui

import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pysh.janus.R
import org.pysh.janus.data.WallpaperManager
import org.pysh.janus.data.WhitelistManager
import org.pysh.janus.receiver.WallpaperRotationReceiver
import org.pysh.janus.util.RootUtils
import org.pysh.janus.util.WallpaperUtils
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Preview(showBackground = true)
@Composable
private fun WallpaperPagePreview() {
    MiuixTheme {
        WallpaperPage(onBack = {})
    }
}

@Composable
fun WallpaperPage(onBack: () -> Unit) {
    val isInPreview = LocalInspectionMode.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val whitelistManager = remember { if (!isInPreview) WhitelistManager(context) else null }
    val wallpaperManager = remember { if (!isInPreview) WallpaperManager(context) else null }

    var wallpapers by remember { mutableStateOf(wallpaperManager?.getWallpapers() ?: emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }

    var wallpaperKeepAlive by remember { mutableStateOf(whitelistManager?.isWallpaperKeepAlive() ?: false) }
    var wallpaperLock by remember { mutableStateOf(whitelistManager?.isWallpaperLocked() ?: false) }
    var wallpaperLoop by remember { mutableStateOf(whitelistManager?.isWallpaperLoop() ?: false) }
    var autoRotate by remember { mutableStateOf(whitelistManager?.isAutoRotateEnabled() ?: false) }
    var autoRotateInterval by remember { mutableStateOf(whitelistManager?.getAutoRotateInterval() ?: 30) }
    var showIntervalDialog by remember { mutableStateOf(false) }

    // Dialog state
    var showActionDialogFor by remember { mutableStateOf<String?>(null) }
    var showRenameDialogFor by remember { mutableStateOf<String?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var replaceTargetId by remember { mutableStateOf<String?>(null) }

    // Init loop status from actual files if available
    if (!isInPreview) {
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                val loopState = WallpaperUtils.isLoopEnabled(context)
                if (loopState != null) wallpaperLoop = loopState
            }
            // Auto resume or start rotation alarm if it was lost during app update
            val wm = WhitelistManager(context)
            if (wm.isAutoRotateEnabled()) {
                WallpaperRotationReceiver.scheduleNext(context, wm.getAutoRotateInterval().coerceAtLeast(1))
            }
        }

        androidx.compose.runtime.DisposableEffect(context) {
            val receiver =
                object : android.content.BroadcastReceiver() {
                    override fun onReceive(
                        ctxt: android.content.Context?,
                        intent: android.content.Intent?,
                    ) {
                        wallpapers = wallpaperManager?.getWallpapers() ?: emptyList()
                    }
                }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    receiver,
                    android.content.IntentFilter("org.pysh.janus.ACTION_WALLPAPER_CHANGED"),
                    android.content.Context.RECEIVER_NOT_EXPORTED,
                )
            } else {
                context.registerReceiver(receiver, android.content.IntentFilter("org.pysh.janus.ACTION_WALLPAPER_CHANGED"))
            }
            onDispose {
                context.unregisterReceiver(receiver)
            }
        }
    }

    val addPicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            isProcessing = true
            scope.launch {
                val entry = withContext(Dispatchers.IO) { wallpaperManager?.addWallpaper(uri) }
                if (entry != null) {
                    wallpapers = wallpaperManager?.getWallpapers() ?: emptyList()
                } else {
                    Toast.makeText(context, context.getString(R.string.wp_import_failed), Toast.LENGTH_SHORT).show()
                }
                isProcessing = false
            }
        }

    val lazyGridState = rememberLazyGridState()
    val reorderableState =
        rememberReorderableLazyGridState(lazyGridState) { from, to ->
            val fromId = from.key as? String ?: return@rememberReorderableLazyGridState
            val toId = to.key as? String ?: return@rememberReorderableLazyGridState
            val mutableList = wallpapers.toMutableList()
            val fromIndex = mutableList.indexOfFirst { it.id == fromId }
            val toIndex = mutableList.indexOfFirst { it.id == toId }
            if (fromIndex != -1 && toIndex != -1) {
                mutableList.add(toIndex, mutableList.removeAt(fromIndex))
                wallpapers = mutableList
            }
        }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.wp_gallery_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.back),
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
                    .padding(top = paddingValues.calculateTopPadding())
                    .padding(horizontal = 4.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            // "Add Wallpaper" Button (Span 2 to take full row instead of a huge card)
            item(key = "add_btn", span = { GridItemSpan(2) }) {
                TextButton(
                    text = "+ " + stringResource(R.string.wp_add),
                    onClick = {
                        if (!isProcessing) {
                            addPicker.launch(arrayOf("video/*"))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }

            // Wallpapers list
            items(wallpapers, key = { it.id }) { wp ->
                ReorderableItem(reorderableState, key = wp.id) { isDragging ->
                    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "drag_elev")

                    val cardModifier =
                        Modifier
                            .padding(8.dp)
                            .aspectRatio(0.8f)
                            .shadow(elevation, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .background(MiuixTheme.colorScheme.surface)
                            .let {
                                if (wp.isApplied) {
                                    it.border(2.dp, MiuixTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                                } else {
                                    it
                                }
                            }.clickable(enabled = !isProcessing) {
                                showActionDialogFor = wp.id
                            }

                    Box(modifier = cardModifier) {
                        // Thumbnail
                        val bmp =
                            remember(wp.thumbnailPath) {
                                try {
                                    BitmapFactory.decodeFile(wp.thumbnailPath)?.asImageBitmap()
                                } catch (_: Exception) {
                                    null
                                }
                            }

                        if (bmp != null) {
                            Image(
                                bitmap = bmp,
                                contentDescription = wp.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surfaceContainerHigh))
                        }

                        // Gradient overlay for better text readability
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .align(Alignment.BottomCenter)
                                    .background(Color(0x66000000)),
                        )

                        // Name label
                        Text(
                            text = wp.name,
                            color = Color.White,
                            style = MiuixTheme.textStyles.body2,
                            maxLines = 1,
                            modifier =
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(8.dp),
                        )

                        // Drag Handle (Top Right)
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0x66000000))
                                    .longPressDraggableHandle(
                                        onDragStopped = {
                                            val reordered = wallpapers.map { it.id }
                                            scope.launch(Dispatchers.IO) {
                                                wallpaperManager?.reorderWallpapers(reordered)
                                            }
                                        },
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = MiuixIcons.More,
                                contentDescription = "Drag to reorder",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }

                        // Applied indicator
                        if (wp.isApplied) {
                            Box(
                                modifier =
                                    Modifier
                                        .align(Alignment.TopStart)
                                        .padding(8.dp)
                                        .background(MiuixTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.wp_applied),
                                    color = MiuixTheme.colorScheme.onPrimary,
                                    style = MiuixTheme.textStyles.body2,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            }

            // Settings section
            item(key = "settings_title", span = { GridItemSpan(2) }) {
                SmallTitle(
                    text = stringResource(R.string.wp_settings),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }

            item(key = "settings_card_1", span = { GridItemSpan(2) }) {
                Card(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    SuperSwitch(
                        title = stringResource(R.string.wallpaper_keep_alive),
                        summary =
                            stringResource(
                                if (wallpaperKeepAlive) R.string.wallpaper_keep_alive_on else R.string.wallpaper_keep_alive_off,
                            ),
                        checked = wallpaperKeepAlive,
                        onCheckedChange = {
                            wallpaperKeepAlive = it
                            whitelistManager?.setWallpaperKeepAlive(it)
                            scope.launch {
                                withContext(Dispatchers.IO) { RootUtils.restartBackScreen() }
                            }
                            Toast
                                .makeText(
                                    context,
                                    context.getString(if (it) R.string.enabled else R.string.disabled),
                                    Toast.LENGTH_SHORT,
                                ).show()
                        },
                    )
                    SuperSwitch(
                        title = stringResource(R.string.wallpaper_lock),
                        summary = stringResource(if (wallpaperLock) R.string.wallpaper_lock_on else R.string.wallpaper_lock_off),
                        checked = wallpaperLock,
                        onCheckedChange = {
                            wallpaperLock = it
                            whitelistManager?.setWallpaperLocked(it)
                            Toast
                                .makeText(
                                    context,
                                    context.getString(if (it) R.string.enabled else R.string.disabled),
                                    Toast.LENGTH_SHORT,
                                ).show()
                        },
                    )
                    SuperSwitch(
                        title = stringResource(R.string.wp_loop),
                        summary = stringResource(if (wallpaperLoop) R.string.wp_loop_on else R.string.wp_loop_off),
                        checked = wallpaperLoop,
                        onCheckedChange = { loop ->
                            wallpaperLoop = loop
                            whitelistManager?.setWallpaperLoop(loop)
                            val appliedWp = wallpapers.find { it.isApplied }
                            if (appliedWp != null) {
                                isProcessing = true
                                scope.launch {
                                    val success =
                                        withContext(Dispatchers.IO) {
                                            WallpaperUtils.setLoop(context, loop)
                                        }
                                    isProcessing = false
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(if (success) R.string.wp_loop_applied else R.string.set_failed),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                }
                            }
                        },
                        enabled = wallpapers.any { it.isApplied } && !isProcessing,
                    )

                    SuperSwitch(
                        title = stringResource(R.string.wp_auto_rotate),
                        summary = stringResource(R.string.wp_auto_rotate_desc),
                        checked = autoRotate,
                        onCheckedChange = { checked ->
                            autoRotate = checked
                            whitelistManager?.setAutoRotateEnabled(checked)
                            if (checked) {
                                WallpaperRotationReceiver.scheduleNext(context, autoRotateInterval)
                                Toast.makeText(context, context.getString(R.string.enabled), Toast.LENGTH_SHORT).show()
                            } else {
                                WallpaperRotationReceiver.cancelAll(context)
                                Toast.makeText(context, context.getString(R.string.disabled), Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = wallpapers.size > 1,
                    )

                    if (autoRotate) {
                        SuperArrow(
                            title = stringResource(R.string.wp_auto_rotate_interval),
                            summary = stringResource(R.string.wp_auto_rotate_interval_summary, autoRotateInterval),
                            onClick = { showIntervalDialog = true },
                        )
                    }
                }
            }

            item(key = "settings_card_2", span = { GridItemSpan(2) }) {
                Card(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        TextButton(
                            text = stringResource(R.string.wp_restore),
                            onClick = {
                                isProcessing = true
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        WallpaperUtils.disableCustomWallpaper()
                                        RootUtils.restartBackScreen()
                                    }
                                    // Clear applied states
                                    wallpaperManager?.getWallpapers()?.forEach { _ ->
                                        wallpaperManager.markApplied("") // dummy ID clears all
                                    }
                                    wallpapers = wallpaperManager?.getWallpapers() ?: emptyList()
                                    isProcessing = false
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(R.string.wp_restore_success),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isProcessing,
                        )
                    }
                }
            }
        } // End of LazyVerticalGrid

        // Action Dialog targeting a wallpaper (Apply, Rename, Replace)
        val actionTargetId = showActionDialogFor
        val actionTarget = wallpapers.find { it.id == actionTargetId }

        SuperDialog(
            show = actionTargetId != null,
            title = actionTarget?.name ?: "",
            onDismissRequest = { showActionDialogFor = null },
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                TextButton(
                    text = stringResource(R.string.wp_apply),
                    onClick = {
                        val targetId = showActionDialogFor ?: return@TextButton
                        showActionDialogFor = null
                        isProcessing = true
                        scope.launch {
                            val uri = Uri.fromFile(java.io.File(actionTarget?.videoPath ?: ""))
                            val success =
                                withContext(Dispatchers.IO) {
                                    WallpaperUtils.replaceVideo(context, uri, wallpaperLoop, actionTarget?.id) ||
                                        WallpaperUtils.createAiWallpaper(context, uri, wallpaperLoop, actionTarget?.id)
                                }
                            isProcessing = false
                            if (success) {
                                wallpaperManager?.markApplied(targetId)
                                wallpapers = wallpaperManager?.getWallpapers() ?: emptyList()
                                Toast.makeText(context, context.getString(R.string.wp_apply_success), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, context.getString(R.string.set_failed), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )

                TextButton(
                    text = stringResource(R.string.wp_rename),
                    onClick = {
                        val targetId = showActionDialogFor ?: return@TextButton
                        showActionDialogFor = null
                        renameInput = actionTarget?.name ?: ""
                        showRenameDialogFor = targetId
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )

                TextButton(
                    text = stringResource(R.string.wp_delete),
                    onClick = {
                        val targetId = showActionDialogFor ?: return@TextButton
                        showActionDialogFor = null
                        wallpaperManager?.removeWallpaper(targetId)
                        wallpapers = wallpaperManager?.getWallpapers() ?: emptyList()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } // End of Action Dialog

        // Rename Dialog
        val renameTargetId = showRenameDialogFor
        SuperDialog(
            show = renameTargetId != null,
            title = stringResource(R.string.wp_rename_title),
            onDismissRequest = { showRenameDialogFor = null },
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
                        onClick = { showRenameDialogFor = null },
                    )
                    TextButton(
                        text = stringResource(R.string.confirm),
                        onClick = {
                            val targetId = showRenameDialogFor ?: return@TextButton
                            if (renameInput.isNotBlank()) {
                                wallpaperManager?.renameWallpaper(targetId, renameInput.trim())
                                wallpapers = wallpaperManager?.getWallpapers() ?: emptyList()
                            }
                            showRenameDialogFor = null
                        },
                    )
                }
            }
        } // End of Rename Dialog

        SuperDialog(
            show = showIntervalDialog,
            title = stringResource(R.string.wp_auto_rotate_interval),
            summary = "",
            onDismissRequest = { showIntervalDialog = false },
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                var customIntervalText by remember(showIntervalDialog) { mutableStateOf(autoRotateInterval.toString()) }

                TextField(
                    value = customIntervalText,
                    onValueChange = { newValue -> customIntervalText = newValue.filter { it.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = "系统底层已切为强效调度，现已支持精准到秒的极速轮播。",
                    modifier = Modifier.padding(top = 8.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        text = stringResource(R.string.cancel),
                        onClick = { showIntervalDialog = false },
                    )
                    TextButton(
                        text = stringResource(R.string.confirm),
                        onClick = {
                            try {
                                val minutes = customIntervalText.toIntOrNull() ?: 1
                                val clamped = minutes.coerceAtLeast(1)
                                autoRotateInterval = clamped
                                whitelistManager?.setAutoRotateInterval(clamped)
                                if (autoRotate) {
                                    WallpaperRotationReceiver.scheduleNext(context, clamped)
                                }
                                Toast.makeText(context, "已设为每 $clamped 秒后轮换", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(context, "设置失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                showIntervalDialog = false
                            }
                        },
                    )
                }
            }
        }
    } // End of Scaffold lambda
}
