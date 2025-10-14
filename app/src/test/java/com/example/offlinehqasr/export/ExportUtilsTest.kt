package com.example.offlinehqasr.export

import com.example.offlinehqasr.data.entities.Recording
import com.example.offlinehqasr.data.entities.Segment
import com.example.offlinehqasr.data.entities.Transcript
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportUtilsTest {

    @Test
    fun buildSessionJson_containsMetadata() {
        val recording = Recording(
            id = 42,
            filePath = "/tmp/audio.wav",
            createdAt = 123456789L,
            durationMs = 60000
        )
        val transcript = Transcript(1, 42, "Bonjour à tous")
        val summaryJson = JSONObject(
            """
            {
              "summary": {"bullets": ["Point A"], "context": "Contexte"},
              "keywords": ["mot"],
              "stt": {"engine": "whisper", "language": "fr"}
            }
            """.trimIndent()
        )
        val segments = listOf(
            Segment(id = 0, recordingId = 42, startMs = 0, endMs = 1500, text = "Salut à tous")
        )

        val json = ExportUtils.buildSessionJson(recording, transcript, summaryJson, segments, "Pixel 7")

        assertEquals("42", json.getString("id"))
        assertEquals("Pixel 7", json.getString("device"))
        val audio = json.getJSONObject("audio")
        assertEquals("/tmp/audio.wav", audio.getString("path"))
        assertEquals(60.0, audio.getDouble("duration_s"), 0.001)
        val stt = json.getJSONObject("stt")
        assertEquals("whisper", stt.getString("engine"))
        assertEquals("fr", stt.getString("language"))
        val summary = json.getJSONObject("summary")
        assertEquals(1, summary.getJSONArray("bullets").length())
        assertTrue(summary.getJSONArray("keywords").length() >= 1)
        assertEquals(1, json.getJSONArray("segments").length())
    }
}
