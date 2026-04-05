package org.pysh.janus.ui

import android.widget.Toast
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.pysh.janus.R
import org.pysh.janus.data.WhitelistManager
import org.pysh.janus.util.RootUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical


@Preview(showBackground = true)
@Composable
private fun FeaturesPagePreview() {
    MiuixTheme {
        FeaturesPage(bottomPadding = 0.dp, onWallpaperClick = {}, onCastingClick = {})
    }
}

@Composable
fun FeaturesPage(
    bottomPadding: Dp,
    onWallpaperClick: () -> Unit,
    onCastingClick: () -> Unit,
) {
    val isInPreview = LocalInspectionMode.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val whitelistManager = remember { if (!isInPreview) WhitelistManager(context) else null }
    var disableTracking by remember { mutableStateOf(whitelistManager?.isTrackingDisabled() ?: false) }
    var hideTimeTip by remember { mutableStateOf(whitelistManager?.isTimeTipHidden() ?: false) }

    val scrollBehavior = MiuixScrollBehavior()
    val title = stringResource(R.string.nav_features)

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
                Card(modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)) {
                    SuperSwitch(
                        title = stringResource(R.string.disable_tracking),
                        summary = stringResource(if (disableTracking) R.string.disable_tracking_on else R.string.disable_tracking_off),
                        checked = disableTracking,
                        onCheckedChange = {
                            disableTracking = it
                            whitelistManager?.setTrackingDisabled(it)
                            Toast.makeText(context, context.getString(if (it) R.string.enabled else R.string.disabled), Toast.LENGTH_SHORT).show()
                        },
                    )
                    SuperSwitch(
                        title = stringResource(R.string.hide_time_tip),
                        summary = stringResource(if (hideTimeTip) R.string.hide_time_tip_on else R.string.hide_time_tip_off),
                        checked = hideTimeTip,
                        onCheckedChange = {
                            hideTimeTip = it
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    whitelistManager?.setTimeTipHidden(it)
                                    RootUtils.restartBackScreen()
                                }
                                Toast.makeText(context, context.getString(if (it) R.string.enabled else R.string.disabled), Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
            }

            item {
                Card(modifier = Modifier.padding(bottom = 12.dp)) {
                    SuperArrow(
                        title = stringResource(R.string.section_wallpaper),
                        summary = stringResource(R.string.wp_gallery_title),
                        onClick = onWallpaperClick,
                    )
                    SuperArrow(
                        title = stringResource(R.string.section_casting),
                        onClick = onCastingClick,
                    )
                }
            }
        }
    }

}
