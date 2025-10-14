package com.example.offlinehqasr.recorder

import android.content.Context
import android.util.Log
import com.example.offlinehqasr.data.entities.Segment
import com.example.offlinehqasr.data.entities.Transcript
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileInputStream

data class TranscriptionResult(val text: String, val segments: List<Segment>, val durationMs: Long) {
    fun toTranscript(recordingId: Long) = Transcript(0, recordingId, normalizedText())

    fun normalizedText(): String = text.replace(Regex("\s+"), " ").trim()

    fun mergeShortSegments(minDurationMs: Long = 750L): List<Segment> {
        if (segments.isEmpty()) return emptyList()
        val merged = mutableListOf<Segment>()
        var current = segments.first()
        for (next in segments.drop(1)) {
            val duration = current.endMs - current.startMs
            current = if (duration < minDurationMs) {
                current.copy(
                    endMs = next.endMs,
                    text = (current.text + " " + next.text).replace(Regex("\s+"), " ").trim()
                )
            } else {
                merged += current
                next
            }
        }
        merged += current
        return merged
    }
}

class VoskEngine(private val ctx: Context) : SpeechToTextEngine {

    override fun transcribeFile(path: String): TranscriptionResult {
        val baseDir = File(ctx.filesDir, "models/vosk")
        val modelDir = resolveModelDir(baseDir)
        val model = Model(modelDir.absolutePath)
        val wav = File(path)
        val buffer = ByteArray(4096)
        val segments = mutableListOf<Segment>()
        val transcriptBuilder = StringBuilder()
        var duration = 0L

        try {
            FileInputStream(wav).use { fis ->
                fis.channel.position(WAV_HEADER_SIZE)
                val recognizer = Recognizer(model, 48_000f)
                try {
                    while (true) {
                        val read = fis.read(buffer)
                        if (read <= 0) break
                        if (recognizer.acceptWaveForm(buffer, read)) {
                            duration = parseResult(recognizer.result, segments, transcriptBuilder, duration)
                        }
                    }
                    // flush any trailing data
                    duration = parseResult(recognizer.finalResult, segments, transcriptBuilder, duration)
                } finally {
                    recognizer.close()
                }
            }
        } finally {
            model.close()
        }

        return TranscriptionResult(transcriptBuilder.toString().trim(), segments, duration)
    }

    private fun parseResult(
        json: String,
        segments: MutableList<Segment>,
        transcriptBuilder: StringBuilder,
        currentDuration: Long
    ): Long {
        if (json.isBlank()) return currentDuration
        return try {
            val obj = JSONObject(json)
            val words = obj.optJSONArray("result") ?: return currentDuration
            if (words.length() == 0) return currentDuration
            val sentence = StringBuilder()
            val first = words.getJSONObject(0)
            val last = words.getJSONObject(words.length() - 1)
            val start = (first.getDouble("start") * 1000).toLong()
            val end = (last.getDouble("end") * 1000).toLong()
            for (i in 0 until words.length()) {
                val word = words.getJSONObject(i).optString("word")
                if (word.isNotBlank()) {
                    if (sentence.isNotEmpty()) sentence.append(' ')
                    sentence.append(word)
                }
            }
            val text = obj.optString("text").ifBlank { sentence.toString() }
            if (text.isNotBlank()) {
                transcriptBuilder.append(text.trim()).append(' ')
                segments.add(
                    Segment(
                        id = 0,
                        recordingId = 0,
                        startMs = start,
                        endMs = end,
                        text = text.trim().replace(Regex("\s+"), " ")
                    )
                )
            }
            end.coerceAtLeast(currentDuration)
        } catch (e: Exception) {
            Log.w(TAG, "Parse error", e)
            currentDuration
        }
    }

    private fun resolveModelDir(baseDir: File): File {
        require(baseDir.exists()) { "Modèle Vosk manquant. Importez-le via le menu." }
        if (containsModelFiles(baseDir)) return baseDir
        val children = baseDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
        if (children.isEmpty()) {
            error("Le dossier du modèle Vosk semble incomplet: ${baseDir.absolutePath}")
        }
        children.forEach { child ->
            if (containsModelFiles(child)) return child
        }
        if (children.size == 1) {
            return resolveModelDir(children.first())
        }
        error("Impossible de localiser le modèle Vosk dans ${baseDir.absolutePath}")
    }

    private fun containsModelFiles(dir: File): Boolean {
        return File(dir, "conf").exists() || File(dir, "model.conf").exists()
    }

    companion object {
        private const val TAG = "VoskEngine"
        private const val WAV_HEADER_SIZE = 44L
    }
}
