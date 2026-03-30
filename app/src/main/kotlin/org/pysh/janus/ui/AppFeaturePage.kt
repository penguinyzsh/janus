package org.pysh.janus.ui

import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import org.pysh.janus.R
import org.pysh.janus.data.HookRulesManager
import org.pysh.janus.data.WhitelistManager
import org.pysh.janus.util.RootUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
private fun AppFeaturePagePreview() {
    MiuixTheme {
        AppFeaturePage(packageName = "com.example.app", onBack = {})
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppFeaturePage(
    packageName: String,
    onBack: () -> Unit,
) {
    val isInPreview = LocalInspectionMode.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val whitelistManager = remember { if (!isInPreview) WhitelistManager(context) else null }
    val rulesManager = remember { if (!isInPreview) HookRulesManager(context) else null }
    var isWhitelisted by remember {
        mutableStateOf(whitelistManager?.let { packageName in it.getWhitelist() } ?: false)
    }

    var rulesVersion by remember { mutableIntStateOf(0) }
    val lyricRules = remember(rulesVersion) {
        rulesManager?.getRulesForPackage(packageName) ?: emptyList()
    }
    var deleteTarget by remember { mutableStateOf<HookRulesManager.RuleInfo?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                rulesManager?.importRule(uri, expectedPackage = packageName)
            }
            if (result != null) {
                Toast.makeText(
                    context,
                    context.getString(R.string.hook_rules_import_success, result.name),
                    Toast.LENGTH_SHORT
                ).show()
                rulesVersion++
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.app_lyric_rules_package_mismatch, packageName),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val pm = context.packageManager
    val appInfo = remember {
        try {
            pm.getApplicationInfo(packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }
    val appName = remember { appInfo?.let { pm.getApplicationLabel(it).toString() } ?: packageName }
    val iconBitmap = remember {
        val density = context.resources.displayMetrics.density
        val sizePx = (48 * density).toInt()
        val drawable = appInfo?.let { pm.getApplicationIcon(it) }
            ?: pm.defaultActivityIcon
        drawable.toBitmap(width = sizePx, height = sizePx).asImageBitmap()
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.app_feature),
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
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 12.dp,
                bottom = 12.dp,
                start = 12.dp,
                end = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // App info card
            item {
                Card {
                    BasicComponent(
                        title = appName,
                        summary = packageName,
                        startAction = {
                            Image(
                                painter = BitmapPainter(iconBitmap),
                                contentDescription = appName,
                                modifier = Modifier.size(48.dp),
                            )
                        },
                    )
                }
            }

            // Whitelist switch card
            item {
                Card {
                    SuperSwitch(
                        title = stringResource(R.string.add_music_whitelist),
                        summary = stringResource(if (isWhitelisted) R.string.whitelist_on else R.string.whitelist_off),
                        checked = isWhitelisted,
                        onCheckedChange = { checked ->
                            isWhitelisted = checked
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    val whitelist = whitelistManager?.getWhitelist()?.toMutableSet()
                                        ?: mutableSetOf()
                                    if (checked) {
                                        whitelist.add(packageName)
                                    } else {
                                        whitelist.remove(packageName)
                                    }
                                    whitelistManager?.saveWhitelist(whitelist)
                                    RootUtils.restartBackScreen()
                                }
                                Toast.makeText(
                                    context,
                                    if (checked) context.getString(R.string.whitelist_added) else context.getString(R.string.whitelist_removed),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                    )
                }
            }

            // Lyric Hook Rules section
            item {
                SmallTitle(text = stringResource(R.string.app_lyric_rules))
            }

            if (lyricRules.isEmpty()) {
                item {
                    Card {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.app_lyric_rules_empty),
                                style = MiuixTheme.textStyles.headline2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            )
                        }
                    }
                }
            }

            items(lyricRules, key = { it.rule.id }) { ruleInfo ->
                Card(
                    modifier = Modifier.combinedClickable(
                        onLongClick = { deleteTarget = ruleInfo },
                        onClick = {},
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ruleInfo.rule.name,
                                style = MiuixTheme.textStyles.headline2,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            ruleInfo.rule.author?.let {
                                Text(
                                    text = it,
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = ruleInfo.enabled,
                            onCheckedChange = { enabled ->
                                rulesManager?.setRuleEnabled(ruleInfo.rule.id, builtin = false, enabled)
                                rulesVersion++
                            },
                        )
                    }
                }
            }

            // Import button
            item {
                TextButton(
                    text = stringResource(R.string.app_lyric_rules_import),
                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }

            item {
                Text(
                    text = stringResource(
                        if (lyricRules.isNotEmpty()) R.string.app_lyric_rules_long_press_hint
                        else R.string.app_lyric_rules_restart_hint
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                )
            }
        }

        // Delete confirmation dialog — inside Scaffold
        val target = deleteTarget
        SuperDialog(
            show = target != null,
            onDismissRequest = { deleteTarget = null },
            title = stringResource(R.string.hook_rules_delete_confirm, target?.rule?.name ?: ""),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = { deleteTarget = null },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(android.R.string.ok),
                    onClick = {
                        target?.let { rulesManager?.deleteRule(it.rule.id) }
                        deleteTarget = null
                        rulesVersion++
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}
