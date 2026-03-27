package org.pysh.janus.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.pysh.janus.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * FeaturesPage UI 测试 — 对应 TC-FEAT 系列
 */
@RunWith(AndroidJUnit4::class)
class FeaturesPageTest {

    @get:Rule
    val rule = createComposeRule()

    private fun s(id: Int): String =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    private fun s(id: Int, vararg args: Any): String =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(id, *args)

    // TC-FEAT-001/002: 统计上报开关展示
    @Test
    fun featuresPage_showsTrackingSwitch() {
        rule.setContent {
            MiuixTheme { FeaturesPage(bottomPadding = 0.dp, currentDpi = 320, onDpiChanged = {}, onWallpaperClick = {}) }
        }
        rule.onNodeWithText(s(R.string.disable_tracking)).assertIsDisplayed()
    }

    // TC-FEAT-WC1/WC2: 天气卡片开关展示
    @Test
    fun featuresPage_showsWeatherCardSwitch() {
        rule.setContent {
            MiuixTheme { FeaturesPage(bottomPadding = 0.dp, currentDpi = 320, onDpiChanged = {}, onWallpaperClick = {}) }
        }
        rule.onNodeWithText(s(R.string.weather_card)).assertIsDisplayed()
    }

    // TC-FEAT-WC1: 默认天气卡片摘要
    @Test
    fun featuresPage_defaultWeatherCardSummary() {
        rule.setContent {
            MiuixTheme { FeaturesPage(bottomPadding = 0.dp, currentDpi = 320, onDpiChanged = {}, onWallpaperClick = {}) }
        }
        rule.onNodeWithText(s(R.string.weather_card_off)).assertIsDisplayed()
    }

    // TC-FEAT-003/004: 背屏常亮开关展示
    @Test
    fun featuresPage_showsKeepAliveSwitch() {
        rule.setContent {
            MiuixTheme { FeaturesPage(bottomPadding = 0.dp, currentDpi = 320, onDpiChanged = {}, onWallpaperClick = {}) }
        }
        rule.onNodeWithText(s(R.string.keep_alive)).assertIsDisplayed()
    }

    // TC-FEAT-005: 唤醒间隔展示
    @Test
    fun featuresPage_showsKeepAliveInterval() {
        rule.setContent {
            MiuixTheme { FeaturesPage(bottomPadding = 0.dp, currentDpi = 320, onDpiChanged = {}, onWallpaperClick = {}) }
        }
        rule.onNodeWithText(s(R.string.keep_alive_interval)).assertIsDisplayed()
    }

