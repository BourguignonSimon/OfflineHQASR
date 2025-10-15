package com.example.offlinehqasr.recorder.audio

import android.content.Context
import java.nio.FloatBuffer
import java.util.Arrays
import java.util.Collections
import kotlin.math.abs
import kotlin.math.max

/**
 * Lightweight RNNoise inspired denoiser.
 *
 * The implementation supports two modes:
 * - If an ONNX runtime implementation is packaged with the app (class
 *   `ai.onnxruntime.OrtEnvironment` available) and an `rnnoise.onnx` asset is
 *   present, the network is executed on 10 ms frames (480 samples @ 48 kHz).
 * - Otherwise a simple adaptive noise gate is used so that the pipeline keeps
 *   working even without the optional model.
 */
class RnNoiseDenoiser(private val context: Context) : AudioProcessor {

    private val onnxReducer: OnnxReducer? = runCatching { OnnxReducer(context) }.getOrNull()
    private val fallback = NoiseGate()

    override fun process(buffer: ShortArray, length: Int) {
        if (onnxReducer != null) {
            onnxReducer.process(buffer, length)
        } else {
            fallback.process(buffer, length)
        }
    }

    private class NoiseGate : AudioProcessor {
        private var noiseEstimate = 0.02f
        private var gain = 1f

        override fun process(buffer: ShortArray, length: Int) {
            if (length <= 0) return
            for (i in 0 until length) {
                val sample = buffer[i] / Short.MAX_VALUE.toFloat()
                val magnitude = abs(sample)
                noiseEstimate = 0.995f * noiseEstimate + 0.005f * magnitude
                val threshold = max(noiseEstimate * 1.8f, 0.01f)
                val targetGain = if (magnitude < threshold) 0.25f else 1f
                gain += (targetGain - gain) * 0.1f
                val out = (sample * gain).coerceIn(-1f, 1f)
                buffer[i] = (out * Short.MAX_VALUE).toInt().toShort()
            }
        }
    }

    private class OnnxReducer(context: Context) : AudioProcessor {
        private val env: Any
        private val session: Any
        private val inputName: String
        private val buffer = FloatBuffer.allocate(FRAME_SIZE)
        private val tempFrame = ShortArray(FRAME_SIZE)
        private val leftover = ShortArray(FRAME_SIZE)
        private var leftoverSize = 0

        init {
            val assetManager = context.assets
            val assetNames = assetManager.list("") ?: emptyArray()
            require(assetNames.contains(MODEL_NAME)) { "rnnoise.onnx asset not found" }

            val envClass = Class.forName("ai.onnxruntime.OrtEnvironment")
            env = envClass.getMethod("getEnvironment").invoke(null)
            val sessionOptionsClass = Class.forName("ai.onnxruntime.OrtSession\\$SessionOptions")
            val options = sessionOptionsClass.getConstructor().newInstance()
            val createSession = envClass.getMethod("createSession", String::class.java, sessionOptionsClass)
            val path = context.filesDir.resolve(MODEL_NAME)
            if (!path.exists()) {
                context.assets.open(MODEL_NAME).use { input ->
                    path.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            session = createSession.invoke(env, path.absolutePath, options)
            val sessionClass = Class.forName("ai.onnxruntime.OrtSession")
            val getInputNames = sessionClass.getMethod("getInputNames")
            @Suppress("UNCHECKED_CAST")
            val inputNames = getInputNames.invoke(session) as Set<String>
            inputName = inputNames.first()
        }

        override fun process(buffer: ShortArray, length: Int) {
            var cursor = 0
            while (cursor < length) {
                val remaining = length - cursor
                val copyCount = minOf(FRAME_SIZE - leftoverSize, remaining)
                System.arraycopy(buffer, cursor, leftover, leftoverSize, copyCount)
                cursor += copyCount
                leftoverSize += copyCount
                if (leftoverSize == FRAME_SIZE) {
                    runFrame(leftover)
                    System.arraycopy(tempFrame, 0, buffer, cursor - FRAME_SIZE, FRAME_SIZE)
                    leftoverSize = 0
                }
            }
            if (leftoverSize > 0) {
                System.arraycopy(buffer, length - leftoverSize, tempFrame, 0, leftoverSize)
                Arrays.fill(tempFrame, leftoverSize, FRAME_SIZE, 0)
                runFrame(tempFrame)
                System.arraycopy(tempFrame, 0, buffer, length - leftoverSize, leftoverSize)
            }
        }

        private fun runFrame(frame: ShortArray) {
            buffer.clear()
            for (i in 0 until FRAME_SIZE) {
                buffer.put(i, frame[i] / Short.MAX_VALUE.toFloat())
            }

            val tensorClass = Class.forName("ai.onnxruntime.OnnxTensor")
            val envClass = env.javaClass
            val createTensor = tensorClass.getMethod("createTensor", envClass, FloatBuffer::class.java, LongArray::class.java)
            val tensor = createTensor.invoke(null, env, buffer, longArrayOf(FRAME_SIZE.toLong()))

            val mapClass = Class.forName("java.util.Map")
            val runMethod = session.javaClass.getMethod("run", mapClass)
            val map = java.util.Collections.singletonMap(inputName, tensor)
            val results = runMethod.invoke(session, map) as List<*>
            val onnxValueClass = Class.forName("ai.onnxruntime.OnnxValue")
            val floatBufferMethod = onnxValueClass.getMethod("getFloatBuffer")
            val outBuffer = floatBufferMethod.invoke(results.first()) as FloatBuffer
            for (i in 0 until FRAME_SIZE) {
                val value = outBuffer.get(i).coerceIn(-1f, 1f)
                tempFrame[i] = (value * Short.MAX_VALUE).toInt().toShort()
            }

            val close = onnxValueClass.getMethod("close")
            results.forEach { close.invoke(it) }
            tensorClass.getMethod("close").invoke(tensor)
        }

        companion object {
            private const val MODEL_NAME = "rnnoise.onnx"
            private const val FRAME_SIZE = 480
        }
    }

    companion object {
        fun isOnnxAvailable(): Boolean = runCatching {
            Class.forName("ai.onnxruntime.OrtEnvironment")
        }.isSuccess
    }
}
