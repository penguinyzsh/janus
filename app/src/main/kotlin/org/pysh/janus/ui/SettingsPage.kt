package org.pysh.janus.ui

import android.content.ComponentName
import android.content.pm.PackageManager
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Preview(showBackground = true)
@Composable
private fun SettingsPagePreview() {
    MiuixTheme {
        SettingsPage(bottomPadding = 0.dp, onAboutClick = {}, onOtherClick = {})
    }
}

@Composable
fun SettingsPage(
    bottomPadding: Dp,
    onAboutClick: () -> Unit,
    onOtherClick: () -> Unit,
) {
    val isInPreview = LocalInspectionMode.current
    val context = LocalContext.current
    val pm = remember { if (!isInPreview) context.packageManager else null }
    val aliasComponent =
        remember {
            if (!isInPreview) ComponentName(context, "${context.packageName}.MainActivityAlias") else null
        }

    var iconHidden by remember {
        mutableStateOf(
            if (!isInPreview && pm != null && aliasComponent != null) {
                pm.getComponentEnabledSetting(aliasComponent) ==
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            } else {
                false
            },
        )
    }
    var showHideDialog by remember { mutableStateOf(false) }

    val scrollBehavior = MiuixScrollBehavior()
    val title = stringResource(R.string.nav_settings)

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
                Card(
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                ) {
                    SuperSwitch(
                        title = stringResource(R.string.hide_icon),
                        summary =
                            stringResource(
                                if (iconHidden) R.string.hide_icon_on else R.string.hide_icon_off,
                            ),
                        checked = iconHidden,
                        onCheckedChange = {
                            if (it) {
                                showHideDialog = true
                            } else {
                                pm?.setComponentEnabledSetting(
                                    aliasComponent!!,
                                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                                    PackageManager.DONT_KILL_APP,
                                )
                                iconHidden = false
                            }
                        },
                    )
                }
            }
            item {
                Card(
                    modifier = Modifier.padding(bottom = 6.dp),
                ) {
                    SuperArrow(
                        title = stringResource(R.string.other),
                        summary = stringResource(R.string.other_summary),
                        onClick = onOtherClick,
                    )
                }
            }
            item {
                Card(
                    modifier = Modifier.padding(bottom = 6.dp),
                ) {
                    SuperArrow(
                        title = stringResource(R.string.about),
                        summary = stringResource(R.string.about_janus),
                        onClick = onAboutClick,
                    )
                }
            }
        }
    }

    SuperDialog(
        show = showHideDialog,
        title = stringResource(R.string.hide_icon_dialog_title),
        summary = stringResource(R.string.hide_icon_dialog_summary),
        onDismissRequest = { showHideDialog = false },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                text = stringResource(R.string.cancel),
                onClick = { showHideDialog = false },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = stringResource(R.string.confirm),
                onClick = {
                    pm?.setComponentEnabledSetting(
                        aliasComponent!!,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                    iconHidden = true
                    showHideDialog = false
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}
