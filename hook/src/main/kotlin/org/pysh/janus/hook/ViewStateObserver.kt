package org.pysh.janus.hook

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.libxposed.api.XposedInterface
import org.json.JSONArray
import org.json.JSONObject

/**
 * Observes the View tree of the hooked process and reports visible text/state
 * in response to broadcast queries. Used for behavioral verification in tests.
 *
 * Runs in the hooked process alongside [HookStatusReporter].
 */
object ViewStateObserver {

    const val ACTION_QUERY = "org.pysh.janus.action.QUERY_VIEW_STATE"
    const val ACTION_REPORT = "org.pysh.janus.action.VIEW_STATE_REPORT"
    const val EXTRA_PROCESS = "process"
    const val EXTRA_VIEWS = "views"

    private const val TAG = "Janus-ViewState"
    private const val JANUS_PACKAGE = "org.pysh.janus"

    private var processName: String? = null

    fun init(module: XposedInterface, packageName: String) {
        processName = packageName
        try {
            val onCreateMethod = Application::class.java.getDeclaredMethod("onCreate")
            module.hook(onCreateMethod).intercept(XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                val app = chain.thisObject as? Application
                if (app != null) {
                    registerReceiver(app.applicationContext)
                }
                result
            })
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to hook Application.onCreate: ${e.message}")
        }
    }

    private fun registerReceiver(context: Context) {
        try {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    sendViewStateReport(ctx)
                }
            }
            val filter = IntentFilter(ACTION_QUERY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(receiver, filter)
            }
            Log.d(TAG, "ViewState receiver registered in $processName")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to register ViewState receiver: ${e.message}")
        }
    }

    private fun sendViewStateReport(context: Context) {
        try {
            val views = collectViewState()
            val payload = views.toString()
            Log.i(TAG, "VIEW_STATE_REPORT:$payload")
            context.sendBroadcast(Intent(ACTION_REPORT).apply {
                setPackage(JANUS_PACKAGE)
                putExtra(EXTRA_PROCESS, processName)
                putExtra(EXTRA_VIEWS, payload)
            })
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to send view state report: ${e.message}")
        }
    }

    private fun collectViewState(): JSONArray {
        val result = JSONArray()
        try {
            val activity = getCurrentActivity() ?: return result
            val rootView = activity.window?.decorView ?: return result
            walkViewTree(rootView, result)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to collect view state: ${e.message}")
        }
        return result
    }

    private fun walkViewTree(view: View, out: JSONArray) {
        if (view is TextView && view.text.isNotEmpty() && view.isShown) {
            val loc = IntArray(2)
            view.getLocationOnScreen(loc)
            out.put(JSONObject().apply {
                put("class", view.javaClass.name)
                put("text", view.text.toString())
                put("shown", view.isShown)
                put("bounds", "${loc[0]},${loc[1]},${view.width},${view.height}")
            })
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                walkViewTree(view.getChildAt(i), out)
            }
        }
    }

    private fun getCurrentActivity(): Activity? {
        return try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentThread = activityThreadClass.getMethod("currentActivityThread").invoke(null)
            val activitiesField = activityThreadClass.getDeclaredField("mActivities")
            activitiesField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val activities = activitiesField.get(currentThread) as? android.util.ArrayMap<*, *>
                ?: return null
            for (record in activities.values) {
                val recordClass = record!!.javaClass
                val pausedField = recordClass.getDeclaredField("paused")
                pausedField.isAccessible = true
                if (!pausedField.getBoolean(record)) {
                    val activityField = recordClass.getDeclaredField("activity")
                    activityField.isAccessible = true
                    return activityField.get(record) as? Activity
                }
            }
            null
        } catch (e: Throwable) {
            Log.w(TAG, "getCurrentActivity failed: ${e.message}")
            null
        }
    }
}
