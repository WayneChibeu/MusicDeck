package com.wayne.musicdeck.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val imagePath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
