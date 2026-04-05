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
 * ActivationPage UI 测试 — 对应 TC-ACT-001~009
 */
@RunWith(AndroidJUnit4::class)
class ActivationPageTest {
    @get:Rule
    val rule = createComposeRule()

    private fun s(id: Int): String = ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    // TC-ACT-003 + TC-ACT-005: 模块和 Root 都就绪
    @Test
    fun activationPage_bothActive_showsGreenCards() {
        rule.setContent {
            MiuixTheme { ActivationPage(isModuleActive = true, hasRoot = true) }
        }
        rule.onNodeWithText(s(R.string.status_active)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.status_available)).assertIsDisplayed()
    }

    // TC-ACT-004 + TC-ACT-006: 模块和 Root 都缺失
    @Test
    fun activationPage_bothInactive_showsRedCards() {
        rule.setContent {
            MiuixTheme { ActivationPage(isModuleActive = false, hasRoot = false) }
        }
        rule.onNodeWithText(s(R.string.status_inactive)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.status_unavailable)).assertIsDisplayed()
    }

    // TC-ACT-008: 模块已激活但无 Root
    @Test
    fun activationPage_moduleActiveNoRoot() {
        rule.setContent {
            MiuixTheme { ActivationPage(isModuleActive = true, hasRoot = false) }
        }
        rule.onNodeWithText(s(R.string.status_active)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.status_unavailable)).assertIsDisplayed()
    }

    // TC-ACT-009: 无模块但有 Root
    @Test
    fun activationPage_noModuleHasRoot() {
        rule.setContent {
            MiuixTheme { ActivationPage(isModuleActive = false, hasRoot = true) }
        }
        rule.onNodeWithText(s(R.string.status_inactive)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.status_available)).assertIsDisplayed()
    }

    // TC-ACT-001: 页面标题为"激活"
    @Test
    fun activationPage_showsTitle() {
        rule.setContent {
            MiuixTheme { ActivationPage(isModuleActive = false, hasRoot = false) }
        }
        rule.onNodeWithText(s(R.string.activation)).assertIsDisplayed()
    }
}
