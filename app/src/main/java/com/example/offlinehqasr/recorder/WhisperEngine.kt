package com.example.offlinehqasr.recorder

import android.content.Context
import android.util.Log
import com.example.offlinehqasr.BuildConfig
import com.example.offlinehqasr.data.entities.Segment
import com.example.offlinehqasr.security.SecureFileUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

class WhisperEngine(private val ctx: Context) : SpeechToTextEngine {

    override fun transcribeFile(path: String): TranscriptionResult {
        if (!BuildConfig.USE_WHISPER) {
            throw WhisperUnavailableException("Whisper désactivé dans la configuration.")
        }
        require(nativeReady) { "La bibliothèque native Whisper est indisponible." }

        val audioFile = File(path)
        require(audioFile.exists()) { "Fichier audio introuvable: $path" }
        val workingFile = SecureFileUtils.decryptWavToTemp(ctx, audioFile)
        val shouldDeleteWorkingFile = workingFile != audioFile
        try {

            val modelFile = Companion.resolveModelFile(File(ctx.filesDir, "models/whisper"))
                ?: throw WhisperUnavailableException("Aucun modèle Whisper trouvé.")
            val wavInfo = readWavInfo(workingFile)
            val durationMs = wavInfo.durationMs

            val handle = nativeInit(
                modelFile.absolutePath,
                DEFAULT_LANGUAGE,
                /*translate=*/false,
                Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            )

            val transcriptBuilder = StringBuilder()
            val segments = mutableListOf<Segment>()
            var offsetMs = 0L
            var windowIndex = 0
            var contextTokens = emptyArray<String>()

            try {
                val maxIterations = ((durationMs / (WINDOW_SIZE_MS - OVERLAP_MS)) + 3).toInt().coerceAtLeast(1)
                while (offsetMs < durationMs && windowIndex < maxIterations) {
                    windowIndex++
                    val json = nativeProcess(
                        handle,
                        workingFile.absolutePath,
                        offsetMs,
                        WINDOW_SIZE_MS,
                        durationMs,
                        contextTokens
                    )
                    if (json.isBlank()) {
                        Log.w(TAG, "Le segment ${windowIndex} n'a retourné aucun résultat.")
                        break
                    }
                    val chunk = parseNativeChunk(json)
                    if (chunk.text.isNotBlank()) {
                        if (transcriptBuilder.isNotEmpty()) transcriptBuilder.append(' ')
                        transcriptBuilder.append(chunk.text.trim())
                    }

                    if (chunk.segments.isNotEmpty()) {
                        mergeSegments(segments, chunk.segments)
                    }

                    contextTokens = chunk.context.toTypedArray()

                    val nextOffset = chunk.nextOffsetMs
                        ?: (offsetMs + WINDOW_SIZE_MS - OVERLAP_MS).coerceAtLeast(offsetMs + MIN_STEP_MS)

                    if (chunk.completed || nextOffset <= offsetMs) {
                        break
                    }

                    offsetMs = min(nextOffset, durationMs)
                }
            } finally {
                nativeRelease(handle)
            }

            return TranscriptionResult(
                transcriptBuilder.toString().trim(),
                segments,
                durationMs
            )
        } finally {
            if (shouldDeleteWorkingFile) {
                workingFile.delete()
            }
        }
    }

    private fun readWavInfo(file: File): WavInfo {
        FileInputStream(file).use { fis ->
            val header = ByteArray(WAV_HEADER_SIZE)
            val read = fis.read(header)
            require(read == WAV_HEADER_SIZE) { "Entête WAV invalide pour ${file.absolutePath}" }
            val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            buffer.position(22)
            val channels = buffer.short.toInt()
            val sampleRate = buffer.int
            buffer.position(34)
            val bitsPerSample = buffer.short.toInt().coerceAtLeast(1)

            val dataSize = max(0L, file.length() - WAV_HEADER_SIZE)
            val bytesPerSample = bitsPerSample / 8
            val samples = if (bytesPerSample > 0 && channels > 0) dataSize / (channels * bytesPerSample) else 0L
            val durationMs = if (sampleRate > 0) (samples * 1000L) / sampleRate else 0L

            return WavInfo(sampleRate, channels, durationMs)
        }
    }

