package org.pysh.janus.hook.engine

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [HookRule], [HookTarget], and [HookAction] data classes
 * including JSON round-trip, defaults, and computed properties.
 */
@RunWith(RobolectricTestRunner::class)
class HookRuleTest {

    // ── fromJson: simple rule ───────────────────────────────────────────

    @Test
    fun fromJson_simpleRule_roundTrips() {
        val json = JSONObject().apply {
            put("schema", "janus_hook_rule_v1")
            put("id", "test_simple")
            put("name", "Test Simple Rule")
            put("targetPackage", "com.example.app")
            put("configFlag", "test_flag")
            put("order", 50)
            put("hooks", JSONArray().put(
                JSONObject().apply {
                    put("id", "hook1")
                    put("className", "com.example.MyClass")
                    put("methodName", "myMethod")
                    put("paramTypes", JSONArray().put("int").put("java.lang.String"))
                    put("action", JSONObject().apply {
                        put("type", "block_method")
                    })
                },
            ))
        }
        val rule = HookRule.fromJson(json)

        assertEquals("janus_hook_rule_v1", rule.schema)
        assertEquals("test_simple", rule.id)
        assertEquals("Test Simple Rule", rule.name)
        assertEquals("com.example.app", rule.targetPackage)
        assertEquals("test_flag", rule.configFlag)
        assertEquals(50, rule.order)
        assertFalse(rule.isEngineRule)
        val hooks = rule.hooks
        assertEquals(1, hooks?.size)

        val hook = hooks!![0]
        assertEquals("hook1", hook.id)
        assertEquals("com.example.MyClass", hook.className)
        assertEquals("myMethod", hook.methodName)
        assertEquals(listOf("int", "java.lang.String"), hook.paramTypes)
        assertEquals("block_method", hook.action.type)
    }

    // ── fromJson: engine rule ───────────────────────────────────────────

    @Test
    fun fromJson_engineRule_roundTrips() {
        val json = JSONObject().apply {
            put("schema", "janus_hook_rule_v1")
            put("id", "test_engine")
            put("name", "Test Engine Rule")
            put("targetPackage", "com.example.app")
            put("engine", "whitelist")
            put("targets", JSONObject().apply {
                put("class_a", "com.example.ClassA")
                put("method_b", "doSomething")
            })
        }
        val rule = HookRule.fromJson(json)

        assertTrue(rule.isEngineRule)
        assertEquals("whitelist", rule.engine)
        val targets = rule.targets
        assertEquals("com.example.ClassA", targets?.get("class_a"))
        assertEquals("doSomething", targets?.get("method_b"))
        assertNull(rule.hooks)
    }

    // ── defaults ────────────────────────────────────────────────────────

    @Test
    fun fromJson_missingOptionalFields_usesDefaults() {
        val json = JSONObject().apply {
            put("schema", "janus_hook_rule_v1")
            put("id", "minimal")
            put("name", "Minimal Rule")
            put("targetPackage", "com.example.app")
            put("engine", "whitelist")
            put("targets", JSONObject().put("key", "value"))
        }
        val rule = HookRule.fromJson(json)

        assertEquals(1, rule.version)
        assertEquals(100, rule.order)
        assertNull(rule.author)
        assertNull(rule.description)
        assertNull(rule.configFlag)
        assertFalse(rule.builtin)
    }

    @Test
    fun fromJson_builtinFlag_setByParameter() {
        val json = JSONObject().apply {
            put("schema", "janus_hook_rule_v1")
            put("id", "test")
            put("name", "Test")
            put("targetPackage", "com.example")
            put("engine", "whitelist")
            put("targets", JSONObject().put("k", "v"))
        }
        assertTrue(HookRule.fromJson(json, builtin = true).builtin)
        assertFalse(HookRule.fromJson(json, builtin = false).builtin)
    }

    // ── nullableString ──────────────────────────────────────────────────

    @Test
    fun fromJson_emptyString_treatedAsNull() {
        val json = JSONObject().apply {
            put("schema", "janus_hook_rule_v1")
            put("id", "test")
            put("name", "Test")
            put("targetPackage", "com.example")
            put("configFlag", "")
            put("engine", "")
            put("author", "null")
            put("hooks", JSONArray().put(
                JSONObject().apply {
                    put("id", "h1")
                    put("className", "C")
                    put("action", JSONObject().put("type", "block_method"))
                },
            ))
        }
        val rule = HookRule.fromJson(json)

        assertNull("empty string should become null", rule.configFlag)
        assertNull("empty string should become null", rule.engine)
        assertNull("literal 'null' string should become null", rule.author)
    }

    // ── computed properties ─────────────────────────────────────────────

    @Test
    fun isEngineRule_trueWhenEnginePresent() {
        val rule = makeRule(engine = "whitelist")
        assertTrue(rule.isEngineRule)
    }

    @Test
    fun isEngineRule_falseWhenEngineNull() {
        val rule = makeRule(engine = null)
        assertFalse(rule.isEngineRule)
    }

    @Test
    fun hasActionType_findsMatchingType() {
        val rule = makeRule(
            hooks = listOf(
                HookTarget("h1", "C", action = HookAction("block_method")),
                HookTarget("h2", "D", action = HookAction("field_set")),
            ),
        )
        assertTrue(rule.hasActionType("block_method"))
        assertTrue(rule.hasActionType("field_set"))
    }

    @Test
    fun hasActionType_returnsFalseForNoMatch() {
        val rule = makeRule(
            hooks = listOf(
                HookTarget("h1", "C", action = HookAction("block_method")),
            ),
        )
        assertFalse(rule.hasActionType("return_constant"))
    }

    @Test
    fun hasActionType_returnsFalseForEngineRule() {
        val rule = makeRule(engine = "whitelist", hooks = null)
        assertFalse(rule.hasActionType("block_method"))
    }

    // ── HookAction.fromJson ─────────────────────────────────────────────

    @Test
    fun hookAction_allFieldsParsed() {
        val json = JSONObject().apply {
            put("type", "lyric_extract")
            put("value", "/some/path")
            put("field", "myField")
            put("source", "whitelist")
            put("paramIndex", 2)
            put("format", "ttml")
        }
        val action = HookAction.fromJson(json)

        assertEquals("lyric_extract", action.type)
        assertEquals("/some/path", action.value)
        assertEquals("myField", action.field)
        assertEquals("whitelist", action.source)
        assertEquals(2, action.paramIndex)
        assertEquals("ttml", action.format)
    }

    @Test
    fun hookAction_missingOptionals_areNull() {
        val json = JSONObject().apply {
            put("type", "block_method")
        }
        val action = HookAction.fromJson(json)

        assertEquals("block_method", action.type)
        assertNull(action.value)
        assertNull(action.field)
        assertNull(action.source)
        assertNull(action.paramIndex)
        assertNull(action.format)
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private fun makeRule(
        engine: String? = null,
        hooks: List<HookTarget>? = null,
        targets: Map<String, String>? = if (engine != null) mapOf("k" to "v") else null,
    ) = HookRule(
        schema = HookRule.SCHEMA_V1,
        id = "test",
        name = "Test",
        targetPackage = "com.example",
        engine = engine,
        hooks = hooks,
        targets = targets,
    )
}
