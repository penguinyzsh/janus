package org.pysh.janus.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pysh.janus.R
import org.pysh.janus.data.CardManager
import org.pysh.janus.data.JanusCleanup
import org.pysh.janus.data.WhitelistManager
import org.pysh.janus.util.RootUtils
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Preview(showBackground = true)
@Composable
private fun OtherPagePreview() {
    MiuixTheme {
        OtherPage(onBack = {})
    }
}

@Composable
fun OtherPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showCleanupDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.other),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = MiuixIcons.Back, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Card(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            SuperArrow(
                title = stringResource(R.string.resync),
                summary = stringResource(R.string.resync_summary),
                onClick = {
                    val resyncMsg = context.getString(R.string.resync_done)
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            WhitelistManager(context).syncAllFlags()
                            CardManager(context).run {
                                syncConfig()
                                prepareCardsForHook()
                            }
                            RootUtils.restartBackScreen()
                        }
                        Toast.makeText(context, resyncMsg, Toast.LENGTH_SHORT).show()
                    }
                },
            )
            SuperArrow(
                title = stringResource(R.string.cleanup),
                summary = stringResource(R.string.cleanup_summary),
                onClick = { showCleanupDialog = true },
            )
        }
    }

    val cleanupSuccessMsg = stringResource(R.string.cleanup_success)
    val cleanupFailedMsg = stringResource(R.string.cleanup_failed)

    SuperDialog(
        show = showCleanupDialog,
        title = stringResource(R.string.cleanup_dialog_title),
        summary = stringResource(R.string.cleanup_dialog_summary),
        onDismissRequest = { showCleanupDialog = false },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                text = stringResource(R.string.cancel),
                onClick = { showCleanupDialog = false },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = stringResource(R.string.confirm),
                onClick = {
                    showCleanupDialog = false
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { JanusCleanup.cleanAll() }
                        Toast.makeText(
                            context,
                            if (ok) cleanupSuccessMsg else cleanupFailedMsg,
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
