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
    val prefs = context.getSharedPreferences("custom_covers", Context.MODE_PRIVATE)
    val customPath = prefs.getString(song.data, null) // Use filePath for stability
    
    if (customPath != null) {
        val file = File(customPath)
        if (file.exists()) {
            this.load(file) {
                crossfade(150)
                placeholder(R.drawable.default_album_art)
                error(R.drawable.default_album_art)
                transformations(RoundedCornersTransformation(12f))
            }
            return
        }
    }
    
    // PRIORITY: Embedded Art from File
    // We use the file path directly (song.data) as Coil is extremely stable at extracting embedded art.
    // We avoid the system URI (MediaStore album_art) entirely because it can trigger IllegalStateException on some devices.
    this.load(song.data) {
        crossfade(150)
        placeholder(R.drawable.default_album_art)
        error(R.drawable.default_album_art) // Non-crashing default fallback
        transformations(RoundedCornersTransformation(12f))
    }
}
