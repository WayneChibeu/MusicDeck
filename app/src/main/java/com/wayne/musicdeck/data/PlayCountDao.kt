package com.wayne.musicdeck.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PlayCountDao {
    @Query("SELECT playCount FROM song_play_counts WHERE songId = :songId")
    suspend fun getPlayCount(songId: Long): Int?
    
    @Query("SELECT * FROM song_play_counts ORDER BY playCount DESC LIMIT :limit")
    suspend fun getMostPlayed(limit: Int = 20): List<SongPlayCount>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(playCount: SongPlayCount)
    
    @Query("UPDATE song_play_counts SET playCount = playCount + 1, lastPlayed = :timestamp WHERE songId = :songId")
    suspend fun incrementPlayCount(songId: Long, timestamp: Long = System.currentTimeMillis())
    
    @Query("INSERT OR IGNORE INTO song_play_counts (songId, playCount, lastPlayed) VALUES (:songId, 0, :timestamp)")
    suspend fun ensureExists(songId: Long, timestamp: Long = System.currentTimeMillis())
}
