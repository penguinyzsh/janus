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
 * SystemCardsPage UI 测试 — 对应 TC-SYS / TC-MUSIC 系列
 */
@RunWith(AndroidJUnit4::class)
class SystemCardsPageTest {

    @get:Rule
    val rule = createComposeRule()

    private fun s(id: Int): String =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    private fun content() {
        rule.setContent { MiuixTheme { SystemCardsPage(onBack = {}) } }
    }

    // TC-SYS-001: 页面标题
    @Test
    fun systemCardsPage_showsTitle() {
        content()
        rule.onNodeWithText(s(R.string.section_system_cards)).assertIsDisplayed()
    }

    // TC-SYS-001: 音乐区域标题
    @Test
    fun systemCardsPage_showsMusicSection() {
        content()
        rule.onNodeWithText(s(R.string.section_music)).assertIsDisplayed()
    }

    // TC-MUSIC-001: 自定义音乐卡片入口
    @Test
    fun systemCardsPage_showsMusicOverrideTitle() {
        content()
        rule.onNodeWithText(s(R.string.music_override_title)).assertIsDisplayed()
    }

    // TC-MUSIC-001: 默认使用官方模板
    @Test
    fun systemCardsPage_defaultMusicOverrideSummary() {
        content()
        rule.onNodeWithText(s(R.string.music_override_none)).assertIsDisplayed()
    }

    // TC-SYS-001: 其他系统卡片区域标题
    @Test
    fun systemCardsPage_showsOtherSystemCardsSection() {
        content()
        rule.onNodeWithText(s(R.string.section_other_system_cards)).assertIsDisplayed()
    }

    // TC-SYS-001: 来电卡片
    @Test
    fun systemCardsPage_showsIncallCard() {
        content()
        rule.onNodeWithText(s(R.string.system_card_incall)).assertIsDisplayed()
    }

    // TC-SYS-001: 闹钟卡片
    @Test
    fun systemCardsPage_showsAlarmCard() {
        content()
        rule.onNodeWithText(s(R.string.system_card_alarm)).assertIsDisplayed()
    }

    // TC-SYS-001: 计时器卡片
    @Test
    fun systemCardsPage_showsCountdownCard() {
        content()
        rule.onNodeWithText(s(R.string.system_card_countdown)).assertIsDisplayed()
    }

    // TC-SYS-001: 打车卡片
    @Test
    fun systemCardsPage_showsCarHailingCard() {
        content()
        rule.onNodeWithText(s(R.string.system_card_car_hailing)).assertIsDisplayed()
    }

    // TC-SYS-001: 外卖卡片
    @Test
    fun systemCardsPage_showsFoodDeliveryCard() {
        content()
        rule.onNodeWithText(s(R.string.system_card_food_delivery)).assertIsDisplayed()
    }
}
