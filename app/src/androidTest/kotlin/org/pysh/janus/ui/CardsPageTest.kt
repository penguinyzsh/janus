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
 * CardsPage UI 测试 — 对应 TC-CARD 系列
 */
@RunWith(AndroidJUnit4::class)
class CardsPageTest {
    @get:Rule
    val rule = createComposeRule()

    private fun s(id: Int): String = ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    // TC-CARD-001: 主开关展示
    @Test
    fun cardsPage_showsMasterToggle() {
        rule.setContent {
            MiuixTheme { CardsPage(bottomPadding = 0.dp, onCardClick = {}, onSystemCardsClick = {}) }
        }
        rule.onNodeWithText(s(R.string.cards_master_toggle)).assertIsDisplayed()
    }

    // TC-CARD-001: 默认关闭摘要
    @Test
    fun cardsPage_defaultMasterOffSummary() {
        rule.setContent {
            MiuixTheme { CardsPage(bottomPadding = 0.dp, onCardClick = {}, onSystemCardsClick = {}) }
        }
        rule.onNodeWithText(s(R.string.cards_master_off)).assertIsDisplayed()
    }

    // TC-SYS-001: 系统卡片入口展示
    @Test
    fun cardsPage_showsSystemCardsEntry() {
        rule.setContent {
            MiuixTheme { CardsPage(bottomPadding = 0.dp, onCardClick = {}, onSystemCardsClick = {}) }
        }
        rule.onNodeWithText(s(R.string.section_system_cards)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.system_cards_summary)).assertIsDisplayed()
    }

    // TC-CARD-003: 导入卡片区域标题
    @Test
    fun cardsPage_showsSectionTitle() {
        rule.setContent {
            MiuixTheme { CardsPage(bottomPadding = 0.dp, onCardClick = {}, onSystemCardsClick = {}) }
        }
        rule.onNodeWithText(s(R.string.section_cards)).assertIsDisplayed()
    }

    // TC-CARD-003: 空状态提示
    @Test
    fun cardsPage_showsEmptyState() {
        rule.setContent {
            MiuixTheme { CardsPage(bottomPadding = 0.dp, onCardClick = {}, onSystemCardsClick = {}) }
        }
        rule.onNodeWithText(s(R.string.cards_empty)).assertIsDisplayed()
    }

    // TC-CARD-004: 导入按钮展示
    @Test
    fun cardsPage_showsImportButton() {
        rule.setContent {
            MiuixTheme { CardsPage(bottomPadding = 0.dp, onCardClick = {}, onSystemCardsClick = {}) }
        }
        rule.onNodeWithText(s(R.string.cards_import)).assertIsDisplayed()
    }

    // TC-SYS-001: 点击系统卡片触发回调
    @Test
    fun cardsPage_clickSystemCards_triggersCallback() {
        var clicked = false
        rule.setContent {
            MiuixTheme {
                CardsPage(
                    bottomPadding = 0.dp,
                    onCardClick = {},
                    onSystemCardsClick = { clicked = true },
                )
            }
        }
        rule.onNodeWithText(s(R.string.section_system_cards)).performClick()
        assertTrue(clicked)
    }
}
