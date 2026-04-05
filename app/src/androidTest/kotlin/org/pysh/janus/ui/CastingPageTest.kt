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
 * CastingPage UI 测试 — 对应 TC-CAST 系列
 */
@RunWith(AndroidJUnit4::class)
class CastingPageTest {
    @get:Rule
    val rule = createComposeRule()

    private fun s(id: Int): String = ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    private fun s(
        id: Int,
        vararg args: Any,
    ): String = ApplicationProvider.getApplicationContext<android.content.Context>().getString(id, *args)

    private fun content(currentDpi: Int? = 320) {
        rule.setContent {
            MiuixTheme {
                CastingPage(currentDpi = currentDpi, onDpiChanged = {}, onBack = {})
            }
        }
    }

    // TC-CAST-015: 投屏旋转展示
    @Test
    fun castingPage_showsCastRotation() {
        content()
        rule.onNodeWithText(s(R.string.cast_rotation)).assertIsDisplayed()
    }

    // TC-CAST-015: 默认旋转摘要
    @Test
    fun castingPage_defaultRotationSummary() {
        content()
        rule.onNodeWithText(s(R.string.cast_rotation_none)).assertIsDisplayed()
    }

    // TC-CAST-015: 点击旋转打开弹窗
    @Test
    fun castingPage_clickRotation_opensDialog() {
        content()
        rule.onNodeWithText(s(R.string.cast_rotation)).performClick()
        rule.onNodeWithText(s(R.string.cast_rotation_none)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.cast_rotation_left)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.cast_rotation_right)).assertIsDisplayed()
    }

    // TC-CAST-015a: 选择旋转方向后弹窗关闭
    @Test
    fun castingPage_selectRotation_closesDialog() {
        content()
        rule.onNodeWithText(s(R.string.cast_rotation)).performClick()
        rule.onNodeWithText(s(R.string.cast_rotation_left)).performClick()
        rule.onNodeWithText(s(R.string.cast_rotation_right)).assertDoesNotExist()
    }

    // TC-CAST-017/018: 投屏常亮展示
    @Test
    fun castingPage_showsCastKeepAlive() {
        content()
        rule.onNodeWithText(s(R.string.cast_keep_alive)).assertIsDisplayed()
    }

    // TC-CAST-017: 默认投屏常亮摘要
    @Test
    fun castingPage_defaultCastKeepAliveSummary() {
        content()
        rule.onNodeWithText(s(R.string.cast_keep_alive_off)).assertIsDisplayed()
    }

    // TC-CAST-003/004: 背屏常亮展示
    @Test
    fun castingPage_showsKeepAlive() {
        content()
        rule.onNodeWithText(s(R.string.keep_alive)).assertIsDisplayed()
    }

    // TC-CAST-003: 默认常亮摘要
    @Test
    fun castingPage_defaultKeepAliveSummary() {
        content()
        rule.onNodeWithText(s(R.string.keep_alive_off)).assertIsDisplayed()
    }

    // TC-CAST-005: 唤醒间隔展示
    @Test
    fun castingPage_showsKeepAliveInterval() {
        content()
        rule.onNodeWithText(s(R.string.keep_alive_interval)).assertIsDisplayed()
    }

    // TC-CAST-007: 点击唤醒间隔打开对话框
    @Test
    fun castingPage_clickInterval_opensDialog() {
        content()
        rule.onNodeWithText(s(R.string.keep_alive_interval_dialog_summary)).assertDoesNotExist()
        rule.onNodeWithText(s(R.string.keep_alive_interval)).performClick()
        rule.onNodeWithText(s(R.string.keep_alive_interval_dialog_summary)).assertIsDisplayed()
    }

    // TC-CAST-007: 唤醒间隔对话框取消
    @Test
    fun castingPage_intervalDialog_cancel_closes() {
        content()
        rule.onNodeWithText(s(R.string.keep_alive_interval)).performClick()
        rule.onNodeWithText(s(R.string.keep_alive_interval_dialog_summary)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.cancel)).performClick()
        rule.onNodeWithText(s(R.string.keep_alive_interval_dialog_summary)).assertDoesNotExist()
    }

    // TC-CAST-009: 恢复默认按钮
    @Test
    fun castingPage_showsResetDefault() {
        content()
        rule.onNodeWithText(s(R.string.reset_default)).assertIsDisplayed()
    }

    // TC-CAST-010: DPI 展示当前值
    @Test
    fun castingPage_showsCurrentDpi() {
        content(currentDpi = 320)
        rule.onNodeWithText(s(R.string.rear_dpi)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.rear_dpi_current, 320)).assertIsDisplayed()
    }

    // TC-CAST-010: DPI 获取中
    @Test
    fun castingPage_dpiLoading() {
        content(currentDpi = null)
        rule.onNodeWithText(s(R.string.loading)).assertIsDisplayed()
    }

    // TC-CAST-012: 点击 DPI 打开对话框
    @Test
    fun castingPage_clickDpi_opensDialog() {
        content()
        rule.onNodeWithText(s(R.string.dpi_dialog_summary)).assertDoesNotExist()
        rule.onNodeWithText(s(R.string.rear_dpi)).performClick()
        rule.onNodeWithText(s(R.string.dpi_dialog_summary)).assertIsDisplayed()
    }

    // TC-CAST-012: DPI 对话框取消
    @Test
    fun castingPage_dpiDialog_cancel_closes() {
        content()
        rule.onNodeWithText(s(R.string.rear_dpi)).performClick()
        rule.onNodeWithText(s(R.string.dpi_dialog_summary)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.cancel)).performClick()
        rule.onNodeWithText(s(R.string.dpi_dialog_summary)).assertDoesNotExist()
    }

    // TC-CAST-013: 设置和重置 DPI 按钮
    @Test
    fun castingPage_showsDpiButtons() {
        content()
        rule.onNodeWithText(s(R.string.set_dpi)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.reset)).assertIsDisplayed()
    }
}
