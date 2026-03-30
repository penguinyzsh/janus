package org.pysh.janus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import org.pysh.janus.ui.MainScreen

class MainActivity : ComponentActivity() {

    fun isModuleActive(): Boolean = JanusApplication.instance?.xposedService != null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isServiceBound by (JanusApplication.instance?.isServiceBound
                ?: androidx.compose.runtime.mutableStateOf(false))
            MainScreen(isModuleActive = isServiceBound)
        }
    }
}
