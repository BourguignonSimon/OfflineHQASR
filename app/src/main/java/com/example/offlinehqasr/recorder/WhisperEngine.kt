package com.example.offlinehqasr.recorder

import android.content.Context

class WhisperEngine(private val ctx: Context) {
    fun transcribeFile(path: String): TranscriptionResult {
        // Placeholder: integrate JNI binding to whisper.cpp here.
        // For now, just throw if called.
        throw UnsupportedOperationException("Whisper JNI non intégré. Voir whisper/README.md")
    }
}
