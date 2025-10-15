package com.example.offlinehqasr.summary

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredSummaryParserTest {

    @Test
    fun `parseOrNull returns structured summary`() {
        val json = JSONObject()
            .put("title", "Réunion produit")
            .put("summary", JSONObject()
                .put("context", "Discussion autour du lancement.")
                .put("bullets", listOf("Décision budgétaire", "Plan de lancement")))
            .put("actions", listOf(
                mapOf(
                    "who" to "Alice",
                    "what" to "Préparer le plan marketing",
                    "due" to "2024-06-01",
                    "priority" to "haute",
                    "status" to "à faire",
                    "confidence" to 0.9,
                    "relatedSegments" to listOf(mapOf("startMs" to 1000, "endMs" to 2000))
                )
            ))
            .put("decisions", listOf(
                mapOf(
                    "description" to "Valider le budget",
                    "owner" to "Comité",
                    "timestampMs" to 1500,
                    "confidence" to 0.8
                )
            ))
            .put("citations", listOf(
                mapOf(
                    "quote" to "Il faut accélérer le calendrier",
                    "speaker" to "Bob",
                    "startMs" to 500,
                    "endMs" to 1200
                )
            ))
            .put("sentiments", listOf(
                mapOf(
                    "target" to "équipe",
                    "value" to "positif",
                    "score" to 0.7
                )
            ))
            .put("participants", listOf(
                mapOf("name" to "Alice", "role" to "PM"),
                mapOf("name" to "Bob", "role" to "CTO")
            ))
            .put("tags", listOf("marketing", "budget"))
            .put("keywords", listOf("lancement", "campagne"))
            .put("topics", listOf("Produit", "Budget"))
            .put("timings", listOf(
                mapOf("label" to "Intro", "startMs" to 0, "endMs" to 60000)
            ))
            .put("durationMs", 180000)
            .toString()

        val parsed = StructuredSummaryParser.parseOrNull(json)
        assertNotNull(parsed)
        parsed!!
        assertEquals("Réunion produit", parsed.title)
        assertEquals("Discussion autour du lancement.", parsed.summary.context)
        assertEquals(listOf("Décision budgétaire", "Plan de lancement"), parsed.summary.bullets)
        assertEquals(1, parsed.actions.size)
        assertEquals("Alice", parsed.actions[0].who)
        assertEquals(0.9, parsed.actions[0].confidence, 0.0001)
        assertEquals(1, parsed.decisions.size)
        assertEquals("Valider le budget", parsed.decisions[0].description)
        assertEquals(1, parsed.citations.size)
        assertEquals("Bob", parsed.citations[0].speaker)
        assertEquals(2, parsed.participants.size)
        assertTrue(parsed.tags.contains("marketing"))
        assertEquals(180000, parsed.durationMs)
    }
}
