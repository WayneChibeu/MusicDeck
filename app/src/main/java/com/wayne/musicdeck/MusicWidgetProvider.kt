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

        fun getRoundedCornerBitmap(bitmap: Bitmap, cornerRadiusDp: Float, context: Context): Bitmap {
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

        fun getCircularBitmap(bitmap: Bitmap, targetSizeDp: Int, context: Context): Bitmap {
            return try {
                val density = context.resources.displayMetrics.density
                val targetSizePx = (targetSizeDp * density).toInt().coerceAtLeast(64)
                val scaled = Bitmap.createScaledBitmap(bitmap, targetSizePx, targetSizePx, true)
                val output = Bitmap.createBitmap(targetSizePx, targetSizePx, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(output)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                val radius = targetSizePx / 2f
                canvas.drawCircle(radius, radius, radius, paint)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                canvas.drawBitmap(scaled, 0f, 0f, paint)
                output
            } catch (e: Exception) {
                bitmap
            }
        }

        fun createVinylRecordBitmap(art: Bitmap?, targetSizeDp: Int, context: Context): Bitmap {
            return try {
                val density = context.resources.displayMetrics.density
                val sizePx = (targetSizeDp * density).toInt().coerceAtLeast(180)
                val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(output)
                val center = sizePx / 2f
                val radius = sizePx / 2f - 2f

                // Disc Body (Deep vinyl black base)
                val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.parseColor("#121215")
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(center, center, radius, discPaint)

                if (art != null) {
                    // Full circular album art covering the entire vinyl face
                    val artSize = (radius * 2).toInt()
                    val scaledArt = Bitmap.createScaledBitmap(art, artSize, artSize, true)
                    val circularArt = Bitmap.createBitmap(artSize, artSize, Bitmap.Config.ARGB_8888)
                    val artCanvas = Canvas(circularArt)
                    val artPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                    artCanvas.drawCircle(radius, radius, radius, artPaint)
                    artPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                    artCanvas.drawBitmap(scaledArt, 0f, 0f, artPaint)

                    canvas.drawBitmap(circularArt, center - radius, center - radius, null)

                    // Vinyl Grooves overlaid across the full album art (subtle dark translucent rings)
                    val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb(85, 0, 0, 0)
                        style = Paint.Style.STROKE
                        strokeWidth = 1.2f
                    }
                    var r = radius * 0.18f
                    while (r < radius * 0.98f) {
                        canvas.drawCircle(center, center, r, groovePaint)
                        r += 3.5f * density
                    }

                    // Subtle dark vignette gradient around the outer rim of the vinyl
                    val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = android.graphics.RadialGradient(
                            center, center, radius,
                            intArrayOf(android.graphics.Color.TRANSPARENT, android.graphics.Color.argb(90, 0, 0, 0)),
                            floatArrayOf(0.72f, 1.0f),
                            android.graphics.Shader.TileMode.CLAMP
                        )
                    }
                    canvas.drawCircle(center, center, radius, vignettePaint)
                } else {
                    // Fallback when no art: Classic black vinyl with grey grooves
                    val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.parseColor("#25252B")
                        style = Paint.Style.STROKE
                        strokeWidth = 1.2f
                    }
                    var r = radius * 0.35f
                    while (r < radius * 0.95f) {
                        canvas.drawCircle(center, center, r, groovePaint)
                        r += 3.5f * density
                    }

                    val defaultLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.parseColor("#2B2B32")
                        style = Paint.Style.FILL
                    }
                    canvas.drawCircle(center, center, radius * 0.38f, defaultLabelPaint)
                }

                // Vinyl Sheen Highlight (Sleek reflections)
                val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.parseColor("#18FFFFFF")
                    style = Paint.Style.STROKE
                    strokeWidth = 3.5f * density
                }
                val sheenRect = RectF(center - radius * 0.72f, center - radius * 0.72f, center + radius * 0.72f, center + radius * 0.72f)
                canvas.drawArc(sheenRect, 30f, 60f, false, sheenPaint)
                canvas.drawArc(sheenRect, 210f, 60f, false, sheenPaint)

                // Spindle Hole in Center
                val spindlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.parseColor("#09090B")
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(center, center, radius * 0.08f, spindlePaint)

                // Outer edge ring
                val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.parseColor("#383842")
                    style = Paint.Style.STROKE
                    strokeWidth = 1.5f
                }
                canvas.drawCircle(center, center, radius, rimPaint)

                output
            } catch (e: Exception) {
                art ?: Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            }
        }

        fun getPendingIntent(context: Context, action: String): PendingIntent {
            val intent = Intent(context, MusicWidgetProvider::class.java).apply {
                this.action = action
            }
            val reqCode = action.hashCode()
            return PendingIntent.getBroadcast(context, reqCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        fun pushUpdate(
            context: Context,
            title: String,
            artist: String,
            isPlaying: Boolean,
            isFavorite: Boolean,
            album_artBitmap: Bitmap? = null,
            duration: Long = 0L,
            position: Long = 0L,
            isShuffle: Boolean = false,
            repeatMode: Int = 0
        ) {
            val manager = AppWidgetManager.getInstance(context)

            // 1. Standard Control Widget (4x1)
            val standardComp = ComponentName(context, MusicWidgetProvider::class.java)
            for (id in manager.getAppWidgetIds(standardComp)) {
                updateAppWidget(context, manager, id, title, artist, isPlaying, isFavorite, album_artBitmap)
            }

            // 2. Minimal Floating Pill (2x1)
            val pillComp = ComponentName(context, PillWidgetProvider::class.java)
            for (id in manager.getAppWidgetIds(pillComp)) {
                PillWidgetProvider.updateAppWidget(context, manager, id, title, artist, isPlaying, album_artBitmap)
            }

            // 3. Spinning Vinyl Turntable (2x2)
            val vinylComp = ComponentName(context, VinylWidgetProvider::class.java)
            for (id in manager.getAppWidgetIds(vinylComp)) {
                VinylWidgetProvider.updateAppWidget(context, manager, id, title, artist, isPlaying, isFavorite, album_artBitmap)
            }

            // 4. Master Deck Dashboard (4x2)
            val masterDeckComp = ComponentName(context, MasterDeckWidgetProvider::class.java)
            for (id in manager.getAppWidgetIds(masterDeckComp)) {
                MasterDeckWidgetProvider.updateAppWidget(
                    context, manager, id, title, artist, isPlaying, isFavorite, album_artBitmap,
                    duration, position, isShuffle, repeatMode
                )
            }
        }
    }
}
