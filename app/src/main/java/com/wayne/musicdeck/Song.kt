package com.wayne.musicdeck

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val uri: Uri,
    val duration: Long,
    val data: String, // File path
    val dateAdded: Long = 0L
)
