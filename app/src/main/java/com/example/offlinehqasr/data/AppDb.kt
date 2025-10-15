package com.example.offlinehqasr.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.offlinehqasr.data.dao.*
import com.example.offlinehqasr.data.entities.*
import com.example.offlinehqasr.security.DatabaseKeyProvider
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

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
            inst ?: run {
                val passphrase = DatabaseKeyProvider.obtainPassphrase(ctx)
                SQLiteDatabase.loadLibs(ctx)
                val factory = SupportFactory(passphrase.copyOf())
                try {
                    Room.databaseBuilder(ctx, AppDb::class.java, "app.db")
                        .openHelperFactory(factory)
                        .build()
                } finally {
                    passphrase.fill(0)
                }
            }.also { inst = it }
        }
    }
}
