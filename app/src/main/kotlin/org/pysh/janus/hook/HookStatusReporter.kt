package org.pysh.janus.hook

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Reports hook installation status back to the Janus app via broadcast.
 *
 * Runs in the hooked process (subscreencenter, music apps). Hooks
 * [Application.onCreate] to obtain a [Context], then registers a dynamic
 * [BroadcastReceiver] that responds to status queries from the Janus UI.
 *
 * Each process has its own singleton instance (separate JVM), so statuses
 * are naturally isolated per process.
 */
object HookStatusReporter {

    const val ACTION_QUERY = "org.pysh.janus.action.QUERY_HOOK_STATUS"
    const val ACTION_REPORT = "org.pysh.janus.action.HOOK_STATUS_REPORT"
    const val EXTRA_PROCESS = "process"
    const val EXTRA_HOOKS = "hooks"

    private const val TAG = "Janus-Status"
    private const val JANUS_PACKAGE = "org.pysh.janus"

    /** hook name → (status, detail). Status: "ok", "error", "skip". */
    private val statuses = ConcurrentHashMap<String, Pair<String, String?>>()
    private var processName: String? = null

    /**
     * Initialise the reporter for this process.
     * Call once per process in [handleLoadPackage][de.robv.android.xposed.IXposedHookLoadPackage.handleLoadPackage].
     */
    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        processName = lpparam.packageName
        try {
            XposedHelpers.findAndHookMethod(
                Application::class.java, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val app = param.thisObject as? Application ?: return
                        registerReceiver(app.applicationContext)
                    }
                }
            )
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] Failed to hook Application.onCreate: ${e.message}")
        }
    }

    /** Record a hook as succeeded or failed. */
    fun report(name: String, ok: Boolean, detail: String? = null) {
        statuses[name] = (if (ok) "ok" else "error") to detail
    }

    /** Record a hook as intentionally skipped (feature disabled / config absent). */
    fun reportSkip(name: String, detail: String? = null) {
        statuses[name] = "skip" to detail
    }

    private fun registerReceiver(context: Context) {
        try {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    sendReport(ctx)
                }
            }
            val filter = IntentFilter(ACTION_QUERY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(receiver, filter)
            }
            XposedBridge.log("[$TAG] Receiver registered in $processName")
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] Failed to register receiver: ${e.message}")
        }
    }

    private fun sendReport(context: Context) {
        try {
            val json = JSONObject()
            for ((name, pair) in statuses) {
                json.put(name, JSONObject().apply {
                    put("status", pair.first)
                    pair.second?.let { put("detail", it) }
                })
            }
            context.sendBroadcast(Intent(ACTION_REPORT).apply {
                setPackage(JANUS_PACKAGE)
                putExtra(EXTRA_PROCESS, processName)
                putExtra(EXTRA_HOOKS, json.toString())
            })
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] Failed to send report: ${e.message}")
        }
    }
}
