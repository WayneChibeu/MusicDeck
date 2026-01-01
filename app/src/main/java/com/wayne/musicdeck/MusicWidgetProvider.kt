package com.wayne.musicdeck

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class MusicWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Perform this loop procedure for each AppWidget that belongs to this provider
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }
    
    // Called when the first widget is created
    override fun onEnabled(context: Context) {
        // Register listening logic if needed, but Service push is better
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.wayne.musicdeck.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.wayne.musicdeck.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.wayne.musicdeck.ACTION_PREVIOUS"
        const val ACTION_FAVORITE = "com.wayne.musicdeck.ACTION_FAVORITE"
        const val ACTION_SHUFFLE = "com.wayne.musicdeck.ACTION_SHUFFLE"
        const val ACTION_REPEAT = "com.wayne.musicdeck.ACTION_REPEAT"
        
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            title: String = "Not Playing",
            artist: String = "MusicDeck",
            isPlaying: Boolean = false,
            isFavorite: Boolean = false,
            albumArtUri: android.net.Uri? = null,
            albumArtBitmap: android.graphics.Bitmap? = null
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_music_control)
            
            // ... (keep existing text/buttons setup) ...
            // Combine title and artist for the new layout
            val displayText = if (artist.isNotEmpty() && artist != "MusicDeck") "$title - $artist" else title
            views.setTextViewText(R.id.tvWidgetTitle, displayText)
            
            // Set Play/Pause icon
            val playIcon = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            views.setImageViewResource(R.id.btnWidgetPlayPause, playIcon)
            
            // Set Favorite Icon and Color
            val favIcon = if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border
            views.setImageViewResource(R.id.btnWidgetFavorite, favIcon)
            
            // Set tint color: RED when favorited, WHITE when not
            val favColor = if (isFavorite) android.graphics.Color.RED else android.graphics.Color.WHITE
            views.setInt(R.id.btnWidgetFavorite, "setColorFilter", favColor)
            
            // PendingIntents for buttons
            views.setOnClickPendingIntent(R.id.btnWidgetPlayPause, getPendingIntent(context, ACTION_PLAY_PAUSE))
            views.setOnClickPendingIntent(R.id.btnWidgetNext, getPendingIntent(context, ACTION_NEXT))
            views.setOnClickPendingIntent(R.id.btnWidgetPrev, getPendingIntent(context, ACTION_PREVIOUS))
            views.setOnClickPendingIntent(R.id.btnWidgetFavorite, getPendingIntent(context, ACTION_FAVORITE))
            
            // Open App on click
            val appIntent = Intent(context, MainActivity::class.java)
            val appPendingIntent = PendingIntent.getActivity(context, 0, appIntent, PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widgetBox, appPendingIntent)

            // Album art - prioritize bitmap, fallback to default
            if (albumArtBitmap != null) {
                 views.setImageViewBitmap(R.id.ivWidgetArt, albumArtBitmap)
            } else {
                 // Always show default art if no valid bitmap
                 views.setImageViewResource(R.id.ivWidgetArt, R.drawable.default_album_art)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
        
        private fun getPendingIntent(context: Context, action: String): PendingIntent {
            val intent = Intent(context, MusicService::class.java).apply {
                this.action = action
            }
            val reqCode = action.hashCode()
            return PendingIntent.getService(context, reqCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        
        fun pushUpdate(context: Context, title: String, artist: String, isPlaying: Boolean, isFavorite: Boolean, albumArtUri: android.net.Uri?, albumArtBitmap: android.graphics.Bitmap? = null) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, MusicWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            for (id in ids) {
                updateAppWidget(context, manager, id, title, artist, isPlaying, isFavorite, albumArtUri, albumArtBitmap)
            }
        }
    }
}
