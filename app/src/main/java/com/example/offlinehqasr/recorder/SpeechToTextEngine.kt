package com.example.offlinehqasr.recorder

interface SpeechToTextEngine {
    fun transcribeFile(path: String): TranscriptionResult
}
