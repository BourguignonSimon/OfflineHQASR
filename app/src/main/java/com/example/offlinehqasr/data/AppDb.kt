package com.example.offlinehqasr.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.offlinehqasr.data.dao.*
import com.example.offlinehqasr.data.entities.*

@Database(
    entities = [Recording::class, Transcript::class, Summary::class, Segment::class],
    version = 1
)
abstract class AppDb : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun transcriptDao(): TranscriptDao
    abstract fun summaryDao(): SummaryDao
    abstract fun segmentDao(): SegmentDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx, AppDb::class.java, "app.db").build().also { inst = it }
        }
    }
}
