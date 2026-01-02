package com.wayne.musicdeck.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "song_play_counts")
data class SongPlayCount(
    @PrimaryKey
    val songId: Long,
    val playCount: Int = 0,
    val lastPlayed: Long = System.currentTimeMillis()
)
