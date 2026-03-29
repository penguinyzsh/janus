package org.pysh.janus.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.pysh.janus.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * HomePage UI 测试 — 对应 TC-HOME-001~006
 */
@RunWith(AndroidJUnit4::class)
class HomePageTest {

    @get:Rule
    val rule = createComposeRule()

    private fun s(id: Int): String =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    // TC-HOME-001: 首页展示状态卡片（模块激活 + Root 正常）
    @Test
    fun homePage_displaysStatusCards() {
        rule.setContent {
            MiuixTheme { HomePage(bottomPadding = 0.dp, isModuleActive = true, hasRoot = true) }
        }
        rule.onNodeWithText(s(R.string.status_active)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.status_available)).assertIsDisplayed()
    }

    // 首页 — 模块未激活 + 无 Root
    @Test
    fun homePage_moduleNotActive_showsWarning() {
        rule.setContent {
            MiuixTheme { HomePage(bottomPadding = 0.dp, isModuleActive = false, hasRoot = false) }
        }
        rule.onNodeWithText(s(R.string.status_inactive)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.status_unavailable)).assertIsDisplayed()
    }

    // 首页 — Root 检测中
    @Test
    fun homePage_rootChecking_showsLoading() {
        rule.setContent {
            MiuixTheme { HomePage(bottomPadding = 0.dp, isModuleActive = true, hasRoot = null) }
        }
        rule.onNodeWithText(s(R.string.status_checking)).assertIsDisplayed()
    }
}
