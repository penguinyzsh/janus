package org.pysh.janus.ui

import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pysh.janus.R
import org.pysh.janus.data.MediaAppInfo
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * AppsPage UI 测试 — 对应 TC-APP 系列
 */
@RunWith(AndroidJUnit4::class)
class AppsPageTest {
    @get:Rule
    val rule = createComposeRule()

    private fun s(id: Int): String = ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    private val testIcon = ColorDrawable(0xFF000000.toInt())

    private val testApps =
        listOf(
            MediaAppInfo("com.netease.cloudmusic", "NetEase Music", testIcon, true),
            MediaAppInfo("com.android.settings", "Settings", testIcon, false),
            MediaAppInfo("com.tencent.mm", "WeChat", testIcon, false),
        )
    private val testMediaApps = testApps.filter { it.isMediaApp }

    private fun content(
        apps: List<MediaAppInfo> = testApps,
        media: List<MediaAppInfo> = testMediaApps,
        onAppClick: (MediaAppInfo) -> Unit = {},
    ) {
        rule.setContent {
            MiuixTheme {
                AppsPage(
                    bottomPadding = 0.dp,
                    whitelistVersion = 0,
                    allApps = apps,
                    mediaApps = media,
                    isRefreshing = false,
                    onRefresh = {},
                    onAppClick = onAppClick,
                )
            }
        }
    }

    // TC-APP-001: 页面标题
    @Test
    fun appsPage_displaysTitle() {
        content()
        rule.onNodeWithText(s(R.string.nav_apps)).assertIsDisplayed()
    }

    // TC-APP-001: 应用名展示
    @Test
    fun appsPage_displaysAppNames() {
        content()
        rule.onNodeWithText("NetEase Music").assertIsDisplayed()
        rule.onNodeWithText("Settings").assertIsDisplayed()
        rule.onNodeWithText("WeChat").assertIsDisplayed()
    }

    // TC-APP-001: 包名展示
    @Test
    fun appsPage_displaysPackageNames() {
        content()
        rule.onNodeWithText("com.netease.cloudmusic").assertIsDisplayed()
    }

    // TC-APP-002: 媒体标签展示
    @Test
    fun appsPage_displaysMediaTag() {
        content()
        rule.onNodeWithText(s(R.string.media_tag)).assertIsDisplayed()
    }

    // TC-APP-003: 搜索栏展示
    @Test
    fun appsPage_displaysSearchBar() {
        content()
        rule.onNodeWithText(s(R.string.search_apps)).assertExists()
    }

    // TC-APP-003: 搜索过滤按名称
    @Test
    fun appsPage_searchByName_filtersResults() {
        content()
        rule.onNodeWithText(s(R.string.search_apps)).performTextInput("NetEase")
        rule.onNodeWithText("NetEase Music").assertIsDisplayed()
        rule.onNodeWithText("Settings").assertDoesNotExist()
    }

    // TC-APP-004: 搜索过滤按包名
    @Test
    fun appsPage_searchByPackage_filtersResults() {
        content()
        rule.onNodeWithText(s(R.string.search_apps)).performTextInput("com.tencent")
        rule.onNodeWithText("WeChat").assertIsDisplayed()
        rule.onNodeWithText("NetEase Music").assertDoesNotExist()
    }

    // TC-APP-006: 空列表不崩溃
    @Test
    fun appsPage_emptyList_noCrash() {
        content(apps = emptyList(), media = emptyList())
        rule.onNodeWithText(s(R.string.nav_apps)).assertIsDisplayed()
    }

    // TC-APP-007: 点击应用触发回调
    @Test
    fun appsPage_clickApp_triggersCallback() {
        var clickedPkg = ""
        content(onAppClick = { clickedPkg = it.packageName })
        rule.onNodeWithText("NetEase Music").performClick()
        assertEquals("com.netease.cloudmusic", clickedPkg)
    }
}
