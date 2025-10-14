package com.example.offlinehqasr.recorder

import android.content.Context
import androidx.work.*
import com.example.offlinehqasr.data.AppDb
import com.example.offlinehqasr.summary.Summarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TranscribeWork(appContext: Context, params: WorkerParameters): CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val recordingId = inputData.getLong("recordingId", -1L)
        val path = inputData.getString("path") ?: return@withContext Result.failure()
        val db = AppDb.get(applicationContext)

        val engine = VoskEngine(applicationContext)
        val result = engine.transcribeFile(path)

        val trId = db.transcriptDao().insert(result.toTranscript(recordingId))
        db.segmentDao().insertAll(result.segments.map { it.copy(recordingId = recordingId) })

        val summary = Summarizer.summarizeToJson(result.text, result.durationMs)
        db.summaryDao().insert(summary.copy(recordingId = recordingId))

        Result.success()
    }

    companion object {
        fun enqueue(ctx: Context, recordingId: Long, path: String) {
            val req = OneTimeWorkRequestBuilder<TranscribeWork>()
                .setInputData(Data.Builder().putLong("recordingId", recordingId).putString("path", path).build())
                .build()
            WorkManager.getInstance(ctx).enqueue(req)
        }
    }
}
