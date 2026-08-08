package com.wayne.musicdeck.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [CustomMetadata::class, SongPlayCount::class, HiddenSong::class, Playlist::class, PlaylistSong::class, PlayHistoryEntry::class], version = 8, exportSchema = false)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun playCountDao(): PlayCountDao
    abstract fun customMetadataDao(): CustomMetadataDao
    abstract fun hiddenSongDao(): HiddenSongDao
    abstract fun playHistoryDao(): PlayHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: MusicDatabase? = null

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE custom_metadata ADD COLUMN notes TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS `play_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `songId` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL)")
            }
        }

        fun getDatabase(context: Context): MusicDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MusicDatabase::class.java,
                    "music_database"
                )
                .addMigrations(MIGRATION_7_8)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
