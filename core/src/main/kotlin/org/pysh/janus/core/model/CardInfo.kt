package org.pysh.janus.core.model

import org.json.JSONArray
import org.json.JSONObject

data class CardInfo(
    val slot: Int,
    val name: String,
    val fileName: String,
    val enabled: Boolean = false,
    val refreshInterval: Int = 30,
    val priority: Int = 100,
    val sortOrder: Int = 0,
) {
    val businessName: String
        get() = "janus_card_$slot"

    fun toJson(): JSONObject = JSONObject().apply {
        put("slot", slot)
        put("name", name)
        put("fileName", fileName)
        put("enabled", enabled)
        put("refreshInterval", refreshInterval)
        put("priority", priority)
        put("sortOrder", sortOrder)
    }

    companion object {
        fun fromJson(json: JSONObject): CardInfo = CardInfo(
            slot = json.getInt("slot"),
            name = json.getString("name"),
            fileName = json.optString("fileName", ""),
            enabled = json.optBoolean("enabled", false),
            refreshInterval = json.optInt("refreshInterval", 30),
            priority = json.optInt("priority", 100),
            sortOrder = json.optInt("sortOrder", 0),
        )

        fun listToJson(cards: List<CardInfo>): String {
            val arr = JSONArray()
            cards.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun listFromJson(json: String): List<CardInfo> {
            if (json.isBlank()) return emptyList()
            val arr = JSONArray(json)
            return (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        }
    }
}
