package org.pysh.janus.hook.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Auto-discovering schema validation for all bundled hook rule JSON files.
 *
 * Adding a new rule file under `assets/rules/` automatically includes it
 * in these tests — no test code changes needed.
 */
@RunWith(RobolectricTestRunner::class)
class HookRuleSchemaTest {

    private lateinit var context: Context
    private lateinit var ruleJsons: Map<String, JSONObject>

    /**
     * Action types supported by [ActionExecutor.createHooker] (line ~58).
     * Update this set when adding a new action type to the `when` block.
     * Intentionally hardcoded — test failure forces acknowledgment of new types.
     */
    private val knownActionTypes = setOf(
        "block_method",
        "return_constant",
        "check_in_set",
        "merge_set",
        "merge_list",
        "field_set",
        "path_redirect",
        "force_arg",
        "lyric_extract",
    )

    /**
     * Engine names registered in [HookEntry.onPackageLoaded] (line ~66-69).
     * Update this set when calling registerEngine() with a new name.
     */
    private val knownEngines = setOf(
        "whitelist",
        "system_card",
        "card_injection",
        "wallpaper_keep_alive",
    )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val files = context.assets.list("rules") ?: emptyArray()
        ruleJsons = files
            .filter { it.endsWith(".json") }
            .associateWith { fileName ->
                val text = context.assets.open("rules/$fileName").bufferedReader().readText()
                JSONObject(text)
            }
        assertTrue("No rule files found in assets/rules/", ruleJsons.isNotEmpty())
    }

    @Test
    fun allRules_parseWithoutException() {
        for ((fileName, json) in ruleJsons) {
            try {
                HookRule.fromJson(json, builtin = true)
            } catch (e: Throwable) {
                fail("Failed to parse $fileName: ${e.message}")
            }
        }
    }

    @Test
    fun allRules_haveValidSchema() {
        for ((fileName, _) in ruleJsons) {
            val rule = HookRule.fromJson(ruleJsons[fileName]!!, builtin = true)
            assertEquals("$fileName: wrong schema", HookRule.SCHEMA_V1, rule.schema)
        }
    }

    @Test
    fun allRules_haveRequiredFields() {
        for ((fileName, _) in ruleJsons) {
            val rule = HookRule.fromJson(ruleJsons[fileName]!!, builtin = true)
            assertTrue("$fileName: id is blank", rule.id.isNotBlank())
            assertTrue("$fileName: name is blank", rule.name.isNotBlank())
            assertTrue("$fileName: targetPackage is blank", rule.targetPackage.isNotBlank())
        }
    }

    @Test
    fun allRules_haveUniqueIds() {
        val ids = mutableMapOf<String, String>()
        for ((fileName, _) in ruleJsons) {
            val rule = HookRule.fromJson(ruleJsons[fileName]!!, builtin = true)
            val existing = ids.put(rule.id, fileName)
            if (existing != null) {
                fail("Duplicate rule id '${rule.id}' in $fileName and $existing")
            }
        }
    }

    @Test
    fun allRules_orderIsPositive() {
        for ((fileName, _) in ruleJsons) {
            val rule = HookRule.fromJson(ruleJsons[fileName]!!, builtin = true)
            assertTrue("$fileName: order ${rule.order} must be > 0", rule.order > 0)
        }
    }

    @Test
    fun simpleRules_haveValidHooks() {
        for ((fileName, _) in ruleJsons) {
            val rule = HookRule.fromJson(ruleJsons[fileName]!!, builtin = true)
            if (rule.isEngineRule) continue

            val hooks = rule.hooks
            assertTrue("$fileName: simple rule must have non-empty hooks", !hooks.isNullOrEmpty())
            for (hook in hooks!!) {
                assertTrue("$fileName/${hook.id}: className is blank", hook.className.isNotBlank())
                assertTrue("$fileName/${hook.id}: action.type is blank", hook.action.type.isNotBlank())
            }
        }
    }

    @Test
    fun engineRules_haveValidTargets() {
        for ((fileName, _) in ruleJsons) {
            val rule = HookRule.fromJson(ruleJsons[fileName]!!, builtin = true)
            if (!rule.isEngineRule) continue

            assertTrue("$fileName: engine name is blank", rule.engine!!.isNotBlank())
            assertTrue("$fileName: engine rule must have non-empty targets", !rule.targets.isNullOrEmpty())
        }
    }

    @Test
    fun allRules_actionTypesAreKnown() {
        for ((fileName, _) in ruleJsons) {
            val rule = HookRule.fromJson(ruleJsons[fileName]!!, builtin = true)
            for (hook in rule.hooks ?: continue) {
                assertTrue(
                    "$fileName/${hook.id}: unknown action type '${hook.action.type}'",
                    hook.action.type in knownActionTypes,
                )
            }
        }
    }

    @Test
    fun allRules_engineNamesAreKnown() {
        for ((fileName, _) in ruleJsons) {
            val rule = HookRule.fromJson(ruleJsons[fileName]!!, builtin = true)
            if (!rule.isEngineRule) continue
            assertTrue(
                "$fileName: unknown engine '${rule.engine}'",
                rule.engine in knownEngines,
            )
        }
    }

    @Test
    fun allRules_eitherHooksOrEngine() {
        for ((fileName, _) in ruleJsons) {
            val rule = HookRule.fromJson(ruleJsons[fileName]!!, builtin = true)
            val hasHooks = !rule.hooks.isNullOrEmpty()
            val hasEngine = rule.isEngineRule
            assertTrue(
                "$fileName: rule must have either hooks or engine (has hooks=$hasHooks, engine=$hasEngine)",
                hasHooks || hasEngine,
            )
        }
    }
}
