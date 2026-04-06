package org.pysh.janus.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pysh.janus.R
import org.pysh.janus.data.CardManager
import org.pysh.janus.core.util.RootUtils
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Preview(showBackground = true)
@Composable
private fun CardsPagePreview() {
    MiuixTheme {
        CardsPage(bottomPadding = 0.dp, onBack = {}, onCardClick = {}, onSystemCardsClick = {})
    }
}

@Composable
fun CardsPage(
    bottomPadding: Dp,
    cardsVersion: Int = 0,
    onBack: () -> Unit,
    onCardClick: (Int) -> Unit,
    onSystemCardsClick: () -> Unit,
    onCardsChanged: () -> Unit = {},
) {
    val isInPreview = LocalInspectionMode.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cardManager = remember { if (!isInPreview) CardManager(context) else null }

    var cards by remember { mutableStateOf(cardManager?.getCards()?.sortedByDescending { it.priority } ?: emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    var actionTargetSlot by remember { mutableStateOf<Int?>(null) }

    // Keep master flag synced with list non-emptiness
    LaunchedEffect(cards.size) {
        if (cardManager == null) return@LaunchedEffect
        val shouldBeOn = cards.isNotEmpty()
        if (cardManager.isMasterEnabled() != shouldBeOn) {
            withContext(Dispatchers.IO) {
                cardManager.setMasterEnabled(shouldBeOn)
                cardManager.syncConfig()
                RootUtils.restartBackScreen()
            }
        }
    }

    // Re-read cards when returning from CardDetailPage (version bumped)
    LaunchedEffect(cardsVersion) {
        if (cardsVersion > 0) {
            cards = cardManager?.getCards()?.sortedByDescending { it.priority } ?: emptyList()
        }
    }

    val cardPicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val result = withContext(Dispatchers.IO) { cardManager?.addCard(uri) }
                if (result != null) {
                    cards = cardManager?.getCards()?.sortedByDescending { it.priority } ?: emptyList()
                    onCardsChanged()
                    withContext(Dispatchers.IO) {
                        cardManager?.syncConfig()
                        cardManager?.prepareCardsForHook()
                        RootUtils.restartBackScreen()
                    }
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

    val lazyGridState = rememberLazyGridState()
    val reorderableState =
        rememberReorderableLazyGridState(lazyGridState) { from, to ->
            val fromSlot = from.key as? Int ?: return@rememberReorderableLazyGridState
            val toSlot = to.key as? Int ?: return@rememberReorderableLazyGridState
            val mutableCards = cards.toMutableList()
            val fromIndex = mutableCards.indexOfFirst { it.slot == fromSlot }
            val toIndex = mutableCards.indexOfFirst { it.slot == toSlot }
            if (fromIndex != -1 && toIndex != -1) {
                mutableCards.add(toIndex, mutableCards.removeAt(fromIndex))
                cards = mutableCards
            }
        }

    val scrollBehavior = MiuixScrollBehavior()
    val title = stringResource(R.string.nav_cards)

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
                actions = {
                    IconButton(
                        onClick = {
                            if (!isProcessing) {
                                cardPicker.launch(arrayOf("application/zip", "application/octet-stream"))
                            }
                        },
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Add,
                            contentDescription = stringResource(R.string.cards_import),
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
            contentPadding = PaddingValues(bottom = bottomPadding + 12.dp),
        ) {
            items(cards, key = { it.slot }) { card ->
                ReorderableItem(reorderableState, key = card.slot) { isDragging ->
                    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "drag_elev")

                    val cardModifier =
                        Modifier
                            .padding(8.dp)
                            .aspectRatio(0.8f)
                            .shadow(elevation, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .background(MiuixTheme.colorScheme.surface)
                            .let {
                                if (card.enabled) {
                                    it.border(2.dp, MiuixTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                                } else {
                                    it
                                }
                            }.longPressDraggableHandle(
                                onDragStopped = {
                                    val reordered = cards.toList()
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            cardManager?.reorderCards(reordered)
                                            cardManager?.syncConfig()
                                            cardManager?.prepareCardsForHook()
                                            RootUtils.restartBackScreen()
                                        }
                                        cards = cardManager?.getCards()?.sortedByDescending { it.priority } ?: emptyList()
                                    }
                                },
                            ).clickable(enabled = !isProcessing) {
                                actionTargetSlot = card.slot
                            }

                    Box(
                        modifier = cardModifier.background(MiuixTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = card.name,
                            fontSize = 16.sp,
                            color = MiuixTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(8.dp),
                        )

                        if (card.enabled) {
                            Box(
                                modifier =
                                    Modifier
                                        .align(Alignment.TopStart)
                                        .padding(8.dp)
                                        .background(MiuixTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.enabled),
                                    color = MiuixTheme.colorScheme.onPrimary,
                                    style = MiuixTheme.textStyles.body2,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            }

            // System cards navigation entry (bottom, span 2)
            item(key = "system_cards_entry", span = { GridItemSpan(2) }) {
                Card(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    SuperArrow(
                        title = stringResource(R.string.section_system_cards),
                        summary = stringResource(R.string.system_cards_summary),
                        onClick = onSystemCardsClick,
                    )
                }
            }
        }

        // Action dialog
        val actionTarget = cards.find { it.slot == actionTargetSlot }
        SuperDialog(
            show = actionTarget != null,
            title = actionTarget?.name ?: "",
            onDismissRequest = { actionTargetSlot = null },
        ) {
            val target = actionTarget ?: return@SuperDialog
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                TextButton(
                    text = stringResource(if (target.enabled) R.string.disabled else R.string.enabled),
                    onClick = {
                        actionTargetSlot = null
                        val updated = target.copy(enabled = !target.enabled)
                        cards = cards.map { if (it.slot == target.slot) updated else it }
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                cardManager?.updateCard(updated)
                                cardManager?.syncConfig()
                                cardManager?.prepareCardsForHook()
                                RootUtils.restartBackScreen()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                TextButton(
                    text = stringResource(R.string.nav_settings),
                    onClick = {
                        val slot = target.slot
                        actionTargetSlot = null
                        onCardClick(slot)
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                TextButton(
                    text = stringResource(R.string.cards_delete),
                    onClick = {
                        val slot = target.slot
                        actionTargetSlot = null
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                cardManager?.removeCard(slot)
                                cardManager?.syncConfig()
                                cardManager?.prepareCardsForHook()
                                RootUtils.restartBackScreen()
                            }
                            cards = cardManager?.getCards()?.sortedByDescending { it.priority } ?: emptyList()
                            onCardsChanged()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

