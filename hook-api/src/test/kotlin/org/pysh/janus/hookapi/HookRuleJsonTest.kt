package org.pysh.janus.hookapi

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM parsing tests for the HookRule JSON contract. These run via
 * `./gradlew :hook-api:test` without any Android/Robolectric bootstrapping,
 * proving the :hook-api module is truly Android-free.
 */
class HookRuleJsonTest {

    @Test
    fun `engine rule parses id, target package and engine key`() {
        val json = JSONObject(
            """
            {
              "schema": "janus_hook_rule_v1",
              "id": "sample_engine_rule",
              "name": "Sample",
              "targetPackage": "com.example.host",
              "engine": "sampleEngine",
              "targets": {
                "clazz": "com.example.host.Target",
                "method": "doStuff"
              }
            }
            """.trimIndent(),
        )

        val rule = HookRule.fromJson(json, builtin = true)

        assertEquals(HookRule.SCHEMA_V1, rule.schema)
        assertEquals("sample_engine_rule", rule.id)
        assertEquals("com.example.host", rule.targetPackage)
        assertEquals("sampleEngine", rule.engine)
        assertTrue(rule.isEngineRule)
        assertTrue(rule.builtin)
        assertEquals("com.example.host.Target", rule.targets?.get("clazz"))
        assertEquals("doStuff", rule.targets?.get("method"))
    }

    @Test
    fun `simple hook rule parses hooks array and action details`() {
        val json = JSONObject(
            """
            {
              "schema": "janus_hook_rule_v1",
              "id": "sample_simple_rule",
              "name": "Simple",
              "targetPackage": "com.example.host",
              "order": 50,
              "hooks": [
                {
                  "id": "hook_1",
                  "className": "com.example.host.Foo",
                  "methodName": "bar",
                  "paramTypes": ["java.lang.String"],
                  "action": { "type": "returnConstant", "value": true }
                }
              ]
            }
            """.trimIndent(),
        )

        val rule = HookRule.fromJson(json)

        assertEquals(50, rule.order)
        assertNull(rule.engine)
        assertTrue(rule.hasActionType("returnConstant"))
        val hook = rule.hooks?.single()
        assertNotNull(hook)
        assertEquals("com.example.host.Foo", hook!!.className)
        assertEquals("bar", hook.methodName)
        assertEquals(listOf("java.lang.String"), hook.paramTypes)
        assertEquals("returnConstant", hook.action.type)
        assertEquals(true, hook.action.value)
    }

    @Test
    fun `nullable string fields return null for empty or JSON-null values`() {
        val json = JSONObject(
            """
            {
              "schema": "janus_hook_rule_v1",
              "id": "x",
              "name": "X",
              "targetPackage": "p",
              "author": "",
              "description": "null",
              "configFlag": ""
            }
            """.trimIndent(),
        )

        val rule = HookRule.fromJson(json)

        assertNull(rule.author)
        assertNull(rule.description)
        assertNull(rule.configFlag)
    }
}