    private fun mergeSegments(existing: MutableList<Segment>, newcomers: List<NativeSegment>) {
        var lastEnd = existing.lastOrNull()?.endMs ?: -1L
        newcomers.forEach { seg ->
            val text = seg.text.trim()
            if (text.isBlank()) return@forEach
            val start = max(0L, seg.startMs)
            val end = max(start, seg.endMs)
            if (end <= lastEnd) return@forEach
            val adjustedStart = if (lastEnd > 0) max(start, lastEnd - OVERLAP_MS) else start
            val segment = Segment(
                id = 0,
                recordingId = 0,
                startMs = adjustedStart,
                endMs = end,
                text = text
            )
            existing.add(segment)
            lastEnd = segment.endMs
        }
    }

    private fun parseNativeChunk(json: String): NativeChunk {
        val obj = JSONObject(json)
        val segments = mutableListOf<NativeSegment>()
        val segArray = obj.optJSONArray("segments") ?: JSONArray()
        for (i in 0 until segArray.length()) {
            val segObj = segArray.optJSONObject(i) ?: continue
            segments.add(
                NativeSegment(
                    startMs = segObj.optLong("start", 0L),
                    endMs = segObj.optLong("end", 0L),
                    text = segObj.optString("text", "")
                )
            )
        }

        val contextTokens = mutableListOf<String>()
        val ctxArray = obj.optJSONArray("context") ?: JSONArray()
        for (i in 0 until ctxArray.length()) {
            contextTokens.add(ctxArray.optString(i))
        }

        val nextOffset = if (obj.has("next_offset_ms")) obj.optLong("next_offset_ms", -1L) else -1L

        return NativeChunk(
            text = obj.optString("text", ""),
            segments = segments,
            context = contextTokens,
            completed = obj.optBoolean("completed", false),
            nextOffsetMs = nextOffset.takeIf { it >= 0 }
        )
    }

    data class WhisperUnavailableException(override val message: String) : IllegalStateException(message)

    private data class WavInfo(val sampleRate: Int, val channels: Int, val durationMs: Long)

    private data class NativeChunk(
        val text: String,
        val segments: List<NativeSegment>,
        val context: List<String>,
        val completed: Boolean,
        val nextOffsetMs: Long?
    )

    private data class NativeSegment(val startMs: Long, val endMs: Long, val text: String)

    private external fun nativeInit(
        modelPath: String,
        language: String,
        translate: Boolean,
        threads: Int
    ): Long

    private external fun nativeProcess(
        handle: Long,
        audioPath: String,
        offsetMs: Long,
        windowSizeMs: Long,
        totalDurationMs: Long,
        context: Array<String>
    ): String

    private external fun nativeRelease(handle: Long)

    companion object {
        private const val TAG = "WhisperEngine"
        private const val WAV_HEADER_SIZE = 44
        private const val WINDOW_SIZE_MS = 30_000L
        private const val OVERLAP_MS = 5_000L
        private const val MIN_STEP_MS = 1_000L
        private const val DEFAULT_LANGUAGE = "fr"

        private val nativeReady: Boolean by lazy {
            runCatching { System.loadLibrary("whisper") }.isSuccess
        }

        fun isAvailable(ctx: Context): Boolean {
            if (!BuildConfig.USE_WHISPER) return false
            if (!nativeReady) return false
            val baseDir = File(ctx.filesDir, "models/whisper")
            return resolveModelFile(baseDir) != null
        }

        private fun resolveModelFile(baseDir: File): File? {
            if (!baseDir.exists()) return null
            val large = findModel(baseDir, "large-v3", "q5_0")
            if (large != null) return large
            val medium = findModel(baseDir, "medium", "q5_0")
            if (medium != null) return medium
            return baseDir.walkTopDown().firstOrNull { it.isFile && it.extension.equals("gguf", true) }
        }

        private fun findModel(baseDir: File, keyword: String, quant: String): File? {
            return baseDir.walkTopDown().firstOrNull { file ->
                file.isFile && file.extension.equals("gguf", true) &&
                    file.name.contains(keyword, ignoreCase = true) &&
                    file.name.contains(quant, ignoreCase = true)
            }
        }
    }
}
