package com.example.offlinehqasr.recorder

import android.content.Context
import android.widget.Toast
import androidx.work.*
import com.example.offlinehqasr.data.AppDb
import com.example.offlinehqasr.R
import com.example.offlinehqasr.settings.SettingsRepository
import com.example.offlinehqasr.settings.UserSettings
import com.example.offlinehqasr.summary.Summarizer
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class TranscribeWork(appContext: Context, params: WorkerParameters): CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val recordingId = inputData.getLong("recordingId", -1L)
        if (recordingId <= 0) return@withContext Result.failure()
        val path = inputData.getString("path") ?: return@withContext Result.failure()
        val db = AppDb.get(applicationContext)

        val settingsRepo = SettingsRepository(applicationContext)
        val settings = settingsRepo.current()
        val selection = selectEngine(settings)
        val engine = selection.engine
        var metadata = selection.metadata
        try {
            val result = engine.transcribeFile(path)
            persistResult(db, recordingId, result, metadata)
            Result.success()
        } catch (e: IOException) {
            Result.retry()
        } catch (e: IllegalStateException) {
            Result.failure()
        } catch (e: UnsupportedOperationException) {
            return@withContext if (engine is WhisperEngine) {
                notifyWhisperFallback()
                val fallback = VoskEngine(applicationContext)
                val result = fallback.transcribeFile(path)
                metadata = metadata.copy(engine = "vosk")
                persistResult(db, recordingId, result, metadata)
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

    private suspend fun selectEngine(settings: UserSettings): EngineSelection {
        testOverride?.let { override ->
            val custom = override(applicationContext, settings)
            if (custom != null) return custom
        }
        if (settings.useWhisper) {
            if (WhisperEngine.isAvailable(settings)) {
                val modelFile = WhisperEngine.resolveModelFile(settings)!!
                return EngineSelection(
                    engine = WhisperEngine(modelFile),
                    metadata = TranscriptionMetadata("whisper", settings.preferredLanguage)
                )
            }
            notifyWhisperFallback()
        }
        return EngineSelection(
            engine = VoskEngine(applicationContext),
            metadata = TranscriptionMetadata("vosk", settings.preferredLanguage)
        )
    }

    private suspend fun notifyWhisperFallback() {
        withContext(Dispatchers.Main) {
            Toast.makeText(applicationContext, R.string.whisper_fallback_toast, Toast.LENGTH_LONG).show()
        }
    }

    private fun persistResult(
        db: AppDb,
        recordingId: Long,
        result: TranscriptionResult,
        metadata: TranscriptionMetadata
    ) {
        val normalizedTranscript = result.toTranscript(recordingId)
        val existingTranscript = db.transcriptDao().getByRecording(recordingId)
        db.transcriptDao().insert(normalizedTranscript.copy(id = existingTranscript?.id ?: 0))

        db.segmentDao().deleteByRecording(recordingId)
        val mergedSegments = result.mergeShortSegments().map { it.copy(recordingId = recordingId, id = 0) }
        db.segmentDao().insertAll(mergedSegments)

        val summary = Summarizer.summarizeToJson(result.normalizedText(), result.durationMs)
        val summaryJson = JSONObject(summary.json)
        val sttJson = JSONObject()
            .put("engine", metadata.engine)
            .put("language", metadata.language)
        summaryJson.put("stt", sttJson)

        val existingSummary = db.summaryDao().getByRecording(recordingId)
        db.summaryDao().insert(
            summary.copy(
                id = existingSummary?.id ?: 0,
                recordingId = recordingId,
                json = summaryJson.toString()
            )
        )
    }

    internal data class TranscriptionMetadata(val engine: String, val language: String)

    internal data class EngineSelection(
        val engine: SpeechToTextEngine,
        val metadata: TranscriptionMetadata
    )

    companion object {
        internal var testOverride: (suspend (Context, UserSettings) -> EngineSelection?)? = null
    }
}
