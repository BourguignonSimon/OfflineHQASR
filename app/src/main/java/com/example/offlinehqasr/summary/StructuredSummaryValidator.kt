package com.example.offlinehqasr.summary

import org.json.JSONArray
import org.json.JSONObject

class StructuredSummaryValidator {
    fun isValid(root: JSONObject): Boolean {
        val requiredKeys = listOf(
            "title",
            "summary",
            "actions",
            "decisions",
            "citations",
            "sentiments",
            "participants",
            "tags",
            "keywords",
            "topics",
            "timings",
            "durationMs"
        )
        if (!requiredKeys.all { root.has(it) }) return false
        if (root.optString("title").isBlank()) return false
        if (!root.optJSONObject("summary").hasNonEmpty("context")) return false
        if (!root.optJSONObject("summary").hasArray("bullets")) return false
        if (!root.optJSONArray("actions").allObjects { it.hasNonEmpty("what") }) return false
        if (!root.optJSONArray("decisions").allObjects { it.hasNonEmpty("description") }) return false
        if (!root.optJSONArray("participants").allObjects { it.hasNonEmpty("name") }) return false
        if (!root.optJSONArray("tags").allStrings()) return false
        if (!root.optJSONArray("keywords").allStrings()) return false
        if (!root.optJSONArray("topics").allStrings()) return false
        if (!root.optJSONArray("timings").allObjects { it.hasNonEmpty("label") }) return false
        if (root.optLong("durationMs", -1) < 0) return false
        return true
    }

    private fun JSONObject?.hasNonEmpty(key: String): Boolean {
        val value = this?.optString(key)
        return !value.isNullOrBlank()
    }

    private fun JSONObject?.hasArray(key: String): Boolean {
        val array = this?.optJSONArray(key)
        return array != null && array.length() >= 0
    }

    private fun JSONArray?.allObjects(predicate: (JSONObject) -> Boolean): Boolean {
        if (this == null) return false
        for (i in 0 until length()) {
            val obj = optJSONObject(i) ?: return false
            if (!predicate(obj)) return false
        }
        return true
    }

    private fun JSONArray?.allStrings(): Boolean {
        if (this == null) return false
        for (i in 0 until length()) {
            if (optString(i).isNullOrBlank()) return false
        }
        return true
    }
}
