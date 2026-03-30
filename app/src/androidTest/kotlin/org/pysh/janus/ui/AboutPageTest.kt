package org.pysh.janus.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pysh.janus.R
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * AboutPage UI 测试 — 对应 TC-SET-007~013
 */
@RunWith(AndroidJUnit4::class)
class AboutPageTest {

    @get:Rule
    val rule = createComposeRule()

    private fun s(id: Int): String =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    private fun content() {
        rule.setContent { MiuixTheme { AboutPage(onBack = {}) } }
    }

    // TC-SET-007: 关于页面标题
    @Test
    fun aboutPage_showsTitle() {
        content()
        rule.onNodeWithText(s(R.string.about)).assertIsDisplayed()
    }

    // TC-SET-007: 应用名
    @Test
    fun aboutPage_showsAppName() {
        content()
        rule.onNodeWithText(s(R.string.app_name)).assertIsDisplayed()
    }

    // TC-SET-008: 项目地址
    @Test
    fun aboutPage_showsProjectLink() {
        content()
        rule.onNodeWithText(s(R.string.project)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.about_github)).assertIsDisplayed()
    }

    // TC-SET-008: 作者主页
    @Test
    fun aboutPage_showsAuthorLink() {
        content()
        rule.onNodeWithText(s(R.string.author)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.about_author_name)).assertIsDisplayed()
    }

    // TC-SET-010a: 赞助支持
    @Test
    fun aboutPage_showsSupportLink() {
        content()
        rule.onNodeWithText(s(R.string.support)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.about_afdian)).assertIsDisplayed()
    }

    // TC-SET-011: QQ 群
    @Test
    fun aboutPage_showsQqGroup() {
        content()
        rule.onNodeWithText(s(R.string.qq_group)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.about_qq_group_number)).assertIsDisplayed()
    }

    // TC-SET-013: 返回按钮回调
    @Test
    fun aboutPage_clickBack_callsCallback() {
        var backClicked = false
        rule.setContent { MiuixTheme { AboutPage(onBack = { backClicked = true }) } }
        rule.onNodeWithContentDescription(s(R.string.back)).performClick()
        assertTrue(backClicked)
    }
}
