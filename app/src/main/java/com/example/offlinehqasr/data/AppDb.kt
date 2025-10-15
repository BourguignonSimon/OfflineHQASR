package com.example.offlinehqasr.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.example.offlinehqasr.data.dao.*
import com.example.offlinehqasr.data.entities.*
import com.example.offlinehqasr.security.DatabaseKeyProvider
import androidx.sqlite.db.SupportSQLiteDatabase
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [Recording::class, Transcript::class, Summary::class, Segment::class, TranscriptFts::class],
    version = 2,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun transcriptDao(): TranscriptDao
    abstract fun summaryDao(): SummaryDao
    abstract fun segmentDao(): SegmentDao
    abstract fun transcriptSearchDao(): TranscriptSearchDao

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
                        .addMigrations(MIGRATION_1_2)
                        .build()
                } finally {
                    passphrase.fill(0)
                }
            }.also { inst = it }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS transcript_fts USING fts5(" +
                        "recordingId, transcript, segments, keywords, tags, participants)"
                )
                db.execSQL(
                    "INSERT INTO transcript_fts(rowid, recordingId, transcript, segments, keywords, tags, participants) " +
                        "SELECT t.id, t.recordingId, IFNULL(t.text, ''), " +
                        "IFNULL(GROUP_CONCAT(s.text, '\n'), ''), '', '', '' " +
                        "FROM transcripts t " +
                        "LEFT JOIN segments s ON s.recordingId = t.recordingId " +
                        "GROUP BY t.id"
                )
            }
        }
    }
}