    // TC-FEAT-010: DPI 区域展示当前值
    @Test
    fun featuresPage_showsCurrentDpi() {
        rule.setContent {
            MiuixTheme { FeaturesPage(bottomPadding = 0.dp, currentDpi = 320, onDpiChanged = {}, onWallpaperClick = {}) }
        }
        rule.onNodeWithText(s(R.string.rear_dpi)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.rear_dpi_current, 320)).assertIsDisplayed()
    }

    // TC-FEAT-010: DPI 获取中状态
    @Test
    fun featuresPage_dpiLoading_showsLoadingText() {
        rule.setContent {
            MiuixTheme { FeaturesPage(bottomPadding = 0.dp, currentDpi = null, onDpiChanged = {}, onWallpaperClick = {}) }
        }
        rule.onNodeWithText(s(R.string.loading)).assertIsDisplayed()
    }

    // TC-FEAT-015: 投屏旋转展示
    @Test
    fun featuresPage_showsCastRotation() {
        rule.setContent {
            MiuixTheme { FeaturesPage(bottomPadding = 0.dp, currentDpi = 320, onDpiChanged = {}, onWallpaperClick = {}) }
        }
        rule.onNodeWithText(s(R.string.cast_rotation)).assertIsDisplayed()
    }

    // TC-FEAT-017/018: 投屏常亮展示
    @Test
    fun featuresPage_showsCastKeepAlive() {
        rule.setContent {
            MiuixTheme { FeaturesPage(bottomPadding = 0.dp, currentDpi = 320, onDpiChanged = {}, onWallpaperClick = {}) }
        }
        rule.onNodeWithText(s(R.string.cast_keep_alive)).assertIsDisplayed()
    }

    // ── 交互测试 ──

    // TC-FEAT-007: 点击唤醒间隔打开对话框
    @Test
    fun featuresPage_clickInterval_opensDialog() {
        rule.setContent {
            MiuixTheme { FeaturesPage(bottomPadding = 0.dp, currentDpi = 320, onDpiChanged = {}, onWallpaperClick = {}) }
        }
        // 对话框提示文字在打开前不存在
        rule.onNodeWithText(s(R.string.keep_alive_interval_dialog_summary)).assertDoesNotExist()
        // 点击唤醒间隔
        rule.onNodeWithText(s(R.string.keep_alive_interval)).performClick()
        // 对话框出现，显示提示"输入 1-300 秒"
        rule.onNodeWithText(s(R.string.keep_alive_interval_dialog_summary)).assertIsDisplayed()
        // 对话框中有确认和取消按钮
        rule.onNodeWithText(s(R.string.confirm)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.cancel)).assertIsDisplayed()
    }

    // TC-FEAT-007: 唤醒间隔对话框取消
    @Test
    fun featuresPage_intervalDialog_cancel_closes() {
        rule.setContent {
            MiuixTheme { FeaturesPage(bottomPadding = 0.dp, currentDpi = 320, onDpiChanged = {}, onWallpaperClick = {}) }
        }
        rule.onNodeWithText(s(R.string.keep_alive_interval)).performClick()
        rule.onNodeWithText(s(R.string.keep_alive_interval_dialog_summary)).assertIsDisplayed()
        // 点击取消
        rule.onNodeWithText(s(R.string.cancel)).performClick()
        // 对话框关闭
        rule.onNodeWithText(s(R.string.keep_alive_interval_dialog_summary)).assertDoesNotExist()
    }

    // TC-FEAT-009: 恢复默认按钮展示
    @Test
    fun featuresPage_showsResetDefaultButton() {
        rule.setContent {
            MiuixTheme { FeaturesPage(bottomPadding = 0.dp, currentDpi = 320, onDpiChanged = {}, onWallpaperClick = {}) }
        }
        rule.onNodeWithText(s(R.string.reset_default)).assertIsDisplayed()
    }

    // TC-FEAT-012: 点击 DPI 区域打开对话框
    @Test
    fun featuresPage_clickDpi_opensDialog() {
        rule.setContent {
            MiuixTheme { FeaturesPage(bottomPadding = 0.dp, currentDpi = 320, onDpiChanged = {}, onWallpaperClick = {}) }
        }
        rule.onNodeWithText(s(R.string.dpi_dialog_summary)).assertDoesNotExist()
        rule.onNodeWithText(s(R.string.rear_dpi)).performClick()
        // 对话框出现，显示提示"输入 100-800"
        rule.onNodeWithText(s(R.string.dpi_dialog_summary)).assertIsDisplayed()
    }

    // TC-FEAT-012: DPI 对话框取消
    @Test
    fun featuresPage_dpiDialog_cancel_closes() {
        rule.setContent {
            MiuixTheme { FeaturesPage(bottomPadding = 0.dp, currentDpi = 320, onDpiChanged = {}, onWallpaperClick = {}) }
        }
        rule.onNodeWithText(s(R.string.rear_dpi)).performClick()
        rule.onNodeWithText(s(R.string.dpi_dialog_summary)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.cancel)).performClick()
        rule.onNodeWithText(s(R.string.dpi_dialog_summary)).assertDoesNotExist()
    }

    // TC-FEAT-013: 设置和重置 DPI 按钮展示
    @Test
    fun featuresPage_showsDpiButtons() {
        rule.setContent {
            MiuixTheme { FeaturesPage(bottomPadding = 0.dp, currentDpi = 320, onDpiChanged = {}, onWallpaperClick = {}) }
        }
        rule.onNodeWithText(s(R.string.set_dpi)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.reset)).assertIsDisplayed()
    }

    // TC-FEAT-015: 点击投屏旋转打开弹窗，显示三个选项
    @Test
    fun featuresPage_clickRotation_opensDialogWithOptions() {
        rule.setContent {
            MiuixTheme { FeaturesPage(bottomPadding = 0.dp, currentDpi = 320, onDpiChanged = {}, onWallpaperClick = {}) }
        }
        rule.onNodeWithText(s(R.string.cast_rotation)).performClick()
        // 三个旋转选项都应显示
        rule.onNodeWithText(s(R.string.cast_rotation_none)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.cast_rotation_left)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.cast_rotation_right)).assertIsDisplayed()
    }

    // TC-FEAT-015a: 选择旋转方向后弹窗关闭，摘要更新
    @Test
    fun featuresPage_selectRotation_updatesAndCloses() {
        rule.setContent {
            MiuixTheme { FeaturesPage(bottomPadding = 0.dp, currentDpi = 320, onDpiChanged = {}, onWallpaperClick = {}) }
        }
        // 打开旋转弹窗
        rule.onNodeWithText(s(R.string.cast_rotation)).performClick()
        // 选择"向左旋转 90°"
        rule.onNodeWithText(s(R.string.cast_rotation_left)).performClick()
        // 弹窗关闭 — "向右旋转 90°" 不再显示（仅弹窗内有此选项）
        rule.onNodeWithText(s(R.string.cast_rotation_right)).assertDoesNotExist()
        // 旧摘要"不旋转"不再显示
        rule.onNodeWithText(s(R.string.cast_rotation_none)).assertDoesNotExist()
    }

    // TC-FEAT-015: 默认旋转摘要为"不旋转"
    @Test
    fun featuresPage_defaultRotationSummary() {
        rule.setContent {
            MiuixTheme { FeaturesPage(bottomPadding = 0.dp, currentDpi = 320, onDpiChanged = {}, onWallpaperClick = {}) }
        }
        rule.onNodeWithText(s(R.string.cast_rotation_none)).assertIsDisplayed()
    }

    // TC-FEAT-001: 默认统计上报摘要
    @Test
    fun featuresPage_defaultTrackingSummary() {
        rule.setContent {
            MiuixTheme { FeaturesPage(bottomPadding = 0.dp, currentDpi = 320, onDpiChanged = {}, onWallpaperClick = {}) }
        }
        rule.onNodeWithText(s(R.string.disable_tracking_off)).assertIsDisplayed()
    }

    // TC-FEAT-003: 默认常亮摘要
    @Test
    fun featuresPage_defaultKeepAliveSummary() {
        rule.setContent {
            MiuixTheme { FeaturesPage(bottomPadding = 0.dp, currentDpi = 320, onDpiChanged = {}, onWallpaperClick = {}) }
        }
        rule.onNodeWithText(s(R.string.keep_alive_off)).assertIsDisplayed()
    }

    // TC-FEAT-017: 默认投屏常亮摘要
    @Test
    fun featuresPage_defaultCastKeepAliveSummary() {
        rule.setContent {
            MiuixTheme { FeaturesPage(bottomPadding = 0.dp, currentDpi = 320, onDpiChanged = {}, onWallpaperClick = {}) }
        }
        rule.onNodeWithText(s(R.string.cast_keep_alive_off)).assertIsDisplayed()
    }
}
