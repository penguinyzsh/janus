package org.pysh.janus.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import org.pysh.janus.util.RootUtils
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

private const val REFRESH_MIN = 10
private const val REFRESH_MAX = 120
private const val PRIORITY_MIN = 1
private const val PRIORITY_MAX = 999

@Preview(showBackground = true)
@Composable
private fun CardDetailPagePreview() {
    MiuixTheme {
        CardDetailPage(slot = 0, onBack = {})
    }
}

@Composable
fun CardDetailPage(
    slot: Int,
    onBack: () -> Unit,
) {
    val isInPreview = LocalInspectionMode.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cardManager = remember { if (!isInPreview) CardManager(context) else null }
    val card = remember { cardManager?.getCards()?.find { it.slot == slot } }

    var refreshInterval by remember { mutableFloatStateOf(card?.refreshInterval?.toFloat() ?: 30f) }
    var priority by remember { mutableFloatStateOf(card?.priority?.toFloat() ?: 100f) }

    // Auto-save on any change (only refreshInterval and priority; enabled is managed on CardsPage)
    var initialized by remember { mutableStateOf(false) }
    LaunchedEffect(refreshInterval.toInt(), priority.toInt()) {
        if (!initialized) {
            initialized = true
            return@LaunchedEffect
        }
        if (card == null) return@LaunchedEffect
        val updated = card.copy(
            refreshInterval = refreshInterval.toInt(),
            priority = priority.toInt(),
        )
        withContext(Dispatchers.IO) { cardManager?.updateCard(updated) }
    }

    var showRefreshDialog by remember { mutableStateOf(false) }
    var showPriorityDialog by remember { mutableStateOf(false) }
    var dialogInput by remember { mutableStateOf("") }

    val scrollBehavior = MiuixScrollBehavior()
    val cardName = when {
        card != null -> card.name
        else -> ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = cardName,
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
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 12.dp),
        ) {
            // Settings
            SmallTitle(text = stringResource(R.string.nav_settings))
            Card(modifier = Modifier.padding(bottom = 12.dp)) {
                    SuperArrow(
                        title = stringResource(R.string.card_refresh_interval),
                        summary = stringResource(R.string.card_refresh_interval_value, refreshInterval.toInt()),
                        onClick = {
                            dialogInput = refreshInterval.toInt().toString()
                            showRefreshDialog = true
                        },
                        bottomAction = {
                            Slider(
                                value = refreshInterval,
                                onValueChange = { refreshInterval = it },
                                valueRange = REFRESH_MIN.toFloat()..REFRESH_MAX.toFloat(),
                            )
                        },
                    )
                    SuperArrow(
                        title = stringResource(R.string.card_priority),
                        summary = stringResource(R.string.card_priority_value, priority.toInt()),
                        onClick = {
                            dialogInput = priority.toInt().toString()
                            showPriorityDialog = true
                        },
                        bottomAction = {
                            Slider(
                                value = priority,
                                onValueChange = { priority = it },
                                valueRange = PRIORITY_MIN.toFloat()..PRIORITY_MAX.toFloat(),
                            )
                        },
                    )
                    TextButton(
                        text = stringResource(R.string.reset_default),
                        onClick = {
                            refreshInterval = 30f
                            priority = 100f
                            Toast.makeText(context, context.getString(R.string.reset_done), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                    )
            }

            // Delete
            Card(modifier = Modifier.padding(bottom = 12.dp)) {
                    TextButton(
                        text = stringResource(R.string.cards_delete),
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    cardManager?.removeCard(slot)
                                    RootUtils.restartBackScreen()
                                }
                                onBack()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    )
            }
        }
    }

    // ── Dialogs ─────────────────────────────────────────────────

    SuperDialog(
        show = showRefreshDialog,
        title = stringResource(R.string.card_refresh_interval),
        summary = stringResource(R.string.card_refresh_interval_summary, REFRESH_MIN, REFRESH_MAX),
        onDismissRequest = { showRefreshDialog = false },
    ) {
        TextField(value = dialogInput, onValueChange = { dialogInput = it })
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                text = stringResource(R.string.cancel),
                onClick = { showRefreshDialog = false },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = stringResource(R.string.confirm),
                onClick = {
                    val v = dialogInput.toIntOrNull()
                    if (v != null && v in REFRESH_MIN..REFRESH_MAX) {
                        refreshInterval = v.toFloat()
                    }
                    showRefreshDialog = false
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }

    SuperDialog(
        show = showPriorityDialog,
        title = stringResource(R.string.card_priority),
        summary = stringResource(R.string.card_priority_summary, PRIORITY_MIN, PRIORITY_MAX),
        onDismissRequest = { showPriorityDialog = false },
    ) {
        TextField(value = dialogInput, onValueChange = { dialogInput = it })
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                text = stringResource(R.string.cancel),
                onClick = { showPriorityDialog = false },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = stringResource(R.string.confirm),
                onClick = {
                    val v = dialogInput.toIntOrNull()
                    if (v != null && v in PRIORITY_MIN..PRIORITY_MAX) {
                        priority = v.toFloat()
                    }
                    showPriorityDialog = false
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }

}
