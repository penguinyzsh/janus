package org.pysh.janus.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.pysh.janus.R
import org.pysh.janus.data.WhitelistManager
import org.pysh.janus.util.RootUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

private const val LYRIC_FADE_MIN = 100
private const val LYRIC_FADE_MAX = 2000
private const val LYRIC_THRESHOLD_MIN_SEC = 1
private const val LYRIC_THRESHOLD_MAX_SEC = 60

@Preview(showBackground = true)
@Composable
private fun FeaturesPagePreview() {
    MiuixTheme {
        FeaturesPage(bottomPadding = 0.dp, onWallpaperClick = {}, onCastingClick = {})
    }
}

@Composable
fun FeaturesPage(
    bottomPadding: Dp,
    onWallpaperClick: () -> Unit,
    onCastingClick: () -> Unit,
) {
    val isInPreview = LocalInspectionMode.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val whitelistManager = remember { if (!isInPreview) WhitelistManager(context) else null }
    var disableTracking by remember { mutableStateOf(whitelistManager?.isTrackingDisabled() ?: false) }
    var hideTimeTip by remember { mutableStateOf(whitelistManager?.isTimeTipHidden() ?: false) }

    var lyricFadeDuration by remember { mutableFloatStateOf(whitelistManager?.getLyricFadeDuration()?.toFloat() ?: 700f) }
    var lyricThreshold by remember { mutableFloatStateOf((whitelistManager?.getLyricModeThreshold()?.toFloat() ?: 15000f) / 1000f) }

    var showLyricFadeDialog by remember { mutableStateOf(false) }
    var showLyricThresholdDialog by remember { mutableStateOf(false) }
    var dialogInput by remember { mutableStateOf("") }

    val scrollBehavior = MiuixScrollBehavior()
    val title = stringResource(R.string.nav_features)

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
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = bottomPadding,
            ),
            overscrollEffect = null,
        ) {
            item {
                Card(modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)) {
                    SuperSwitch(
                        title = stringResource(R.string.disable_tracking),
                        summary = stringResource(if (disableTracking) R.string.disable_tracking_on else R.string.disable_tracking_off),
                        checked = disableTracking,
                        onCheckedChange = {
                            disableTracking = it
                            whitelistManager?.setTrackingDisabled(it)
                            Toast.makeText(context, context.getString(if (it) R.string.enabled else R.string.disabled), Toast.LENGTH_SHORT).show()
                        },
                    )
                    SuperSwitch(
                        title = stringResource(R.string.hide_time_tip),
                        summary = stringResource(if (hideTimeTip) R.string.hide_time_tip_on else R.string.hide_time_tip_off),
                        checked = hideTimeTip,
                        onCheckedChange = {
                            hideTimeTip = it
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    whitelistManager?.setTimeTipHidden(it)
                                    RootUtils.restartBackScreen()
                                }
                                Toast.makeText(context, context.getString(if (it) R.string.enabled else R.string.disabled), Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
            }

            item { SmallTitle(text = stringResource(R.string.section_lyric)) }
            item {
                Card(modifier = Modifier.padding(bottom = 12.dp)) {
                    SuperArrow(
                        title = stringResource(R.string.lyric_fade_duration),
                        summary = stringResource(R.string.lyric_fade_duration_value, lyricFadeDuration.toInt()),
                        onClick = {
                            dialogInput = lyricFadeDuration.toInt().toString()
                            showLyricFadeDialog = true
                        },
                        bottomAction = {
                            Slider(
                                value = lyricFadeDuration,
                                onValueChange = { lyricFadeDuration = it },
                                valueRange = LYRIC_FADE_MIN.toFloat()..LYRIC_FADE_MAX.toFloat(),
                            )
                        },
                    )
                    SuperArrow(
                        title = stringResource(R.string.lyric_mode_threshold),
                        summary = stringResource(R.string.lyric_mode_threshold_value, lyricThreshold.toInt()),
                        onClick = {
                            dialogInput = lyricThreshold.toInt().toString()
                            showLyricThresholdDialog = true
                        },
                        bottomAction = {
                            Slider(
                                value = lyricThreshold,
                                onValueChange = { lyricThreshold = it },
                                valueRange = LYRIC_THRESHOLD_MIN_SEC.toFloat()..LYRIC_THRESHOLD_MAX_SEC.toFloat(),
                            )
                        },
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(
                            text = stringResource(R.string.lyric_save),
                            onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        whitelistManager?.setLyricFadeDuration(lyricFadeDuration.toInt())
                                        whitelistManager?.setLyricModeThreshold((lyricThreshold * 1000).toInt())
                                    }
                                    Toast.makeText(context, context.getString(R.string.enabled), Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                        TextButton(
                            text = stringResource(R.string.reset_default),
                            onClick = {
                                lyricFadeDuration = 700f
                                lyricThreshold = 15f
                                Toast.makeText(context, context.getString(R.string.reset_done), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.padding(bottom = 12.dp)) {
                    SuperArrow(
                        title = stringResource(R.string.section_wallpaper),
                        onClick = onWallpaperClick,
                    )
                    SuperArrow(
                        title = stringResource(R.string.section_casting),
                        onClick = onCastingClick,
                    )
                }
            }
        }
    }

    // 歌词淡入时长输入弹窗
    SuperDialog(
        show = showLyricFadeDialog,
        title = stringResource(R.string.lyric_fade_duration),
        summary = stringResource(R.string.lyric_fade_duration_summary, LYRIC_FADE_MIN, LYRIC_FADE_MAX),
        onDismissRequest = { showLyricFadeDialog = false },
    ) {
        TextField(
            value = dialogInput,
            onValueChange = { dialogInput = it.filter { c -> c.isDigit() } },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                text = stringResource(R.string.cancel),
                onClick = { showLyricFadeDialog = false },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = stringResource(R.string.confirm),
                onClick = {
                    val ms = dialogInput.toIntOrNull()?.coerceIn(LYRIC_FADE_MIN, LYRIC_FADE_MAX)
                    if (ms != null) lyricFadeDuration = ms.toFloat()
                    showLyricFadeDialog = false
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }

    // 歌词检测阈值输入弹窗
    SuperDialog(
        show = showLyricThresholdDialog,
        title = stringResource(R.string.lyric_mode_threshold),
        summary = stringResource(R.string.lyric_mode_threshold_summary, LYRIC_THRESHOLD_MIN_SEC, LYRIC_THRESHOLD_MAX_SEC),
        onDismissRequest = { showLyricThresholdDialog = false },
    ) {
        TextField(
            value = dialogInput,
            onValueChange = { dialogInput = it.filter { c -> c.isDigit() } },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                text = stringResource(R.string.cancel),
                onClick = { showLyricThresholdDialog = false },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = stringResource(R.string.confirm),
                onClick = {
                    val sec = dialogInput.toIntOrNull()?.coerceIn(LYRIC_THRESHOLD_MIN_SEC, LYRIC_THRESHOLD_MAX_SEC)
                    if (sec != null) lyricThreshold = sec.toFloat()
                    showLyricThresholdDialog = false
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }

}
