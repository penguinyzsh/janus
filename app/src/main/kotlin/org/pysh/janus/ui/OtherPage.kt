package org.pysh.janus.ui

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pysh.janus.R
import org.pysh.janus.data.CardManager
import org.pysh.janus.data.JanusCleanup
import org.pysh.janus.data.ThemeManager
import org.pysh.janus.data.WhitelistManager
import org.pysh.janus.core.util.RootUtils
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Preview(showBackground = true)
@Composable
private fun OtherPagePreview() {
    MiuixTheme {
        OtherPage(onBack = {})
    }
}

@Composable
fun OtherPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showCleanupDialog by remember { mutableStateOf(false) }
    var showClearThemesDialog by remember { mutableStateOf(false) }
    var showHideIconDialog by remember { mutableStateOf(false) }
    var showBackgroundPermDialog by remember { mutableStateOf(false) }

    // Background permission state — recomputed each time the user opens the
    // dialog via [permRefreshKey], so returning from system settings shows
    // updated grant status.
    var permRefreshKey by remember { mutableIntStateOf(0) }
    val powerManager = remember { context.getSystemService(PowerManager::class.java) }
    val notifGranted =
        remember(permRefreshKey) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        }
    val batteryExempt =
        remember(permRefreshKey) {
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        }
    val isMiui =
        remember {
            // Detect MIUI/HyperOS by presence of Security Center
            try {
                context.packageManager.getPackageInfo("com.miui.securitycenter", 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
    // Re-evaluate status whenever this page resumes (user may have toggled
    // something in system settings and come back).
    @Suppress("DEPRECATION")
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) permRefreshKey++
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val cleanupSuccessMsg = stringResource(R.string.cleanup_success)
    val cleanupFailedMsg = stringResource(R.string.cleanup_failed)
    val themeClearDoneMsg = stringResource(R.string.theme_clear_root_done)

    val whitelistManager = remember { WhitelistManager(context) }
    var themeAutoRestart by remember { mutableStateOf(whitelistManager.isThemeAutoRestart()) }

    val pm = remember { context.packageManager }
    val aliasComponent =
        remember { ComponentName(context, "${context.packageName}.MainActivityAlias") }
    var iconHidden by remember {
        mutableStateOf(
            pm.getComponentEnabledSetting(aliasComponent) ==
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        )
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.other),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = MiuixIcons.Back, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Card(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
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
                        showHideIconDialog = true
                    } else {
                        pm.setComponentEnabledSetting(
                            aliasComponent,
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            PackageManager.DONT_KILL_APP,
                        )
                        iconHidden = false
                    }
                },
            )
            SuperArrow(
                title = stringResource(R.string.resync),
                summary = stringResource(R.string.resync_summary),
                onClick = {
                    val resyncMsg = context.getString(R.string.resync_done)
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            WhitelistManager(context).syncAllFlags()
                            CardManager(context).run {
                                syncConfig()
                                prepareCardsForHook()
                                prepareSystemCardOverridesForHook()
                            }
                            RootUtils.restartBackScreen()
                        }
                        Toast.makeText(context, resyncMsg, Toast.LENGTH_SHORT).show()
                    }
                },
            )
            SuperArrow(
                title = stringResource(R.string.background_perm_title),
                summary = stringResource(R.string.background_perm_summary),
                onClick = { showBackgroundPermDialog = true },
            )
            SuperSwitch(
                title = stringResource(R.string.theme_auto_restart_title),
                summary = stringResource(R.string.theme_auto_restart_summary),
                checked = themeAutoRestart,
                onCheckedChange = {
                    themeAutoRestart = it
                    whitelistManager.setThemeAutoRestart(it)
                },
            )
            SuperArrow(
                title = stringResource(R.string.theme_clear_root_title),
                summary = stringResource(R.string.theme_clear_root_summary),
                onClick = { showClearThemesDialog = true },
            )
            SuperArrow(
                title = stringResource(R.string.cleanup),
                summary = stringResource(R.string.cleanup_summary),
                onClick = { showCleanupDialog = true },
            )
        }

        SuperDialog(
            show = showClearThemesDialog,
            title = stringResource(R.string.theme_clear_root_title),
            summary = stringResource(R.string.theme_clear_root_confirm),
            onDismissRequest = { showClearThemesDialog = false },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showClearThemesDialog = false },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.confirm),
                    onClick = {
                        showClearThemesDialog = false
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                ThemeManager(context).clearAllRootData()
                                // clearAllRootData internally calls deactivate() which only
                                // clears the pointer file. subscreencenter still has the old
                                // MRC loaded in memory, so we need a full restart to make the
                                // visual change stick. Same class of bug as ThemesPage delete
                                // prior to 2026-04-06 — keep these two paths in sync.
                                RootUtils.restartBackScreen()
                            }
                            Toast.makeText(context, themeClearDoneMsg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }

        SuperDialog(
            show = showHideIconDialog,
            title = stringResource(R.string.hide_icon_dialog_title),
            summary = stringResource(R.string.hide_icon_dialog_summary),
            onDismissRequest = { showHideIconDialog = false },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showHideIconDialog = false },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.confirm),
                    onClick = {
                        pm.setComponentEnabledSetting(
                            aliasComponent,
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP,
                        )
                        iconHidden = true
                        showHideIconDialog = false
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }

        SuperDialog(
            show = showCleanupDialog,
            title = stringResource(R.string.cleanup_dialog_title),
            summary = stringResource(R.string.cleanup_dialog_summary),
            onDismissRequest = { showCleanupDialog = false },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showCleanupDialog = false },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.confirm),
                    onClick = {
                        showCleanupDialog = false
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { JanusCleanup.cleanAll() }
                            Toast
                                .makeText(
                                    context,
                                    if (ok) cleanupSuccessMsg else cleanupFailedMsg,
                                    Toast.LENGTH_SHORT,
                                ).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }

        // Background permission check dialog — one place to inspect and jump to
        // the three system settings pages that matter for FGS survival on MIUI.
        SuperDialog(
            show = showBackgroundPermDialog,
            title = stringResource(R.string.background_perm_dialog_title),
            summary = stringResource(R.string.background_perm_dialog_intro),
            onDismissRequest = { showBackgroundPermDialog = false },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 1. Notification permission (POST_NOTIFICATIONS)
                PermissionRow(
                    title = stringResource(R.string.background_perm_notification),
                    description = stringResource(R.string.background_perm_notification_desc),
                    statusText =
                        stringResource(
                            if (notifGranted) {
                                R.string.background_perm_status_granted
                            } else {
                                R.string.background_perm_status_denied
                            },
                        ),
                    statusGranted = notifGranted,
                    actionLabel = stringResource(R.string.background_perm_go),
                    showAction = !notifGranted,
                    onAction = {
                        runCatching {
                            val intent =
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            context.startActivity(intent)
                        }
                    },
                )
                Spacer(modifier = Modifier.height(12.dp))

                // 2. Battery optimization whitelist
                PermissionRow(
                    title = stringResource(R.string.background_perm_battery),
                    description = stringResource(R.string.background_perm_battery_desc),
                    statusText =
                        stringResource(
                            if (batteryExempt) {
                                R.string.background_perm_status_granted
                            } else {
                                R.string.background_perm_status_denied
                            },
                        ),
                    statusGranted = batteryExempt,
                    actionLabel = stringResource(R.string.background_perm_go),
                    showAction = !batteryExempt,
                    onAction = {
                        runCatching {
                            @Suppress("BatteryLife") // This is the entire point of this UI
                            val intent =
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                            context.startActivity(intent)
                        }
                    },
                )
                Spacer(modifier = Modifier.height(12.dp))

                // 3. MIUI autostart (can't be queried from a non-system app, so
                // we always show it as "manual action required" on MIUI and
                // "not applicable" on non-MIUI devices).
                PermissionRow(
                    title = stringResource(R.string.background_perm_autostart),
                    description = stringResource(R.string.background_perm_autostart_desc),
                    statusText =
                        stringResource(
                            if (isMiui) {
                                R.string.background_perm_status_manual
                            } else {
                                R.string.background_perm_autostart_not_miui
                            },
                        ),
                    statusGranted = !isMiui, // on non-MIUI this row is "done"
                    actionLabel = stringResource(R.string.background_perm_go),
                    showAction = isMiui,
                    onAction = {
                        val success =
                            runCatching {
                                val intent =
                                    Intent().apply {
                                        setClassName(
                                            "com.miui.securitycenter",
                                            "com.miui.permcenter.autostart.AutoStartManagementActivity",
                                        )
                                        putExtra("package_name", context.packageName)
                                        putExtra("package_label", context.getString(R.string.app_name))
                                    }
                                context.startActivity(intent)
                            }.isSuccess
                        if (!success) {
                            Toast
                                .makeText(
                                    context,
                                    context.getString(R.string.background_perm_autostart_failed),
                                    Toast.LENGTH_LONG,
                                ).show()
                        }
                    },
                )
                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    text = stringResource(R.string.confirm),
                    onClick = { showBackgroundPermDialog = false },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

/**
 * Single row of the background-permission check dialog. Shows the feature
 * title, a one-line explanation, a colored status chip, and an optional
 * "go to settings" action button.
 */
@Composable
private fun PermissionRow(
    title: String,
    description: String,
    statusText: String,
    statusGranted: Boolean,
    actionLabel: String,
    showAction: Boolean,
    onAction: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = statusText,
                style = MiuixTheme.textStyles.body2,
                color =
                    if (statusGranted) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurfaceContainerVariant
                    },
            )
        }
        Text(
            text = description,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (showAction) {
            TextButton(
                text = actionLabel,
                onClick = onAction,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
