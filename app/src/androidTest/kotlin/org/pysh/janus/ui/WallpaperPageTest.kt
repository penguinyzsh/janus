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
 * WallpaperPage UI 测试 — 对应 TC-FEAT-WP/WPL/WPC 系列
 */
@RunWith(AndroidJUnit4::class)
class WallpaperPageTest {

    @get:Rule
    val rule = createComposeRule()

    private fun s(id: Int): String =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    private fun content() {
        rule.setContent { MiuixTheme { WallpaperPage(onBack = {}) } }
    }

    // TC-FEAT-WP1: 壁纸防打断开关展示
    @Test
    fun wallpaperPage_showsKeepAliveSwitch() {
        content()
        rule.onNodeWithText(s(R.string.wallpaper_keep_alive)).assertIsDisplayed()
    }

    // TC-FEAT-WP1: 默认壁纸防打断摘要
    @Test
    fun wallpaperPage_defaultKeepAliveSummary() {
        content()
        rule.onNodeWithText(s(R.string.wallpaper_keep_alive_off)).assertIsDisplayed()
    }

    // TC-FEAT-WPL1: 壁纸锁定开关展示
    @Test
    fun wallpaperPage_showsLockSwitch() {
        content()
        rule.onNodeWithText(s(R.string.wallpaper_lock)).assertIsDisplayed()
    }

    // TC-FEAT-WPL1: 默认壁纸锁定摘要
    @Test
    fun wallpaperPage_defaultLockSummary() {
        content()
        rule.onNodeWithText(s(R.string.wallpaper_lock_off)).assertIsDisplayed()
    }

    // TC-FEAT-WPC2: 自定义壁纸入口展示
    @Test
    fun wallpaperPage_showsCustomTitle() {
        content()
        rule.onNodeWithText(s(R.string.wp_custom_title)).assertIsDisplayed()
    }

    // TC-FEAT-WPC5: 循环播放开关展示
    @Test
    fun wallpaperPage_showsLoopSwitch() {
        content()
        rule.onNodeWithText(s(R.string.wp_loop)).assertIsDisplayed()
    }

    // TC-FEAT-WPC8: 恢复按钮始终可见
    @Test
    fun wallpaperPage_showsRestoreButton() {
        content()
        rule.onNodeWithText(s(R.string.wp_restore)).assertIsDisplayed()
    }
}
