package com.example.offlinehqasr.data.dao

import androidx.room.*
import com.example.offlinehqasr.data.entities.*

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY createdAt DESC")
    fun getAll(): List<Recording>

    @Query("SELECT * FROM recordings WHERE id = :id")
    fun getById(id: Long): Recording?

    @Insert
    fun insert(rec: Recording): Long
}

@Dao
interface TranscriptDao {
    @Query("SELECT * FROM transcripts WHERE recordingId = :recId")
    fun getByRecording(recId: Long): Transcript?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(tr: Transcript): Long
}

@Dao
interface SummaryDao {
    @Query("SELECT * FROM summaries WHERE recordingId = :recId")
    fun getByRecording(recId: Long): Summary?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(s: Summary): Long
}

@Dao
interface SegmentDao {
    @Query("SELECT * FROM segments WHERE recordingId = :recId ORDER BY startMs")
    fun getByRecording(recId: Long): List<Segment>

    @Insert
    fun insertAll(items: List<Segment>)

    @Query("DELETE FROM segments WHERE recordingId = :recId")
    fun deleteByRecording(recId: Long)
}
