package com.example.offlinehqasr.summary

import org.json.JSONArray
import org.json.JSONObject

object StructuredSummaryParser {
    fun parseOrNull(json: String?): StructuredSummary? {
        if (json.isNullOrBlank()) return null
        return runCatching { JSONObject(json) }
            .mapCatching { parse(it) }
            .getOrNull()
    }

    fun parse(root: JSONObject): StructuredSummary {
        val summary = StructuredSummary(
            title = root.optString("title"),
            summary = StructuredSummary.SummarySection(
                context = root.optJSONObject("summary")?.optString("context") ?: "",
                bullets = root.optJSONObject("summary")?.optJSONArray("bullets").toStringList()
            ),
            actions = root.optJSONArray("actions").toList { obj ->
                StructuredSummary.Action(
                    who = obj.optString("who"),
                    what = obj.optString("what"),
                    due = obj.optString("due"),
                    priority = obj.optString("priority"),
                    status = obj.optString("status"),
                    confidence = obj.optDouble("confidence", 0.0),
                    relatedSegments = obj.optJSONArray("relatedSegments").toList { seg ->
                        StructuredSummary.Segment(
                            startMs = seg.optLong("startMs", 0),
                            endMs = seg.optLong("endMs", 0)
                        )
                    }
                )
            },
            decisions = root.optJSONArray("decisions").toList { obj ->
                StructuredSummary.Decision(
                    description = obj.optString("description"),
                    owner = obj.optString("owner"),
                    timestampMs = obj.optLong("timestampMs", 0),
                    confidence = obj.optDouble("confidence", 0.0)
                )
            },
            citations = root.optJSONArray("citations").toList { obj ->
                StructuredSummary.Citation(
                    quote = obj.optString("quote"),
                    speaker = obj.optString("speaker"),
                    startMs = obj.optLong("startMs", 0),
                    endMs = obj.optLong("endMs", 0)
                )
            },
            sentiments = root.optJSONArray("sentiments").toList { obj ->
                StructuredSummary.Sentiment(
                    target = obj.optString("target"),
                    value = obj.optString("value"),
                    score = obj.optDouble("score", 0.0)
                )
            },
            participants = root.optJSONArray("participants").toList { obj ->
                StructuredSummary.Participant(
                    name = obj.optString("name"),
                    role = obj.optString("role")
                )
            },
            tags = root.optJSONArray("tags").toStringList(),
            keywords = root.optJSONArray("keywords").toStringList(),
            topics = root.optJSONArray("topics").toStringList(),
            timings = root.optJSONArray("timings").toList { obj ->
                StructuredSummary.Timing(
                    label = obj.optString("label"),
                    startMs = obj.optLong("startMs", 0),
                    endMs = obj.optLong("endMs", 0)
                )
            },
            durationMs = root.optLong("durationMs", 0)
        )
        return summary
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until length()) {
            optString(i)?.takeIf { it.isNotBlank() }?.let { result.add(it) }
        }
        return result
    }

    private fun <T> JSONArray?.toList(mapper: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        val result = mutableListOf<T>()
        for (i in 0 until length()) {
            val obj = optJSONObject(i) ?: continue
            result.add(mapper(obj))
        }
        return result
    }
}
