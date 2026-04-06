package org.pysh.janus.hook

import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import org.pysh.janus.hook.BuildConfig
import org.pysh.janus.hook.config.SharedPreferencesConfigSource
import org.pysh.janus.hook.engine.RuleEngine
import org.pysh.janus.hook.engine.RuleLoader
import org.pysh.janus.hook.engine.engines.CardInjectionEngine
import org.pysh.janus.hook.engine.engines.SystemCardEngine
import org.pysh.janus.hook.engine.engines.WallpaperKeepAliveEngine
import org.pysh.janus.hook.engine.engines.WhitelistEngine
import org.pysh.janus.hookapi.ConfigSource

@android.annotation.SuppressLint("NewApi")
class HookEntry : XposedModule() {

    override fun onPackageLoaded(param: PackageLoadedParam) {
        val packageName = param.packageName
        val classLoader = param.defaultClassLoader

        Log.d(TAG, "onPackageLoaded: $packageName")

        // Infrastructure: status reporter + view state observer (always active).
        // Uses unified Application.onCreate hook to avoid double-hooking.
        HookStatusReporter.init(packageName)
        ViewStateObserver.init(packageName)
        AppLifecycleHook.init(this, packageName)

        // Build the engine-facing config store backed by LSPosed RemotePreferences
        // (falls back to Empty sentinel when the service isn't bound yet).
        val config: ConfigSource = buildConfigSource()

        // Load rules for this package. `rulesPrefs` is a separate remote store
        // that persists user-imported custom rules; it is not the same as the
        // engine config above and still talks to SharedPreferences directly.
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

        val engine = RuleEngine(module = this, config = config)
        engine.registerEngine(WhitelistEngine.ENGINE_NAME, WhitelistEngine())
        engine.registerEngine(SystemCardEngine.ENGINE_NAME, SystemCardEngine())
        engine.registerEngine(CardInjectionEngine.ENGINE_NAME, CardInjectionEngine())
        engine.registerEngine(WallpaperKeepAliveEngine.ENGINE_NAME, WallpaperKeepAliveEngine())

        Log.i(
            TAG,
            "=== Janus ${BuildConfig.MODULE_VERSION} " +
                "(vc${BuildConfig.MODULE_VERSION_CODE}) loaded for $packageName, " +
                "engines: ${engine.engineNames()} ===",
        )

        engine.install(rules, classLoader)
    }

    private fun buildConfigSource(): ConfigSource =
        try {
            SharedPreferencesConfigSource(getRemotePreferences("janus_config"))
        } catch (e: Throwable) {
            Log.w(TAG, "RemotePreferences unavailable, using Empty fallback: ${e.message}")
            ConfigSource.Empty
        }

    private companion object {
        const val TAG = "Janus"
    }
}
