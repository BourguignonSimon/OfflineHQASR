package com.example.offlinehqasr.recorder

import com.example.offlinehqasr.settings.UserSettings
import java.io.File

class WhisperEngine(private val modelFile: File) : SpeechToTextEngine {

    init {
        require(modelFile.exists()) { "Whisper model missing at ${modelFile.absolutePath}" }
    }

    override fun transcribeFile(path: String): TranscriptionResult {
        // Placeholder: integrate JNI binding to whisper.cpp here.
        // For now, just throw if called.
        throw UnsupportedOperationException("Whisper JNI non intégré. Voir whisper/README.md")
    }

    companion object {
        private val nativeReady: Boolean by lazy {
            runCatching { System.loadLibrary("whisper") }.isSuccess
        }

        fun resolveModelFile(settings: UserSettings): File? {
            val path = settings.whisperModelPath ?: return null
            val file = File(path)
            return if (file.exists()) file else null
        }

        fun isAvailable(settings: UserSettings): Boolean {
            return resolveModelFile(settings) != null && nativeReady
        }
    }
}
