package org.pysh.janus.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
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
    const val ACTION_QUERY_BEHAVIOR = "org.pysh.janus.action.QUERY_BEHAVIOR"
    const val ACTION_BEHAVIOR_REPORT = "org.pysh.janus.action.BEHAVIOR_REPORT"
    const val EXTRA_PROCESS = "process"
    const val EXTRA_HOOKS = "hooks"
    const val EXTRA_BEHAVIORS = "behaviors"

    private const val TAG = "Janus-Status"
    private const val JANUS_PACKAGE = "org.pysh.janus"

    /** hook name -> (status, detail). Status: "ok", "error", "skip". */
    private val statuses = ConcurrentHashMap<String, Pair<String, String?>>()

    /** hook id -> last behavioral data as JSON string. */
    private val behaviors = ConcurrentHashMap<String, String>()

    /** hook id -> invocation count. */
    private val behaviorCounts = ConcurrentHashMap<String, Int>()

    private var processName: String? = null

    /**
     * Initialise the reporter for this process.
     * Call once per process before installing any hooks.
     *
     * NOTE: This no longer hooks Application.onCreate() directly.
     * Use [AppLifecycleHook] to drive [onAppCreated] instead.
     */
    fun init(packageName: String) {
        processName = packageName
    }

    /**
     * Called by [AppLifecycleHook] when Application.onCreate() fires.
     * Registers the broadcast receiver for status queries.
     */
    fun onAppCreated(context: android.content.Context, packageName: String) {
        processName = packageName
        registerReceiver(context)
    }

    /** Record a hook as succeeded or failed. */
    fun report(name: String, ok: Boolean, detail: String? = null) {
        statuses[name] = (if (ok) "ok" else "error") to detail
    }

    /** Record a hook as intentionally skipped (feature disabled / config absent). */
    fun reportSkip(name: String, detail: String? = null) {
        statuses[name] = "skip" to detail
    }

    /** Record what a hook actually did at runtime (for behavioral verification). */
    fun reportBehavior(hookId: String, data: JSONObject) {
        val count = behaviorCounts.merge(hookId, 1) { old, _ -> old + 1 } ?: 1
        data.put("call_count", count)
        val payload = data.toString()
        behaviors[hookId] = payload
        // Per-call visibility: log each behavior event as debug so logcat -s
        // Janus-Status surfaces them live without waiting for a pull query.
        Log.d(TAG, "behavior $hookId: $payload")
    }

    private fun registerReceiver(context: Context) {
        try {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    when (intent.action) {
                        ACTION_QUERY -> sendReport(ctx)
                        ACTION_QUERY_BEHAVIOR -> sendBehaviorReport(ctx)
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(ACTION_QUERY)
                addAction(ACTION_QUERY_BEHAVIOR)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(receiver, filter)
            }
            Log.d(TAG, "Receiver registered in $processName")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to register receiver: ${e.message}")
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
            val payload = json.toString()
            // Log to logcat for ADB-based test scripts to read
            Log.i(TAG, "HOOK_STATUS_REPORT:$payload")
            context.sendBroadcast(Intent(ACTION_REPORT).apply {
                setPackage(JANUS_PACKAGE)
                putExtra(EXTRA_PROCESS, processName)
                putExtra(EXTRA_HOOKS, payload)
            })
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to send report: ${e.message}")
        }
    }

    private fun sendBehaviorReport(context: Context) {
        try {
            val json = JSONObject()
            for ((hookId, data) in behaviors) {
                json.put(hookId, JSONObject(data))
            }
            val payload = json.toString()
            // Log to logcat for ADB-based test scripts to read
            Log.i(TAG, "BEHAVIOR_REPORT:$payload")
            context.sendBroadcast(Intent(ACTION_BEHAVIOR_REPORT).apply {
                setPackage(JANUS_PACKAGE)
                putExtra(EXTRA_PROCESS, processName)
                putExtra(EXTRA_BEHAVIORS, payload)
            })
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to send behavior report: ${e.message}")
        }
    }
}
