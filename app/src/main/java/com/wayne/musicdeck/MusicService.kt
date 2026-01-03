package com.wayne.musicdeck

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import com.google.common.util.concurrent.ListenableFuture
import androidx.media3.session.SessionResult
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.ForwardingPlayer
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import androidx.media3.session.SessionCommand
import androidx.media3.session.CommandButton

class MusicService : MediaSessionService() {

    private lateinit var playlistRepository: com.wayne.musicdeck.data.PlaylistRepository
    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.Job())
    private var favoritesPlaylistId: Long = -1L
    private var mediaSession: MediaSession? = null
    private var sleepTimerJob: kotlinx.coroutines.Job? = null
    private var isCurrentSongFavorite = false
    
    companion object {
        const val ACTION_SET_SLEEP_TIMER = "com.wayne.musicdeck.ACTION_SET_SLEEP_TIMER"
        const val ACTION_CANCEL_SLEEP_TIMER = "com.wayne.musicdeck.ACTION_CANCEL_SLEEP_TIMER"
        const val EXTRA_TIMER_MINUTES = "extra_timer_minutes"
    }

    override fun onCreate() {
        super.onCreate()
        val database = com.wayne.musicdeck.data.MusicDatabase.getDatabase(applicationContext)
        playlistRepository = com.wayne.musicdeck.data.PlaylistRepository(database.playlistDao())
        
        // Initialize favorites ID
        serviceScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val favPlaylist = playlistRepository.getOrCreateFavoritesPlaylist()
            favoritesPlaylistId = favPlaylist.id
        }

        val exoPlayer: ExoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setWakeMode(C.WAKE_MODE_LOCAL) 
            .build()
            
        // Initialize Audio Effects Manager
        AudioEffectManager.initialize(exoPlayer.audioSessionId, this)
            
        val player = AutoPlayForwardingPlayer(exoPlayer)

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )


        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .setCallback(object : MediaSession.Callback {
                // Must authorize custom commands for controllers to use them
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    // Define custom commands that controllers can use
                    val shuffleCommand = SessionCommand("SHUFFLE", android.os.Bundle.EMPTY)
                    val repeatCommand = SessionCommand("REPEAT", android.os.Bundle.EMPTY)
                    
                    // Add custom commands to the session commands
                    val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(shuffleCommand)
                        .add(repeatCommand)
                        .add(SessionCommand("TOGGLE_FAVORITE", android.os.Bundle.EMPTY))
                        .build()
                    
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(sessionCommands)
                        .build()
                }
                
                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: android.os.Bundle
                ): com.google.common.util.concurrent.ListenableFuture<SessionResult> {
                    android.util.Log.d("MusicService", "onCustomCommand received: ${customCommand.customAction}")
                    when (customCommand.customAction) {
                        "TOGGLE_FAVORITE" -> {
                            val currentId = player.currentMediaItem?.mediaId?.toLongOrNull()
                            if (currentId != null && favoritesPlaylistId != -1L) {
                                serviceScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    val isFav = playlistRepository.isSongInPlaylist(favoritesPlaylistId, currentId)
                                    if (isFav) {
                                        playlistRepository.removeSongFromPlaylist(favoritesPlaylistId, currentId)
                                        isCurrentSongFavorite = false
                                    } else {
                                        playlistRepository.addSongToPlaylist(favoritesPlaylistId, currentId)
                                        isCurrentSongFavorite = true
                                    }
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        // Update Widget and Notification
                                        updateWidget(player)
                                        // Force notification refresh by re-setting custom layout
                                        updateMediaSessionLayout(player)
                                    }
                                }
                            }
                            return com.google.common.util.concurrent.Futures.immediateFuture(
                                SessionResult(SessionResult.RESULT_SUCCESS)
                            )
                        }
                        "SHUFFLE" -> {
                            android.util.Log.d("MusicService", "Toggling shuffle")
                            player.shuffleModeEnabled = !player.shuffleModeEnabled
                            return com.google.common.util.concurrent.Futures.immediateFuture(
                                SessionResult(SessionResult.RESULT_SUCCESS)
                            )
                        }
                        "REPEAT" -> {
                            android.util.Log.d("MusicService", "Cycling repeat mode")
                            player.repeatMode = when (player.repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                            return com.google.common.util.concurrent.Futures.immediateFuture(
                                SessionResult(SessionResult.RESULT_SUCCESS)
                            )
                        }
                    }
                    return super.onCustomCommand(session, controller, customCommand, args)
                }
                
                override fun onPostConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ) {
                    // Send custom layout with shuffle/repeat buttons to all controllers
                    val shuffleButton = CommandButton.Builder()
                        .setDisplayName("Shuffle")
                        .setIconResId(R.drawable.ic_shuffle)
                        .setSessionCommand(SessionCommand("SHUFFLE", android.os.Bundle.EMPTY))
                        .setEnabled(true)
                        .build()
                    
                    val repeatButton = CommandButton.Builder()
                        .setDisplayName("Repeat")
                        .setIconResId(R.drawable.ic_repeat)
                        .setSessionCommand(SessionCommand("REPEAT", android.os.Bundle.EMPTY))
                        .setEnabled(true)
                        .build()
                    
                    session.setCustomLayout(listOf(shuffleButton, repeatButton))
                }
            })
            .setExtras(android.os.Bundle().apply {
                putInt("AUDIO_SESSION_ID", exoPlayer.audioSessionId)
            })
            .build()
            
        player.addListener(object : Player.Listener {
             override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                 updateMediaSessionLayout(player)
             }
             override fun onRepeatModeChanged(repeatMode: Int) {
                 updateMediaSessionLayout(player)
             }
             override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                 updateWidget(player)
             }
             override fun onIsPlayingChanged(isPlaying: Boolean) {
                  updateWidget(player)
             }
             // No redundant updateWidget in onPlaybackStateChanged needed if covered by others, but keeping original structure is fine if I don't remove it.
             // I will replace only what I selected.
        })
        
        updateMediaSessionLayout(player)
            

        
        exoPlayer.setHandleAudioBecomingNoisy(true)
        
        exoPlayer.setHandleAudioBecomingNoisy(true)
        
        val notificationProvider = CustomNotificationProvider(this)
        setMediaNotificationProvider(notificationProvider)
    }

    private inner class CustomNotificationProvider(context: android.content.Context) : 
        androidx.media3.session.DefaultMediaNotificationProvider(context) {
            
        override fun getMediaButtons(
            session: MediaSession,
            playerCommands: androidx.media3.common.Player.Commands,
            customLayout: com.google.common.collect.ImmutableList<CommandButton>,
            showPauseButton: Boolean
        ): com.google.common.collect.ImmutableList<CommandButton> {
            val builder = com.google.common.collect.ImmutableList.builder<CommandButton>()
            val player = session.player
            
            // 1. Favorite Button (Replaces Shuffle)
            val favIcon = if (isCurrentSongFavorite) R.drawable.ic_favorite_red else R.drawable.ic_favorite_border
            val favBtn = CommandButton.Builder()
                .setDisplayName(if (isCurrentSongFavorite) "Unfavorite" else "Favorite")
                .setIconResId(favIcon)
                .setSessionCommand(SessionCommand("TOGGLE_FAVORITE", android.os.Bundle.EMPTY))
                .setEnabled(true)
                .build()
            builder.add(favBtn)

            // 2. Previous
            val prevBtn = CommandButton.Builder()
                .setDisplayName("Previous")
                .setIconResId(R.drawable.ic_widget_prev)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                .setEnabled(playerCommands.contains(Player.COMMAND_SEEK_TO_PREVIOUS))
                .build()
            builder.add(prevBtn)

            // 3. Play/Pause
            val playIcon = if (showPauseButton) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            val playBtn = CommandButton.Builder()
                .setDisplayName(if (showPauseButton) "Pause" else "Play")
                .setIconResId(playIcon)
                .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                .setEnabled(playerCommands.contains(Player.COMMAND_PLAY_PAUSE))
                .build()
            builder.add(playBtn)

            // 4. Next
            val nextBtn = CommandButton.Builder()
                .setDisplayName("Next")
                .setIconResId(R.drawable.ic_widget_next)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .setEnabled(playerCommands.contains(Player.COMMAND_SEEK_TO_NEXT))
                .build()
            builder.add(nextBtn)

            // 5. Repeat - State-aware icon with "1" for repeat one
            val repeatMode = player.repeatMode
            val repeatIcon = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> androidx.media3.ui.R.drawable.exo_icon_repeat_one
                Player.REPEAT_MODE_ALL -> androidx.media3.ui.R.drawable.exo_icon_repeat_all
                else -> androidx.media3.ui.R.drawable.exo_icon_repeat_off
            }
            val repeatDisplayName = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> "Repeat: One"
                Player.REPEAT_MODE_ALL -> "Repeat: All"
                else -> "Repeat: Off"
            }
            val repeatBtn = CommandButton.Builder()
                .setDisplayName(repeatDisplayName)
                .setIconResId(repeatIcon)
                .setSessionCommand(SessionCommand("REPEAT", android.os.Bundle.EMPTY))
                .setEnabled(true)
                .build()
            builder.add(repeatBtn)
            
            return builder.build()
        }
    }
    
    private fun updateWidget(player: Player) {
        val mediaItem = player.currentMediaItem
        val title = mediaItem?.mediaMetadata?.title?.toString() ?: "Not Playing"
        val artist = mediaItem?.mediaMetadata?.artist?.toString() ?: "MusicDeck"
        val isPlaying = player.isPlaying
        val artUri = mediaItem?.mediaMetadata?.artworkUri
        
        // Check favorite status
        val currentId = mediaItem?.mediaId?.toLongOrNull() ?: -1L

        serviceScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val isFav = if (currentId != -1L && favoritesPlaylistId != -1L) {
                playlistRepository.isSongInPlaylist(favoritesPlaylistId, currentId)
            } else {
                false
            }
            if (currentId != -1L) isCurrentSongFavorite = isFav

            // Load Bitmap for Widget (Fixes missing art on some launchers)
            // Tries artUri first, then fallback to embedded MP3 art
            var artBitmap: android.graphics.Bitmap? = null
            if (artUri != null) {
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        val source = if (artUri.scheme == "file") {
                             android.graphics.ImageDecoder.createSource(java.io.File(artUri.path!!))
                        } else {
                             android.graphics.ImageDecoder.createSource(contentResolver, artUri)
                        }
                        artBitmap = android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                            decoder.setAllocator(android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE)
                            decoder.setTargetSampleSize(2) // Downsample for widget
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        artBitmap = android.provider.MediaStore.Images.Media.getBitmap(contentResolver, artUri)
                    }
                } catch (e: Exception) {
                    // Bitmap load failed - will try embedded art fallback
                    android.util.Log.e("MusicService", "Failed to load widget art from URI: ${e.message}")
                }
            }
            
            // Fallback: Try embedded art from MP3 file (like PlayerBottomSheetFragment does)
            if (artBitmap == null && currentId != -1L) {
                try {
                    val uri = android.content.ContentUris.withAppendedId(
                        android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        currentId
                    )
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(this@MusicService, uri)
                    val embeddedArt = retriever.embeddedPicture
                    retriever.release()
                    if (embeddedArt != null) {
                        artBitmap = android.graphics.BitmapFactory.decodeByteArray(embeddedArt, 0, embeddedArt.size)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MusicService", "Failed to load embedded art: ${e.message}")
                }
            }

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                MusicWidgetProvider.pushUpdate(this@MusicService, title, artist, isPlaying, isFav, artUri, artBitmap)
                if (currentId != -1L) updateMediaSessionLayout(player)
                
                // ALWAYS update artwork data for Notification to prevent stale cached art
                // This ensures each song gets its own art (or lack thereof)
                if (mediaItem != null) {
                    val newMetaBuilder = mediaItem.mediaMetadata.buildUpon()
                    
                    if (artBitmap != null) {
                        val stream = java.io.ByteArrayOutputStream()
                        artBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                        val byteArray = stream.toByteArray()
                        newMetaBuilder.setArtworkData(byteArray, androidx.media3.common.MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    }
                    // Note: Can't set null artwork in Media3, but widget gets explicit null bitmap
                    
                    val newMeta = newMetaBuilder.build()
                    val newItem = mediaItem.buildUpon().setMediaMetadata(newMeta).build()
                    
                    val idx = player.currentMediaItemIndex
                    if (idx != -1 && player.currentMediaItem?.mediaId == newItem.mediaId) {
                         player.replaceMediaItem(idx, newItem)
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val player = mediaSession?.player
        if (player != null && intent != null && intent.action != null) {
            when (intent.action) {
                MusicWidgetProvider.ACTION_PLAY_PAUSE -> {
                    if (player.isPlaying) {
                        player.pause()
                    } else {
                        // Resume Logic
                        if (player.mediaItemCount == 0) {
                            restoreLastSession(player)
                        } else {
                            player.play()
                        }
                    }
                }
                MusicWidgetProvider.ACTION_FAVORITE -> {
                    val currentId = player.currentMediaItem?.mediaId?.toLongOrNull()
                    if (currentId != null && favoritesPlaylistId != -1L) {
                        serviceScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val isFav = playlistRepository.isSongInPlaylist(favoritesPlaylistId, currentId)
                            if (isFav) {
                                playlistRepository.removeSongFromPlaylist(favoritesPlaylistId, currentId)
                            } else {
                                playlistRepository.addSongToPlaylist(favoritesPlaylistId, currentId)
                            }
                             kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                 updateWidget(player)
                                 updateMediaSessionLayout(player) // Update notification favorite state if we added it there too
                             }
                        }
                    }
                }
                ACTION_SET_SLEEP_TIMER -> {
                    val minutes = intent.getIntExtra(EXTRA_TIMER_MINUTES, 0)
                    if (minutes > 0) {
                        startSleepTimer(minutes)
                    }
                }
                ACTION_CANCEL_SLEEP_TIMER -> {
                    cancelSleepTimer()
                }
                MusicWidgetProvider.ACTION_SHUFFLE -> {
                    player.shuffleModeEnabled = !player.shuffleModeEnabled
                }
                MusicWidgetProvider.ACTION_REPEAT -> {
                    player.repeatMode = when (player.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun updateMediaSessionLayout(player: Player) {
        val shuffleOn = player.shuffleModeEnabled
        val repeatMode = player.repeatMode
        
        // Use our custom modern icons
        val shuffleIcon = if (shuffleOn) {
            R.drawable.ic_shuffle_on
        } else {
            R.drawable.ic_shuffle_off
        }
        
        val repeatIcon = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
            Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat_all
            else -> R.drawable.ic_repeat_off
        }
        
        // Display names show current state
        val shuffleDisplayName = if (shuffleOn) "Shuffle: On" else "Shuffle: Off"
        val repeatDisplayName = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> "Repeat: One"
            Player.REPEAT_MODE_ALL -> "Repeat: All"
            else -> "Repeat: Off"
        }

        val shuffleButton = androidx.media3.session.CommandButton.Builder()
            .setDisplayName(shuffleDisplayName)
            .setIconResId(shuffleIcon)
            .setSessionCommand(androidx.media3.session.SessionCommand("SHUFFLE", android.os.Bundle()))
            .build()

        val repeatButton = androidx.media3.session.CommandButton.Builder()
            .setDisplayName(repeatDisplayName)
            .setIconResId(repeatIcon)
            .setSessionCommand(androidx.media3.session.SessionCommand("REPEAT", android.os.Bundle()))
            .build()
            
        // Favorite Button for Session Layout (Ensures state consistency)
        val favIcon = if (isCurrentSongFavorite) R.drawable.ic_favorite_red else R.drawable.ic_favorite_border
        val favButton = androidx.media3.session.CommandButton.Builder()
             .setDisplayName(if (isCurrentSongFavorite) "Unfavorite" else "Favorite")
             .setIconResId(favIcon)
             .setSessionCommand(androidx.media3.session.SessionCommand("TOGGLE_FAVORITE", android.os.Bundle()))
             .build()
            
        // We'll add these to the custom layout
        // The standard prev/play/next are handled by the system style usually, 
        // but setCustomLayout adds EXTRA buttons.
        // Adding Favorite here triggers the notification provider update and exposes it to other controllers
        mediaSession?.setCustomLayout(listOf(favButton, shuffleButton, repeatButton))
    }
    
    // We need to handle these commands in the callback
    // See initialization update below 


    private fun restoreLastSession(player: Player) {
        val prefs = getSharedPreferences("musicdeck_prefs", android.content.Context.MODE_PRIVATE)
        val lastId = prefs.getLong("last_song_id", -1)
        val lastPos = prefs.getLong("last_position", 0)
        
        if (lastId == -1L) return
        
        serviceScope.launch(kotlinx.coroutines.Dispatchers.IO) {
             val contentUri = android.content.ContentUris.withAppendedId(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                lastId
            )
            var title = "Unknown Title"
            var artist = "Unknown Artist"
            var albumId = 0L
            
            try {
                contentResolver.query(
                    contentUri,
                    arrayOf(
                        android.provider.MediaStore.Audio.Media.TITLE,
                        android.provider.MediaStore.Audio.Media.ARTIST,
                        android.provider.MediaStore.Audio.Media.ALBUM_ID
                    ),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        title = cursor.getString(0)
                        artist = cursor.getString(1)
                        albumId = cursor.getLong(2)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            val mediaItem = androidx.media3.common.MediaItem.Builder()
                .setMediaId(lastId.toString())
                .setUri(contentUri)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist(artist)
                        .setArtworkUri(
                            android.content.ContentUris.withAppendedId(
                                android.net.Uri.parse("content://media/external/audio/albumart"),
                                albumId
                            )
                        )
                        .build()
                )
                .build()

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                player.setMediaItem(mediaItem)
                player.seekTo(lastPos)
                player.prepare()
                player.play()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        serviceScope.cancel()
        AudioEffectManager.release()
        super.onDestroy()
    }
    
    private fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        serviceScope.launch {
            android.widget.Toast.makeText(this@MusicService, "Sleep timer set for $minutes min", android.widget.Toast.LENGTH_SHORT).show()
            val totalMillis = minutes * 60 * 1000L
            val fadeDuration = 50_000L // 50 seconds fade out (10% every 5s)
            
            if (totalMillis > fadeDuration) {
                kotlinx.coroutines.delay(totalMillis - fadeDuration)
                softFadeOut()
            } else {
                kotlinx.coroutines.delay(totalMillis)
                mediaSession?.player?.pause()
            }
            sleepTimerJob = null
        }.also { sleepTimerJob = it }
    }

    private fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        mediaSession?.player?.volume = 1.0f // Reset volume if cancelled
    }
    
    private suspend fun softFadeOut() {
        val player = mediaSession?.player ?: return
        val steps = 10
        val stepDelay = 5000L // 5 seconds
        var currentVol = 1.0f
        
        repeat(steps) {
            currentVol -= 0.1f
            if (currentVol < 0f) currentVol = 0f
            player.volume = currentVol
            kotlinx.coroutines.delay(stepDelay)
        }
        player.pause()
        player.volume = 1.0f
    }

    private inner class AutoPlayForwardingPlayer(player: Player) : ForwardingPlayer(player) {
        override fun play() {
            if (!isPlaying) {
                volume = 0f
                super.play()
                fadeIn()
            } else {
                super.play()
            }
        }

        private fun fadeIn() {
            serviceScope.launch {
                val steps = 10
                val duration = 500L
                val stepDelay = duration / steps
                var vol = 0f
                repeat(steps) {
                    vol += 0.1f
                    if (vol > 1f) vol = 1f
                    volume = vol
                    kotlinx.coroutines.delay(stepDelay)
                }
                volume = 1f
            }
        }
        
        override fun seekToNext() {
            super.seekToNext()
            if (!isPlaying) play()
        }
        override fun seekToPrevious() {
            super.seekToPrevious()
            if (!isPlaying) play()
        }
        override fun seekToNextMediaItem() {
            super.seekToNextMediaItem()
            if (!isPlaying) play()
        }
         override fun seekToPreviousMediaItem() {
            super.seekToPreviousMediaItem()
            if (!isPlaying) play()
        }
    }
}
