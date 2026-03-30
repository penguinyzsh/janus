package org.pysh.janus.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pysh.janus.R
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * SettingsPage UI 测试 — 对应 TC-SET 系列
 */
@RunWith(AndroidJUnit4::class)
class SettingsPageTest {

    @get:Rule
    val rule = createComposeRule()

    private fun s(id: Int): String =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    private fun content(
        onAboutClick: () -> Unit = {},
        onOtherClick: () -> Unit = {},
    ) {
        rule.setContent {
            MiuixTheme {
                SettingsPage(
                    bottomPadding = 0.dp,
                    onAboutClick = onAboutClick,
                    onOtherClick = onOtherClick,
                )
            }
        }
    }

    // TC-SET-001: 页面显示隐藏图标开关
    @Test
    fun settingsPage_showsHideIconSwitch() {
        content()
        rule.onNodeWithText(s(R.string.hide_icon)).assertIsDisplayed()
    }

    // TC-SET-001: 默认图标未隐藏摘要
    @Test
    fun settingsPage_defaultHideIconSummary() {
        content()
        rule.onNodeWithText(s(R.string.hide_icon_off)).assertIsDisplayed()
    }

    // TC-SET-001: 其他菜单展示
    @Test
    fun settingsPage_showsOtherItem() {
        content()
        rule.onNodeWithText(s(R.string.other)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.other_summary)).assertIsDisplayed()
    }

    // TC-SET-001: 关于菜单展示
    @Test
    fun settingsPage_showsAboutItem() {
        content()
        rule.onNodeWithText(s(R.string.about)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.about_janus)).assertIsDisplayed()
    }

    // TC-SET-002: 开启隐藏图标弹出确认对话框
    @Test
    fun settingsPage_toggleHideIcon_opensDialog() {
        content()
        rule.onNodeWithText(s(R.string.hide_icon_dialog_summary)).assertDoesNotExist()
        rule.onNodeWithText(s(R.string.hide_icon)).performClick()
        rule.onNodeWithText(s(R.string.hide_icon_dialog_summary)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.confirm)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.cancel)).assertIsDisplayed()
    }

    // TC-SET-004: 取消隐藏图标对话框
    @Test
    fun settingsPage_hideIconDialog_cancel_closes() {
        content()
        rule.onNodeWithText(s(R.string.hide_icon)).performClick()
        rule.onNodeWithText(s(R.string.hide_icon_dialog_summary)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.cancel)).performClick()
        rule.onNodeWithText(s(R.string.hide_icon_dialog_summary)).assertDoesNotExist()
    }

    // TC-SET-006a: 点击"其他"触发回调
    @Test
    fun settingsPage_clickOther_triggersCallback() {
        var clicked = false
        content(onOtherClick = { clicked = true })
        rule.onNodeWithText(s(R.string.other)).performClick()
        assertTrue(clicked)
    }

    // TC-SET-006b: 点击"关于"触发回调
    @Test
    fun settingsPage_clickAbout_triggersCallback() {
        var clicked = false
        content(onAboutClick = { clicked = true })
        rule.onNodeWithText(s(R.string.about)).performClick()
        assertTrue(clicked)
    }
}
