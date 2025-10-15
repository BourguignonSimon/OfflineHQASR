package com.example.offlinehqasr.summary

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredSummaryValidatorTest {

    @Test
    fun `validator accepts complete document`() {
        val json = JSONObject()
            .put("title", "Réunion")
            .put("summary", JSONObject().put("context", "Texte").put("bullets", listOf("Point")))
            .put("actions", listOf(mapOf("who" to "Alice", "what" to "Faire", "due" to "", "priority" to "", "status" to "", "confidence" to 0.5, "relatedSegments" to emptyList<Any>())))
            .put("decisions", listOf(mapOf("description" to "Décision", "owner" to "Bob", "timestampMs" to 0, "confidence" to 0.5)))
            .put("citations", listOf(mapOf("quote" to "Citation", "speaker" to "Bob", "startMs" to 0, "endMs" to 0)))
            .put("sentiments", listOf(mapOf("target" to "équipe", "value" to "positif", "score" to 0.5)))
            .put("participants", listOf(mapOf("name" to "Alice", "role" to "PM")))
            .put("tags", listOf("tag"))
            .put("keywords", listOf("mot"))
            .put("topics", listOf("sujet"))
            .put("timings", listOf(mapOf("label" to "Intro", "startMs" to 0, "endMs" to 1)))
            .put("durationMs", 10)
        val validator = StructuredSummaryValidator()
        assertTrue(validator.isValid(json))
    }

    @Test
    fun `validator rejects missing fields`() {
        val json = JSONObject().put("title", "Réunion")
        val validator = StructuredSummaryValidator()
        assertFalse(validator.isValid(json))
    }
}
