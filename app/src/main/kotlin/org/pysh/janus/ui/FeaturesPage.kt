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
import org.pysh.janus.service.ScreenKeepAliveService
import org.pysh.janus.util.DisplayUtils
import org.pysh.janus.util.RootUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import top.yukonga.miuix.kmp.extra.SuperRadioButton
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Preview(showBackground = true)
@Composable
private fun FeaturesPagePreview() {
    MiuixTheme {
        FeaturesPage(bottomPadding = 0.dp, currentDpi = 320, onDpiChanged = {}, onWallpaperClick = {})
    }
}

@Composable
fun FeaturesPage(
    bottomPadding: Dp,
    currentDpi: Int?,
    onDpiChanged: (Int?) -> Unit,
    onWallpaperClick: () -> Unit,
) {
    val isInPreview = LocalInspectionMode.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val whitelistManager = remember { if (!isInPreview) WhitelistManager(context) else null }
    var keepAlive by remember { mutableStateOf(if (!isInPreview) (whitelistManager?.isKeepAliveEnabled() ?: false) else false) }
    var intervalValue by remember { mutableFloatStateOf(whitelistManager?.getKeepAliveInterval()?.toFloat() ?: 10f) }
    var disableTracking by remember { mutableStateOf(whitelistManager?.isTrackingDisabled() ?: false) }

    var weatherCard by remember { mutableStateOf(whitelistManager?.isWeatherCardEnabled() ?: false) }

    var dpiSliderValue by remember { mutableFloatStateOf(currentDpi?.toFloat() ?: 320f) }
    var castRotation by remember { mutableStateOf(whitelistManager?.getCastRotation() ?: 0) }
    var castKeepAlive by remember { mutableStateOf(whitelistManager?.isCastKeepAlive() ?: false) }

    var showIntervalDialog by remember { mutableStateOf(false) }
    var showDpiDialog by remember { mutableStateOf(false) }
    var showRotationDialog by remember { mutableStateOf(false) }
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
                }
            }

            item {
                Card(modifier = Modifier.padding(bottom = 12.dp)) {
                    SuperSwitch(
                        title = stringResource(R.string.weather_card),
                        summary = stringResource(if (weatherCard) R.string.weather_card_on else R.string.weather_card_off),
                        checked = weatherCard,
                        onCheckedChange = {
                            weatherCard = it
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    whitelistManager?.setWeatherCardEnabled(it)
                                    RootUtils.restartBackScreen()
                                }
                                Toast.makeText(context, context.getString(if (it) R.string.enabled else R.string.disabled), Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
            }

            item {
                Card(modifier = Modifier.padding(bottom = 12.dp)) {
                    SuperArrow(
                        title = stringResource(R.string.section_wallpaper),
                        onClick = onWallpaperClick,
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
                                ScreenKeepAliveService.start(context, intervalValue.toInt())
                            } else {
                                ScreenKeepAliveService.stop(context)
                            }
                            Toast.makeText(context, context.getString(if (it) R.string.enabled else R.string.disabled), Toast.LENGTH_SHORT).show()
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
                                        ScreenKeepAliveService.start(context, intervalValue.toInt())
                                    }
                                },
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
                            text = stringResource(R.string.reset_default),
                            onClick = {
                                intervalValue = ScreenKeepAliveService.DEFAULT_INTERVAL.toFloat()
                                whitelistManager?.setKeepAliveInterval(ScreenKeepAliveService.DEFAULT_INTERVAL)
                                if (keepAlive) {
                                    ScreenKeepAliveService.start(context, ScreenKeepAliveService.DEFAULT_INTERVAL)
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
                        title = stringResource(R.string.rear_dpi),
                        summary = when (currentDpi) {
                            null -> stringResource(R.string.loading)
                            else -> stringResource(R.string.rear_dpi_current, currentDpi)
                        },
                        onClick = {
                            dialogInput = (currentDpi ?: 320).toString()
                            showDpiDialog = true
                        },
                        bottomAction = {
                            Slider(
                                value = dpiSliderValue,
                                onValueChange = { dpiSliderValue = it },
                                valueRange = 100f..800f,
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
                            text = stringResource(R.string.set_dpi),
                            onClick = {
                                val dpi = dpiSliderValue.toInt()
                                scope.launch {
                                    val (success, newDpi) = withContext(Dispatchers.IO) {
                                        val ok = DisplayUtils.setRearDpi(dpi)
                                        if (ok) RootUtils.restartBackScreen()
                                        ok to DisplayUtils.getRearDpi()
                                    }
                                    onDpiChanged(newDpi)
                                    if (newDpi != null) dpiSliderValue = newDpi.toFloat()
                                    Toast.makeText(
                                        context,
                                        if (success) context.getString(R.string.dpi_set_success, dpi) else context.getString(R.string.set_failed),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                        TextButton(
                            text = stringResource(R.string.reset),
                            onClick = {
                                scope.launch {
                                    val (success, newDpi) = withContext(Dispatchers.IO) {
                                        val ok = DisplayUtils.resetRearDpi()
                                        if (ok) RootUtils.restartBackScreen()
                                        ok to DisplayUtils.getRearDpi()
                                    }
                                    onDpiChanged(newDpi)
                                    if (newDpi != null) dpiSliderValue = newDpi.toFloat()
                                    Toast.makeText(
                                        context,
                                        if (success) context.getString(R.string.dpi_reset_success) else context.getString(R.string.reset_failed),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // 投屏设置
            item {
                Card(modifier = Modifier.padding(bottom = 12.dp)) {
                    SuperArrow(
                        title = stringResource(R.string.cast_rotation),
                        summary = stringResource(
                            when (castRotation) {
                                1 -> R.string.cast_rotation_left
                                3 -> R.string.cast_rotation_right
                                else -> R.string.cast_rotation_none
                            }
                        ),
                        onClick = { showRotationDialog = true },
                    )
                    SuperSwitch(
                        title = stringResource(R.string.cast_keep_alive),
                        summary = stringResource(if (castKeepAlive) R.string.cast_keep_alive_on else R.string.cast_keep_alive_off),
                        checked = castKeepAlive,
                        onCheckedChange = {
                            castKeepAlive = it
                            whitelistManager?.setCastKeepAlive(it)
                            Toast.makeText(context, context.getString(if (it) R.string.enabled else R.string.disabled), Toast.LENGTH_SHORT).show()
                        },
                    )
                }
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
                            ScreenKeepAliveService.start(context, seconds)
                        }
                    }
                    showIntervalDialog = false
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }

    SuperDialog(
        show = showDpiDialog,
        title = stringResource(R.string.rear_dpi),
        summary = stringResource(R.string.dpi_dialog_summary),
        onDismissRequest = { showDpiDialog = false },
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
                onClick = { showDpiDialog = false },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = stringResource(R.string.confirm),
                onClick = {
                    val dpi = dialogInput.toIntOrNull()?.coerceIn(100, 800)
                    if (dpi != null) {
                        dpiSliderValue = dpi.toFloat()
                    }
                    showDpiDialog = false
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }

    // 投屏旋转选择弹窗
    SuperDialog(
        show = showRotationDialog,
        title = stringResource(R.string.cast_rotation),
        onDismissRequest = { showRotationDialog = false },
    ) {
        Card(modifier = Modifier.padding(bottom = 8.dp)) {
            SuperRadioButton(
                title = stringResource(R.string.cast_rotation_none),
                selected = castRotation == 0,
                onClick = {
                    castRotation = 0
                    whitelistManager?.setCastRotation(0)
                    showRotationDialog = false
                },
            )
        }
        Card(modifier = Modifier.padding(bottom = 8.dp)) {
            SuperRadioButton(
                title = stringResource(R.string.cast_rotation_left),
                selected = castRotation == 1,
                onClick = {
                    castRotation = 1
                    whitelistManager?.setCastRotation(1)
                    showRotationDialog = false
                },
            )
        }
        Card {
            SuperRadioButton(
                title = stringResource(R.string.cast_rotation_right),
                selected = castRotation == 3,
                onClick = {
                    castRotation = 3
                    whitelistManager?.setCastRotation(3)
                    showRotationDialog = false
                },
            )
        }
    }
}
