package org.pysh.janus.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.pysh.janus.R
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Preview(showBackground = true)
@Composable
private fun ActivationPagePreview() {
    MiuixTheme {
        ActivationPage(isModuleActive = false, hasRoot = false)
    }
}

@Composable
fun ActivationPage(
    isModuleActive: Boolean,
    hasRoot: Boolean,
) {
    if (!LocalInspectionMode.current) {
        val activity = LocalContext.current as Activity
        BackHandler { activity.finish() }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(title = stringResource(R.string.activation))
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
                    .padding(horizontal = 12.dp),
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            StatusCard(
                isModuleActive = isModuleActive,
                hasRoot = hasRoot,
            )
        }
    }
}
