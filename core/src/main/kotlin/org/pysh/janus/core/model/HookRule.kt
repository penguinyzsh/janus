package org.pysh.janus.core.model

import org.json.JSONObject

/** Safe optString that returns null for missing, empty, or JSON-null values. */
private fun JSONObject.nullableString(key: String): String? =
    optString(key).takeIf { it.isNotEmpty() && it != "null" }

data class HookRule(
    val schema: String,
    val id: String,
    val name: String,
    val version: Int = 1,
    val author: String? = null,
    val description: String? = null,
    val targetPackage: String,
    val configFlag: String? = null,
    val order: Int = 100,
    val builtin: Boolean = false,
    val engine: String? = null,
    val hooks: List<HookTarget>? = null,
    val targets: Map<String, String>? = null,
) {
    val isEngineRule: Boolean get() = engine != null

    fun hasActionType(type: String): Boolean =
        hooks?.any { it.action.type == type } == true

    companion object {
        const val SCHEMA_V1 = "janus_hook_rule_v1"

        fun fromJson(json: JSONObject, builtin: Boolean = false): HookRule {
            val hooks = json.optJSONArray("hooks")?.let { arr ->
                (0 until arr.length()).map { HookTarget.fromJson(arr.getJSONObject(it)) }
            }
            val targets = json.optJSONObject("targets")?.let { obj ->
                obj.keys().asSequence().associateWith { obj.getString(it) }
            }
            return HookRule(
                schema = json.getString("schema"),
                id = json.getString("id"),
                name = json.getString("name"),
                version = json.optInt("version", 1),
                author = json.nullableString("author"),
                description = json.nullableString("description"),
                targetPackage = json.getString("targetPackage"),
                configFlag = json.nullableString("configFlag"),
                order = json.optInt("order", 100),
                builtin = builtin,
                engine = json.nullableString("engine"),
                hooks = hooks,
                targets = targets,
            )
        }
    }
}

data class HookTarget(
    val id: String,
    val className: String,
    val methodName: String? = null,
    val paramTypes: List<String>? = null,
    val action: HookAction,
) {
    companion object {
        fun fromJson(json: JSONObject): HookTarget {
            val paramTypes = json.optJSONArray("paramTypes")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            }
            return HookTarget(
                id = json.getString("id"),
                className = json.getString("className"),
                methodName = json.nullableString("methodName"),
                paramTypes = paramTypes,
                action = HookAction.fromJson(json.getJSONObject("action")),
            )
        }
    }
}

data class HookAction(
    val type: String,
    val value: Any? = null,
    val field: String? = null,
    val source: String? = null,
    val paramIndex: Int? = null,
    val format: String? = null,
) {
    companion object {
        fun fromJson(json: JSONObject): HookAction {
            return HookAction(
                type = json.getString("type"),
                value = json.opt("value"),
                field = json.nullableString("field"),
                source = json.nullableString("source"),
                paramIndex = if (json.has("paramIndex")) json.getInt("paramIndex") else null,
                format = json.nullableString("format"),
            )
        }
    }
}
