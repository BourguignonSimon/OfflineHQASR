package com.example.offlinehqasr.summary

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min
import com.example.offlinehqasr.data.entities.Summary

object PremiumPrompt {
    val JSON_STRUCT_PROMPT = """
RÔLE: Tu es un secrétaire de réunion expert.
OBJECTIF: À partir de la transcription, génère un JSON structuré :
- title
- summary (context, bullets)
- actions (who, what, when, priority)
- topics
- entities
- keywords
CONTRAINTES:
- Français, fidèle au texte
- Sortie JSON uniquement
TRANSCRIPTION:
<<<TRANSCRIPT>>>
""".trimIndent()
}

object Summarizer {
    // Offline naive summarizer. Replace with local LLM if available.
    fun summarizeToJson(text: String, durationMs: Long): com.example.offlinehqasr.data.entities.Summary {
        val title = text.split('.', '!', '?').firstOrNull()?.take(80) ?: "Session audio"
        val context = if (text.length > 200) text.take(200) + "…" else text
        val bullets = text.split('.', '
').map { it.trim() }.filter { it.length > 20 }.take(5)
        val topics = extractTopWords(text, 5)
        val keywords = extractTopWords(text, 10)

        val root = JSONObject()
        root.put("title", title)
        val sum = JSONObject()
        sum.put("context", context)
        val b = JSONArray(); bullets.forEach { b.put(it) }
        sum.put("bullets", b)
        root.put("summary", sum)
        root.put("actions", JSONArray()) // user to fill
        val t = JSONArray(); topics.forEach { t.put(it) }
        root.put("topics", t)
        val k = JSONArray(); keywords.forEach { k.put(it) }
        root.put("keywords", k)
        return Summary(0, 0, root.toString())
    }

    private fun extractTopWords(text: String, n: Int): List<String> {
        val words = text.lowercase()
            .replace(Regex("[^a-zàâçéèêëîïôûùüÿñæœ0-9\s-]"), " ")
            .split(Regex("\s+"))
            .filter { it.length > 3 && it !in stopFr }
        val counts = words.groupingBy { it }.eachCount()
        return counts.entries.sortedByDescending { it.value }.map { it.key }.take(n)
    }

    private val stopFr = setOf("dans","pour","avec","vous","nous","mais","elles","ils","elles","ceci","cela","cest","sont","plus","alors","comme","avoir","être","vous","quoi","tout","aussi","leur","leurs","dont","chez","entre","ainsi","cela","cette","plusieurs","moins","tous")
}
