package org.pysh.janus.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.pysh.janus.R
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
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
) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    val title = stringResource(R.string.nav_home)

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
            modifier =
                Modifier
                    .fillMaxHeight()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
            contentPadding =
                PaddingValues(
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

            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://qm.qq.com/q/UJBp9bNnIQ")),
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
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://ifdian.net/a/janus")),
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
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/penguinyzsh/janus")),
                            )
                        },
                    )
                }
            }
        }
    }
}
