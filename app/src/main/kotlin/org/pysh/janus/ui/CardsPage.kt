package org.pysh.janus.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pysh.janus.R
import org.pysh.janus.data.CardInfo
import org.pysh.janus.data.CardManager
import org.pysh.janus.util.RootUtils
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Preview(showBackground = true)
@Composable
private fun CardsPagePreview() {
    MiuixTheme {
        CardsPage(bottomPadding = 0.dp, onCardClick = {})
    }
}

@Composable
fun CardsPage(
    bottomPadding: Dp,
    cardsVersion: Int = 0,
    onCardClick: (Int) -> Unit,
) {
    val isInPreview = LocalInspectionMode.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cardManager = remember { if (!isInPreview) CardManager(context) else null }

    var masterEnabled by remember { mutableStateOf(cardManager?.isMasterEnabled() ?: false) }
    var cards by remember { mutableStateOf(cardManager?.getCards()?.sortedBy { it.sortOrder } ?: emptyList()) }

    // Re-read cards when returning from CardDetailPage (version bumped)
    LaunchedEffect(cardsVersion) {
        if (cardsVersion > 0) {
            cards = cardManager?.getCards()?.sortedBy { it.sortOrder } ?: emptyList()
        }
    }

    val cardPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) { cardManager?.addCard(uri) }
            if (result != null) {
                cards = cardManager?.getCards()?.sortedBy { it.sortOrder } ?: emptyList()
                Toast.makeText(context, context.getString(R.string.cards_import_success, result.name), Toast.LENGTH_SHORT).show()
            } else {
                if (cardManager?.getNextAvailableSlot() == null) {
                    Toast.makeText(context, context.getString(R.string.cards_no_slot), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, context.getString(R.string.cards_import_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val scrollBehavior = MiuixScrollBehavior()
    val title = stringResource(R.string.nav_cards)

    val lazyListState = rememberLazyListState()
    // Card items start after 2 fixed items (master toggle card + section title)
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromCardIndex = from.index - 2
        val toCardIndex = to.index - 2
        if (fromCardIndex in cards.indices && toCardIndex in cards.indices) {
            cards = cards.toMutableList().apply {
                add(toCardIndex, removeAt(fromCardIndex))
            }
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
            state = lazyListState,
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
            // Master toggle
            item(key = "master_toggle") {
                Card(modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)) {
                    SuperSwitch(
                        title = stringResource(R.string.cards_master_toggle),
                        summary = stringResource(if (masterEnabled) R.string.cards_master_on else R.string.cards_master_off),
                        checked = masterEnabled,
                        onCheckedChange = {
                            masterEnabled = it
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    cardManager?.setMasterEnabled(it)
                                    cardManager?.syncConfig()
                                    RootUtils.restartBackScreen()
                                }
                                Toast.makeText(context, context.getString(if (it) R.string.enabled else R.string.disabled), Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
            }

            // Section title
            item(key = "section_title") {
                SmallTitle(text = stringResource(R.string.section_cards))
            }

            // Card list
            if (cards.isEmpty()) {
                item(key = "empty_state") {
                    Card(modifier = Modifier.padding(bottom = 8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.cards_empty),
                                style = MiuixTheme.textStyles.headline2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            )
                        }
                    }
                }
            } else {
                items(cards, key = { it.slot }) { card ->
                    ReorderableItem(reorderableState, key = card.slot) { isDragging ->
                        val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "drag_elevation")
                        Card(
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .shadow(elevation),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onCardClick(card.slot) }
                                    .padding(horizontal = 12.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Drag handle
                                Icon(
                                    imageVector = MiuixIcons.More,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .draggableHandle(),
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                )
                                Spacer(modifier = Modifier.width(12.dp))

                                // Card info
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = card.name,
                                        style = MiuixTheme.textStyles.headline2,
                                        color = MiuixTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = stringResource(R.string.card_summary, card.priority, card.refreshInterval),
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))

                                // Enable/disable switch
                                Switch(
                                    checked = card.enabled,
                                    onCheckedChange = { enabled ->
                                        val updated = card.copy(enabled = enabled)
                                        cards = cards.map { if (it.slot == card.slot) updated else it }
                                        scope.launch {
                                            withContext(Dispatchers.IO) { cardManager?.updateCard(updated) }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // Import button
            item(key = "import_button") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        text = stringResource(R.string.cards_import),
                        onClick = { cardPicker.launch(arrayOf("application/zip", "application/octet-stream")) },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = stringResource(R.string.cards_save_restart),
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    cardManager?.reorderCards(cards)
                                    cardManager?.syncConfig()
                                    cardManager?.prepareCardsForHook()
                                    RootUtils.restartBackScreen()
                                }
                                Toast.makeText(context, context.getString(R.string.enabled), Toast.LENGTH_SHORT).show()
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
