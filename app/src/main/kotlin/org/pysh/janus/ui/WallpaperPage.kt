package org.pysh.janus.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.pysh.janus.R
import org.pysh.janus.data.WhitelistManager
import org.pysh.janus.util.RootUtils
import org.pysh.janus.util.WallpaperUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
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

    var wallpaperKeepAlive by remember { mutableStateOf(whitelistManager?.isWallpaperKeepAlive() ?: false) }
    var wallpaperLock by remember { mutableStateOf(whitelistManager?.isWallpaperLocked() ?: false) }
    var wallpaperLoop by remember { mutableStateOf(whitelistManager?.isWallpaperLoop() ?: false) }
    var hasWallpaper by remember { mutableStateOf(false) }
    var isWpProcessing by remember { mutableStateOf(false) }

    if (!isInPreview) {
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                val wp = WallpaperUtils.detectWallpaper()
                hasWallpaper = wp != null
                if (wp != null) {
                    val loopState = WallpaperUtils.isLoopEnabled(context)
                    if (loopState != null) wallpaperLoop = loopState
                }
            }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        isWpProcessing = true
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                WallpaperUtils.replaceVideo(context, uri, wallpaperLoop)
                    || WallpaperUtils.createAiWallpaper(context, uri, wallpaperLoop)
            }
            isWpProcessing = false
            if (success && !hasWallpaper) hasWallpaper = true
            Toast.makeText(
                context,
                context.getString(if (success) R.string.wp_replace_success else R.string.wp_replace_failed),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.section_wallpaper),
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
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .padding(horizontal = 12.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Card(modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)) {
                SuperSwitch(
                    title = stringResource(R.string.wallpaper_keep_alive),
                    summary = stringResource(if (wallpaperKeepAlive) R.string.wallpaper_keep_alive_on else R.string.wallpaper_keep_alive_off),
                    checked = wallpaperKeepAlive,
                    onCheckedChange = {
                        wallpaperKeepAlive = it
                        whitelistManager?.setWallpaperKeepAlive(it)
                        scope.launch {
                            withContext(Dispatchers.IO) { RootUtils.restartBackScreen() }
                        }
                        Toast.makeText(context, context.getString(if (it) R.string.enabled else R.string.disabled), Toast.LENGTH_SHORT).show()
                    },
                )
                SuperSwitch(
                    title = stringResource(R.string.wallpaper_lock),
                    summary = stringResource(if (wallpaperLock) R.string.wallpaper_lock_on else R.string.wallpaper_lock_off),
                    checked = wallpaperLock,
                    onCheckedChange = {
                        wallpaperLock = it
                        whitelistManager?.setWallpaperLocked(it)
                        Toast.makeText(context, context.getString(if (it) R.string.enabled else R.string.disabled), Toast.LENGTH_SHORT).show()
                    },
                )
            }

            Card(modifier = Modifier.padding(bottom = 12.dp)) {
                SuperArrow(
                    title = stringResource(R.string.wp_custom_title),
                    summary = stringResource(
                        when {
                            isWpProcessing -> R.string.wp_processing
                            !hasWallpaper -> R.string.wp_create_hint
                            else -> R.string.wp_ready
                        }
                    ),
                    onClick = {
                        if (isWpProcessing) return@SuperArrow
                        videoPicker.launch(arrayOf("video/*"))
                    },
                    enabled = !isWpProcessing,
                )
                SuperSwitch(
                    title = stringResource(R.string.wp_loop),
                    summary = stringResource(if (wallpaperLoop) R.string.wp_loop_on else R.string.wp_loop_off),
                    checked = wallpaperLoop,
                    onCheckedChange = {
                        wallpaperLoop = it
                        whitelistManager?.setWallpaperLoop(it)
                        if (hasWallpaper) {
                            isWpProcessing = true
                            scope.launch {
                                val success = withContext(Dispatchers.IO) {
                                    WallpaperUtils.setLoop(context, it)
                                }
                                isWpProcessing = false
                                Toast.makeText(
                                    context,
                                    context.getString(if (success) R.string.wp_loop_applied else R.string.set_failed),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                    enabled = hasWallpaper && !isWpProcessing,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                ) {
                    TextButton(
                        text = stringResource(R.string.wp_restore),
                        onClick = {
                            isWpProcessing = true
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    WallpaperUtils.disableCustomWallpaper()
                                    RootUtils.restartBackScreen()
                                }
                                isWpProcessing = false
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.wp_restore_success),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isWpProcessing,
                    )
                }
            }
        }
    }
}
