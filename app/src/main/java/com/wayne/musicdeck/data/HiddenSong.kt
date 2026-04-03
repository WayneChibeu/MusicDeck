package com.wayne.musicdeck.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hidden_songs")
data class HiddenSong(
    @PrimaryKey val songId: Long,
    val hiddenAt: Long = System.currentTimeMillis()
)
