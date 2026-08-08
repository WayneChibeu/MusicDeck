package com.wayne.musicdeck.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "play_history")
data class PlayHistoryEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val songId: Long,
    val timestamp: Long = System.currentTimeMillis()
)
