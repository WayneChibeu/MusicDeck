package com.wayne.musicdeck

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.widget.RemoteViews

class MasterDeckWidgetProvider : AppWidgetProvider() {

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
            album_artBitmap: Bitmap? = null,
            duration: Long = 0L,
            position: Long = 0L,
            isShuffle: Boolean = false,
            repeatMode: Int = 0
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_master_deck)

            views.setTextViewText(R.id.tvMasterDeckTitle, title)
            views.setTextViewText(R.id.tvMasterDeckArtist, if (artist.isNotEmpty()) artist else "MusicDeck")

            // Play/Pause icon
            val playIcon = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            views.setImageViewResource(R.id.btnMasterDeckPlayPause, playIcon)

            // Favorite button
            val favIcon = if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border
            views.setImageViewResource(R.id.btnMasterDeckFavorite, favIcon)
            if (isFavorite) {
                views.setInt(R.id.btnMasterDeckFavorite, "setColorFilter", Color.RED)
            } else {
                views.setInt(R.id.btnMasterDeckFavorite, "setColorFilter", Color.WHITE)
            }

            // Shuffle button
            val shuffleIcon = if (isShuffle) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle_off
            val shuffleTint = if (isShuffle) Color.parseColor("#F5A623") else Color.parseColor("#8E8E96")
            views.setImageViewResource(R.id.btnMasterDeckShuffle, shuffleIcon)
            views.setInt(R.id.btnMasterDeckShuffle, "setColorFilter", shuffleTint)

            // Repeat button
            val (repeatIcon, repeatTint) = when (repeatMode) {
                1 -> Pair(R.drawable.ic_repeat_one, Color.parseColor("#F5A623"))
                2 -> Pair(R.drawable.ic_repeat_all, Color.parseColor("#F5A623"))
                else -> Pair(R.drawable.ic_repeat_off, Color.parseColor("#8E8E96"))
            }
            views.setImageViewResource(R.id.btnMasterDeckRepeat, repeatIcon)
            views.setInt(R.id.btnMasterDeckRepeat, "setColorFilter", repeatTint)

            // Progress Bar & Times
            val progress = if (duration > 0) ((position.toFloat() / duration) * 1000).toInt().coerceIn(0, 1000) else 0
            views.setProgressBar(R.id.pbMasterDeckProgress, 1000, progress, false)
            views.setTextViewText(R.id.tvMasterDeckCurrentTime, formatTime(position))
            views.setTextViewText(R.id.tvMasterDeckTotalTime, formatTime(duration))

            // Click intents
            views.setOnClickPendingIntent(R.id.btnMasterDeckPlayPause, MusicWidgetProvider.getPendingIntent(context, MusicWidgetProvider.ACTION_PLAY_PAUSE))
            views.setOnClickPendingIntent(R.id.btnMasterDeckNext, MusicWidgetProvider.getPendingIntent(context, MusicWidgetProvider.ACTION_NEXT))
            views.setOnClickPendingIntent(R.id.btnMasterDeckPrev, MusicWidgetProvider.getPendingIntent(context, MusicWidgetProvider.ACTION_PREVIOUS))
            views.setOnClickPendingIntent(R.id.btnMasterDeckFavorite, MusicWidgetProvider.getPendingIntent(context, MusicWidgetProvider.ACTION_FAVORITE))
            views.setOnClickPendingIntent(R.id.btnMasterDeckShuffle, MusicWidgetProvider.getPendingIntent(context, MusicWidgetProvider.ACTION_SHUFFLE))
            views.setOnClickPendingIntent(R.id.btnMasterDeckRepeat, MusicWidgetProvider.getPendingIntent(context, MusicWidgetProvider.ACTION_REPEAT))

            val appIntent = Intent(context, MainActivity::class.java)
            val appPendingIntent = PendingIntent.getActivity(context, 103, appIntent, PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widgetMasterDeckBox, appPendingIntent)

            // Hero album art (squircle with 16dp corners)
            views.setImageViewResource(R.id.ivMasterDeckArt, R.drawable.default_album_art)
            if (album_artBitmap != null) {
                val roundedArt = MusicWidgetProvider.getRoundedCornerBitmap(album_artBitmap, 16f, context)
                views.setImageViewBitmap(R.id.ivMasterDeckArt, roundedArt)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun updateProgressOnly(context: Context, position: Long, duration: Long) {
            val manager = AppWidgetManager.getInstance(context)
            val comp = android.content.ComponentName(context, MasterDeckWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(comp)
            if (ids == null || ids.isEmpty()) return

            val progress = if (duration > 0) ((position.toFloat() / duration) * 1000).toInt().coerceIn(0, 1000) else 0
            val views = RemoteViews(context.packageName, R.layout.widget_master_deck)
            views.setProgressBar(R.id.pbMasterDeckProgress, 1000, progress, false)
            views.setTextViewText(R.id.tvMasterDeckCurrentTime, formatTime(position))
            if (duration > 0) {
                views.setTextViewText(R.id.tvMasterDeckTotalTime, formatTime(duration))
            }

            manager.partiallyUpdateAppWidget(ids, views)
        }

        private fun formatTime(millis: Long): String {
            if (millis <= 0) return "0:00"
            val totalSeconds = millis / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%d:%02d", minutes, seconds)
        }
    }
}
