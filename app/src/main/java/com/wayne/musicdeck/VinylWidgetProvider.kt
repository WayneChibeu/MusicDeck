package com.wayne.musicdeck

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.widget.RemoteViews

class VinylWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val kv = com.tencent.mmkv.MMKV.defaultMMKV()
        val lastTitle = kv.decodeString("last_title", "Not Playing") ?: "Not Playing"
        val lastArtist = kv.decodeString("last_artist", "MusicDeck") ?: "MusicDeck"
        val lastIsFavorite = kv.decodeBool("last_is_favorite", false)

        for (appWidgetId in appWidgetIds) {
            updateAppWidget(
                context,
                appWidgetManager,
                appWidgetId,
                title = lastTitle,
                artist = lastArtist,
                isFavorite = lastIsFavorite
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action.startsWith("com.wayne.musicdeck.ACTION_")) {
            val serviceIntent = Intent(context, MusicService::class.java).apply {
                this.action = action
            }
            context.startForegroundService(serviceIntent)
        }
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            title: String = "Not Playing",
            artist: String = "MusicDeck",
            isPlaying: Boolean = false,
            isFavorite: Boolean = false,
            album_artBitmap: Bitmap? = null
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_vinyl_turntable)

            views.setTextViewText(R.id.tvVinylTitle, title)
            views.setTextViewText(R.id.tvVinylArtist, if (artist.isNotEmpty()) artist else "MusicDeck")

            val playIcon = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            views.setImageViewResource(R.id.btnVinylPlayPause, playIcon)

            val favIcon = if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border
            views.setImageViewResource(R.id.btnVinylFavorite, favIcon)
            if (isFavorite) {
                views.setInt(R.id.btnVinylFavorite, "setColorFilter", Color.RED)
            } else {
                views.setInt(R.id.btnVinylFavorite, "setColorFilter", Color.WHITE)
            }

            // Click intents
            views.setOnClickPendingIntent(R.id.btnVinylPlayPause, MusicWidgetProvider.getPendingIntent(context, MusicWidgetProvider.ACTION_PLAY_PAUSE))
            views.setOnClickPendingIntent(R.id.btnVinylNext, MusicWidgetProvider.getPendingIntent(context, MusicWidgetProvider.ACTION_NEXT))
            views.setOnClickPendingIntent(R.id.btnVinylPrev, MusicWidgetProvider.getPendingIntent(context, MusicWidgetProvider.ACTION_PREVIOUS))
            views.setOnClickPendingIntent(R.id.btnVinylFavorite, MusicWidgetProvider.getPendingIntent(context, MusicWidgetProvider.ACTION_FAVORITE))

            val appIntent = Intent(context, MainActivity::class.java)
            val appPendingIntent = PendingIntent.getActivity(context, 102, appIntent, PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widgetVinylBox, appPendingIntent)

            // Dynamic Vinyl Record with authentic grooved disc & artwork center
            val vinylBitmap = MusicWidgetProvider.createVinylRecordBitmap(album_artBitmap, 180, context)
            views.setImageViewBitmap(R.id.ivVinylRecord, vinylBitmap)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
