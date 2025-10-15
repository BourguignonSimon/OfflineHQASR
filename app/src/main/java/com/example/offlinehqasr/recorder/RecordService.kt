package com.example.offlinehqasr.recorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.os.Build
import android.os.IBinder
import android.media.AudioRecord.READ_BLOCKING
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.util.Log
import com.example.offlinehqasr.R
import com.example.offlinehqasr.data.AppDb
import com.example.offlinehqasr.data.entities.Recording
import com.example.offlinehqasr.recorder.audio.AudioDeviceSelector
import com.example.offlinehqasr.recorder.audio.AudioProcessingChain
import com.example.offlinehqasr.recorder.audio.GainNormalizer
import com.example.offlinehqasr.recorder.audio.RnNoiseDenoiser
import com.example.offlinehqasr.security.AesGcmCipher
import com.example.offlinehqasr.security.AppKeystore
import com.example.offlinehqasr.security.EncryptionMetadata
import com.example.offlinehqasr.security.SecureFileUtils
import com.example.offlinehqasr.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

class RecordService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var startTime: Long = 0L
    private lateinit var outFile: File

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (!running.compareAndSet(false, true)) {
            Log.w(TAG, "RecordService already running")
            return START_STICKY
        }
        job = serviceScope.launch { recordLoop() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        running.set(false)
        broadcastMicrophoneStatus(STATUS_IDLE, null)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, "rec")
            .setContentTitle("Enregistrement en cours")
            .setContentText("Micro actif")
            .setSmallIcon(android.R.drawable.stat_sys_speakerphone)
            .setContentIntent(pi)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel("rec", "Recording", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(chan)
        }
    }

    private suspend fun recordLoop() {
        try {
            val sampleRate = 48_000
            val selection = AudioDeviceSelector.select(this)
            val encoding = AudioFormat.ENCODING_PCM_16BIT
            val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
            require(minBuf > 0) { "Invalid buffer size reported by AudioRecord" }

            val deviceSelection = AudioDeviceSelector.select(this)
            Log.i(TAG, "recordLoop: selected input=${deviceSelection.label}")

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(encoding)
                .setChannelMask(channelConfig)
                .build()

            val recorder = AudioRecord.Builder()
                .setAudioSource(deviceSelection.audioSource)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(minBuf * 2)
                .apply { deviceSelection.preferredDevice?.let { setPreferredDevice(it) } }
                .build()

            val dir = File(filesDir, "audio"); dir.mkdirs()
            outFile = File(dir, "rec_${System.currentTimeMillis()}.wav")

            var totalPlainBytes = 0L
            var encryptionMetadata: EncryptionMetadata? = null
            SecureFileUtils.clearMetadata(outFile)
            var encryptionCompleted = false
            try {
                FileOutputStream(outFile).use { fos ->
                    // Write provisional WAV header (will fix sizes at end)
                    writeWavHeader(fos, 1, sampleRate, 16, 0)
                    val encrypting = AesGcmCipher.wrapForEncryption(AppKeystore.AUDIO_KEY_ALIAS, fos)
                    encryptionMetadata = encrypting.metadata
                    val cipherStream = encrypting.stream
                    val byteBuffer = ByteArray(minBuf)
                    val shortBuffer = ShortArray(minBuf / 2)
                    val processingChain = AudioProcessingChain(
                        listOf(
                            GainNormalizer(),
                            RnNoiseDenoiser(this)
                        )
                    )
                    val outputBuffer = ByteArray(minBuf)
                    startTime = System.currentTimeMillis()
                    recorder.startRecording()
                    try {
                        while (isActive) {
                            val read = recorder.read(byteBuffer, 0, byteBuffer.size, READ_BLOCKING)
                            when {
                                read > 0 -> {
                                    val samples = read / 2
                                    ByteBuffer.wrap(byteBuffer, 0, read)
                                        .order(ByteOrder.LITTLE_ENDIAN)
                                        .asShortBuffer()
                                        .get(shortBuffer, 0, samples)
                                    processingChain.process(shortBuffer, samples)
                                    shortsToBytes(shortBuffer, samples, outputBuffer)
                                    cipherStream.write(outputBuffer, 0, samples * 2)
                                    totalPlainBytes += samples * 2
                                }
                            read == AudioRecord.ERROR_INVALID_OPERATION -> {
                                notifyRecordingError(ERROR_INVALID_OPERATION)
                                break
                            }
                            read == AudioRecord.ERROR_BAD_VALUE -> {
                                notifyRecordingError(ERROR_BAD_VALUE)
                                break
                            }
                            read == AudioRecord.ERROR_DEAD_OBJECT -> {
                                notifyRecordingError(ERROR_DISCONNECTED)
                                break
                            }
                            else -> {
                                if (read < 0) {
                                    notifyRecordingError(ERROR_UNKNOWN)
                                    break
                                }
                                delay(10)
                            }
                        }
                    } finally {
                        cipherStream.close()
                    }

                    recorder.stop()
                    // Fix header sizes
                    RandomAccessFile(outFile, "rw").use { raf ->
                        raf.seek(0)
                        writeWavHeader(raf, 1, sampleRate, 16, totalPlainBytes.toInt())
                    }
                }
                encryptionMetadata?.let {
                    SecureFileUtils.persistMetadata(outFile, it)
                    encryptionCompleted = true
                }
            } catch (e: Exception) {
                if (!encryptionCompleted) {
                    SecureFileUtils.clearMetadata(outFile)
                }
                throw e
            } finally {
                runCatching { noiseSuppressor?.release() }
                runCatching { agc?.release() }
                recorder.release()
            }

            val duration = System.currentTimeMillis() - startTime
            val db = AppDb.get(this)
            val recId = db.recordingDao().insert(
                Recording(0, outFile.absolutePath, System.currentTimeMillis(), duration)
            )

            TranscribeWork.enqueue(this, recId, outFile.absolutePath)
        } catch (e: SecurityException) {
            Log.e(TAG, "Recording permission revoked", e)
            notifyRecordingError(ERROR_PERMISSION)
        } catch (e: Exception) {
            Log.e(TAG, "Recording failed", e)
            notifyRecordingError(ERROR_UNKNOWN)
        } finally {
            running.set(false)
            broadcastMicrophoneStatus(STATUS_IDLE, null)
            stopSelf()
        }
    }

    private fun writeWavHeader(stream: java.io.OutputStream, channels: Int, sampleRate: Int, bits: Int, dataLen: Long) {
        val byteRate = sampleRate * channels * bits / 8
        val safeDataLen = dataLen.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val totalDataLen = safeDataLen + 36
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(totalDataLen)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16) // PCM
        header.putShort(1) // PCM format
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort((channels * bits / 8).toShort())
        header.putShort(bits.toShort())
        header.put("data".toByteArray())
        header.putInt(safeDataLen)
        stream.write(header.array(), 0, 44)
    }

    private fun notifyRecordingError(code: String) {
        Log.w(TAG, "recording error: $code")
        val intent = Intent(ACTION_RECORDING_ERROR)
            .putExtra(EXTRA_ERROR_CODE, code)
        sendBroadcast(intent)
    }

    private fun shortsToBytes(input: ShortArray, length: Int, output: ByteArray) {
        var o = 0
        for (i in 0 until length) {
            val value = input[i].toInt()
            output[o++] = (value and 0xFF).toByte()
            output[o++] = ((value ushr 8) and 0xFF).toByte()
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val WAV_HEADER_SIZE = 44L
        private val running = AtomicBoolean(false)
        private const val TAG = "RecordService"
        const val ACTION_RECORDING_ERROR = "com.example.offlinehqasr.RECORDING_ERROR"
        const val EXTRA_ERROR_CODE = "code"
        const val ERROR_PERMISSION = "permission"
        const val ERROR_INVALID_OPERATION = "invalid_operation"
        const val ERROR_BAD_VALUE = "bad_value"
        const val ERROR_DISCONNECTED = "disconnected"
        const val ERROR_UNKNOWN = "unknown"

        fun start(context: Context) {
            val intent = Intent(context, RecordService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RecordService::class.java))
        }

        fun isRunning(): Boolean = running.get()
    }

    private fun broadcastMicrophoneStatus(status: String, label: String?) {
        val intent = Intent(ACTION_MICROPHONE_STATUS).apply {
            putExtra(EXTRA_STATUS, status)
            putExtra(EXTRA_MESSAGE, label)
        }
        sendBroadcast(intent)
    }
}
