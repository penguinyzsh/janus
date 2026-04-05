package org.pysh.janus.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pysh.janus.R
import org.pysh.janus.data.CardManager
import org.pysh.janus.data.SystemCard
import org.pysh.janus.util.RootUtils
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Preview(showBackground = true)
@Composable
private fun SystemCardsPagePreview() {
    MiuixTheme {
        SystemCardsPage(onBack = {})
    }
}

@Composable
fun SystemCardsPage(onBack: () -> Unit) {
    val isInPreview = LocalInspectionMode.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cardManager = remember { if (!isInPreview) CardManager(context) else null }

    // Music state
    var musicOverrideName by remember { mutableStateOf(cardManager?.getMusicOverrideName()) }
    var showMusicRemoveDialog by remember { mutableStateOf(false) }

    // Non-music system cards state
    var systemCardOverrides by remember {
        mutableStateOf(
            SystemCard.nonMusic.associateWith { cardManager?.getSystemCardOverrideName(it) },
        )
    }
    var pendingSystemCard by remember { mutableStateOf<SystemCard?>(null) }
    var showSystemCardRemoveDialog by remember { mutableStateOf<SystemCard?>(null) }

    // File pickers
    val musicPicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val name = withContext(Dispatchers.IO) { cardManager?.importMusicOverride(uri) }
                if (name != null) {
                    musicOverrideName = name
                    Toast.makeText(context, context.getString(R.string.music_override_import_success, name), Toast.LENGTH_SHORT).show()
                    withContext(Dispatchers.IO) { RootUtils.restartBackScreen() }
                } else {
                    Toast.makeText(context, context.getString(R.string.music_override_import_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }

    val systemCardPicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val card = pendingSystemCard ?: return@rememberLauncherForActivityResult
            scope.launch {
                val name = withContext(Dispatchers.IO) { cardManager?.importSystemCardOverride(card, uri) }
                if (name != null) {
                    systemCardOverrides = systemCardOverrides.toMutableMap().apply { put(card, name) }
                    val label = context.getString(card.labelResId)
                    Toast
                        .makeText(
                            context,
                            context.getString(R.string.system_card_override_import_success, label, name),
                            Toast.LENGTH_SHORT,
                        ).show()
                    withContext(Dispatchers.IO) { RootUtils.restartBackScreen() }
                } else {
                    Toast.makeText(context, context.getString(R.string.music_override_import_failed), Toast.LENGTH_SHORT).show()
                }
                pendingSystemCard = null
            }
        }

    val scrollBehavior = MiuixScrollBehavior()
    val title = stringResource(R.string.section_system_cards)

    Scaffold(
        topBar = {
            TopAppBar(
                title = title,
                largeTitle = title,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    top.yukonga.miuix.kmp.basic.IconButton(onClick = onBack) {
                        top.yukonga.miuix.kmp.basic.Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
            contentPadding =
                PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = 12.dp,
                ),
            overscrollEffect = null,
        ) {
            // Music section
            item(key = "music_section_title") {
                SmallTitle(text = stringResource(R.string.section_music))
            }
            item(key = "music_override") {
                Card(modifier = Modifier.padding(bottom = if (musicOverrideName != null) 0.dp else 12.dp)) {
                    SuperArrow(
                        title = stringResource(R.string.music_override_title),
                        summary =
                            if (musicOverrideName != null) {
                                stringResource(R.string.music_override_set, musicOverrideName!!)
                            } else {
                                stringResource(R.string.music_override_none)
                            },
                        onClick = {
                            musicPicker.launch(arrayOf("application/zip", "application/octet-stream"))
                        },
                    )
                    if (musicOverrideName != null) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = 12.dp),
                        ) {
                            TextButton(
                                text = stringResource(R.string.music_override_remove),
                                onClick = { showMusicRemoveDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            // Other system cards section
            item(key = "other_system_cards_title") {
                SmallTitle(text = stringResource(R.string.section_other_system_cards))
            }
            item(key = "system_card_overrides") {
                Card(modifier = Modifier.padding(bottom = 12.dp)) {
                    SystemCard.nonMusic.forEach { card ->
                        val overrideName = systemCardOverrides[card]
                        SuperArrow(
                            title = stringResource(card.labelResId),
                            summary =
                                if (overrideName != null) {
                                    stringResource(R.string.music_override_set, overrideName)
                                } else {
                                    stringResource(R.string.music_override_none)
                                },
                            onClick = {
                                pendingSystemCard = card
                                systemCardPicker.launch(arrayOf("application/zip", "application/octet-stream"))
                            },
                        )
                        if (overrideName != null) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp)
                                        .padding(bottom = 12.dp),
                            ) {
                                TextButton(
                                    text = stringResource(R.string.music_override_remove),
                                    onClick = { showSystemCardRemoveDialog = card },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Music remove dialog
        SuperDialog(
            show = showMusicRemoveDialog,
            title = stringResource(R.string.music_override_remove),
            summary = stringResource(R.string.cards_delete_confirm, musicOverrideName ?: ""),
            onDismissRequest = { showMusicRemoveDialog = false },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showMusicRemoveDialog = false },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.confirm),
                    onClick = {
                        showMusicRemoveDialog = false
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                cardManager?.removeMusicOverride()
                                RootUtils.restartBackScreen()
                            }
                            musicOverrideName = null
                            Toast.makeText(context, context.getString(R.string.music_override_removed), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }

        // System card remove dialog
        val systemCardToRemove = showSystemCardRemoveDialog
        if (systemCardToRemove != null) {
            val removeLabel = stringResource(systemCardToRemove.labelResId)
            val removeName = systemCardOverrides[systemCardToRemove] ?: ""
            SuperDialog(
                show = true,
                title = stringResource(R.string.music_override_remove),
                summary = stringResource(R.string.cards_delete_confirm, removeName),
                onDismissRequest = { showSystemCardRemoveDialog = null },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        text = stringResource(R.string.cancel),
                        onClick = { showSystemCardRemoveDialog = null },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = stringResource(R.string.confirm),
                        onClick = {
                            showSystemCardRemoveDialog = null
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    cardManager?.removeSystemCardOverride(systemCardToRemove)
                                    RootUtils.restartBackScreen()
                                }
                                systemCardOverrides =
                                    systemCardOverrides.toMutableMap().apply {
                                        put(systemCardToRemove, null)
                                    }
                                Toast
                                    .makeText(
                                        context,
                                        context.getString(R.string.system_card_override_removed, removeLabel),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }
}
