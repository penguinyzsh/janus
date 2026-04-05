package org.pysh.janus.hook.engine.engines

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.util.Log
import io.github.libxposed.api.XposedInterface
import org.json.JSONObject
import org.pysh.janus.hook.HookStatusReporter
import org.pysh.janus.hook.engine.HookEnginePlugin
import org.pysh.janus.hook.engine.HookRule
import org.pysh.janus.hook.engine.RuleEngine

/**
 * Engine plugin for wallpaper keep-alive (prevents onPause widget destruction).
 *
 * Extracted from the legacy HookEntry.hookWallpaperKeepAlive().
 * Hooks SubScreenLauncher.onPause() to skip its body while satisfying
 * Activity lifecycle requirements (dispatchActivityPaused + mCalled).
 *
 * Required targets in JSON rule:
 * - `launcher_class` (e.g. "com.xiaomi.subscreencenter.SubScreenLauncher")
 * - `on_pause_method` (e.g. "onPause")
 *
 * Config flag: `wallpaper_keep_alive` (boolean, default false)
 */
class WallpaperKeepAliveEngine : HookEnginePlugin {

    companion object {
        const val ENGINE_NAME = "wallpaper_keep_alive"
        private const val TAG = "Janus-KeepAlive"
    }

    @SuppressLint("BlockedPrivateApi")
    override fun install(
        module: XposedInterface,
        rule: HookRule,
        classLoader: ClassLoader,
        config: SharedPreferences,
    ) {
        val targets = rule.targets!!
        val launcherClassName = targets["launcher_class"]!!
        val onPauseMethodName = targets["on_pause_method"]!!

        try {
            val launcherClass = classLoader.loadClass(launcherClassName)
            val onPauseMethod = launcherClass.getDeclaredMethod(onPauseMethodName)

            // Hook SubScreenLauncher.onPause() to prevent MainPanel.A() -> widget destruction.
            // onPause triggers: MainPanel.A() -> widget cleanup -> MamlView.onDestroy()
            // -> ScreenElementRoot destroyed -> animation restarts from 0 on resume.
            // By skipping onPause body, widgets stay alive and animation continues.
            module.hook(onPauseMethod).intercept(XposedInterface.Hooker { chain ->
                if (!RuleEngine.isConfigEnabled(config, "wallpaper_keep_alive")) {
                    return@Hooker chain.proceed()
                }

                try {
                    // Must satisfy Activity lifecycle check to avoid SuperNotCalledException.
                    // Activity.onPause() sets mCalled=true and calls dispatchActivityPaused().
                    val activityClass = android.app.Activity::class.java
                    val dispatchMethod = activityClass.getDeclaredMethod("dispatchActivityPaused")
                    dispatchMethod.isAccessible = true
                    dispatchMethod.invoke(chain.thisObject)

                    val mCalledField = activityClass.getDeclaredField("mCalled")
                    mCalledField.isAccessible = true
                    mCalledField.setBoolean(chain.thisObject, true)

                    HookStatusReporter.reportBehavior("wallpaper_keep_alive", JSONObject().apply {
                        put("action", "keep_alive")
                        put("blocked_pause", true)
                    })
                    Log.d(TAG, "Blocked SubScreenLauncher.onPause -> wallpaper kept alive")
                    // Skip original onPause body by not calling chain.proceed()
                    null
                } catch (e: Throwable) {
                    Log.e(TAG, "super.onPause simulation failed: ${e.message}")
                    // Let original onPause run to avoid crash
                    chain.proceed()
                }
            })

            Log.d(TAG, "Wallpaper keep-alive hook installed on $launcherClassName.$onPauseMethodName")
            HookStatusReporter.report("wallpaper_keep_alive", true, launcherClassName)
        } catch (e: Throwable) {
            Log.e(TAG, "install failed: ${e.message}")
            HookStatusReporter.report("wallpaper_keep_alive", false, e.message)
        }
    }
}
