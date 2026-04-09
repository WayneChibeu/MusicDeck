package com.wayne.musicdeck

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.wayne.musicdeck.data.LyricLine
import com.wayne.musicdeck.data.LyricsRepository
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject

class FloatingLyricService : Service() {

    companion object {
        private const val TAG = "FloatingLyric"
        private const val CHANNEL_ID = "floating_lyrics_channel"
        private const val NOTIFICATION_ID = 2001

        fun start(context: Context) {
            val intent = Intent(context, FloatingLyricService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingLyricService::class.java))
        }
    }

    private val lyricsRepository: LyricsRepository by inject()
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var closeTargetView: View? = null
    private var tvFloatingLyric: TextView? = null

    private var mediaController: MediaController? = null
    private var currentLyrics: List<LyricLine> = emptyList()
    private var currentSongPath: String? = null
    private var syncJob: Job? = null

    // Drag state
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        connectToMediaSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun connectToMediaSession() {
        try {
            val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
            val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()

            controllerFuture.addListener({
                try {
                    mediaController = controllerFuture.get()
                    Log.d(TAG, "MediaController connected!")
                    showFloatingView()
                    setupPlayerListener()
                    onSongChanged(mediaController?.currentMediaItem)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to connect to MediaSession", e)
                    stopSelf()
                }
            }, MoreExecutors.directExecutor())
        } catch (e: Exception) {
            Log.e(TAG, "Error creating session token", e)
            stopSelf()
        }
    }

    private fun setupPlayerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                onSongChanged(mediaItem)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    startLyricSync()
                } else {
                    stopLyricSync()
                    tvFloatingLyric?.text = "▷  Paused"
                }
            }
        })
    }

    private fun onSongChanged(mediaItem: MediaItem?) {
        val path = mediaItem?.mediaId ?: return
        if (path == currentSongPath) return
        currentSongPath = path

        // Load lyrics for the new song
        val lrcPath = lyricsRepository.getLyricPath(path)
            ?: lyricsRepository.findLocalLrcFile(path)

        if (lrcPath != null) {
            currentLyrics = lyricsRepository.parseLrcFile(lrcPath)
            Log.d(TAG, "Loaded ${currentLyrics.size} lyric lines for: $path")
        } else {
            currentLyrics = emptyList()
            tvFloatingLyric?.text = mediaItem.mediaMetadata?.title?.toString() ?: "♫"
        }

        if (mediaController?.isPlaying == true) {
            startLyricSync()
        }
    }

    private fun startLyricSync() {
        stopLyricSync()
        if (currentLyrics.isEmpty()) return

        syncJob = serviceScope.launch {
            while (isActive) {
                val player = mediaController ?: break
                val position = player.currentPosition

                // Find the active lyric line
                var activeLine: LyricLine? = null
                for (line in currentLyrics) {
                    if (line.timeMs <= position + 30) { // 30ms lookahead
                        activeLine = line
                    } else {
                        break
                    }
                }

                if (activeLine != null && activeLine.text.isNotBlank()) {
                    tvFloatingLyric?.text = activeLine.text
                }

                delay(100) // Sync every 100ms for butter-smooth updates
            }
        }
    }

    private fun stopLyricSync() {
        syncJob?.cancel()
        syncJob = null
    }

    // ============================
    // FLOATING WINDOW MANAGEMENT
    // ============================

    private fun showFloatingView() {
        if (floatingView != null) return

        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.layout_floating_lyric, null)
        tvFloatingLyric = floatingView?.findViewById(R.id.tvFloatingLyric)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }

        // Setup drag behavior
        setupDragBehavior(params)

        try {
            windowManager?.addView(floatingView, params)
            Log.d(TAG, "Floating view added")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating view", e)
            stopSelf()
        }
    }

    private fun setupDragBehavior(params: WindowManager.LayoutParams) {
        floatingView?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY

                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isDragging = true
                        // Show close target when dragging starts
                        showCloseTarget()
                    }

                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()

                    try {
                        windowManager?.updateViewLayout(floatingView, params)
                    } catch (e: Exception) {
                        // View may have been removed
                    }

                    // Check proximity to close zone
                    val closeTarget = closeTargetView?.findViewById<View>(R.id.closeTargetPill)
                    if (closeTarget != null) {
                        val location = IntArray(2)
                        closeTarget.getLocationOnScreen(location)
                        val targetCenterX = location[0] + closeTarget.width / 2
                        val targetCenterY = location[1] + closeTarget.height / 2
                        val distance = Math.sqrt(
                            Math.pow((event.rawX - targetCenterX).toDouble(), 2.0) +
                                    Math.pow((event.rawY - targetCenterY).toDouble(), 2.0)
                        )

                        // Visual feedback: scale up close target when near
                        if (distance < 200) {
                            closeTarget.animate().scaleX(1.4f).scaleY(1.4f).setDuration(150).start()
                        } else {
                            closeTarget.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                        }
                    }

                    true
                }

                MotionEvent.ACTION_UP -> {
                    hideCloseTarget()

                    if (isDragging) {
                        // Check if dropped on close target
                        val closeTarget = closeTargetView?.findViewById<View>(R.id.closeTargetPill)
                        if (closeTarget != null) {
                            val location = IntArray(2)
                            closeTarget.getLocationOnScreen(location)
                            val targetCenterX = location[0] + closeTarget.width / 2
                            val targetCenterY = location[1] + closeTarget.height / 2
                            val distance = Math.sqrt(
                                Math.pow((event.rawX - targetCenterX).toDouble(), 2.0) +
                                        Math.pow((event.rawY - targetCenterY).toDouble(), 2.0)
                            )

                            if (distance < 200) {
                                // Dropped on close target - dismiss!
                                stopSelf()
                                return@setOnTouchListener true
                            }
                        }
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun showCloseTarget() {
        if (closeTargetView != null) return

        val inflater = LayoutInflater.from(this)
        closeTargetView = inflater.inflate(R.layout.layout_floating_close_target, null)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )

        try {
            windowManager?.addView(closeTargetView, params)
            // Fade in
            closeTargetView?.alpha = 0f
            closeTargetView?.animate()
                ?.alpha(1f)
                ?.setDuration(200)
                ?.start()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add close target", e)
        }
    }

    private fun hideCloseTarget() {
        closeTargetView?.let { view ->
            view.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    try {
                        windowManager?.removeView(view)
                    } catch (e: Exception) {
                        // Already removed
                    }
                    closeTargetView = null
                }
                .start()
        }
    }

    // ============================
    // NOTIFICATION & LIFECYCLE
    // ============================

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Desktop Lyrics",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows lyrics floating over other apps"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, FloatingLyricService::class.java).apply {
            action = "STOP"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Desktop Lyrics Active")
            .setContentText("Tap to return to MusicDeck")
            .setSmallIcon(R.drawable.ic_music_note)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(R.drawable.ic_close, "Stop", stopPendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed, cleaning up")

        stopLyricSync()
        serviceScope.cancel()

        // Remove floating views
        try {
            floatingView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) { /* already removed */ }
        try {
            closeTargetView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) { /* already removed */ }

        floatingView = null
        closeTargetView = null

        mediaController?.release()
        mediaController = null
    }
}
