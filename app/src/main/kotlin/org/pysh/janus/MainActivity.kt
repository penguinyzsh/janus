package org.pysh.janus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import org.pysh.janus.ui.MainScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val fallback = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            val isServiceBound by (JanusApplication.instance?.isServiceBound ?: fallback)
            MainScreen(isModuleActive = isServiceBound)
        }
    }
}
