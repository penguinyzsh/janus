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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pysh.janus.R
import org.pysh.janus.data.WhitelistManager
import org.pysh.janus.core.util.RootUtils
import org.pysh.janus.service.JanusBackgroundService
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Preview(showBackground = true)
@Composable
private fun FeaturesPagePreview() {
    MiuixTheme {
        FeaturesPage(
            bottomPadding = 0.dp,
            onWallpaperClick = {},
            onCastingClick = {},
            onThemesClick = {},
            onCardsClick = {},
        )
    }
}

@Composable
fun FeaturesPage(
    bottomPadding: Dp,
    onWallpaperClick: () -> Unit,
    onCastingClick: () -> Unit,
    onThemesClick: () -> Unit,
    onCardsClick: () -> Unit,
) {
    val isInPreview = LocalInspectionMode.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val whitelistManager = remember { if (!isInPreview) WhitelistManager(context) else null }
    var disableTracking by remember { mutableStateOf(whitelistManager?.isTrackingDisabled() ?: false) }
    var hideTimeTip by remember { mutableStateOf(whitelistManager?.isTimeTipHidden() ?: false) }
    var focusNoticeAllow by remember { mutableStateOf(whitelistManager?.isFocusNoticeAllowEnabled() ?: false) }
    var keepAlive by remember { mutableStateOf(whitelistManager?.isKeepAliveEnabled() ?: false) }
    var intervalValue by remember { mutableFloatStateOf(whitelistManager?.getKeepAliveInterval()?.toFloat() ?: 10f) }
    var showIntervalDialog by remember { mutableStateOf(false) }
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
            modifier =
                Modifier
                    .fillMaxHeight()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
            contentPadding =
                PaddingValues(
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
                            Toast
                                .makeText(
                                    context,
                                    context.getString(if (it) R.string.enabled else R.string.disabled),
                                    Toast.LENGTH_SHORT,
                                ).show()
                        },
                    )
                    SuperSwitch(
                        title = stringResource(R.string.focus_notice_allow),
                        summary = stringResource(if (focusNoticeAllow) R.string.focus_notice_allow_on else R.string.focus_notice_allow_off),
                        checked = focusNoticeAllow,
                        onCheckedChange = {
                            focusNoticeAllow = it
                            whitelistManager?.setFocusNoticeAllowEnabled(it)
                            Toast
                                .makeText(
                                    context,
                                    context.getString(if (it) R.string.enabled else R.string.disabled),
                                    Toast.LENGTH_SHORT,
                                ).show()
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
                                Toast
                                    .makeText(
                                        context,
                                        context.getString(if (it) R.string.enabled else R.string.disabled),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                            }
                        },
                    )
                }
            }

            item {
                Card(modifier = Modifier.padding(bottom = 12.dp)) {
                    SuperSwitch(
                        title = stringResource(R.string.keep_alive),
                        summary = stringResource(if (keepAlive) R.string.keep_alive_on else R.string.keep_alive_off),
                        checked = keepAlive,
                        onCheckedChange = {
                            keepAlive = it
                            whitelistManager?.setKeepAliveEnabled(it)
                            if (it) {
                                JanusBackgroundService.startKeepAlive(context, intervalValue.toInt())
                            } else {
                                JanusBackgroundService.stopKeepAlive(context)
                            }
                            Toast
                                .makeText(
                                    context,
                                    context.getString(if (it) R.string.enabled else R.string.disabled),
                                    Toast.LENGTH_SHORT,
                                ).show()
                        },
                    )
                    SuperArrow(
                        title = stringResource(R.string.keep_alive_interval),
                        summary = stringResource(R.string.keep_alive_interval_value, intervalValue.toInt()),
                        onClick = {
                            dialogInput = intervalValue.toInt().toString()
                            showIntervalDialog = true
                        },
                        bottomAction = {
                            Slider(
                                value = intervalValue,
                                onValueChange = { intervalValue = it },
                                valueRange = 1f..300f,
                                onValueChangeFinished = {
                                    whitelistManager?.setKeepAliveInterval(intervalValue.toInt())
                                    if (keepAlive) {
                                        JanusBackgroundService.startKeepAlive(context, intervalValue.toInt())
                                    }
                                },
                            )
                        },
                    )
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(
                            text = stringResource(R.string.reset_default),
                            onClick = {
                                intervalValue = JanusBackgroundService.DEFAULT_KEEP_ALIVE_INTERVAL.toFloat()
                                whitelistManager?.setKeepAliveInterval(JanusBackgroundService.DEFAULT_KEEP_ALIVE_INTERVAL)
                                if (keepAlive) {
                                    JanusBackgroundService.startKeepAlive(context, JanusBackgroundService.DEFAULT_KEEP_ALIVE_INTERVAL)
                                }
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
                        title = stringResource(R.string.themes_title),
                        summary = stringResource(R.string.features_themes_summary),
                        onClick = onThemesClick,
                    )
                    SuperArrow(
                        title = stringResource(R.string.nav_cards),
                        summary = stringResource(R.string.features_cards_summary),
                        onClick = onCardsClick,
                    )
                    SuperArrow(
                        title = stringResource(R.string.section_wallpaper),
                        summary = stringResource(R.string.features_wallpaper_summary),
                        onClick = onWallpaperClick,
                    )
                    SuperArrow(
                        title = stringResource(R.string.section_casting),
                        summary = stringResource(R.string.features_casting_summary),
                        onClick = onCastingClick,
                    )
                }
            }
        }

        SuperDialog(
            show = showIntervalDialog,
            title = stringResource(R.string.keep_alive_interval),
            summary = stringResource(R.string.keep_alive_interval_dialog_summary),
            onDismissRequest = { showIntervalDialog = false },
        ) {
            TextField(
                value = dialogInput,
                onValueChange = { dialogInput = it.filter { c -> c.isDigit() } },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showIntervalDialog = false },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.confirm),
                    onClick = {
                        val seconds = dialogInput.toIntOrNull()?.coerceIn(1, 300)
                        if (seconds != null) {
                            intervalValue = seconds.toFloat()
                            whitelistManager?.setKeepAliveInterval(seconds)
                            if (keepAlive) {
                                JanusBackgroundService.startKeepAlive(context, seconds)
                            }
                        }
                        showIntervalDialog = false
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}
