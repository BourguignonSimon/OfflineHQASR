package com.example.offlinehqasr.recorder

import android.content.Context
import com.example.offlinehqasr.BuildConfig
import java.io.File

class WhisperEngine(private val ctx: Context) : SpeechToTextEngine {
    override fun transcribeFile(path: String): TranscriptionResult {
        // Placeholder: integrate JNI binding to whisper.cpp here.
        // For now, just throw if called.
        throw UnsupportedOperationException("Whisper JNI non intégré. Voir whisper/README.md")
    }

    companion object {
        private val nativeReady: Boolean by lazy {
            runCatching { System.loadLibrary("whisper") }.isSuccess
        }

        fun isAvailable(ctx: Context): Boolean {
            if (!BuildConfig.USE_WHISPER) return false
            val modelDir = File(ctx.filesDir, "models/whisper")
            val hasModel = modelDir.exists() && (modelDir.listFiles()?.isNotEmpty() == true)
            return hasModel && nativeReady
        }
    }
}
