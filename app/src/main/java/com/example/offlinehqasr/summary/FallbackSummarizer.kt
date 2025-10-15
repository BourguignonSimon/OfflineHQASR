package com.example.offlinehqasr.summary

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min

object FallbackSummarizer {
    fun generate(text: String, durationMs: Long): JSONObject {
        val cleanText = text.trim()
        val title = buildTitle(cleanText)
        val context = buildContext(cleanText)
        val bullets = extractSentences(cleanText, 5)
        val keywords = extractTopWords(cleanText, 12)
        val topics = keywords.take(5)
        val participants = extractPotentialParticipants(cleanText)

        val root = JSONObject()
        root.put("title", title)
        root.put("summary", JSONObject().apply {
            put("context", context)
            put("bullets", JSONArray().apply { bullets.forEach { put(it) } })
        })
        root.put("actions", JSONArray().apply {
            if (bullets.isNotEmpty()) {
                put(JSONObject().apply {
                    put("who", participants.firstOrNull() ?: "Équipe")
                    put("what", bullets.first())
                    put("due", "")
                    put("priority", "moyenne")
                    put("status", "à confirmer")
                    put("confidence", 0.2)
                    put("relatedSegments", JSONArray())
                })
            }
        })
        root.put("decisions", JSONArray().apply {
            if (bullets.size > 1) {
                put(JSONObject().apply {
                    put("description", bullets[1])
                    put("owner", participants.firstOrNull() ?: "Groupe")
                    put("timestampMs", 0)
                    put("confidence", 0.2)
                })
            }
        })
        root.put("citations", JSONArray().apply {
            extractSentences(cleanText, 2).forEach { sentence ->
                put(JSONObject().apply {
                    put("quote", sentence)
                    put("speaker", participants.firstOrNull() ?: "Intervenant")
                    put("startMs", 0)
                    put("endMs", min(durationMs, 30_000))
                })
            }
        })
        root.put("sentiments", JSONArray().apply {
            put(JSONObject().apply {
                put("target", "général")
                put("value", "neutre")
                put("score", 0.0)
            })
        })
        root.put("participants", JSONArray().apply {
            participants.forEach { name ->
                put(JSONObject().apply {
                    put("name", name)
                    put("role", "participant")
                })
            }
        })
        root.put("tags", JSONArray().apply {
            topics.forEach { put(it) }
        })
        root.put("keywords", JSONArray().apply {
            keywords.forEach { put(it) }
        })
        root.put("topics", JSONArray().apply {
            topics.forEach { put(it) }
        })
        root.put("timings", JSONArray().apply {
            put(JSONObject().apply {
                put("label", "Introduction")
                put("startMs", 0)
                put("endMs", min(durationMs, 120_000))
            })
            put(JSONObject().apply {
                put("label", "Clôture")
                put("startMs", min(durationMs, maxOf(durationMs - 120_000, 0)))
                put("endMs", durationMs)
            })
        })
        root.put("durationMs", durationMs)
        return root
    }

    private fun buildTitle(text: String): String {
        return extractSentences(text, 1).firstOrNull()?.take(120) ?: "Session audio"
    }

    private fun buildContext(text: String): String {
        val trimmed = text.replace('\n', ' ').trim()
        return if (trimmed.length <= 320) trimmed else trimmed.take(320) + "…"
    }

    private fun extractSentences(text: String, maxCount: Int): List<String> {
        if (text.isBlank()) return emptyList()
        val raw = text.split('.', '!', '?', '\n')
            .map { it.trim() }
            .filter { it.length > 10 }
        return raw.take(maxCount)
    }

    private fun extractPotentialParticipants(text: String): List<String> {
        val regex = Regex("\\b([A-ZÉÈÊÀÂÎÏÔÛÙ][a-zàâçéèêëîïôûùüÿñœ]+)\\b")
        val matches = regex.findAll(text)
            .map { it.value }
            .filter { it.length > 2 }
            .toMutableList()
        return matches.distinct().take(5)
    }

    private fun extractTopWords(text: String, limit: Int): List<String> {
        if (text.isBlank()) return emptyList()
        val normalized = text.lowercase()
            .replace(Regex("[^a-zàâçéèêëîïôûùüÿñæœ0-9\\s-]"), " ")
        val tokens = normalized.split(Regex("\\s+")).filter { it.length > 3 && it !in stopWords }
        val freq = tokens.groupingBy { it }.eachCount()
        return freq.entries.sortedByDescending { it.value }.map { it.key }.take(limit)
    }

    private val stopWords = setOf(
        "dans","pour","avec","vous","nous","mais","elles","ils","ceci","cela","cest","sont","plus","alors","comme","avoir",
        "être","quoi","tout","aussi","leur","leurs","dont","chez","entre","ainsi","cette","plusieurs","moins","tous",
        "this","that","with","have","from","your","about","there","will","their","they","them","been","were","when","what",
        "where","which","than","then","into","over","after","before","because","while","should","could","would","these","those",
        "here","onto","ours","ourselves","hers","herself","himself","yourself"
    )
}
