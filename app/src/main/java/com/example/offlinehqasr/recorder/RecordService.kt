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
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.util.Log
import com.example.offlinehqasr.R
import com.example.offlinehqasr.data.AppDb
import com.example.offlinehqasr.data.entities.Recording
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
            val channelCount = if (selection.channelMask == AudioFormat.CHANNEL_IN_STEREO) 2 else 1
            val bytesPerFrame = channelCount * 2
            val minBuf = AudioRecord.getMinBufferSize(sampleRate, selection.channelMask, encoding)
            if (minBuf <= 0) {
                throw IllegalStateException("Min buffer size invalid: $minBuf")
            }
            val bufferSizeInBytes = (minBuf * 2).coerceAtLeast(sampleRate * bytesPerFrame)
            val recorder = AudioRecord.Builder()
                .setAudioSource(selection.audioSource)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(encoding)
                        .setChannelMask(selection.channelMask)
                        .build()
                )
                .setBufferSizeInBytes(bufferSizeInBytes)
                .build()

            selection.preferredDevice?.let {
                try {
                    recorder.preferredDevice = it
                } catch (t: Throwable) {
                    Log.w(TAG, "Unable to set preferred device", t)
                }
            }

            val noiseSuppressor = if (NoiseSuppressor.isAvailable()) {
                runCatching { NoiseSuppressor.create(recorder.audioSessionId) }.getOrNull()?.apply { enabled = true }
            } else null
            val agc = if (AutomaticGainControl.isAvailable()) {
                runCatching { AutomaticGainControl.create(recorder.audioSessionId) }.getOrNull()?.apply { enabled = false }
            } else null

            val preprocessor = AudioPreprocessor(channelCount)

            val dir = File(filesDir, "audio"); dir.mkdirs()
            outFile = File(dir, "rec_${System.currentTimeMillis()}.wav")

            broadcastMicrophoneStatus(STATUS_OK, selection.label)

            try {
                FileOutputStream(outFile).use { fos ->
                    writeWavHeader(fos, channelCount, sampleRate, 16, 0)
                    val shortBuffer = ShortArray(bufferSizeInBytes / 2)
                    val byteBuffer = ByteBuffer.allocate(shortBuffer.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                    startTime = System.currentTimeMillis()
                    recorder.startRecording()

                    var silentSamples = 0L
                    var lastStatus = STATUS_OK
                    val silenceThreshold = (sampleRate.toLong() * channelCount * SILENCE_WARNING_MS / 1000L)
                        .coerceAtLeast(sampleRate.toLong() / 4)

                    while (isActive && running.get()) {
                        val read = recorder.read(shortBuffer, 0, shortBuffer.size, AudioRecord.READ_BLOCKING)
                        if (read <= 0) {
                            if (read == AudioRecord.ERROR_INVALID_OPERATION ||
                                read == AudioRecord.ERROR_BAD_VALUE ||
                                read == AudioRecord.ERROR_DEAD_OBJECT
                            ) {
                                broadcastMicrophoneStatus(STATUS_ERROR, "lecture invalide ($read)")
                                throw IllegalStateException("AudioRecord read failed: $read")
                            }
                            continue
                        }

                        val stats = preprocessor.process(shortBuffer, read)
                        silentSamples = if (stats.isSilence) silentSamples + read else 0L

                        if (silentSamples >= silenceThreshold && lastStatus != STATUS_WARNING) {
                            broadcastMicrophoneStatus(STATUS_WARNING, selection.label)
                            lastStatus = STATUS_WARNING
                        } else if (!stats.isSilence && lastStatus != STATUS_OK) {
                            broadcastMicrophoneStatus(STATUS_OK, selection.label)
                            lastStatus = STATUS_OK
                        }

                        byteBuffer.clear()
                        for (i in 0 until read) {
                            byteBuffer.putShort(shortBuffer[i])
                        }
                        fos.write(byteBuffer.array(), 0, read * 2)
                    }

                    recorder.stop()
                    val totalAudioLen = outFile.length() - WAV_HEADER_SIZE
                    RandomAccessFile(outFile, "rw").use { raf ->
                        raf.seek(0)
                        writeWavHeader(raf, channelCount, sampleRate, 16, totalAudioLen)
                    }
                }
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
        } catch (e: CancellationException) {
            Log.i(TAG, "Recording cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Recording failed", e)
            broadcastMicrophoneStatus(STATUS_ERROR, e.message ?: "erreur inconnue")
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

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val WAV_HEADER_SIZE = 44L
        private val running = AtomicBoolean(false)
        private const val TAG = "RecordService"
        private const val SILENCE_WARNING_MS = 3_000

        const val ACTION_MICROPHONE_STATUS = "com.example.offlinehqasr.recorder.MIC_STATUS"
        const val EXTRA_STATUS = "status"
        const val EXTRA_MESSAGE = "message"
        const val STATUS_OK = "ok"
        const val STATUS_WARNING = "warning"
        const val STATUS_ERROR = "error"
        const val STATUS_IDLE = "idle"

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
