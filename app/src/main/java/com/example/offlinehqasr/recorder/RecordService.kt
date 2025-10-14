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
import android.media.MediaRecorder
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
        running.set(true)
        job = serviceScope.launch { recordLoop() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        running.set(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_recording_title))
            .setContentText(getString(R.string.notification_recording_text))
            .setSmallIcon(android.R.drawable.stat_sys_speakerphone)
            .setContentIntent(pi)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_recording),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(chan)
        }
    }

    private suspend fun recordLoop() {
        try {
            val sampleRate = 48_000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val encoding = AudioFormat.ENCODING_PCM_16BIT
            val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                encoding,
                minBuf * 2
            )

            val dir = File(filesDir, "audio"); dir.mkdirs()
            outFile = File(dir, "rec_${System.currentTimeMillis()}.wav")

            try {
                FileOutputStream(outFile).use { fos ->
                    // Write provisional WAV header (will fix sizes at end)
                    writeWavHeader(fos, 1, sampleRate, 16, 0)
                    val buffer = ByteArray(minBuf)
                    startTime = System.currentTimeMillis()
                    recorder.startRecording()
                    while (isActive) {
                        val read = recorder.read(buffer, 0, buffer.size)
                        if (read > 0) fos.write(buffer, 0, read)
                        delay(10)
                    }
                    recorder.stop()
                    // Fix header sizes
                    val totalAudioLen = outFile.length() - WAV_HEADER_SIZE
                    RandomAccessFile(outFile, "rw").use { raf ->
                        raf.seek(0)
                        writeWavHeader(raf, 1, sampleRate, 16, totalAudioLen.toInt())
                    }
                }
            } finally {
                recorder.release()
            }

            val duration = System.currentTimeMillis() - startTime
            val db = AppDb.get(this)
            val recId = db.recordingDao().insert(
                Recording(0, outFile.absolutePath, System.currentTimeMillis(), duration)
            )

            // Enqueue transcription work
            TranscribeWork.enqueue(this, recId, outFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Recording failed", e)
        } finally {
            stopSelf()
        }
    }

    private fun writeWavHeader(stream: java.io.OutputStream, channels: Int, sampleRate: Int, bits: Int, dataLen: Int) {
        val byteRate = sampleRate * channels * bits / 8
        val totalDataLen = dataLen + 36
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
        header.putInt(dataLen)
        stream.write(header.array(), 0, 44)
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val WAV_HEADER_SIZE = 44L
        private val running = AtomicBoolean(false)
        private const val TAG = "RecordService"
        private const val CHANNEL_ID = "rec"

        fun start(context: Context) {
            val intent = Intent(context, RecordService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RecordService::class.java))
        }

        fun isRunning(): Boolean = running.get()
    }
}
