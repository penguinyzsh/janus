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
 * HomePage UI 测试 — 对应 TC-HOME 系列
 */
@RunWith(AndroidJUnit4::class)
class HomePageTest {

    @get:Rule
    val rule = createComposeRule()

    private fun s(id: Int): String =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    private fun s(id: Int, vararg args: Any): String =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(id, *args)

    // TC-HOME-001: 首页展示状态卡片（模块激活 + Root 正常）
    @Test
    fun homePage_displaysStatusCards() {
        rule.setContent {
            MiuixTheme { HomePage(bottomPadding = 0.dp, isModuleActive = true, hasRoot = true) }
        }
        rule.onNodeWithText(s(R.string.status_active)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.status_available)).assertIsDisplayed()
    }

    // TC-HOME-003: 模块未激活 + 无 Root
    @Test
    fun homePage_moduleNotActive_showsWarning() {
        rule.setContent {
            MiuixTheme { HomePage(bottomPadding = 0.dp, isModuleActive = false, hasRoot = false) }
        }
        rule.onNodeWithText(s(R.string.status_inactive)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.status_unavailable)).assertIsDisplayed()
    }

    // TC-HOME-006: Root 检测中
    @Test
    fun homePage_rootChecking_showsLoading() {
        rule.setContent {
            MiuixTheme { HomePage(bottomPadding = 0.dp, isModuleActive = true, hasRoot = null) }
        }
        rule.onNodeWithText(s(R.string.status_checking)).assertIsDisplayed()
    }

    // TC-HOME-012: 未激活不显示 Hook 状态
    @Test
    fun homePage_moduleInactive_noHookStatus() {
        rule.setContent {
            MiuixTheme { HomePage(bottomPadding = 0.dp, isModuleActive = false, hasRoot = true) }
        }
        rule.onNodeWithText(s(R.string.hook_status_title)).assertDoesNotExist()
    }

    // TC-HOME-012: 激活后显示 Hook 状态标题
    @Test
    fun homePage_moduleActive_showsHookStatusTitle() {
        rule.setContent {
            MiuixTheme { HomePage(bottomPadding = 0.dp, isModuleActive = true, hasRoot = true) }
        }
        rule.onNodeWithText(s(R.string.hook_status_title)).assertIsDisplayed()
    }

    // TC-HOME-007: 更新通告弹窗展示
    @Test
    fun homePage_showUpdateDialog() {
        rule.setContent {
            MiuixTheme {
                HomePage(
                    bottomPadding = 0.dp,
                    isModuleActive = true,
                    hasRoot = true,
                    showUpdateDialog = true,
                    onDismissUpdateDialog = {},
                )
            }
        }
        rule.onNodeWithText(s(R.string.update_dismiss)).assertIsDisplayed()
    }

    // TC-HOME-008: 点击"知道了"关闭弹窗
    @Test
    fun homePage_dismissUpdateDialog() {
        var dismissed = false
        rule.setContent {
            MiuixTheme {
                HomePage(
                    bottomPadding = 0.dp,
                    isModuleActive = true,
                    hasRoot = true,
                    showUpdateDialog = true,
                    onDismissUpdateDialog = { dismissed = true },
                )
            }
        }
        rule.onNodeWithText(s(R.string.update_dismiss)).performClick()
        assertTrue(dismissed)
    }

    // TC-HOME-007: 更新弹窗包含 QQ 群入口
    @Test
    fun homePage_updateDialog_showsQqGroup() {
        rule.setContent {
            MiuixTheme {
                HomePage(
                    bottomPadding = 0.dp,
                    isModuleActive = true,
                    hasRoot = true,
                    showUpdateDialog = true,
                    onDismissUpdateDialog = {},
                )
            }
        }
        rule.onNodeWithText(s(R.string.update_join_group)).assertIsDisplayed()
    }

    // TC-HOME-007: 更新弹窗包含赞助入口
    @Test
    fun homePage_updateDialog_showsSupport() {
        rule.setContent {
            MiuixTheme {
                HomePage(
                    bottomPadding = 0.dp,
                    isModuleActive = true,
                    hasRoot = true,
                    showUpdateDialog = true,
                    onDismissUpdateDialog = {},
                )
            }
        }
        rule.onNodeWithText(s(R.string.update_support)).assertIsDisplayed()
    }

    // TC-HOME-017: 弹窗关闭后不显示弹窗内容
    @Test
    fun homePage_noUpdateDialog_byDefault() {
        rule.setContent {
            MiuixTheme {
                HomePage(bottomPadding = 0.dp, isModuleActive = true, hasRoot = true)
            }
        }
        rule.onNodeWithText(s(R.string.update_dismiss)).assertDoesNotExist()
    }
}
