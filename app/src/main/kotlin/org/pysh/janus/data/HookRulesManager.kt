package org.pysh.janus.data

import android.content.Context
import android.net.Uri
import android.util.Log
import io.github.libxposed.service.XposedService
import org.json.JSONObject
import org.pysh.janus.JanusApplication
import org.pysh.janus.hook.engine.HookRule
import java.io.File

/**
 * Manages hook rule files on the app side.
 * Handles import, storage, enable/disable, deletion, and scope management.
 *
 * Rules are stored in:
 * - Built-in: assets/rules/ (read-only, always present)
 * - User-imported: app files/rules/ (read-write)
 *
 * Enabled rules are synced to RemotePreferences for the hook side to read.
 */
class HookRulesManager(private val context: Context) {

    companion object {
        private const val TAG = "Janus-Rules"
        private const val PREFS_NAME = "janus_rules_local"
        private const val KEY_ENABLED_IDS = "enabled_rule_ids"
        private const val KEY_DISABLED_BUILTIN_IDS = "disabled_builtin_ids"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val rulesDir = File(context.filesDir, "rules").also { it.mkdirs() }

    // ── Read ─────────────────────────────────────────────────────────

    /** Get all rules (built-in + user-imported) with their enabled status. */
    fun getAllRules(): List<RuleInfo> {
        val builtin = loadBuiltinRules()
        val custom = loadCustomRules()
        val disabledBuiltins = prefs.getStringSet(KEY_DISABLED_BUILTIN_IDS, emptySet()) ?: emptySet()
        val enabledCustom = prefs.getStringSet(KEY_ENABLED_IDS, emptySet()) ?: emptySet()

        val result = mutableListOf<RuleInfo>()
        for (rule in builtin) {
            result.add(RuleInfo(rule, builtin = true, enabled = rule.id !in disabledBuiltins))
        }
        for (rule in custom) {
            result.add(RuleInfo(rule, builtin = false, enabled = rule.id in enabledCustom))
        }
        return result.sortedBy { it.rule.order }
    }

    /** Get only built-in rules with their enabled status. */
    fun getBuiltinRules(): List<RuleInfo> {
        val disabledBuiltins = prefs.getStringSet(KEY_DISABLED_BUILTIN_IDS, emptySet()) ?: emptySet()
        return loadBuiltinRules().map { rule ->
            RuleInfo(rule, builtin = true, enabled = rule.id !in disabledBuiltins)
        }.sortedBy { it.rule.order }
    }

    /** Get custom rules filtered by target package. */
    fun getRulesForPackage(packageName: String): List<RuleInfo> {
        val enabledCustom = prefs.getStringSet(KEY_ENABLED_IDS, emptySet()) ?: emptySet()
        return loadCustomRules()
            .filter { it.targetPackage == packageName }
            .map { rule -> RuleInfo(rule, builtin = false, enabled = rule.id in enabledCustom) }
            .sortedBy { it.rule.order }
    }

    // ── Import ───────────────────────────────────────────────────────

    /** Import a rule from a content URI (file picker). Returns the rule or null on error.
     *  If [expectedPackage] is set, returns null when the rule's targetPackage doesn't match. */
    fun importRule(uri: Uri, expectedPackage: String? = null): HookRule? {
        return try {
            val json = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.readText() ?: return null
            val rule = HookRule.fromJson(JSONObject(json))
            if (rule.schema != HookRule.SCHEMA_V1) {
                Log.e(TAG, "Unsupported schema: ${rule.schema}")
                return null
            }
            if (expectedPackage != null && rule.targetPackage != expectedPackage) {
                Log.w(TAG, "Package mismatch: rule targets ${rule.targetPackage}, expected $expectedPackage")
                return null
            }

            // Save to local storage
            val file = File(rulesDir, "${rule.id}.json")
            file.writeText(json)

            // Enable it
            val enabled = prefs.getStringSet(KEY_ENABLED_IDS, mutableSetOf())?.toMutableSet()
                ?: mutableSetOf()
            enabled.add(rule.id)
            prefs.edit().putStringSet(KEY_ENABLED_IDS, enabled).commit()

            // Sync to RemotePreferences
            syncToRemotePrefs()

            // Request scope if needed
            requestScopeIfNeeded(rule.targetPackage)

            Log.i(TAG, "Imported rule: ${rule.id} targeting ${rule.targetPackage}")
            rule
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to import rule: ${e.message}")
            null
        }
    }

    // ── Enable/Disable ───────────────────────────────────────────────

    fun setRuleEnabled(ruleId: String, builtin: Boolean, enabled: Boolean) {
        if (builtin) {
            val disabled = prefs.getStringSet(KEY_DISABLED_BUILTIN_IDS, mutableSetOf())?.toMutableSet()
                ?: mutableSetOf()
            if (enabled) disabled.remove(ruleId) else disabled.add(ruleId)
            prefs.edit().putStringSet(KEY_DISABLED_BUILTIN_IDS, disabled).commit()
        } else {
            val enabledSet = prefs.getStringSet(KEY_ENABLED_IDS, mutableSetOf())?.toMutableSet()
                ?: mutableSetOf()
            if (enabled) enabledSet.add(ruleId) else enabledSet.remove(ruleId)
            prefs.edit().putStringSet(KEY_ENABLED_IDS, enabledSet).commit()
        }
        syncToRemotePrefs()
    }

    // ── Delete ───────────────────────────────────────────────────────

    fun deleteRule(ruleId: String): Boolean {
        val file = File(rulesDir, "$ruleId.json")
        if (!file.exists()) return false

        file.delete()
        val enabled = prefs.getStringSet(KEY_ENABLED_IDS, mutableSetOf())?.toMutableSet()
            ?: mutableSetOf()
        enabled.remove(ruleId)
        prefs.edit().putStringSet(KEY_ENABLED_IDS, enabled).commit()

        syncToRemotePrefs()
        Log.i(TAG, "Deleted rule: $ruleId")
        return true
    }

    // ── Sync ─────────────────────────────────────────────────────────

    /** Sync all enabled rules to RemotePreferences for the hook side. */
    fun syncToRemotePrefs() {
        val service = JanusApplication.instance?.xposedService ?: return
        try {
            val rulesPrefs = service.getRemotePreferences("janus_rules")
            val editor = rulesPrefs.edit()

            // Clear existing custom rules
            for (key in rulesPrefs.all.keys) {
                if (key != "enabled_rules") editor.remove(key)
            }

            // Write enabled custom rules
            val enabledIds = mutableSetOf<String>()
            val disabledBuiltins = prefs.getStringSet(KEY_DISABLED_BUILTIN_IDS, emptySet()) ?: emptySet()

            // All builtin rules are enabled by default (except explicitly disabled)
            for (rule in loadBuiltinRules()) {
                if (rule.id !in disabledBuiltins) {
                    enabledIds.add(rule.id)
                }
            }

            // Custom rules
            val enabledCustom = prefs.getStringSet(KEY_ENABLED_IDS, emptySet()) ?: emptySet()
            for (file in rulesDir.listFiles()?.filter { it.extension == "json" } ?: emptyList()) {
                try {
                    val rule = HookRule.fromJson(JSONObject(file.readText()))
                    if (rule.id in enabledCustom) {
                        editor.putString(rule.id, file.readText())
                        enabledIds.add(rule.id)
                    }
                } catch (_: Throwable) { }
            }

            editor.putStringSet("enabled_rules", enabledIds)
            editor.commit()
            Log.d(TAG, "Synced ${enabledIds.size} rules to RemotePreferences")
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to sync rules: ${e.message}")
        }
    }

    // ── Scope ────────────────────────────────────────────────────────

    private fun requestScopeIfNeeded(targetPackage: String) {
        val service = JanusApplication.instance?.xposedService ?: return
        try {
            val currentScope = service.scope
            if (targetPackage !in currentScope) {
                service.requestScope(
                    listOf(targetPackage),
                    object : XposedService.OnScopeEventListener {
                        override fun onScopeRequestApproved(approved: List<String>) {
                            Log.i(TAG, "Scope approved for: $approved")
                        }

                        override fun onScopeRequestFailed(message: String) {
                            Log.w(TAG, "Scope request failed: $message")
                        }
                    }
                )
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to manage scope: ${e.message}")
        }
    }

    // ── Internal ─────────────────────────────────────────────────────

    private fun loadBuiltinRules(): List<HookRule> {
        val rules = mutableListOf<HookRule>()
        try {
            val files = context.assets.list("rules") ?: return rules
            for (name in files) {
                if (!name.endsWith(".json")) continue
                try {
                    val json = context.assets.open("rules/$name").bufferedReader().readText()
                    rules.add(HookRule.fromJson(JSONObject(json), builtin = true))
                } catch (e: Throwable) {
                    Log.w(TAG, "Failed to parse builtin rule $name: ${e.message}")
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to list builtin rules: ${e.message}")
        }
        return rules
    }

    private fun loadCustomRules(): List<HookRule> {
        val rules = mutableListOf<HookRule>()
        for (file in rulesDir.listFiles()?.filter { it.extension == "json" } ?: emptyList()) {
            try {
                rules.add(HookRule.fromJson(JSONObject(file.readText())))
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to parse custom rule ${file.name}: ${e.message}")
            }
        }
        return rules
    }

    /** Info wrapper combining a rule with its enabled state. */
    data class RuleInfo(
        val rule: HookRule,
        val builtin: Boolean,
        val enabled: Boolean,
    )
}
