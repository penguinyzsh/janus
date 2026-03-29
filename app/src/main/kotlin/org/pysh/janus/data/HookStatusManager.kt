package org.pysh.janus.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import org.pysh.janus.hook.HookStatusReporter

/**
 * UI-side manager that queries hook status from hooked processes via broadcast.
 *
 * Usage in Compose:
 * ```
 * val manager = remember { HookStatusManager(context) }
 * DisposableEffect(manager) {
 *     manager.register()
 *     onDispose { manager.unregister() }
 * }
 * LaunchedEffect(Unit) { manager.query() }
 * ```
 */
class HookStatusManager(private val context: Context) {

    data class HookStatus(val status: String, val detail: String?)

    /** Process name → (hook name → status). Observable by Compose. */
    private val _statuses = mutableStateMapOf<String, Map<String, HookStatus>>()
    val statuses: Map<String, Map<String, HookStatus>> get() = _statuses

    var isQuerying by mutableStateOf(false)
        private set

    private val handler = Handler(Looper.getMainLooper())

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val process = intent.getStringExtra(HookStatusReporter.EXTRA_PROCESS) ?: return
            val hooksJson = intent.getStringExtra(HookStatusReporter.EXTRA_HOOKS) ?: return
            try {
                val hooks = mutableMapOf<String, HookStatus>()
                val json = JSONObject(hooksJson)
                for (key in json.keys()) {
                    val obj = json.getJSONObject(key)
                    hooks[key] = HookStatus(
                        status = obj.getString("status"),
                        detail = obj.optString("detail").takeIf { it.isNotEmpty() },
                    )
                }
                _statuses[process] = hooks
            } catch (_: Throwable) { }
        }
    }

    fun register() {
        val filter = IntentFilter(HookStatusReporter.ACTION_REPORT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }

    fun unregister() {
        try { context.unregisterReceiver(receiver) } catch (_: Throwable) { }
        handler.removeCallbacksAndMessages(null)
    }

    /** Send a query broadcast and wait up to 2 s for responses. */
    fun query() {
        isQuerying = true
        context.sendBroadcast(Intent(HookStatusReporter.ACTION_QUERY))
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ isQuerying = false }, 2_000)
    }
}
