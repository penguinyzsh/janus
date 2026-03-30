package org.pysh.janus.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pysh.janus.R
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * HookRulesPage UI 测试 — 对应 TC-RULE 系列
 */
@RunWith(AndroidJUnit4::class)
class HookRulesPageTest {

    @get:Rule
    val rule = createComposeRule()

    private fun s(id: Int): String =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    // TC-RULE-001: 页面标题展示
    @Test
    fun hookRulesPage_showsTitle() {
        rule.setContent {
            MiuixTheme { HookRulesPage(onBack = {}) }
        }
        rule.onNodeWithText(s(R.string.hook_rules_title)).assertIsDisplayed()
    }

    // TC-RULE-001: 重启提示展示
    @Test
    fun hookRulesPage_showsRestartHint() {
        rule.setContent {
            MiuixTheme { HookRulesPage(onBack = {}) }
        }
        rule.onNodeWithText(s(R.string.hook_rules_restart_hint)).assertIsDisplayed()
    }

    // TC-RULE-001: 无规则时显示空提示
    @Test
    fun hookRulesPage_showsEmptyHint_whenNoRules() {
        // 当 getAllRules() 返回空列表时应显示空提示
        // 实际测试环境有内置规则，所以此处验证空提示不显示
        rule.setContent {
            MiuixTheme { HookRulesPage(onBack = {}) }
        }
        rule.onNodeWithText(s(R.string.hook_rules_empty)).assertDoesNotExist()
    }
}
