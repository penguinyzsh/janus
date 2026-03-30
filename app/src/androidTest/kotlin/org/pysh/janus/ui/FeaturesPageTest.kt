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
 * FeaturesPage UI 测试 — 对应 TC-FEAT 系列
 */
@RunWith(AndroidJUnit4::class)
class FeaturesPageTest {

    @get:Rule
    val rule = createComposeRule()

    private fun s(id: Int): String =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    private fun content(
        onWallpaperClick: () -> Unit = {},
        onCastingClick: () -> Unit = {},
        onHookRulesClick: () -> Unit = {},
    ) {
        rule.setContent {
            MiuixTheme {
                FeaturesPage(
                    bottomPadding = 0.dp,
                    onWallpaperClick = onWallpaperClick,
                    onCastingClick = onCastingClick,
                    onHookRulesClick = onHookRulesClick,
                )
            }
        }
    }

    // TC-FEAT-001: 统计上报开关展示
    @Test
    fun featuresPage_showsTrackingSwitch() {
        content()
        rule.onNodeWithText(s(R.string.disable_tracking)).assertIsDisplayed()
    }

    // TC-FEAT-001: 默认统计上报摘要
    @Test
    fun featuresPage_defaultTrackingSummary() {
        content()
        rule.onNodeWithText(s(R.string.disable_tracking_off)).assertIsDisplayed()
    }

    // TC-FEAT-TT1: 时间胶囊开关展示
    @Test
    fun featuresPage_showsTimeTipSwitch() {
        content()
        rule.onNodeWithText(s(R.string.hide_time_tip)).assertIsDisplayed()
    }

    // TC-FEAT-TT1: 默认时间胶囊摘要
    @Test
    fun featuresPage_defaultTimeTipSummary() {
        content()
        rule.onNodeWithText(s(R.string.hide_time_tip_off)).assertIsDisplayed()
    }

    // TC-FEAT-NAV1: 壁纸导航入口展示
    @Test
    fun featuresPage_showsWallpaperEntry() {
        content()
        rule.onNodeWithText(s(R.string.section_wallpaper)).assertIsDisplayed()
    }

    // TC-FEAT-NAV2: 投屏导航入口展示
    @Test
    fun featuresPage_showsCastingEntry() {
        content()
        rule.onNodeWithText(s(R.string.section_casting)).assertIsDisplayed()
    }

    // TC-FEAT-NAV3: Hook Rules 导航入口展示
    @Test
    fun featuresPage_showsHookRulesEntry() {
        content()
        rule.onNodeWithText(s(R.string.section_hook_rules)).assertIsDisplayed()
    }

    // TC-FEAT-NAV1: 点击壁纸触发回调
    @Test
    fun featuresPage_clickWallpaper_triggersCallback() {
        var clicked = false
        content(onWallpaperClick = { clicked = true })
        rule.onNodeWithText(s(R.string.section_wallpaper)).performClick()
        assertTrue(clicked)
    }

    // TC-FEAT-NAV2: 点击投屏触发回调
    @Test
    fun featuresPage_clickCasting_triggersCallback() {
        var clicked = false
        content(onCastingClick = { clicked = true })
        rule.onNodeWithText(s(R.string.section_casting)).performClick()
        assertTrue(clicked)
    }

    // TC-FEAT-NAV3: 点击 Hook Rules 触发回调
    @Test
    fun featuresPage_clickHookRules_triggersCallback() {
        var clicked = false
        content(onHookRulesClick = { clicked = true })
        rule.onNodeWithText(s(R.string.section_hook_rules)).performClick()
        assertTrue(clicked)
    }
}
