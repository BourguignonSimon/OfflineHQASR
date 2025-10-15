package com.example.offlinehqasr.recorder.audio

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Simple adaptive gain normalizer.
 *
 * The implementation keeps a smoothed peak envelope and adjusts the gain in
 * small increments so that the output RMS hovers around [targetLevel].
 */
class GainNormalizer(
    private val targetLevel: Float = 0.18f,
    private val attack: Float = 0.15f,
    private val release: Float = 0.01f,
    private val maxGain: Float = 6f
) : AudioProcessor {

    private var currentGain = 1f

    override fun process(buffer: ShortArray, length: Int) {
        if (length <= 0) return
        var peak = 0f
        for (i in 0 until length) {
            val sample = buffer[i] / Short.MAX_VALUE.toFloat()
            peak = max(peak, abs(sample))
        }
        if (peak < 1e-5f) {
            // Nothing to normalize.
            return
        }

        val desiredGain = min(targetLevel / peak, maxGain)
        val step = if (desiredGain > currentGain) attack else release
        currentGain += (desiredGain - currentGain) * step

        for (i in 0 until length) {
            val normalized = buffer[i] * currentGain
            buffer[i] = normalized.coerceIn(-32768f, 32767f).toInt().toShort()
        }
    }
}
