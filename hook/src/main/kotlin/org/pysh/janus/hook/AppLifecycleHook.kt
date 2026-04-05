package org.pysh.janus.hook

import android.app.Application
import android.util.Log
import io.github.libxposed.api.XposedInterface

/**
 * Unified Application.onCreate() hook that initialises all subsystems
 * requiring an application [android.content.Context].
 *
 * Consolidates the separate hooks previously installed by
 * [HookStatusReporter] and [ViewStateObserver] into a single intercept,
 * avoiding double-hooking the same method.
 */
object AppLifecycleHook {

    private const val TAG = "Janus-Lifecycle"

    /**
     * Hook [Application.onCreate] once, delegating context delivery to
     * [HookStatusReporter] and [ViewStateObserver].
     */
    fun init(module: XposedInterface, packageName: String) {
        try {
            val onCreateMethod = Application::class.java.getDeclaredMethod("onCreate")
            module.hook(onCreateMethod).intercept(XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                val app = chain.thisObject as? Application
                if (app != null) {
                    val context = app.applicationContext
                    HookStatusReporter.onAppCreated(context, packageName)
                    ViewStateObserver.onAppCreated(context, packageName)
                }
                result
            })
            Log.d(TAG, "Application.onCreate hook installed for $packageName")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to hook Application.onCreate: ${e.message}")
        }
    }
}
