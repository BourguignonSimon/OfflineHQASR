package com.example.offlinehqasr.recorder

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.*
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.offlinehqasr.R
import com.example.offlinehqasr.data.AppDb
import com.example.offlinehqasr.data.entities.Recording
import com.example.offlinehqasr.ui.MainActivity
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.system.measureTimeMillis

class RecordService : Service() {

    private var job: Job? = null
    private var startTime: Long = 0L
    private lateinit var outFile: File

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, buildNotification())
        job = CoroutineScope(Dispatchers.IO).launch { recordLoop() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
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
        val sr = 48000
        val ch = AudioFormat.CHANNEL_IN_MONO
        val fmt = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioRecord.getMinBufferSize(sr, ch, fmt)
        val rec = AudioRecord(MediaRecorder.AudioSource.MIC, sr, ch, fmt, minBuf * 2)

        val dir = File(filesDir, "audio"); dir.mkdirs()
        outFile = File(dir, "rec_${System.currentTimeMillis()}.wav")
        FileOutputStream(outFile).use { fos ->
            // Write provisional WAV header (will fix sizes at end)
            writeWavHeader(fos, 1, sr, 16, 0)
            val buf = ByteArray(minBuf)
            startTime = System.currentTimeMillis()
            rec.startRecording()
            while (isActive) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) fos.write(buf, 0, n)
                delay(10)
            }
            rec.stop()
            rec.release()
            // Fix header sizes
            val totalAudioLen = outFile.length() - 44
            RandomAccessFile(outFile, "rw").use { raf ->
                raf.seek(0)
                writeWavHeader(raf, 1, sr, 16, totalAudioLen.toInt())
            }
        }

        val dur = System.currentTimeMillis() - startTime
        val db = AppDb.get(this)
        val recId = db.recordingDao().insert(Recording(0, outFile.absolutePath, System.currentTimeMillis(), dur))

        // Enqueue transcription work
        TranscribeWork.enqueue(this, recId, outFile.absolutePath)
        stopSelf()
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
}
