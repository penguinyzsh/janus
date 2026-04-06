package org.pysh.janus.ui

import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pysh.janus.R
import org.pysh.janus.data.WhitelistManager
import org.pysh.janus.core.util.RootUtils
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Preview(showBackground = true)
@Composable
private fun AppFeaturePagePreview() {
    MiuixTheme {
        AppFeaturePage(packageName = "com.example.app", onBack = {})
    }
}

@Composable
fun AppFeaturePage(
    packageName: String,
    onBack: () -> Unit,
) {
    val isInPreview = LocalInspectionMode.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val whitelistManager = remember { if (!isInPreview) WhitelistManager(context) else null }
    var isWhitelisted by remember {
        mutableStateOf(whitelistManager?.let { packageName in it.getWhitelist() } ?: false)
    }

    val pm = context.packageManager
    val appInfo =
        remember {
            try {
                pm.getApplicationInfo(packageName, 0)
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }
    val appName = remember { appInfo?.let { pm.getApplicationLabel(it).toString() } ?: packageName }
    val iconBitmap =
        remember {
            val density = context.resources.displayMetrics.density
            val sizePx = (48 * density).toInt()
            val drawable =
                appInfo?.let { pm.getApplicationIcon(it) }
                    ?: pm.defaultActivityIcon
            drawable.toBitmap(width = sizePx, height = sizePx).asImageBitmap()
        }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.app_feature),
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
    ) { innerPadding ->
        LazyColumn(
            contentPadding =
                PaddingValues(
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    bottom = 12.dp,
                    start = 12.dp,
                    end = 12.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card {
                    BasicComponent(
                        title = appName,
                        summary = packageName,
                        startAction = {
                            Image(
                                painter = BitmapPainter(iconBitmap),
                                contentDescription = appName,
                                modifier = Modifier.size(48.dp),
                            )
                        },
                    )
                }
            }

            item {
                Card {
                    SuperSwitch(
                        title = stringResource(R.string.add_music_whitelist),
                        summary = stringResource(if (isWhitelisted) R.string.whitelist_on else R.string.whitelist_off),
                        checked = isWhitelisted,
                        onCheckedChange = { checked ->
                            isWhitelisted = checked
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    val whitelist =
                                        whitelistManager?.getWhitelist()?.toMutableSet()
                                            ?: mutableSetOf()
                                    if (checked) {
                                        whitelist.add(packageName)
                                    } else {
                                        whitelist.remove(packageName)
                                    }
                                    whitelistManager?.saveWhitelist(whitelist)
                                    RootUtils.restartBackScreen()
                                }
                                val msgRes = if (checked) R.string.whitelist_added else R.string.whitelist_removed
                                Toast.makeText(context, context.getString(msgRes), Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
            }
        }
    }
}
