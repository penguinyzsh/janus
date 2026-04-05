package org.pysh.janus.hook

import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import org.pysh.janus.hook.engine.RuleEngine
import org.pysh.janus.hook.engine.RuleLoader
import org.pysh.janus.hook.engine.engines.AppleMusicLyricEngine
import org.pysh.janus.hook.engine.engines.CardInjectionEngine
import org.pysh.janus.hook.engine.engines.SystemCardEngine
import org.pysh.janus.hook.engine.engines.WallpaperKeepAliveEngine
import org.pysh.janus.hook.engine.engines.WhitelistEngine

@android.annotation.SuppressLint("NewApi")
class HookEntry : XposedModule() {

    companion object {
        private const val TAG = "Janus"
        private const val TARGET_PACKAGE = "com.xiaomi.subscreencenter"
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        val packageName = param.packageName
        val classLoader = param.defaultClassLoader

        Log.d(TAG, "onPackageLoaded: $packageName")

        // Infrastructure: status reporter + view state observer (always active)
        // Uses unified Application.onCreate hook to avoid double-hooking
        HookStatusReporter.init(packageName)
        ViewStateObserver.init(packageName)
        AppLifecycleHook.init(this, packageName)

        // Load config from RemotePreferences (with file-flag fallback)
        val config = try {
            getRemotePreferences("janus_config")
        } catch (e: Throwable) {
            Log.w(TAG, "RemotePreferences not available, using empty config: ${e.message}")
            null
        }

        // Load rules for this package
        val rulesPrefs = try {
            getRemotePreferences("janus_rules")
        } catch (_: Throwable) {
            null
        }

        val rules = RuleLoader.loadForPackage(
            packageName = packageName,
            moduleAppInfo = moduleApplicationInfo,
            rulesPrefs = rulesPrefs,
        )

        if (rules.isEmpty()) {
            Log.d(TAG, "No rules for $packageName")
            return
        }

        // Create engine and install rules
        val engine = RuleEngine(
            module = this,
            config = config ?: EmptyPreferences,
        )
        engine.registerEngine(WhitelistEngine.ENGINE_NAME, WhitelistEngine())
        engine.registerEngine(SystemCardEngine.ENGINE_NAME, SystemCardEngine())
        engine.registerEngine(CardInjectionEngine.ENGINE_NAME, CardInjectionEngine())
        engine.registerEngine(WallpaperKeepAliveEngine.ENGINE_NAME, WallpaperKeepAliveEngine())
        engine.registerEngine(AppleMusicLyricEngine.ENGINE_NAME, AppleMusicLyricEngine())

        Log.i(TAG, "=== Janus v1.1.2-lyric code loaded for $packageName, engines: ${engine.engineNames()} ===")

        engine.install(rules, classLoader)
    }

    /**
     * Empty SharedPreferences fallback when RemotePreferences is unavailable.
     */
    private object EmptyPreferences : android.content.SharedPreferences {
        override fun getAll(): MutableMap<String, *> = mutableMapOf<String, Any>()
        override fun getString(key: String?, defValue: String?): String? = defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = defValue
        override fun getLong(key: String?, defValue: Long): Long = defValue
        override fun getFloat(key: String?, defValue: Float): Float = defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue
        override fun contains(key: String?): Boolean = false
        override fun edit(): android.content.SharedPreferences.Editor = throw UnsupportedOperationException()
        override fun registerOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }
}
