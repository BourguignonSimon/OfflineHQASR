package com.example.offlinehqasr.recorder.audio

/**
 * Applies a list of [AudioProcessor] sequentially on in-memory PCM frames.
 */
class AudioProcessingChain(
    private val processors: List<AudioProcessor>
) {
    fun process(buffer: ShortArray, length: Int) {
        processors.forEach { processor -> processor.process(buffer, length) }
    }
}

fun interface AudioProcessor {
    fun process(buffer: ShortArray, length: Int)
}
