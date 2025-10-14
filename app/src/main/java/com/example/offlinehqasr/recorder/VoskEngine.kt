package com.example.offlinehqasr.recorder

import android.content.Context
import android.util.Log
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.SpeechStreamService
import java.io.File
import java.io.FileInputStream
import java.nio.charset.Charset
import org.json.JSONObject
import com.example.offlinehqasr.data.entities.Segment
import com.example.offlinehqasr.data.entities.Transcript

data class TranscriptionResult(val text: String, val segments: List<Segment>, val durationMs: Long) {
    fun toTranscript(recordingId: Long) = Transcript(0, recordingId, text)
}

class VoskEngine(private val ctx: Context) {

    fun transcribeFile(path: String): TranscriptionResult {
        val modelDir = File(ctx.filesDir, "models/vosk")
        require(modelDir.exists()) { "Modèle Vosk manquant. Importez-le via le menu." }
        val model = Model(modelDir.absolutePath)

        val wav = File(path)
        val fis = FileInputStream(wav)
        val rec = Recognizer(model, 48000.0f)

        val buf = ByteArray(4096)
        val segs = mutableListOf<Segment>()
        val sb = StringBuilder()
        var totalMs = 0L

        while (true) {
            val n = fis.read(buf)
            if (n <= 0) break
            if (rec.acceptWaveForm(buf, n)) {
                val res = rec.result
                try {
                    val json = JSONObject(res)
                    val text = json.optString("text")
                    sb.append(text).append(' ')
                    val words = json.optJSONArray("result")
                    if (words != null) {
                        for (i in 0 until words.length()) {
                            val w = words.getJSONObject(i)
                            val start = (w.getDouble("start") * 1000).toLong()
                            val end = (w.getDouble("end") * 1000).toLong()
                            val wt = w.getString("word")
                            segs.add(Segment(0, 0, start, end, wt))
                            totalMs = end
                        }
                    }
                } catch (e: Exception) {
                    Log.w("VoskEngine", "Parse err: ${e.message}")
                }
            } else {
                // partial result ignored
            }
        }
        rec.close()
        fis.close()
        return TranscriptionResult(sb.toString().trim(), segs, totalMs)
    }
}
