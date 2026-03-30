package org.pysh.janus.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pysh.janus.R
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * OtherPage UI 测试 — 对应 TC-OTH 系列
 */
@RunWith(AndroidJUnit4::class)
class OtherPageTest {

    @get:Rule
    val rule = createComposeRule()

    private fun s(id: Int): String =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    private fun content() {
        rule.setContent { MiuixTheme { OtherPage(onBack = {}) } }
    }

    // TC-OTH-001: 页面标题
    @Test
    fun otherPage_showsTitle() {
        content()
        rule.onNodeWithText(s(R.string.other)).assertIsDisplayed()
    }

    // TC-OTH-001: 重新同步菜单项
    @Test
    fun otherPage_showsResyncItem() {
        content()
        rule.onNodeWithText(s(R.string.resync)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.resync_summary)).assertIsDisplayed()
    }

    // TC-OTH-001: 清除数据菜单项
    @Test
    fun otherPage_showsCleanupItem() {
        content()
        rule.onNodeWithText(s(R.string.cleanup)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.cleanup_summary)).assertIsDisplayed()
    }

    // TC-OTH-003: 点击清除弹出确认对话框
    @Test
    fun otherPage_clickCleanup_opensDialog() {
        content()
        rule.onNodeWithText(s(R.string.cleanup_dialog_summary)).assertDoesNotExist()
        rule.onNodeWithText(s(R.string.cleanup)).performClick()
        rule.onNodeWithText(s(R.string.cleanup_dialog_summary)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.confirm)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.cancel)).assertIsDisplayed()
    }

    // TC-OTH-003: 取消清除对话框
    @Test
    fun otherPage_cleanupDialog_cancel_closes() {
        content()
        rule.onNodeWithText(s(R.string.cleanup)).performClick()
        rule.onNodeWithText(s(R.string.cleanup_dialog_summary)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.cancel)).performClick()
        rule.onNodeWithText(s(R.string.cleanup_dialog_summary)).assertDoesNotExist()
    }
}
