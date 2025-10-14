package com.example.offlinehqasr.recorder

import android.content.Context
import androidx.work.*
import com.example.offlinehqasr.data.AppDb
import com.example.offlinehqasr.summary.Summarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class TranscribeWork(appContext: Context, params: WorkerParameters): CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val recordingId = inputData.getLong("recordingId", -1L)
        if (recordingId <= 0) return@withContext Result.failure()
        val path = inputData.getString("path") ?: return@withContext Result.failure()
        val db = AppDb.get(applicationContext)

        val engine = selectEngine()
        try {
            val result = engine.transcribeFile(path)
            persistResult(db, recordingId, result)
            Result.success()
        } catch (e: IOException) {
            Result.retry()
        } catch (e: IllegalStateException) {
            Result.failure()
        } catch (e: UnsupportedOperationException) {
            return@withContext if (engine is WhisperEngine) {
                val fallback = VoskEngine(applicationContext)
                val result = fallback.transcribeFile(path)
                persistResult(db, recordingId, result)
                Result.success()
            } else {
                Result.failure()
            }
        } catch (e: Exception) {
            Result.failure()
        }
    }

    companion object {
        fun enqueue(ctx: Context, recordingId: Long, path: String) {
            val req = OneTimeWorkRequestBuilder<TranscribeWork>()
                .setInputData(Data.Builder().putLong("recordingId", recordingId).putString("path", path).build())
                .build()
            WorkManager.getInstance(ctx).enqueue(req)
        }
    }

    private fun selectEngine(): SpeechToTextEngine {
        return if (WhisperEngine.isAvailable(applicationContext)) {
            WhisperEngine(applicationContext)
        } else {
            VoskEngine(applicationContext)
        }
    }

    private fun persistResult(db: AppDb, recordingId: Long, result: TranscriptionResult) {
        val existingTranscript = db.transcriptDao().getByRecording(recordingId)
        db.transcriptDao().insert(
            result.toTranscript(recordingId).copy(id = existingTranscript?.id ?: 0)
        )
        db.segmentDao().deleteByRecording(recordingId)
        db.segmentDao().insertAll(result.segments.map { it.copy(recordingId = recordingId) })

        val summary = Summarizer.summarizeToJson(result.text, result.durationMs)
        val existingSummary = db.summaryDao().getByRecording(recordingId)
        db.summaryDao().insert(summary.copy(id = existingSummary?.id ?: 0, recordingId = recordingId))
    }
}
