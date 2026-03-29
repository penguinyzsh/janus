package org.pysh.janus.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.unit.sp
import org.pysh.janus.BuildConfig
import org.pysh.janus.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun StatusCard(
    isModuleActive: Boolean,
    hasRoot: Boolean?,
    modifier: Modifier = Modifier,
) {
    // Debug: each card toggles between two states on click
    var debugActive by remember { mutableIntStateOf(1) }
    var debugRoot by remember { mutableIntStateOf(1) }

    val effectiveActive = if (BuildConfig.DEBUG) debugActive == 1 else isModuleActive
    val effectiveRoot = if (BuildConfig.DEBUG) debugRoot == 1 else hasRoot != false

    val overallOk = effectiveActive && effectiveRoot
    val isDark = isSystemInDarkTheme()

    val cardColor = if (overallOk) {
        if (isDark) Color(0xFF1A3825) else Color(0xFFDFFAE4)
    } else {
        if (isDark) Color(0xFF3B1010) else Color(0xFFF8E2E2)
    }

    val iconTint = if (overallOk) Color(0xFF36D167) else Color(0xFFF72727)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left — status card with decorative icon
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            colors = CardDefaults.defaultColors(color = cardColor),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(38.dp, 45.dp),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    Icon(
                        modifier = Modifier.size(170.dp),
                        imageVector = if (overallOk) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        tint = iconTint.copy(alpha = 0.3f),
                        contentDescription = null,
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                ) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.app_name),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.app_version_format, BuildConfig.VERSION_NAME),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        // Right — LSPosed & Root
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .then(
                        if (BuildConfig.DEBUG) Modifier.clickable {
                            debugActive = 1 - debugActive
                        } else Modifier,
                    ),
                insideMargin = PaddingValues(16.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = "LSPosed",
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    )
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(
                            if (effectiveActive) R.string.status_active else R.string.status_inactive,
                        ),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .then(
                        if (BuildConfig.DEBUG) Modifier.clickable {
                            debugRoot = 1 - debugRoot
                        } else Modifier,
                    ),
                insideMargin = PaddingValues(16.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = "Root",
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    )
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(
                            if (effectiveRoot) R.string.status_available else R.string.status_unavailable,
                        ),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
