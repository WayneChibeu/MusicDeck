package com.wayne.musicdeck

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.widget.ImageView
import coil.load
import coil.transform.RoundedCornersTransformation
import java.io.File

/**
 * Extension to load song cover, prioritizing custom covers from repository.
 */
fun ImageView.loadSongCover(song: Song) {
    val context = this.context
    // Directly access SharedPreferences to avoid dependency injection complexity in Views
    // This matches logic in CustomCoverRepository
    val prefs = context.getSharedPreferences("custom_covers", Context.MODE_PRIVATE)
    val customPath = prefs.getString(song.id.toString(), null)
    
    if (customPath != null) {
        val file = File(customPath)
        if (file.exists()) {
            this.load(file) {
                crossfade(150)
                placeholder(R.drawable.default_album_art)
                error(R.drawable.default_album_art)
                transformations(RoundedCornersTransformation(12f))
                memoryCacheKey("custom_cover_${song.id}_${file.lastModified()}")
            }
            return
        }
    }
    
    // Fallback to MediaStore
    val albumArtUri = ContentUris.withAppendedId(
        Uri.parse("content://media/external/audio/albumart"),
        song.albumId
    )
    
    this.load(albumArtUri) {
        crossfade(150)
        placeholder(R.drawable.default_album_art)
        error(R.drawable.default_album_art)
        transformations(RoundedCornersTransformation(12f))
        memoryCacheKey("album_art_${song.albumId}")
        diskCacheKey("album_art_${song.albumId}")
    }
}
