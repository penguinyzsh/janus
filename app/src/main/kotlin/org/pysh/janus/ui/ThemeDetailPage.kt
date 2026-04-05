package org.pysh.janus.ui

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pysh.janus.R
import org.pysh.janus.core.util.RootUtils
import org.pysh.janus.data.ThemeManager
import org.pysh.janus.data.WhitelistManager
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Detail page for a single theme entry. Shows preview, metadata, and
 * exposes Apply / Deactivate / Delete actions.
 *
 * Pops back to the list via [onBack] after a successful delete. All I/O
 * runs on Dispatchers.IO; the page owns a small in-memory busy flag while
 * operations are in flight.
 */
@Composable
fun ThemeDetailPage(
    themeId: String,
    onBack: () -> Unit,
) {
    val isInPreview = LocalInspectionMode.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val themeManager = remember { if (!isInPreview) ThemeManager(context) else null }
    val whitelistManager = remember { if (!isInPreview) WhitelistManager(context) else null }

    var theme by remember { mutableStateOf(themeManager?.getTheme(themeId)) }
    var activeId by remember { mutableStateOf(themeManager?.getActiveThemeId()) }
    var isBusy by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(themeId) {
        withContext(Dispatchers.IO) {
            theme = themeManager?.getTheme(themeId)
            activeId = themeManager?.getActiveThemeId()
        }
    }

    val currentTheme = theme
    if (currentTheme == null && !isInPreview) {
        // Theme was deleted or storage glitch — pop immediately.
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val isActive = currentTheme != null && currentTheme.id == activeId

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = currentTheme?.displayName ?: stringResource(R.string.theme_detail_title),
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
    ) { paddingValues ->
        if (currentTheme == null) return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ThemePreviewCard(currentTheme, themeManager)

            MetadataCard(currentTheme)

            ActionButtons(
                isActive = isActive,
                isBusy = isBusy,
                onApply = {
                    if (themeManager == null) return@ActionButtons
                    isBusy = true
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            themeManager.applyTheme(currentTheme.id)
                        }
                        if (ok) {
                            activeId = currentTheme.id
                            if (whitelistManager?.isThemeAutoRestart() != false) {
                                withContext(Dispatchers.IO) { RootUtils.restartBackScreen() }
                            }
                            Toast.makeText(
                                context,
                                context.getString(R.string.theme_apply_success),
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.theme_apply_failed),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                        isBusy = false
                    }
                },
                onDeactivate = {
                    if (themeManager == null) return@ActionButtons
                    isBusy = true
                    scope.launch {
                        withContext(Dispatchers.IO) { themeManager.deactivate() }
                        if (whitelistManager?.isThemeAutoRestart() != false) {
                            withContext(Dispatchers.IO) { RootUtils.restartBackScreen() }
                        }
                        activeId = null
                        Toast.makeText(
                            context,
                            context.getString(R.string.theme_deactivated),
                            Toast.LENGTH_SHORT,
                        ).show()
                        isBusy = false
                    }
                },
                onDelete = { showDeleteDialog = true },
            )
        }
    }

    if (showDeleteDialog && currentTheme != null) {
        val dismiss = remember { mutableStateOf(true) }
        SuperDialog(
            show = dismiss,
            title = stringResource(R.string.theme_delete_confirm_title),
            summary = stringResource(R.string.theme_delete_confirm_message, currentTheme.displayName),
            onDismissRequest = { showDeleteDialog = false },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showDeleteDialog = false },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.theme_delete),
                    onClick = {
                        showDeleteDialog = false
                        if (themeManager == null) return@TextButton
                        scope.launch {
                            withContext(Dispatchers.IO) { themeManager.deleteTheme(currentTheme.id) }
                            onBack()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ThemePreviewCard(
    theme: ThemeManager.ThemeEntry,
    themeManager: ThemeManager?,
) {
    Card {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(RoundedCornerShape(12.dp))
                .background(MiuixTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            val thumbFile = themeManager?.thumbnailFile(theme.id)
            val bitmap = remember(thumbFile, theme.hasThumbnail) {
                if (theme.hasThumbnail && thumbFile?.exists() == true) {
                    try {
                        BitmapFactory.decodeFile(thumbFile.absolutePath)
                    } catch (_: Exception) {
                        null
                    }
                } else {
                    null
                }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = theme.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = theme.displayName,
                    fontSize = 24.sp,
                    color = MiuixTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun MetadataCard(theme: ThemeManager.ThemeEntry) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!theme.author.isNullOrBlank()) {
                MetadataRow(stringResource(R.string.theme_detail_author), theme.author)
            }
            if (!theme.description.isNullOrBlank()) {
                MetadataRow(stringResource(R.string.theme_detail_description), theme.description)
            }
            if (!theme.mamlVersion.isNullOrBlank()) {
                MetadataRow(stringResource(R.string.theme_detail_maml_version), theme.mamlVersion)
            }
            if (theme.fileSize > 0) {
                MetadataRow(
                    stringResource(R.string.theme_detail_file_size),
                    formatBytes(theme.fileSize),
                )
            }
            if (theme.sourceFileName.isNotBlank()) {
                MetadataRow(stringResource(R.string.theme_detail_source), theme.sourceFileName)
            }
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            modifier = Modifier.weight(0.35f),
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier.weight(0.65f),
        )
    }
}

@Composable
private fun ActionButtons(
    isActive: Boolean,
    isBusy: Boolean,
    onApply: () -> Unit,
    onDeactivate: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isActive) {
            TextButton(
                text = stringResource(R.string.theme_deactivate),
                onClick = onDeactivate,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            TextButton(
                text = stringResource(R.string.theme_apply),
                onClick = onApply,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        TextButton(
            text = stringResource(R.string.theme_delete),
            onClick = onDelete,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth(),
            colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return ""
    val units = arrayOf("B", "KB", "MB", "GB")
    var size = bytes.toDouble()
    var unit = 0
    while (size >= 1024 && unit < units.lastIndex) {
        size /= 1024
        unit++
    }
    return "%.1f %s".format(size, units[unit])
}
