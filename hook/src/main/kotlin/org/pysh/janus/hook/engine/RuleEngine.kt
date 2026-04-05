package org.pysh.janus.hook.engine

import android.util.Log
import io.github.libxposed.api.XposedInterface
import org.pysh.janus.hookapi.ConfigSource
import org.pysh.janus.hookapi.HookRule

/**
 * Core hook rule engine. Loads rules and dispatches to ActionExecutor (simple rules)
 * or HookEnginePlugin (engine rules).
 *
 * All rules are always installed regardless of configFlag state.
 * configFlag is checked at RUNTIME inside each hook interceptor,
 * so toggling a switch takes effect immediately without restarting.
 */
class RuleEngine(
    private val module: XposedInterface,
    private val config: ConfigSource,
) {
    private val engines = mutableMapOf<String, HookEnginePlugin>()

    fun registerEngine(name: String, engine: HookEnginePlugin) {
        engines[name] = engine
    }

    fun engineNames(): Set<String> = engines.keys

    /**
     * Install all hooks defined by the given rules.
     * Rules should already be filtered by targetPackage and sorted by order.
     */
    fun install(rules: List<HookRule>, classLoader: ClassLoader) {
        for (rule in rules) {
            try {
                if (rule.isEngineRule) {
                    installEngineRule(rule, classLoader)
                } else {
                    installSimpleRule(rule, classLoader)
                }
                Log.d(TAG, "Rule ${rule.id} installed successfully")
            } catch (e: Throwable) {
                Log.e(TAG, "Rule ${rule.id} failed: ${e.message}")
            }
        }
    }

    private fun installSimpleRule(rule: HookRule, classLoader: ClassLoader) {
        for (hook in rule.hooks ?: return) {
            ActionExecutor.install(module, hook, classLoader, rule, config)
        }
    }

    private fun installEngineRule(rule: HookRule, classLoader: ClassLoader) {
        val engine = engines[rule.engine]
        if (engine == null) {
            Log.e(TAG, "Unknown engine: ${rule.engine} for rule ${rule.id}")
            return
        }
        engine.install(module, rule, classLoader, config)
    }

    private companion object {
        const val TAG = "Janus-Engine"
    }
}
