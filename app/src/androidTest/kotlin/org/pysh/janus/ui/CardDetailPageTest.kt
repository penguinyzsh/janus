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
 * CardDetailPage UI 测试 — 对应 TC-CARD-D 系列
 *
 * CardDetailPage 内部通过 CardManager 查找 slot 对应的卡片。
 * 测试环境无导入卡片时 card == null，页面仍可渲染（显示默认值）。
 */
@RunWith(AndroidJUnit4::class)
class CardDetailPageTest {
    @get:Rule
    val rule = createComposeRule()

    private fun s(id: Int): String = ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    private fun s(
        id: Int,
        vararg args: Any,
    ): String = ApplicationProvider.getApplicationContext<android.content.Context>().getString(id, *args)

    private fun content() {
        rule.setContent { MiuixTheme { CardDetailPage(slot = 0, onBack = {}) } }
    }

    // TC-CARD-D01: 刷新间隔展示
    @Test
    fun cardDetailPage_showsRefreshInterval() {
        content()
        rule.onNodeWithText(s(R.string.card_refresh_interval)).assertIsDisplayed()
    }

    // TC-CARD-D01: 默认刷新间隔值
    @Test
    fun cardDetailPage_defaultRefreshValue() {
        content()
        rule.onNodeWithText(s(R.string.card_refresh_interval_value, 30)).assertIsDisplayed()
    }

    // TC-CARD-D01: 优先级展示
    @Test
    fun cardDetailPage_showsPriority() {
        content()
        rule.onNodeWithText(s(R.string.card_priority)).assertIsDisplayed()
    }

    // TC-CARD-D01: 默认优先级值
    @Test
    fun cardDetailPage_defaultPriorityValue() {
        content()
        rule.onNodeWithText(s(R.string.card_priority_value, 100)).assertIsDisplayed()
    }

    // TC-CARD-D03: 点击刷新间隔打开弹窗
    @Test
    fun cardDetailPage_clickRefresh_opensDialog() {
        content()
        rule.onNodeWithText(s(R.string.card_refresh_interval_summary, 10, 120)).assertDoesNotExist()
        rule.onNodeWithText(s(R.string.card_refresh_interval)).performClick()
        rule.onNodeWithText(s(R.string.card_refresh_interval_summary, 10, 120)).assertIsDisplayed()
    }

    // TC-CARD-D03: 刷新间隔弹窗取消
    @Test
    fun cardDetailPage_refreshDialog_cancel_closes() {
        content()
        rule.onNodeWithText(s(R.string.card_refresh_interval)).performClick()
        rule.onNodeWithText(s(R.string.card_refresh_interval_summary, 10, 120)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.cancel)).performClick()
        rule.onNodeWithText(s(R.string.card_refresh_interval_summary, 10, 120)).assertDoesNotExist()
    }

    // TC-CARD-D06: 恢复默认按钮
    @Test
    fun cardDetailPage_showsResetDefault() {
        content()
        rule.onNodeWithText(s(R.string.reset_default)).assertIsDisplayed()
    }

    // TC-CARD-D07: 删除按钮
    @Test
    fun cardDetailPage_showsDeleteButton() {
        content()
        rule.onNodeWithText(s(R.string.cards_delete)).assertIsDisplayed()
    }

    // TC-CARD-D01: 设置标题
    @Test
    fun cardDetailPage_showsSettingsTitle() {
        content()
        rule.onNodeWithText(s(R.string.nav_settings)).assertIsDisplayed()
    }
}
