package org.pysh.janus.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.pysh.janus.R
import org.pysh.janus.core.model.CardInfo
import org.pysh.janus.data.CardManager
import org.pysh.janus.data.HookStatusManager
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Preview(showBackground = true)
@Composable
private fun HomePagePreview() {
    MiuixTheme {
        HomePage(bottomPadding = 0.dp, isModuleActive = true, hasRoot = true)
    }
}

@Composable
fun HomePage(
    bottomPadding: Dp,
    isModuleActive: Boolean,
    hasRoot: Boolean?,
    isVisible: Boolean = true,
    showUpdateDialog: Boolean = false,
    onDismissUpdateDialog: () -> Unit = {},
) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    val title = stringResource(R.string.nav_home)

    val isPreview = LocalInspectionMode.current
    val hookStatusManager = remember { HookStatusManager(context) }
    val cardManager = remember { CardManager(context) }
    var cards by remember { mutableStateOf(if (!isPreview) cardManager.getCards() else emptyList<CardInfo>()) }
    if (!isPreview) {
        DisposableEffect(hookStatusManager) {
            hookStatusManager.register()
            onDispose { hookStatusManager.unregister() }
        }
        DisposableEffect(cardManager) {
            val unregister = cardManager.observeCards { cards = cardManager.getCards() }
            onDispose { unregister() }
        }
        LaunchedEffect(isModuleActive, isVisible) {
            if (isModuleActive && isVisible) hookStatusManager.query()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = title,
                largeTitle = title,
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = bottomPadding,
            ),
            overscrollEffect = null,
        ) {
            item {
                StatusCard(
                    isModuleActive = isModuleActive,
                    hasRoot = hasRoot,
                    modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
                )
            }

            if (isModuleActive) {
                item {
                    HookStatusSection(
                        statuses = hookStatusManager.statuses,
                        isQuerying = hookStatusManager.isQuerying,
                        onRefresh = { hookStatusManager.query() },
                        cardNames = cards.map { it.name },
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://qm.qq.com/q/UJBp9bNnIQ"))
                        )
                    },
                    onLongPress = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("qq_group", "119655862"))
                        Toast.makeText(context, context.getString(R.string.qq_group_copied), Toast.LENGTH_SHORT).show()
                    },
                ) {
                    BasicComponent(
                        title = stringResource(R.string.qq_group),
                        summary = stringResource(R.string.about_qq_group_number),
                    )
                }
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    BasicComponent(
                        title = stringResource(R.string.support),
                        summary = stringResource(R.string.about_afdian),
                        endActions = {
                            Icon(
                                imageVector = MiuixIcons.Link,
                                tint = MiuixTheme.colorScheme.onSurface,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://ifdian.net/a/janus"))
                            )
                        },
                    )
                }
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    BasicComponent(
                        title = stringResource(R.string.project),
                        summary = stringResource(R.string.about_github),
                        endActions = {
                            Icon(
                                imageVector = MiuixIcons.Link,
                                tint = MiuixTheme.colorScheme.onSurface,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/penguinyzsh/janus"))
                            )
                        },
                    )
                }
            }
        }
    }

    SuperDialog(
        show = showUpdateDialog,
        title = stringResource(R.string.update_title, org.pysh.janus.BuildConfig.VERSION_NAME),
        summary = stringResource(R.string.update_changelog),
        onDismissRequest = onDismissUpdateDialog,
    ) {
        val qqGroupCopiedText = stringResource(R.string.qq_group_copied)
        Card(modifier = Modifier.padding(bottom = 12.dp)) {
            @OptIn(ExperimentalFoundationApi::class)
            Box(
                modifier = Modifier.combinedClickable(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://qm.qq.com/q/UJBp9bNnIQ"))
                        )
                    },
                    onLongClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("qq_group", "119655862"))
                        Toast.makeText(context, qqGroupCopiedText, Toast.LENGTH_SHORT).show()
                    },
                ),
            ) {
                SuperArrow(
                    title = stringResource(R.string.update_join_group),
                    endActions = {
                        Text(
                            text = stringResource(R.string.about_qq_group_number),
                            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                        )
                    },
                )
            }
        }
        Card(modifier = Modifier.padding(bottom = 12.dp)) {
            SuperArrow(
                title = stringResource(R.string.update_support),
                endActions = {
                    Text(
                        text = "ifdian.net/a/janus",
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    )
                },
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://ifdian.net/a/janus"))
                    )
                },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            TextButton(
                text = stringResource(R.string.update_dismiss),
                onClick = onDismissUpdateDialog,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

// ── Hook Status Section ────────────────────────────────────────

/** Display order for hooks within a process. Unknown hooks sort last. */
private val HOOK_ORDER = listOf(
    "music_whitelist", "tracking_block",
    "hide_time_tip",
    "wallpaper_keep_alive", "wallpaper_lock", "wallpaper_redirect",
    "music_template", "system_card_patch", "card_injection",
    "apple_music_lyric",
    "luna_music_manager", "luna_music_remote",
)

@Composable
private fun HookStatusSection(
    statuses: Map<String, Map<String, HookStatusManager.HookStatus>>,
    isQuerying: Boolean,
    onRefresh: () -> Unit,
    cardNames: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (statuses.isEmpty() && !isQuerying) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.hook_status_empty),
                    modifier = Modifier.padding(16.dp),
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
            }
        } else {
            for ((process, hooks) in statuses) {
                ProcessInfoCard(process = process, hooks = hooks, cardNames = cardNames)
            }
        }
    }
}

