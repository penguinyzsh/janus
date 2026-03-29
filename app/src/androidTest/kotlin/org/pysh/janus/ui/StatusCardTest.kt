package org.pysh.janus.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.pysh.janus.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * StatusCard UI 测试 — 对应 TC-HOME-002~006、TC-ACT-003~006
 */
@RunWith(AndroidJUnit4::class)
class StatusCardTest {

    @get:Rule
    val rule = createComposeRule()

    private fun s(id: Int): String =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    // TC-HOME-002: 模块已激活 — 绿色卡片
    @Test
    fun moduleActivated_showsCorrectText() {
        rule.setContent { MiuixTheme { StatusCard(isModuleActive = true, hasRoot = true) } }
        rule.onNodeWithText(s(R.string.status_active)).assertIsDisplayed()
    }

    // TC-HOME-003: 模块未激活 — 红色卡片
    @Test
    fun moduleNotActivated_showsCorrectText() {
        rule.setContent { MiuixTheme { StatusCard(isModuleActive = false, hasRoot = true) } }
        rule.onNodeWithText(s(R.string.status_inactive)).assertIsDisplayed()
    }

    // TC-HOME-004: Root 权限正常 — 绿色卡片
    @Test
    fun rootOk_showsCorrectText() {
        rule.setContent { MiuixTheme { StatusCard(isModuleActive = true, hasRoot = true) } }
        rule.onNodeWithText(s(R.string.status_available)).assertIsDisplayed()
    }

    // TC-HOME-005: 无 Root 权限 — 红色卡片
    @Test
    fun rootMissing_showsCorrectText() {
        rule.setContent { MiuixTheme { StatusCard(isModuleActive = true, hasRoot = false) } }
        rule.onNodeWithText(s(R.string.status_unavailable)).assertIsDisplayed()
    }

    // TC-HOME-006: Root 检测中 — 灰色卡片
    @Test
    fun rootChecking_showsLoadingText() {
        rule.setContent { MiuixTheme { StatusCard(isModuleActive = true, hasRoot = null) } }
        rule.onNodeWithText(s(R.string.status_checking)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.status_available)).assertDoesNotExist()
        rule.onNodeWithText(s(R.string.status_unavailable)).assertDoesNotExist()
    }
}
