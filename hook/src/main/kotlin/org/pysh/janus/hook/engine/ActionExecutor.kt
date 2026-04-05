package org.pysh.janus.hook.engine

import org.pysh.janus.hookapi.ConfigSource
import android.util.Log
import io.github.libxposed.api.XposedInterface
import org.json.JSONObject
import org.pysh.janus.hook.HookStatusReporter
import org.pysh.janus.hookapi.HookAction
import org.pysh.janus.hookapi.HookRule
import org.pysh.janus.hookapi.HookTarget
import org.pysh.janus.hook.ReflectUtils
import java.io.File

/**
 * Executes simple hook actions defined in JSON rules.
 * Each action type maps to a specific interceptor pattern using the libxposed API.
 */
object ActionExecutor {

    private const val TAG = "Janus-Action"

    fun install(
        module: XposedInterface,
        target: HookTarget,
        classLoader: ClassLoader,
        rule: HookRule,
        config: ConfigSource,
    ) {
        try {
            val clazz = classLoader.loadClass(target.className)

            if (target.methodName == null) {
                // Hook all constructors
                for (ctor in clazz.declaredConstructors) {
                    val hooker = createHooker(target.id, target.action, rule, config)
                    module.hook(ctor).intercept(hooker)
                }
            } else {
                val paramClasses = target.paramTypes?.map { resolveType(it, classLoader) }?.toTypedArray()
                val method = if (paramClasses != null) {
                    clazz.getDeclaredMethod(target.methodName, *paramClasses)
                } else {
                    val candidates = clazz.declaredMethods.filter { it.name == target.methodName }
                    if (candidates.isEmpty()) {
                        throw NoSuchMethodException("${target.methodName} not found in ${target.className}")
                    }
                    if (candidates.size > 1) {
                        Log.w(TAG, "Multiple methods named ${target.methodName} in ${target.className}, " +
                            "specify paramTypes to disambiguate. Using first match.")
                    }
                    candidates.first()
                }
                val hooker = createHooker(target.id, target.action, rule, config)
                module.hook(method).intercept(hooker)
            }

            HookStatusReporter.report(target.id, true, "${target.className}.${target.methodName ?: "<init>"}")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to install hook ${target.id}: ${e.message}")
            HookStatusReporter.report(target.id, false, e.message)
        }
    }

    private fun createHooker(
        hookId: String,
        action: HookAction,
        rule: HookRule,
        config: ConfigSource,
    ): XposedInterface.Hooker = when (action.type) {
        "block_method" -> blockMethod(hookId, config, rule.configFlag)
        "return_constant" -> returnConstant(hookId, action.value, config, rule.configFlag)
        "check_in_set" -> checkInSet(hookId, action, config)
        "merge_set" -> mergeSet(hookId, action, config)
        "merge_list" -> mergeList(hookId, action, config)
        "field_set" -> fieldSet(hookId, action, config, rule.configFlag)
        "path_redirect" -> pathRedirect(hookId, action)
        "path_from_active_theme" -> pathFromActiveTheme(hookId)
        "force_arg" -> forceArg(hookId, action, config, rule.configFlag)
        else -> throw IllegalArgumentException("Unknown action type: ${action.type}")
    }

    private fun blockMethod(hookId: String, config: ConfigSource, flag: String?) = XposedInterface.Hooker { chain ->
        val blocked = flag == null || config.getBoolean(flag, false)
        HookStatusReporter.reportBehavior(hookId, JSONObject().apply {
            put("action", "block_method")
            put("blocked", blocked)
        })
        if (blocked) null else chain.proceed()
    }

