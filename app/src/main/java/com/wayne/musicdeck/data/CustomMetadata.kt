package com.wayne.musicdeck.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores custom metadata overrides for songs.
 * Used when Android 10+ prevents editing actual MediaStore data.
 * If a song has a CustomMetadata entry, those values override MediaStore values.
 */
@Entity(tableName = "custom_metadata")
data class CustomMetadata(
    @PrimaryKey
    val songId: Long,
    val customTitle: String? = null,
    val customArtist: String? = null,
    val customAlbum: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
