package com.example.offlinehqasr.recorder

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.offlinehqasr.data.AppDb
import com.example.offlinehqasr.data.entities.TranscriptFts
import com.example.offlinehqasr.summary.Summarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject

class TranscribeWork(appContext: Context, params: WorkerParameters): CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val recordingId = inputData.getLong("recordingId", -1L)
        if (recordingId <= 0) return@withContext Result.failure()
        val path = inputData.getString("path") ?: return@withContext Result.failure()
        val db = AppDb.get(applicationContext)

        try {
            val result = transcribeWithPreferredEngine(path)
            persistResult(db, recordingId, result)
            Result.success()
        } catch (e: IOException) {
            Result.retry()
        } catch (e: IllegalStateException) {
            Result.failure()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    companion object {
        fun enqueue(ctx: Context, recordingId: Long, path: String) {
            val req = OneTimeWorkRequestBuilder<TranscribeWork>()
                .setInputData(Data.Builder().putLong("recordingId", recordingId).putString("path", path).build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORKER)
                .build()
            WorkManager.getInstance(ctx).enqueue(req)
        }
    }

    private fun transcribeWithPreferredEngine(path: String): TranscriptionResult {
        if (WhisperEngine.isAvailable(applicationContext)) {
            try {
                val whisper = WhisperEngine(applicationContext)
                return whisper.transcribeFile(path)
            } catch (e: WhisperEngine.WhisperUnavailableException) {
                Log.w(TAG, "Modèle Whisper indisponible, bascule sur Vosk", e)
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "Mémoire insuffisante pour Whisper, bascule sur Vosk", e)
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Bibliothèque native Whisper manquante", e)
            }
        }
        return VoskEngine(applicationContext).transcribeFile(path)
    }

    private fun persistResult(db: AppDb, recordingId: Long, result: TranscriptionResult) {
        db.runInTransaction {
            val transcriptDao = db.transcriptDao()
            val segmentDao = db.segmentDao()
            val summaryDao = db.summaryDao()
            val searchDao = db.transcriptSearchDao()

            val existingTranscript = transcriptDao.getByRecording(recordingId)
            val transcript = result.toTranscript(recordingId).copy(id = existingTranscript?.id ?: 0)
            transcriptDao.insert(transcript)

            segmentDao.deleteByRecording(recordingId)
            segmentDao.insertAll(result.segments.map { it.copy(recordingId = recordingId) })

            val summary = Summarizer.summarizeToJson(result.text, result.durationMs)
            val existingSummary = summaryDao.getByRecording(recordingId)
            val summaryEntity = summary.copy(id = existingSummary?.id ?: 0, recordingId = recordingId)
            summaryDao.insert(summaryEntity)

            val metadata = extractSummaryMetadata(summaryEntity.json)
            val keywords = (metadata.keywords + metadata.topics).map { it.trim() }.filter { it.isNotEmpty() }
            val segmentsText = result.segments
                .map { it.text.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(separator = "\n")
            val tags = metadata.tags.map { it.trim() }.filter { it.isNotEmpty() }
            val participants = metadata.participants.map { it.trim() }.filter { it.isNotEmpty() }

            searchDao.deleteByRecording(recordingId)
            searchDao.insert(
                TranscriptFts(
                    recordingId = recordingId,
                    transcript = result.text.trim(),
                    segments = segmentsText,
                    keywords = keywords.joinToString(separator = "\n"),
                    tags = tags.joinToString(separator = "\n"),
                    participants = participants.joinToString(separator = "\n")
                )
            )
        }
    }
    private companion object {
        const val TAG = "TranscribeWork"
    }

    private data class SummaryMetadata(
        val keywords: List<String>,
        val tags: List<String>,
        val participants: List<String>,
        val topics: List<String>
    )

    private fun extractSummaryMetadata(json: String?): SummaryMetadata {
        if (json.isNullOrBlank()) {
            return SummaryMetadata(emptyList(), emptyList(), emptyList(), emptyList())
        }
        return runCatching {
            val root = JSONObject(json)
            val keywords = root.optJSONArray("keywords").toStringList()
            val tags = root.optJSONArray("tags").toStringList()
            val topics = root.optJSONArray("topics").toStringList()
            val participants = mutableSetOf<String>()
            participants.addAll(root.optJSONArray("participants").toStringList())
            val actions = root.optJSONArray("actions")
            if (actions != null) {
                for (i in 0 until actions.length()) {
                    val who = actions.optJSONObject(i)?.optString("who")?.trim()
                    if (!who.isNullOrEmpty()) {
                        participants.add(who)
                    }
                }
            }
            SummaryMetadata(keywords, tags, participants.toList(), topics)
        }.getOrDefault(SummaryMetadata(emptyList(), emptyList(), emptyList(), emptyList()))
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val values = mutableListOf<String>()
        for (i in 0 until length()) {
            optString(i)?.trim()?.takeIf { it.isNotEmpty() }?.let { values.add(it) }
        }
        return values
    }
}
