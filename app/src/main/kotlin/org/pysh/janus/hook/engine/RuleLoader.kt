package org.pysh.janus.hook.engine

import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.util.Log
import org.json.JSONObject

/**
 * Loads hook rules from bundled assets and user-imported RemotePreferences.
 */
object RuleLoader {

    private const val TAG = "Janus-RuleLoader"
    private const val RULES_PREFS_GROUP = "janus_rules"
    private const val KEY_ENABLED_RULES = "enabled_rules"

    /**
     * Load all rules applicable to [packageName].
     * Merges bundled (assets/rules/) and user-imported rules, sorted by order.
     */
    fun loadForPackage(
        packageName: String,
        moduleAppInfo: ApplicationInfo,
        rulesPrefs: SharedPreferences? = null,
    ): List<HookRule> {
        val bundled = loadBundledRules(moduleAppInfo)
        val custom = if (rulesPrefs != null) loadCustomRules(rulesPrefs) else emptyList()
        val enabledIds = rulesPrefs?.getStringSet(KEY_ENABLED_RULES, null)

        return (bundled + custom)
            .filter { it.targetPackage == packageName }
            .filter { rule ->
                // Built-in rules are enabled by default; custom rules check enabled set
                rule.builtin || enabledIds == null || rule.id in enabledIds
            }
            .sortedBy { it.order }
    }

    private fun loadBundledRules(moduleAppInfo: ApplicationInfo): List<HookRule> {
        return try {
            val appFile = java.util.zip.ZipFile(moduleAppInfo.sourceDir)
            val rules = mutableListOf<HookRule>()
            for (entry in appFile.entries()) {
                if (entry.name.startsWith("assets/rules/") && entry.name.endsWith(".json")) {
                    try {
                        val json = appFile.getInputStream(entry).bufferedReader().readText()
                        val rule = HookRule.fromJson(JSONObject(json), builtin = true)
                        if (rule.schema == HookRule.SCHEMA_V1) {
                            rules.add(rule)
                        }
                    } catch (e: Throwable) {
                        Log.w(TAG, "Failed to parse rule ${entry.name}: ${e.message}")
                    }
                }
            }
            appFile.close()
            rules
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load bundled rules: ${e.message}")
            emptyList()
        }
    }

    private fun loadCustomRules(prefs: SharedPreferences): List<HookRule> {
        val rules = mutableListOf<HookRule>()
        for ((key, value) in prefs.all) {
            if (key == KEY_ENABLED_RULES) continue
            if (value !is String) continue
            try {
                val rule = HookRule.fromJson(JSONObject(value), builtin = false)
                if (rule.schema == HookRule.SCHEMA_V1) {
                    rules.add(rule)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to parse custom rule $key: ${e.message}")
            }
        }
        return rules
    }
}
