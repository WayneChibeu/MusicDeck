package com.wayne.musicdeck

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import android.widget.RemoteViews

class MusicWidgetProvider : AppWidgetProvider() {

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
            updateAppWidget(context, appWidgetManager, appWidgetId, title = lastTitle, artist = lastArtist, isFavorite = lastIsFavorite)
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
    
    override fun onEnabled(context: Context) {}

    override fun onDisabled(context: Context) {}

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
            album_artBitmap: Bitmap? = null
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_music_control)
            
            // Clean title and artist hierarchy
            views.setTextViewText(R.id.tvWidgetTitle, title)
            if (artist.isNotEmpty() && artist != "MusicDeck") {
                views.setTextViewText(R.id.tvWidgetArtist, artist)
                views.setViewVisibility(R.id.tvWidgetArtist, View.VISIBLE)
            } else {
                views.setTextViewText(R.id.tvWidgetArtist, "MusicDeck")
                views.setViewVisibility(R.id.tvWidgetArtist, View.VISIBLE)
            }
            
            // Set Play/Pause icon
            val playIcon = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            views.setImageViewResource(R.id.btnWidgetPlayPause, playIcon)
            
            // Set Favorite Icon and Color
            val favIcon = if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border
            views.setImageViewResource(R.id.btnWidgetFavorite, favIcon)
            if (isFavorite) {
                views.setInt(R.id.btnWidgetFavorite, "setColorFilter", android.graphics.Color.RED)
            } else {
                views.setInt(R.id.btnWidgetFavorite, "setColorFilter", android.graphics.Color.WHITE)
            }
            
            // Wire up buttons
            views.setOnClickPendingIntent(R.id.btnWidgetPlayPause, getPendingIntent(context, ACTION_PLAY_PAUSE))
            views.setOnClickPendingIntent(R.id.btnWidgetNext, getPendingIntent(context, ACTION_NEXT))
            views.setOnClickPendingIntent(R.id.btnWidgetPrev, getPendingIntent(context, ACTION_PREVIOUS))
            views.setOnClickPendingIntent(R.id.btnWidgetFavorite, getPendingIntent(context, ACTION_FAVORITE))

            // Open App on click
            val appIntent = Intent(context, MainActivity::class.java)
            val appPendingIntent = PendingIntent.getActivity(context, 0, appIntent, PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widgetBox, appPendingIntent)

            // Album art - ALWAYS set explicitly with scaled squircle transformation
            views.setImageViewResource(R.id.ivWidgetArt, R.drawable.default_album_art)
            if (album_artBitmap != null) {
                val roundedArt = getRoundedCornerBitmap(album_artBitmap, 12f, context)
                views.setImageViewBitmap(R.id.ivWidgetArt, roundedArt)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun getRoundedCornerBitmap(bitmap: Bitmap, cornerRadiusDp: Float, context: Context): Bitmap {
            return try {
                val density = context.resources.displayMetrics.density
                val targetSizePx = (56 * density).toInt().coerceAtLeast(100)
                
                // Downscale to target widget size first so corner radius scales accurately
                val scaledBitmap = if (bitmap.width > targetSizePx || bitmap.height > targetSizePx) {
                    Bitmap.createScaledBitmap(bitmap, targetSizePx, targetSizePx, true)
                } else {
                    bitmap
                }

                val radiusPx = cornerRadiusDp * density
                val output = Bitmap.createBitmap(scaledBitmap.width, scaledBitmap.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(output)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                val rect = Rect(0, 0, scaledBitmap.width, scaledBitmap.height)
                val rectF = RectF(rect)
                
                canvas.drawRoundRect(rectF, radiusPx, radiusPx, paint)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                canvas.drawBitmap(scaledBitmap, rect, rect, paint)
                output
            } catch (e: Exception) {
                bitmap
            }
        }

        private fun getPendingIntent(context: Context, action: String): PendingIntent {
            val intent = Intent(context, MusicWidgetProvider::class.java).apply {
                this.action = action
            }
            val reqCode = action.hashCode()
            return PendingIntent.getBroadcast(context, reqCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        fun pushUpdate(context: Context, title: String, artist: String, isPlaying: Boolean, isFavorite: Boolean, album_artBitmap: Bitmap? = null) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, MusicWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            for (id in ids) {
                updateAppWidget(context, manager, id, title, artist, isPlaying, isFavorite, album_artBitmap)
            }
        }
    }
}
