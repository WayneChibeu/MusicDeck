package com.wayne.musicdeck

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews

class PillWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val kv = com.tencent.mmkv.MMKV.defaultMMKV()
        val lastTitle = kv.decodeString("last_title", "Not Playing") ?: "Not Playing"
        val lastArtist = kv.decodeString("last_artist", "MusicDeck") ?: "MusicDeck"
        
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, title = lastTitle, artist = lastArtist)
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
            album_artBitmap: Bitmap? = null
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_minimal_pill)

            views.setTextViewText(R.id.tvPillTitle, title)
            views.setTextViewText(R.id.tvPillArtist, if (artist.isNotEmpty()) artist else "MusicDeck")

            val playIcon = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            views.setImageViewResource(R.id.btnPillPlayPause, playIcon)

            // Click intents
            views.setOnClickPendingIntent(R.id.btnPillPlayPause, MusicWidgetProvider.getPendingIntent(context, MusicWidgetProvider.ACTION_PLAY_PAUSE))
            views.setOnClickPendingIntent(R.id.btnPillNext, MusicWidgetProvider.getPendingIntent(context, MusicWidgetProvider.ACTION_NEXT))

            val appIntent = Intent(context, MainActivity::class.java)
            val appPendingIntent = PendingIntent.getActivity(context, 101, appIntent, PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widgetPillBox, appPendingIntent)

            views.setImageViewResource(R.id.ivPillArt, R.drawable.default_album_art)
            if (album_artBitmap != null) {
                val circleArt = MusicWidgetProvider.getCircularBitmap(album_artBitmap, 38, context)
                views.setImageViewBitmap(R.id.ivPillArt, circleArt)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