    private fun returnConstant(hookId: String, value: Any?, config: ConfigSource, flag: String?) = XposedInterface.Hooker { chain ->
        val enabled = flag == null || config.getBoolean(flag, false)
        if (!enabled) {
            chain.proceed()
        } else {
            HookStatusReporter.reportBehavior(hookId, JSONObject().apply {
                put("action", "return_constant")
                put("returned", value)
            })
            value
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun checkInSet(hookId: String, action: HookAction, config: ConfigSource) = XposedInterface.Hooker { chain ->
        val result = chain.proceed()
        if (result == true) return@Hooker result
        val arg = chain.args[action.paramIndex ?: 0] as? String ?: return@Hooker result
        val set = getDataSource(action.source, config)
        val inSet = arg in set
        HookStatusReporter.reportBehavior(hookId, JSONObject().apply {
            put("action", "check_in_set")
            put("checked", arg)
            put("in_set", inSet)
        })
        if (inSet) true else result
    }

    @Suppress("UNCHECKED_CAST")
    private fun mergeSet(hookId: String, action: HookAction, config: ConfigSource) = XposedInterface.Hooker { chain ->
        val result = chain.proceed()
        val customSet = getDataSource(action.source, config)
        if (customSet.isEmpty()) return@Hooker result
        HookStatusReporter.reportBehavior(hookId, JSONObject().apply {
            put("action", "merge_set")
            put("merged_count", customSet.size)
        })
        try {
            (result as MutableCollection<String>).addAll(customSet)
            result
        } catch (_: Throwable) {
            val merged = HashSet<String>()
            if (result is Collection<*>) {
                merged.addAll(result as Collection<String>)
            }
            merged.addAll(customSet)
            merged
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun mergeList(hookId: String, action: HookAction, config: ConfigSource) = XposedInterface.Hooker { chain ->
        val list = chain.args[action.paramIndex ?: 0] as? MutableList<String>
        var mergedCount = 0
        if (list != null) {
            val customSet = getDataSource(action.source, config)
            for (pkg in customSet) {
                if (pkg !in list) {
                    list.add(pkg)
                    mergedCount++
                }
            }
        }
        HookStatusReporter.reportBehavior(hookId, JSONObject().apply {
            put("action", "merge_list")
            put("merged_count", mergedCount)
        })
        chain.proceed()
    }

    private fun fieldSet(hookId: String, action: HookAction, config: ConfigSource, flag: String?) = XposedInterface.Hooker { chain ->
        val result = chain.proceed()
        if (flag != null && !config.getBoolean(flag, false)) return@Hooker result
        val obj = chain.thisObject ?: return@Hooker result
        val fieldName = action.field ?: return@Hooker result
        when (val value = action.value) {
            is Boolean -> ReflectUtils.setBooleanField(obj, fieldName, value)
            is Number -> ReflectUtils.setIntField(obj, fieldName, value.toInt())
            else -> ReflectUtils.setField(obj, fieldName, value)
        }
        HookStatusReporter.reportBehavior(hookId, JSONObject().apply {
            put("action", "field_set")
            put("field", fieldName)
            put("value", action.value)
        })
        result
    }

    private fun pathRedirect(hookId: String, action: HookAction) = XposedInterface.Hooker { chain ->
        val original = chain.proceed() as? String ?: return@Hooker null
        val targetPath = action.value as? String ?: return@Hooker original
        val exists = File(targetPath).exists()
        val redirected = exists && original != targetPath
        HookStatusReporter.reportBehavior(hookId, JSONObject().apply {
            put("action", "path_redirect")
            put("original", original)
            put("redirected_to", targetPath)
            put("exists", exists)
            put("redirected", redirected)
        })
        if (redirected) targetPath else original
    }

    /**
     * Layered path resolver for the rear-screen wallpaper hook point.
     *
     * Priority order (highest first):
     *  1. **Active theme** — if `$config/active_theme` contains a non-empty
     *     theme id AND `$themesBase/<id>/theme.mrc` exists on disk, return it.
     *  2. **Custom wallpaper** — if `rearScreenWhite/janus/custom.mrc` exists
     *     (set by the legacy [WallpaperManager] video import flow), return it.
     *  3. **System default** — fall through to `chain.proceed()`.
     *
     * All pointer/theme paths are relative to the current Android user id,
     * resolved via `Process.myUid() / 100_000` so multi-user devices work.
     */
    private fun pathFromActiveTheme(hookId: String) = XposedInterface.Hooker { chain ->
        val userId = android.os.Process.myUid() / 100_000
        val pointerPath = org.pysh.janus.core.util.JanusPaths.HOOK_CONFIG_DIR
            .replace("\$user_id", userId.toString()) + "/active_theme"
        val themesBase = org.pysh.janus.core.util.JanusPaths.HOOK_THEMES_BASE
            .replace("\$user_id", userId.toString())
        val customMrc = "/data/system/theme/rearScreenWhite/janus/custom.mrc"

        var resolved: String? = null
        var reason = "system_default"

        try {
            val pointerFile = File(pointerPath)
            if (pointerFile.exists()) {
                val themeId = pointerFile.readText().trim()
                if (themeId.isNotEmpty()) {
                    val themeFile = File("$themesBase/$themeId/${org.pysh.janus.core.util.JanusPaths.THEME_FILE_NAME}")
                    if (themeFile.exists()) {
                        resolved = themeFile.absolutePath
                        reason = "active_theme"
                    }
                }
            }
        } catch (_: Throwable) { /* fall through */ }

        if (resolved == null && File(customMrc).exists()) {
            resolved = customMrc
            reason = "custom_wallpaper"
        }

        val original = chain.proceed() as? String
        val finalPath = resolved ?: original

        HookStatusReporter.reportBehavior(hookId, JSONObject().apply {
            put("action", "path_from_active_theme")
            put("original", original)
            put("resolved_to", finalPath)
            put("reason", reason)
        })

        finalPath
    }

    private fun forceArg(hookId: String, action: HookAction, config: ConfigSource, flag: String?) = XposedInterface.Hooker { chain ->
        if (flag != null && !config.getBoolean(flag, false)) {
            return@Hooker chain.proceed()
        }
        val index = action.paramIndex ?: 0
        val args = chain.args.toTypedArray()
        args[index] = action.value
        HookStatusReporter.reportBehavior(hookId, JSONObject().apply {
            put("action", "force_arg")
            put("index", index)
            put("forced", action.value)
        })
        chain.proceed(args)
    }

    private fun getDataSource(source: String?, config: ConfigSource): Set<String> {
        if (source == null) return emptySet()
        return when (source) {
            "whitelist" -> {
                val raw = config.getString("whitelist", "") ?: ""
                if (raw.isEmpty()) emptySet()
                else raw.split(",").filter { it.isNotBlank() }.toSet()
            }
            else -> emptySet()
        }
    }

    private fun resolveType(typeName: String, classLoader: ClassLoader): Class<*> = when (typeName) {
        "boolean" -> Boolean::class.javaPrimitiveType!!
        "byte" -> Byte::class.javaPrimitiveType!!
        "char" -> Char::class.javaPrimitiveType!!
        "short" -> Short::class.javaPrimitiveType!!
        "int" -> Int::class.javaPrimitiveType!!
        "long" -> Long::class.javaPrimitiveType!!
        "float" -> Float::class.javaPrimitiveType!!
        "double" -> Double::class.javaPrimitiveType!!
        "void" -> Void.TYPE
        else -> classLoader.loadClass(typeName)
    }
}
