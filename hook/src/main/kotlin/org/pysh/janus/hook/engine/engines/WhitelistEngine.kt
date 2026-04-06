package org.pysh.janus.hook.engine.engines

import org.pysh.janus.hookapi.ConfigSource
import android.util.Log
import io.github.libxposed.api.XposedInterface
import org.json.JSONObject
import org.pysh.janus.hook.HookStatusReporter
import org.pysh.janus.hook.engine.HookEnginePlugin
import org.pysh.janus.hookapi.HookRule

/**
 * Engine plugin for music whitelist injection.
 *
 * Extracted from the legacy HookEntry.hookMusicWhitelist().
 * Three hooks:
 * 1. `whitelist_check_method`: after proceed, if result false and arg[0] in whitelist -> return true
 * 2. `whitelist_get_method`: after proceed, merge whitelist into returned Set
 * 3. `music_controller_add_method`: before proceed, add whitelist packages to arg[0] MutableList
 *
 * Required targets in JSON rule:
 * - `whitelist_class` (e.g. "p2.a")
 * - `whitelist_check_method` (e.g. "c")
 * - `whitelist_get_method` (e.g. "b")
 * - `music_controller_class` (e.g. "com.miui.maml.elements.MusicController")
 * - `music_controller_add_method` (e.g. "addMusicPackageList")
 */
class WhitelistEngine : HookEnginePlugin {

    companion object {
        const val ENGINE_NAME = "whitelist"
        private const val TAG = "Janus-Whitelist"
    }

    override fun install(
        module: XposedInterface,
        rule: HookRule,
        classLoader: ClassLoader,
        config: ConfigSource,
    ) {
        val targets = rule.targets!!
        val whitelistClassName = targets["whitelist_class"]!!
        val checkMethodName = targets["whitelist_check_method"]!!
        val getMethodName = targets["whitelist_get_method"]!!
        val musicControllerClassName = targets["music_controller_class"]!!
        val addMethodName = targets["music_controller_add_method"]!!

        try {
            val targetClass = classLoader.loadClass(whitelistClassName)

            // Hook 1: check method — c(String) -> boolean
            hookCheckMethod(module, targetClass, checkMethodName, config)

            // Hook 2: get method — b() -> Set<String>
            hookGetMethod(module, targetClass, getMethodName, config)

            // Hook 3: MusicController.addMusicPackageList(List)
            hookMusicControllerPackageList(module, classLoader, musicControllerClassName, addMethodName, config)

            Log.d(TAG, "All whitelist hooks installed")
            HookStatusReporter.report("whitelist", true, whitelistClassName)
        } catch (e: Throwable) {
            Log.e(TAG, "install failed: ${e.message}")
            HookStatusReporter.report("whitelist", false, e.message)
        }
    }

    private fun hookCheckMethod(
        module: XposedInterface,
        targetClass: Class<*>,
        methodName: String,
        config: ConfigSource,
    ) {
        try {
            val method = targetClass.getDeclaredMethod(methodName, String::class.java)
            module.hook(method).intercept(XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                if (result as? Boolean == true) return@Hooker true

                val packageName = chain.args[0] as? String ?: return@Hooker result
                val customWhitelist = getCustomWhitelist(config)
                val inSet = packageName in customWhitelist
                HookStatusReporter.reportBehavior("whitelist_check", JSONObject().apply {
                    put("action", "check_in_set")
                    put("checked", packageName)
                    put("in_set", inSet)
                })
                if (inSet) {
                    Log.d(TAG, "Allowed: $packageName")
                    true
                } else {
                    result
                }
            })
        } catch (e: Throwable) {
            Log.e(TAG, "hookCheckMethod: ${e.message}")
        }
    }

    private fun hookGetMethod(
        module: XposedInterface,
        targetClass: Class<*>,
        methodName: String,
        config: ConfigSource,
    ) {
        try {
            val method = targetClass.getDeclaredMethod(methodName)
            module.hook(method).intercept(XposedInterface.Hooker { chain ->
                val original = chain.proceed()
                val customWhitelist = getCustomWhitelist(config)
                if (customWhitelist.isEmpty()) return@Hooker original
                HookStatusReporter.reportBehavior("whitelist_merge_set", JSONObject().apply {
                    put("action", "merge_set")
                    put("merged_count", customWhitelist.size)
                })

                @Suppress("UNCHECKED_CAST")
                try {
                    (original as MutableCollection<String>).addAll(customWhitelist)
                    original
                } catch (_: Throwable) {
                    val merged = HashSet<String>()
                    if (original is Collection<*>) {
                        @Suppress("UNCHECKED_CAST")
                        merged.addAll(original as Collection<String>)
                    }
                    merged.addAll(customWhitelist)
                    merged
                }
            })
        } catch (e: Throwable) {
            Log.e(TAG, "hookGetMethod: ${e.message}")
        }
    }

    private fun hookMusicControllerPackageList(
        module: XposedInterface,
        classLoader: ClassLoader,
        className: String,
        methodName: String,
        config: ConfigSource,
    ) {
        try {
            val cls = classLoader.loadClass(className)
            val method = cls.getDeclaredMethod(methodName, java.util.List::class.java)
            module.hook(method).intercept(XposedInterface.Hooker { chain ->
                @Suppress("UNCHECKED_CAST")
                val list = chain.args[0] as? MutableList<String>
                if (list != null) {
                    val whitelist = getCustomWhitelist(config)
                    for (pkg in whitelist) {
                        if (pkg !in list) list.add(pkg)
                    }
                    if (whitelist.isNotEmpty()) {
                        HookStatusReporter.reportBehavior("whitelist_merge_list", JSONObject().apply {
                            put("action", "merge_list")
                            put("merged_count", whitelist.size)
                        })
                        Log.d(TAG, "Injected ${whitelist.size} packages into MusicController")
                    }
                }
                chain.proceed()
            })
        } catch (e: Throwable) {
            Log.e(TAG, "hookMusicControllerPackageList: ${e.message}")
        }
    }

    private fun getCustomWhitelist(config: ConfigSource): Set<String> =
        try {
            val raw = config.getString("whitelist", "") ?: ""
            if (raw.isEmpty()) {
                emptySet()
            } else {
                raw.split(",").filter { it.isNotBlank() }.toSet()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to read whitelist: ${e.message}")
            emptySet()
        }
}
