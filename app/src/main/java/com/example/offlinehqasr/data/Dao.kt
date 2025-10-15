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

data class RecordingSearchRow(
    @Embedded(prefix = "recording_") val recording: Recording,
    @ColumnInfo(name = "snippet") val snippet: String?
)

@Dao
interface TranscriptSearchDao {
    @Query("DELETE FROM transcript_fts WHERE recordingId = :recordingId")
    fun deleteByRecording(recordingId: Long)

    @Insert
    fun insert(entry: TranscriptFts)

    @Query(
        "SELECT " +
            "recordings.id AS recording_id, " +
            "recordings.filePath AS recording_filePath, " +
            "recordings.createdAt AS recording_createdAt, " +
            "recordings.durationMs AS recording_durationMs, " +
            "snippet(transcript_fts, 1, '', '', '…', 10) AS snippet " +
            "FROM transcript_fts " +
            "INNER JOIN recordings ON recordings.id = transcript_fts.recordingId " +
            "WHERE transcript_fts MATCH :matchQuery " +
            "ORDER BY bm25(transcript_fts)"
    )
    fun searchRecordings(matchQuery: String): List<RecordingSearchRow>

    @Query("SELECT tags FROM transcript_fts WHERE tags != ''")
    fun getAllTagsRaw(): List<String>

    @Query("SELECT participants FROM transcript_fts WHERE participants != ''")
    fun getAllParticipantsRaw(): List<String>
}
