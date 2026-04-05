package org.pysh.janus.hook.engine

import android.content.SharedPreferences
import android.util.Log
import io.github.libxposed.api.XposedInterface
import org.json.JSONObject
import org.pysh.janus.hook.HookStatusReporter
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
        config: SharedPreferences,
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
        config: SharedPreferences,
    ): XposedInterface.Hooker = when (action.type) {
        "block_method" -> blockMethod(hookId, config, rule.configFlag)
        "return_constant" -> returnConstant(hookId, action.value, config, rule.configFlag)
        "check_in_set" -> checkInSet(hookId, action, config)
        "merge_set" -> mergeSet(hookId, action, config)
        "merge_list" -> mergeList(hookId, action, config)
        "field_set" -> fieldSet(hookId, action, config, rule.configFlag)
        "path_redirect" -> pathRedirect(hookId, action)
        "force_arg" -> forceArg(hookId, action, config, rule.configFlag)
        else -> throw IllegalArgumentException("Unknown action type: ${action.type}")
    }

    private fun blockMethod(hookId: String, config: SharedPreferences, flag: String?) = XposedInterface.Hooker { chain ->
        val blocked = flag == null || RuleEngine.isConfigEnabled(config, flag)
        HookStatusReporter.reportBehavior(hookId, JSONObject().apply {
            put("action", "block_method")
            put("blocked", blocked)
        })
        if (blocked) null else chain.proceed()
    }

    private fun returnConstant(hookId: String, value: Any?, config: SharedPreferences, flag: String?) = XposedInterface.Hooker { chain ->
        val enabled = flag == null || RuleEngine.isConfigEnabled(config, flag)
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
    private fun checkInSet(hookId: String, action: HookAction, config: SharedPreferences) = XposedInterface.Hooker { chain ->
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
    private fun mergeSet(hookId: String, action: HookAction, config: SharedPreferences) = XposedInterface.Hooker { chain ->
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
    private fun mergeList(hookId: String, action: HookAction, config: SharedPreferences) = XposedInterface.Hooker { chain ->
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

    private fun fieldSet(hookId: String, action: HookAction, config: SharedPreferences, flag: String?) = XposedInterface.Hooker { chain ->
        val result = chain.proceed()
        if (flag != null && !RuleEngine.isConfigEnabled(config, flag)) return@Hooker result
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

    private fun forceArg(hookId: String, action: HookAction, config: SharedPreferences, flag: String?) = XposedInterface.Hooker { chain ->
        if (flag != null && !RuleEngine.isConfigEnabled(config, flag)) {
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

    private fun getDataSource(source: String?, config: SharedPreferences): Set<String> {
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