@Composable
private fun ProcessInfoCard(
    process: String,
    hooks: Map<String, HookStatusManager.HookStatus>,
    cardNames: List<String>,
) {
    val sortedHooks = hooks.entries.sortedBy { (name, _) ->
        val idx = HOOK_ORDER.indexOf(name)
        if (idx >= 0) idx else Int.MAX_VALUE
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            for ((index, entry) in sortedHooks.withIndex()) {
                val (hookName, _) = entry
                val isLast = index == sortedHooks.size - 1
                val contentText = if (hookName == "card_injection" && cardNames.isNotEmpty()) {
                    cardNames.joinToString("、")
                } else {
                    hookTargetName(hookName)
                }
                Text(
                    text = hookDisplayName(hookName),
                    fontSize = MiuixTheme.textStyles.headline1.fontSize,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = contentText,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = if (isLast) 0.dp else 24.dp),
                )
            }
        }
    }
}

private fun hookTargetName(hookName: String): String = when (hookName) {
    "music_whitelist" -> "p2.a"
    "hide_time_tip" -> "m2.a"
    "tracking_block" -> "DailyTrackReceiver"
    "wallpaper_keep_alive" -> "SubScreenLauncher.onPause"
    "wallpaper_lock" -> "Z1.t.e"
    "wallpaper_redirect" -> "m2.a.d"
    "music_template" -> "Z1.d0"
    "system_card_patch" -> "p2.a.d"
    "card_injection" -> "p2.c"
    "apple_music_lyric" -> "TTMLParserNative"
    "luna_music_manager" -> "BlueToothLyricsManager"
    "luna_music_remote" -> "RemoteControlContext.a"
    else -> hookName
}

@Composable
private fun hookDisplayName(hookName: String): String = when (hookName) {
    "music_whitelist" -> stringResource(R.string.hook_music_whitelist)
    "hide_time_tip" -> stringResource(R.string.hook_hide_time_tip)
    "tracking_block" -> stringResource(R.string.hook_tracking_block)
    "wallpaper_keep_alive" -> stringResource(R.string.hook_wallpaper_keep_alive)
    "wallpaper_lock" -> stringResource(R.string.hook_wallpaper_lock)
    "wallpaper_redirect" -> stringResource(R.string.hook_wallpaper_redirect)
    "music_template" -> stringResource(R.string.hook_music_template)
    "system_card_patch" -> stringResource(R.string.hook_system_card_patch)
    "card_injection" -> stringResource(R.string.hook_card_injection)
    "apple_music_lyric" -> stringResource(R.string.hook_apple_music_lyric)
    "luna_music_manager" -> stringResource(R.string.hook_luna_music_manager)
    "luna_music_remote" -> stringResource(R.string.hook_luna_music_remote)
    else -> hookName
}

@Composable
private fun processDisplayName(process: String): String = when (process) {
    "com.xiaomi.subscreencenter" -> stringResource(R.string.process_subscreencenter)
    "com.apple.android.music" -> "Apple Music"
    "com.luna.music" -> stringResource(R.string.process_luna_music)
    else -> process
}
