package com.example.offlinehqasr.data.entities

import androidx.room.*

@Entity(tableName = "recordings")
data class Recording(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val filePath: String,
    val createdAt: Long,
    val durationMs: Long
)

@Entity(tableName = "transcripts",
    foreignKeys = [ForeignKey(entity = Recording::class, parentColumns = ["id"], childColumns = ["recordingId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("recordingId")]
)
data class Transcript(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val recordingId: Long,
    val text: String
)

@Entity(tableName = "summaries",
    foreignKeys = [ForeignKey(entity = Recording::class, parentColumns = ["id"], childColumns = ["recordingId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("recordingId")]
)
data class Summary(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val recordingId: Long,
    val json: String
)

@Entity(tableName = "segments",
    foreignKeys = [ForeignKey(entity = Recording::class, parentColumns = ["id"], childColumns = ["recordingId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("recordingId")]
)
data class Segment(
    @PrimaryKey(autoGenerate = true) val id: Long,
    var recordingId: Long,
    val startMs: Long,
    val endMs: Long,
    val text: String
)
