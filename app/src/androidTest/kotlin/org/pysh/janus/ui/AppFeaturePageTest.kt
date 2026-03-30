package org.pysh.janus.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.pysh.janus.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * AppFeaturePage UI 测试 — 对应 TC-APP-008~011
 *
 * AppFeaturePage 接收 packageName: String，内部通过 PackageManager 获取应用信息。
 * 测试使用 Settings 包名（系统必定存在）作为测试数据。
 */
@RunWith(AndroidJUnit4::class)
class AppFeaturePageTest {

    @get:Rule
    val rule = createComposeRule()

    private fun s(id: Int): String =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    /** 使用系统 Settings 作为测试包名（任何设备上都存在） */
    private val testPackageName = "com.android.settings"

    // TC-APP-008: 详情页标题为"功能"
    @Test
    fun appFeaturePage_showsTitle() {
        rule.setContent {
            MiuixTheme { AppFeaturePage(packageName = testPackageName, onBack = {}) }
        }
        rule.onNodeWithText(s(R.string.app_feature)).assertIsDisplayed()
    }

    // TC-APP-008: 详情页显示包名
    @Test
    fun appFeaturePage_showsPackageName() {
        rule.setContent {
            MiuixTheme { AppFeaturePage(packageName = testPackageName, onBack = {}) }
        }
        rule.onNodeWithText(testPackageName).assertIsDisplayed()
    }

    // TC-APP-009/010: 白名单开关展示
    @Test
    fun appFeaturePage_showsWhitelistSwitch() {
        rule.setContent {
            MiuixTheme { AppFeaturePage(packageName = testPackageName, onBack = {}) }
        }
        rule.onNodeWithText(s(R.string.add_music_whitelist)).assertIsDisplayed()
    }

    // TC-APP-011: 返回按钮触发回调
    @Test
    fun appFeaturePage_backButton_callsCallback() {
        var backCalled = false
        rule.setContent {
            MiuixTheme {
                AppFeaturePage(packageName = testPackageName, onBack = { backCalled = true })
            }
        }
        rule.onNodeWithContentDescription(s(R.string.back)).performClick()
        assertTrue(backCalled)
    }

    // TC-APP-014: 歌词规则区域标题展示
    @Test
    fun appFeaturePage_showsLyricRulesSection() {
        rule.setContent {
            MiuixTheme { AppFeaturePage(packageName = testPackageName, onBack = {}) }
        }
        rule.onNodeWithText(s(R.string.app_lyric_rules)).assertIsDisplayed()
    }

    // TC-APP-014: 歌词规则导入按钮展示
    @Test
    fun appFeaturePage_showsLyricRulesImportButton() {
        rule.setContent {
            MiuixTheme { AppFeaturePage(packageName = testPackageName, onBack = {}) }
        }
        rule.onNodeWithText(s(R.string.app_lyric_rules_import)).assertIsDisplayed()
    }

    // TC-APP-015: 无歌词规则时显示空提示
    @Test
    fun appFeaturePage_showsEmptyLyricRulesHint() {
        rule.setContent {
            MiuixTheme { AppFeaturePage(packageName = testPackageName, onBack = {}) }
        }
        rule.onNodeWithText(s(R.string.app_lyric_rules_empty)).assertIsDisplayed()
    }
}
