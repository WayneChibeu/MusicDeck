package com.wayne.musicdeck.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HiddenSongDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun hide(hiddenSong: HiddenSong)

    @Query("DELETE FROM hidden_songs WHERE songId = :songId")
    suspend fun unhide(songId: Long)

    @Query("SELECT songId FROM hidden_songs")
    suspend fun getHiddenSongIds(): List<Long>

    @Query("SELECT songId FROM hidden_songs")
    fun getHiddenSongIdsLive(): LiveData<List<Long>>

    @Query("SELECT EXISTS(SELECT 1 FROM hidden_songs WHERE songId = :songId)")
    suspend fun isHidden(songId: Long): Boolean

    @Query("SELECT COUNT(*) FROM hidden_songs")
    suspend fun getHiddenCount(): Int
}
