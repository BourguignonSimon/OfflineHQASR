package com.example.offlinehqasr.recorder

import com.example.offlinehqasr.data.entities.Segment
import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptionResultTest {

    @Test
    fun normalizedText_trimsWhitespace() {
        val result = TranscriptionResult("  Bonjour\n  le   monde  ", emptyList(), 1000)
        assertEquals("Bonjour le monde", result.normalizedText())
    }

    @Test
    fun mergeShortSegments_combinesSubThreshold() {
        val segments = listOf(
            Segment(id = 0, recordingId = 0, startMs = 0, endMs = 200, text = "Salut"),
            Segment(id = 0, recordingId = 0, startMs = 200, endMs = 350, text = "tout"),
            Segment(id = 0, recordingId = 0, startMs = 350, endMs = 1200, text = "le monde")
        )
        val result = TranscriptionResult("", segments, 1200)
        val merged = result.mergeShortSegments(minDurationMs = 400)
        assertEquals(2, merged.size)
        assertEquals(0, merged[0].startMs)
        assertEquals(350, merged[0].endMs)
        assertEquals("Salut tout", merged[0].text)
        assertEquals("le monde", merged[1].text)
    }
}
