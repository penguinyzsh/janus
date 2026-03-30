package org.pysh.janus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.pysh.janus.R
import org.pysh.janus.data.HookRulesManager
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
private fun HookRulesPagePreview() {
    MiuixTheme {
        HookRulesPage(onBack = {})
    }
}

@Composable
fun HookRulesPage(onBack: () -> Unit) {
    val isInPreview = LocalInspectionMode.current
    val context = LocalContext.current
    val manager = remember { if (!isInPreview) HookRulesManager(context) else null }

    var rulesVersion by remember { mutableIntStateOf(0) }
    val builtinRules = remember(rulesVersion) { manager?.getBuiltinRules() ?: emptyList() }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.hook_rules_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding() + 12.dp,
                bottom = 12.dp,
                start = 12.dp,
                end = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (builtinRules.isNotEmpty()) {
                items(builtinRules, key = { it.rule.id }) { ruleInfo ->
                    Card {
                        SuperSwitch(
                            title = ruleInfo.rule.name,
                            summary = buildString {
                                append(ruleInfo.rule.targetPackage)
                                if (ruleInfo.rule.engine != null) append(" | ${ruleInfo.rule.engine}")
                                else if (ruleInfo.rule.hooks != null) append(" | " + context.getString(R.string.hook_rules_hooks_count, ruleInfo.rule.hooks.size))
                                ruleInfo.rule.author?.let { append(" | $it") }
                            },
                            checked = ruleInfo.enabled,
                            onCheckedChange = {
                                manager?.setRuleEnabled(ruleInfo.rule.id, builtin = true, it)
                                rulesVersion++
                            },
                        )
                    }
                }
            }

            if (builtinRules.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.hook_rules_empty),
                        modifier = Modifier.padding(16.dp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.hook_rules_restart_hint),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                )
            }
        }
    }
}
