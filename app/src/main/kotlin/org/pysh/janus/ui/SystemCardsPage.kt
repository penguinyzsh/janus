package org.pysh.janus.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pysh.janus.R
import org.pysh.janus.data.CardManager
import org.pysh.janus.data.SystemCard
import org.pysh.janus.core.util.RootUtils
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    val cards = remember { SystemCard.entries.toList() }
    var overrides by remember {
        mutableStateOf(
            SystemCard.entries.associateWith { cardManager?.getSystemCardOverrideName(it) },
        )
    }
    var actionTarget by remember { mutableStateOf<SystemCard?>(null) }
    var pendingImport by remember { mutableStateOf<SystemCard?>(null) }
    var removeTarget by remember { mutableStateOf<SystemCard?>(null) }

    val picker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            val card = pendingImport
            pendingImport = null
            if (uri == null || card == null) return@rememberLauncherForActivityResult
            scope.launch {
                val name = withContext(Dispatchers.IO) { cardManager?.importSystemCardOverride(card, uri) }
                if (name != null) {
                    overrides = overrides.toMutableMap().apply { put(card, name) }
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
            }
        }

    val lazyGridState = rememberLazyGridState()
    val scrollBehavior = MiuixScrollBehavior()
    val title = stringResource(R.string.section_system_cards)

    Scaffold(
        topBar = {
            TopAppBar(
                title = title,
                largeTitle = title,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = lazyGridState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(top = innerPadding.calculateTopPadding())
                    .padding(horizontal = 4.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            items(cards, key = { it.business }) { card ->
                val overrideName = overrides[card]
                val isOverridden = overrideName != null

                val cardModifier =
                    Modifier
                        .padding(8.dp)
                        .aspectRatio(0.8f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MiuixTheme.colorScheme.secondaryContainer)
                        .let {
                            if (isOverridden) {
                                it.border(2.dp, MiuixTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                            } else {
                                it
                            }
                        }.clickable { actionTarget = card }

                Box(modifier = cardModifier, contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) {
                        Text(
                            text = stringResource(card.labelResId),
                            fontSize = 16.sp,
                            color = MiuixTheme.colorScheme.onSecondaryContainer,
                        )
                        if (overrideName != null) {
                            Text(
                                text = overrideName,
                                fontSize = 11.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                            )
                        }
                    }

                    if (isOverridden) {
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                                    .background(MiuixTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.theme_active_badge),
                                color = MiuixTheme.colorScheme.onPrimary,
                                style = MiuixTheme.textStyles.body2,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        }

        // Action dialog for a tapped system card
        val target = actionTarget
        SuperDialog(
            show = target != null,
            title = target?.let { stringResource(it.labelResId) } ?: "",
            onDismissRequest = { actionTarget = null },
        ) {
            val current = target ?: return@SuperDialog
            val currentOverride = overrides[current]
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                TextButton(
                    text = stringResource(R.string.music_override_title),
                    onClick = {
                        actionTarget = null
                        pendingImport = current
                        picker.launch(arrayOf("application/zip", "application/octet-stream"))
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                if (currentOverride != null) {
                    TextButton(
                        text = stringResource(R.string.music_override_remove),
                        onClick = {
                            removeTarget = current
                            actionTarget = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }

        // Remove confirmation
        val toRemove = removeTarget
        if (toRemove != null) {
            val removeLabel = stringResource(toRemove.labelResId)
            val removeName = overrides[toRemove] ?: ""
            SuperDialog(
                show = true,
                title = stringResource(R.string.music_override_remove),
                summary = stringResource(R.string.cards_delete_confirm, removeName),
                onDismissRequest = { removeTarget = null },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        text = stringResource(R.string.cancel),
                        onClick = { removeTarget = null },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = stringResource(R.string.confirm),
                        onClick = {
                            removeTarget = null
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    cardManager?.removeSystemCardOverride(toRemove)
                                    RootUtils.restartBackScreen()
                                }
                                overrides =
                                    overrides.toMutableMap().apply { put(toRemove, null) }
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
