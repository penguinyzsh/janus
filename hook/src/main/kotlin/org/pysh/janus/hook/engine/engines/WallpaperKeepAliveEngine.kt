package org.pysh.janus.hook.engine.engines

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.util.Log
import io.github.libxposed.api.XposedInterface
import org.json.JSONObject
import org.pysh.janus.core.model.HookRule
import org.pysh.janus.hook.HookStatusReporter
import org.pysh.janus.hook.engine.HookEnginePlugin
import org.pysh.janus.hook.engine.RuleEngine

class WallpaperKeepAliveEngine : HookEnginePlugin {
    companion object { private const val TAG = "Janus-KeepAlive" }

    @SuppressLint("BlockedPrivateApi")
    override fun install(module: XposedInterface, rule: HookRule, classLoader: ClassLoader, config: SharedPreferences) {
        val targets = rule.targets!!
        val launcherClassName = targets["launcher_class"]!!
        val onPauseMethodName = targets["on_pause_method"]!!
        try {
            val launcherClass = classLoader.loadClass(launcherClassName)
            val onPauseMethod = launcherClass.getDeclaredMethod(onPauseMethodName)
            module.hook(onPauseMethod).intercept(XposedInterface.Hooker { chain ->
                if (!RuleEngine.isConfigEnabled(config, "wallpaper_keep_alive")) { return@Hooker chain.proceed() }
                try {
                    val activityClass = android.app.Activity::class.java
                    val dispatchMethod = activityClass.getDeclaredMethod("dispatchActivityPaused")
                    dispatchMethod.isAccessible = true
                    dispatchMethod.invoke(chain.thisObject)
                    val mCalledField = activityClass.getDeclaredField("mCalled")
                    mCalledField.isAccessible = true
                    mCalledField.setBoolean(chain.thisObject, true)
                    HookStatusReporter.reportBehavior("wallpaper_keep_alive", JSONObject().apply { put("action", "keep_alive"); put("blocked_pause", true) })
                    Log.d(TAG, "Blocked SubScreenLauncher.onPause -> wallpaper kept alive")
                    null
                } catch (e: Throwable) { Log.e(TAG, "super.onPause simulation failed: ${e.message}"); chain.proceed() }
            })
            Log.d(TAG, "Wallpaper keep-alive hook installed on $launcherClassName.$onPauseMethodName")
            HookStatusReporter.report("wallpaper_keep_alive", true, launcherClassName)
        } catch (e: Throwable) { Log.e(TAG, "install failed: ${e.message}"); HookStatusReporter.report("wallpaper_keep_alive", false, e.message) }
    }
}
