package com.wayne.musicdeck.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PlayHistoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlay(entry: PlayHistoryEntry)

    @Query("SELECT * FROM play_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentPlays(limit: Int = 100): List<PlayHistoryEntry>

    @Query("SELECT COUNT(*) FROM play_history WHERE timestamp >= :sinceTimestamp")
    suspend fun getPlaysSince(sinceTimestamp: Long): Int

    @Query("SELECT DISTINCT (timestamp / 86400000) FROM play_history ORDER BY timestamp DESC")
    suspend fun getDistinctPlayDays(): List<Long>
    
    @Query("DELETE FROM play_history")
    suspend fun clearHistory()
}
