package com.example.offlinehqasr.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.offlinehqasr.data.dao.TranscriptSearchDao
import com.example.offlinehqasr.data.entities.Recording
import com.example.offlinehqasr.data.entities.Transcript
import com.example.offlinehqasr.data.entities.TranscriptFts
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TranscriptSearchDaoTest {

    private lateinit var db: AppDb
    private lateinit var searchDao: TranscriptSearchDao

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDb::class.java)
            .allowMainThreadQueries()
            .build()
        searchDao = db.transcriptSearchDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun searchMatchesTranscriptKeywordsTagsAndParticipants() {
        val recordingOne = Recording(0, "first.wav", System.currentTimeMillis(), 60_000)
        val recordingTwo = Recording(0, "second.wav", System.currentTimeMillis(), 45_000)
        val idOne = db.recordingDao().insert(recordingOne)
        val idTwo = db.recordingDao().insert(recordingTwo)

        db.transcriptDao().insert(Transcript(0, idOne, "Compte rendu de la réunion budget"))
        db.transcriptDao().insert(Transcript(0, idTwo, "Planification sprint et revue"))

        searchDao.insert(
            TranscriptFts(
                recordingId = idOne,
                transcript = "Compte rendu de la réunion budget",
                segments = "Budget approuvé",
                keywords = "budget\nfinance",
                tags = "prioritaire\nQ1",
                participants = "Alice\nBob"
            )
        )
        searchDao.insert(
            TranscriptFts(
                recordingId = idTwo,
                transcript = "Planification sprint et revue",
                segments = "Sprint backlog",
                keywords = "agile\nsprint",
                tags = "daily",
                participants = "Charlie"
            )
        )

        val keywordResults = searchDao.searchRecordings("budget")
        assertEquals(1, keywordResults.size)
        assertEquals(idOne, keywordResults.first().recording.id)
        assertTrue(keywordResults.first().snippet?.contains("budget", ignoreCase = true) == true)

        val tagResults = searchDao.searchRecordings("tags:\"daily\"")
        assertEquals(1, tagResults.size)
        assertEquals(idTwo, tagResults.first().recording.id)

        val participantResults = searchDao.searchRecordings("participants:\"Alice\"")
        assertEquals(1, participantResults.size)
        assertEquals(idOne, participantResults.first().recording.id)

        val tags = searchDao.getAllTagsRaw()
        assertTrue(tags.any { it.contains("daily") })
    }
}
