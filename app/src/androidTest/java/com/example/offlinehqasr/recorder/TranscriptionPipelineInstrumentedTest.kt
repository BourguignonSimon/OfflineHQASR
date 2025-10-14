package com.example.offlinehqasr.recorder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Data
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.offlinehqasr.data.AppDb
import com.example.offlinehqasr.data.entities.Recording
import com.example.offlinehqasr.export.ExportUtils
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TranscriptionPipelineInstrumentedTest {

    private lateinit var context: Context
    private lateinit var db: AppDb
    private val tempFiles = mutableListOf<File>()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = AppDb.get(context)
        TranscribeWork.testOverride = { _, settings ->
            val segments = listOf(
                com.example.offlinehqasr.data.entities.Segment(0, 0, 0, 500, "Bonjour"),
                com.example.offlinehqasr.data.entities.Segment(0, 0, 500, 1200, "à tous")
            )
            TranscribeWork.EngineSelection(
                engine = object : SpeechToTextEngine {
                    override fun transcribeFile(path: String): TranscriptionResult {
                        return TranscriptionResult("Bonjour à tous", segments, 1200)
                    }
                },
                metadata = TranscribeWork.TranscriptionMetadata("test", settings.preferredLanguage)
            )
        }
    }

    @After
    fun tearDown() {
        TranscribeWork.testOverride = null
        tempFiles.forEach { it.delete() }
    }

    @Test
    fun workerAndExport_generateJson() = runBlocking {
        val audioDir = File(context.filesDir, "audio").apply { mkdirs() }
        val audioFile = File(audioDir, "instrumented_test.wav")
        audioFile.writeBytes(ByteArray(48_000))
        tempFiles += audioFile

        val recId = db.recordingDao().insert(
            Recording(
                id = 0,
                filePath = audioFile.absolutePath,
                createdAt = System.currentTimeMillis(),
                durationMs = 10_000
            )
        )

        val worker = TestListenableWorkerBuilder<TranscribeWork>(context)
            .setInputData(
                Data.Builder()
                    .putLong("recordingId", recId)
                    .putString("path", audioFile.absolutePath)
                    .build()
            )
            .build()

        val result = worker.doWork()
        assertTrue(result is androidx.work.ListenableWorker.Result.Success)

        val exportPath = ExportUtils.exportOneToJson(context, recId)
        val exportFile = File(exportPath)
        tempFiles += exportFile
        assertTrue(exportFile.exists())

        val exported = JSONObject(exportFile.readText())
        assertEquals("test", exported.getJSONObject("stt").getString("engine"))
        assertEquals(1, exported.getJSONArray("segments").length())
    }
}
