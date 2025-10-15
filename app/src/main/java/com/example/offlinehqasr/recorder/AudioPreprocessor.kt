package com.example.offlinehqasr.recorder

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Lightweight audio conditioning: normalisation and RNNoise-inspired noise gating.
 * Operates frame-by-frame on 16-bit PCM buffers and returns basic statistics for monitoring.
 */
class AudioPreprocessor(
    private val channelCount: Int,
    private val targetRms: Double = 0.12,
    private val gainSmoothing: Double = 0.92,
    private val noiseAdaptation: Double = 0.02
) {

    private var gain = 1.0
    private var noiseFloor = 600.0
    private val channelScale = sqrt(channelCount.coerceAtLeast(1).toDouble())

    data class FrameStats(val isSilence: Boolean, val peakDb: Double)

    fun process(buffer: ShortArray, length: Int): FrameStats {
        if (length <= 0) return FrameStats(isSilence = true, peakDb = -120.0)

        var sumSquares = 0.0
        var peak = 0.0
        for (i in 0 until length) {
            val sample = buffer[i].toDouble()
            sumSquares += sample * sample
            peak = max(peak, abs(sample))
        }

        val rms = sqrt(sumSquares / (length.toDouble().coerceAtLeast(1.0)))
        val normalizedRms = rms / Short.MAX_VALUE

        val adapt = if (normalizedRms < 0.02) noiseAdaptation else noiseAdaptation / 4
        noiseFloor = (1 - adapt) * noiseFloor + adapt * max(rms, 1.0)

        val targetLevel = targetRms * Short.MAX_VALUE * channelScale
        val computedGain = if (rms > 1.0) targetLevel / rms else 1.0
        val clampedGain = computedGain.coerceIn(0.25, 8.0)
        gain = gainSmoothing * gain + (1 - gainSmoothing) * clampedGain

        val noiseThreshold = max(noiseFloor * 1.6, 400.0)
        for (i in 0 until length) {
            var sample = buffer[i] * gain
            val magnitude = abs(sample)
            if (magnitude < noiseThreshold) {
                val attenuation = magnitude / noiseThreshold
                sample *= attenuation
            }
            sample = min(Short.MAX_VALUE.toDouble(), max(Short.MIN_VALUE.toDouble(), sample))
            buffer[i] = sample.toInt().toShort()
        }

        val peakDb = if (peak <= 1.0) {
            -120.0
        } else {
            20.0 * log10(peak / Short.MAX_VALUE)
        }

        val isSilence = normalizedRms < 0.01
        return FrameStats(isSilence, peakDb)
    }
}
