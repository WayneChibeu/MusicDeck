package com.wayne.musicdeck.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PlayCountDao {
    @Query("SELECT playCount FROM song_play_counts WHERE filePath = :filePath")
    suspend fun getPlayCount(filePath: String): Int?
    
    @Query("SELECT * FROM song_play_counts ORDER BY playCount DESC LIMIT :limit")
    suspend fun getMostPlayed(limit: Int = 20): List<SongPlayCount>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(playCount: SongPlayCount)
    
    @Query("UPDATE song_play_counts SET playCount = playCount + 1, lastPlayed = :timestamp WHERE filePath = :filePath")
    suspend fun incrementPlayCount(filePath: String, timestamp: Long = System.currentTimeMillis())
    
    @Query("INSERT OR IGNORE INTO song_play_counts (filePath, songId, playCount, lastPlayed) VALUES (:filePath, :songId, 0, :timestamp)")
    suspend fun ensureExists(filePath: String, songId: Long, timestamp: Long = System.currentTimeMillis())
    
    @Query("SELECT * FROM song_play_counts")
    suspend fun getAllPlayCounts(): List<SongPlayCount>
}
