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
import kotlinx.coroutines.isActive
import androidx.media3.session.SessionCommand
import androidx.media3.session.CommandButton
import org.koin.android.ext.android.inject
import com.wayne.musicdeck.utils.SettingsManager

class MusicService : MediaSessionService() {

    private val playlistRepository: com.wayne.musicdeck.data.PlaylistRepository by inject()
    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.Job())
    private var favoritesPlaylistId: Long = -1L
    private var mediaSession: MediaSession? = null
    private var sleepTimerJob: kotlinx.coroutines.Job? = null
    private var isCurrentSongFavorite = false
    private lateinit var volumeManager: com.wayne.musicdeck.utils.VolumeManager
    private val customMetadataDao: com.wayne.musicdeck.data.CustomMetadataDao by inject()
    private val playCountDao: com.wayne.musicdeck.data.PlayCountDao by inject()
    private val playHistoryDao: com.wayne.musicdeck.data.PlayHistoryDao by inject()
    private val settingsManager: SettingsManager by inject()
    private var shakeDetector: com.wayne.musicdeck.utils.ShakeDetector? = null
    private var playCountJob: kotlinx.coroutines.Job? = null
    private var playbackPositionJob: kotlinx.coroutines.Job? = null
    private var stopAtEndOfCurrentSong: Boolean = false
    private var cachedArtBitmap: android.graphics.Bitmap? = null
    private var cachedArtSongPath: String? = null
    private var favoritesObserverJob: kotlinx.coroutines.Job? = null
    
    companion object {
        private const val USER_AGENT = "MusicDeck/2.10.2"
        const val ACTION_SET_SLEEP_TIMER = "com.wayne.musicdeck.ACTION_SET_SLEEP_TIMER"
        const val ACTION_SET_SLEEP_TIMER_END_OF_SONG = "com.wayne.musicdeck.ACTION_SET_SLEEP_TIMER_END_OF_SONG"
        const val ACTION_CANCEL_SLEEP_TIMER = "com.wayne.musicdeck.ACTION_CANCEL_SLEEP_TIMER"
        const val EXTRA_TIMER_MINUTES = "extra_timer_minutes"
    }

    override fun onCreate() {
        super.onCreate()
        
        // Initialize favorites ID and observe changes reactively
        serviceScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val favPlaylist = playlistRepository.getOrCreateFavoritesPlaylist()
            favoritesPlaylistId = favPlaylist.id
            observeFavoritesFlow()
        }

        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(this)
            .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableAudioFloatOutput(true)

        val exoPlayer: ExoPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(5000)
            .setSeekForwardIncrementMs(10000)
            .build().apply {
                setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
            }
            
        // Initialize Audio Effects Manager eagerly. If it fails due to Session 0 hardware restrictions,
        // it will retry dynamically during onIsPlayingChanged.
        AudioEffectManager.initialize(exoPlayer.audioSessionId, this)
        
        volumeManager = com.wayne.musicdeck.utils.VolumeManager(exoPlayer, serviceScope)
            
        val player = AutoPlayForwardingPlayer(exoPlayer)
        
        shakeDetector = com.wayne.musicdeck.utils.ShakeDetector(this) {
            if (settingsManager.isShakeToShuffleEnabled) {
                triggerShakeShuffle(player)
            }
        }

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
                            val currentPath = player.currentMediaItem?.mediaId
                            val currentId = player.currentMediaItem?.requestMetadata?.extras?.getLong("songId") ?: -1L
                            if (currentPath != null && favoritesPlaylistId != -1L) {
                                serviceScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    val isFav = playlistRepository.isSongInPlaylist(favoritesPlaylistId, currentPath)
                                    if (isFav) {
                                        playlistRepository.removeSongFromPlaylist(favoritesPlaylistId, currentPath)
                                    } else {
                                        playlistRepository.addSongToPlaylist(favoritesPlaylistId, currentId, currentPath)
                                    }
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        updateFavoriteState(!isFav)
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

                private var mediaButtonPressCount = 0
                private val mediaButtonPressTimeout = 400L
                private var mediaButtonPressJob: kotlinx.coroutines.Job? = null
                private var lastSkipKeyCode = 0
                private var lastSkipTimestamp = 0L

                override fun onMediaButtonEvent(
                    session: MediaSession,
                    controllerInfo: MediaSession.ControllerInfo,
                    intent: Intent
                ): Boolean {
                    val ke = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, android.view.KeyEvent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra<android.view.KeyEvent>(Intent.EXTRA_KEY_EVENT)
                    }
                    if (ke != null && ke.action == android.view.KeyEvent.ACTION_DOWN) {
                        when (ke.keyCode) {
                            android.view.KeyEvent.KEYCODE_HEADSETHOOK,
                            android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                mediaButtonPressCount++
                                mediaButtonPressJob?.cancel()
                                mediaButtonPressJob = serviceScope.launch {
                                    kotlinx.coroutines.delay(mediaButtonPressTimeout)
                                    when (mediaButtonPressCount) {
                                        1 -> {
                                            if (player.isPlaying) player.pause() else player.play()
                                        }
                                        2 -> {
                                            player.seekToNextMediaItem()
                                        }
                                        3 -> {
                                            player.seekToPreviousMediaItem()
                                        }
                                        4 -> {
                                            if (settingsManager.isEarbudComboShuffleEnabled) {
                                                triggerShakeShuffle(player)
                                            }
                                        }
                                    }
                                    mediaButtonPressCount = 0
                                }
                                return true
                            }
                            android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> {
                                if (settingsManager.isEarbudComboShuffleEnabled) {
                                    val now = System.currentTimeMillis()
                                    if (lastSkipKeyCode == android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS && (now - lastSkipTimestamp) <= 1200L) {
                                        lastSkipKeyCode = 0
                                        triggerShakeShuffle(player)
                                        return true
                                    }
                                    lastSkipKeyCode = android.view.KeyEvent.KEYCODE_MEDIA_NEXT
                                    lastSkipTimestamp = now
                                }
                            }
                            android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                                if (settingsManager.isEarbudComboShuffleEnabled) {
                                    val now = System.currentTimeMillis()
                                    if (lastSkipKeyCode == android.view.KeyEvent.KEYCODE_MEDIA_NEXT && (now - lastSkipTimestamp) <= 1200L) {
                                        lastSkipKeyCode = 0
                                        triggerShakeShuffle(player)
                                        return true
                                    }
                                    lastSkipKeyCode = android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
                                    lastSkipTimestamp = now
                                }
                            }
                        }
                    }
                    return super.onMediaButtonEvent(session, controllerInfo, intent)
                }
                
                override fun onPostConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ) {
                    // Send custom layout with shuffle/repeat buttons to all controllers
                    val shuffleButton = CommandButton.Builder()
                        .setDisplayName("Shuffle")
                        .setIconResId(R.drawable.ic_notif_shuffle_off)
                        .setSessionCommand(SessionCommand("SHUFFLE", android.os.Bundle.EMPTY))
                        .setEnabled(true)
                        .build()
                    
                    val repeatButton = CommandButton.Builder()
                        .setDisplayName("Repeat")
                        .setIconResId(R.drawable.ic_notif_repeat_off)
                        .setSessionCommand(SessionCommand("REPEAT", android.os.Bundle.EMPTY))
                        .setEnabled(true)
                        .build()
                    
                    session.setCustomLayout(listOf(shuffleButton, repeatButton))
                }
                
                // === GOOGLE ASSISTANT VOICE SEARCH HANDLER ===
                // "Hey Google, play I Feel It Coming by The Weeknd"
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<androidx.media3.common.MediaItem>
                ): ListenableFuture<MutableList<androidx.media3.common.MediaItem>> {
                    val resolvedItems = mutableListOf<androidx.media3.common.MediaItem>()
                    
                    for (requestItem in mediaItems) {
                        val searchQuery = requestItem.requestMetadata.searchQuery
                        val mediaUri = requestItem.requestMetadata.mediaUri
                        val mediaId = requestItem.mediaId
                        
                        if (!searchQuery.isNullOrBlank()) {
                            // Voice search: "Play I Feel It Coming by The Weeknd"
                            android.util.Log.d("MusicService", "Google Assistant search: '$searchQuery'")
                            val matchedSongs = searchSongsFromMediaStore(searchQuery)
                            
                            if (matchedSongs.isNotEmpty()) {
                                resolvedItems.addAll(matchedSongs)
                            } else {
                                // No exact match, play all music shuffled as fallback
                                android.util.Log.d("MusicService", "No match for '$searchQuery', playing all songs")
                                resolvedItems.addAll(getAllSongsFromMediaStore())
                            }
                        } else if (mediaUri != null || mediaId.isNotEmpty()) {
                            // Direct URI or mediaId: pass through unchanged
                            resolvedItems.add(requestItem)
                        } else {
                            // Generic "play music" command: play all songs shuffled
                            android.util.Log.d("MusicService", "Generic play command, playing all songs")
                            resolvedItems.addAll(getAllSongsFromMediaStore())
                        }
                    }
                    
                    return com.google.common.util.concurrent.Futures.immediateFuture(resolvedItems)
                }
            })
            .setExtras(android.os.Bundle().apply {
                putInt("AUDIO_SESSION_ID", exoPlayer.audioSessionId)
            })
            .build()
            
        player.addListener(object : Player.Listener {
             private var suppressFadeIn = false

             override fun onPositionDiscontinuity(
                 oldPosition: Player.PositionInfo,
                 newPosition: Player.PositionInfo,
                 reason: Int
             ) {
                 if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
                     suppressFadeIn = true
                     volumeManager.resetVolume()
                 }
             }

             override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                 updateMediaSessionLayout(player)
             }
             override fun onRepeatModeChanged(repeatMode: Int) {
                 updateMediaSessionLayout(player)
             }
             override fun onAudioSessionIdChanged(audioSessionId: Int) {
                 super.onAudioSessionIdChanged(audioSessionId)
                 // This is the absolute golden moment to initialize the Equalizer.
                 // Before this triggers, the sessionId is 0 and fails on many devices.
                 if (audioSessionId != 0) {
                     android.util.Log.d("MusicService", "Audio Session ID generated: $audioSessionId. Initializing EQ...")
                     AudioEffectManager.initialize(audioSessionId, this@MusicService)
                 }
             }
             override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                 if (stopAtEndOfCurrentSong && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                     stopAtEndOfCurrentSong = false
                     player.pause()
                     cancelSleepTimer()
                 }
                 
                 // Suppress fade-in during song transitions to prevent "skip-stop-play" glitch.
                 // We only want to fade in when resuming from a paused/stopped state,
                 // OR if crossfade is enabled.
                 if (settingsManager.isCrossfadeEnabled && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                     suppressFadeIn = false
                     volumeManager.fadeIn(1500)
                 } else {
                     suppressFadeIn = true
                     volumeManager.resetVolume()
                 }
                 updateWidget(player)
                 startPlayCountHeartbeat(mediaItem)
                 
                 // Sound Check: Automatically normalize volume to safe hearing level
                 if (settingsManager.isSoundCheckEnabled) {
                     applySoundCheckVolumeLimit()
                 }
             }
             override fun onIsPlayingChanged(isPlaying: Boolean) {
                  updateWidget(player)
                  if (isPlaying) {
                      if (settingsManager.isShakeToShuffleEnabled) {
                          shakeDetector?.start()
                      }
                      // Always start/resume at 100% full volume instantly without stutter
                      volumeManager.resetVolume()
                      suppressFadeIn = false
                      
                      // Sound Check: Automatically normalize volume to safe hearing level
                      if (settingsManager.isSoundCheckEnabled) {
                          applySoundCheckVolumeLimit()
                      }
                      
                      startPlayCountHeartbeat(player.currentMediaItem)
                      startPlaybackPositionHeartbeat(player)
                      
                      // Aggressive EQ Init Retry: If it failed early (session 0), try again now
                      // that the audio engine is actively pumping output.
                      if (!AudioEffectManager.isInitialized()) {
                          val currentSession = exoPlayer.audioSessionId
                          if (currentSession != 0) {
                              android.util.Log.d("MusicService", "Aggressive EQ Retry on playing start")
                              AudioEffectManager.initialize(currentSession, this@MusicService)
                          }
                      }
                  } else {
                      shakeDetector?.stop()
                      playCountJob?.cancel()
                      playbackPositionJob?.cancel()
                      saveFinalPosition(player)
                  }
             }
         
          override fun onPlaybackStateChanged(playbackState: Int) {
              if (playbackState == Player.STATE_ENDED) {
                  if (settingsManager.isSunsetTransitionEnabled) {
                      // Note: STATE_ENDED means it already stopped, but we can reset volume for next play
                      volumeManager.resetVolume()
                  }
              }
          }
    })
        
        updateMediaSessionLayout(player)
        exoPlayer.setHandleAudioBecomingNoisy(true)
        
        val notificationProvider = CustomNotificationProvider(this)
        setMediaNotificationProvider(notificationProvider)
    }

    private fun applySoundCheckVolumeLimit() {
        try {
            val audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return
            val maxVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            val safeLimit = (maxVol * 0.8f).toInt()
            val curVol = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
            if (curVol > safeLimit) {
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, safeLimit, 0)
            }
        } catch (e: Exception) {
            android.util.Log.w("MusicService", "Failed to apply Sound Check volume limit", e)
        }
    }

    /**
     * Center-crops non-square artwork (e.g. 16:9 YouTube video thumbnails) to a pure 1:1 square.
     * Prevents black pillarbox bars in Android System Media Controls and notification player cards.
     */
    private fun cropToSquare(bitmap: android.graphics.Bitmap): android.graphics.Bitmap {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            if (width == height) return bitmap
            val size = Math.min(width, height)
            val x = (width - size) / 2
            val y = (height - size) / 2
            android.graphics.Bitmap.createBitmap(bitmap, x, y, size, size)
        } catch (e: Exception) {
            bitmap
        }
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
                Player.REPEAT_MODE_ONE -> R.drawable.ic_notif_repeat_one
                Player.REPEAT_MODE_ALL -> R.drawable.ic_notif_repeat_all
                else -> R.drawable.ic_notif_repeat_off
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
    
    private fun startPlayCountHeartbeat(mediaItem: androidx.media3.common.MediaItem?) {
        playCountJob?.cancel()
        val filePath = mediaItem?.mediaId ?: return
        val songId = mediaItem.requestMetadata.extras?.getLong("songId") ?: -1L
        
        playCountJob = serviceScope.launch {
            // Wait for 30 seconds of continuous play
            kotlinx.coroutines.delay(30000)
            
            // If still active after 30s, count it!
            playCountDao.ensureExists(filePath, songId)
            playCountDao.incrementPlayCount(filePath)
            
            if (settingsManager.isInsightsEnabled) {
                playHistoryDao.insertPlay(com.wayne.musicdeck.data.PlayHistoryEntry(songId = songId))
            }
            android.util.Log.d("MusicService", "Play counted for path: $filePath (30s heartbeat reached)")
        }
    }

    private fun startPlaybackPositionHeartbeat(player: Player) {
        playbackPositionJob?.cancel()
        playbackPositionJob = serviceScope.launch {
            var hasStartedCrossfade = false
            while (isActive) {
                kotlinx.coroutines.delay(1000)
                saveFinalPosition(player)
                
                if (settingsManager.isCrossfadeEnabled && player.isPlaying) {
                    val pos = player.currentPosition
                    val dur = player.duration
                    if (dur > 0 && dur - pos <= 2000 && !hasStartedCrossfade) {
                        hasStartedCrossfade = true
                        volumeManager.fadeOut(dur - pos) {}
                    }
                    if (dur > 0 && dur - pos > 2000) {
                        hasStartedCrossfade = false // reset for next track
                    }
                }
            }
        }
    }

    private fun saveFinalPosition(player: Player) {
        val mediaItem = player.currentMediaItem ?: return
        val filePath = mediaItem.mediaId
        val songId = mediaItem.requestMetadata.extras?.getLong("songId") ?: -1L
        val position = player.currentPosition
        val title = mediaItem.mediaMetadata.title?.toString() ?: "Unknown Title"
        val artist = mediaItem.mediaMetadata.artist?.toString() ?: "Unknown Artist"
        
        settingsManager.lastPlayedSongPath = filePath
        settingsManager.lastPlayedSongId = songId
        settingsManager.lastPlayedPosition = position
        settingsManager.lastPlayedTitle = title
        settingsManager.lastPlayedArtist = artist
    }

    private fun observeFavoritesFlow() {
        if (favoritesPlaylistId == -1L) return
        favoritesObserverJob?.cancel()
        favoritesObserverJob = serviceScope.launch {
            playlistRepository.getSongsForPlaylistFlow(favoritesPlaylistId).collect { favSongs ->
                val player = mediaSession?.player ?: return@collect
                val currentPath = player.currentMediaItem?.mediaId ?: return@collect
                val isFav = favSongs.any { it.songPath == currentPath }
                if (isFav != isCurrentSongFavorite) {
                    isCurrentSongFavorite = isFav
                    updateFavoriteState(isFav)
                }
            }
        }
    }

    private fun updateFavoriteState(isFav: Boolean) {
        val player = mediaSession?.player ?: return
        isCurrentSongFavorite = isFav
        settingsManager.lastPlayedIsFavorite = isFav
        val mediaItem = player.currentMediaItem
        val title = mediaItem?.mediaMetadata?.title?.toString() ?: "Unknown"
        val artist = mediaItem?.mediaMetadata?.artist?.toString() ?: "Unknown Artist"
        val isPlaying = player.isPlaying
        MusicWidgetProvider.pushUpdate(this@MusicService, title, artist, isPlaying, isFav, cachedArtBitmap)
        updateMediaSessionLayout(player)
    }

    private fun updateWidget(player: Player) {
        try {
            val mediaItem = player.currentMediaItem
            val title = mediaItem?.mediaMetadata?.title?.toString() ?: "Unknown"
            val artist = mediaItem?.mediaMetadata?.artist?.toString() ?: "Unknown Artist"
            val isPlaying = player.isPlaying
            
            val currentPath = mediaItem?.mediaId ?: return
            
            serviceScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val isFav = if (favoritesPlaylistId != -1L) {
                    playlistRepository.isSongInPlaylist(favoritesPlaylistId, currentPath)
                } else {
                    false
                }
                isCurrentSongFavorite = isFav

                // Load Bitmap for Widget (Fixes missing art on some launchers)
                var artBitmap: android.graphics.Bitmap? = if (currentPath == cachedArtSongPath) cachedArtBitmap else null
                if (artBitmap == null) {
                    val artUri = mediaItem.mediaMetadata.artworkUri
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
                            android.util.Log.e("MusicService", "Failed to load widget art from URI: ${e.message}")
                        }
                    }
                    
                    // Fallback: Try embedded art from MP3 file (like PlayerBottomSheetFragment does)
                    if (artBitmap == null) {
                        try {
                            val uri = android.net.Uri.fromFile(java.io.File(currentPath))
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
                    // Ensure artwork is preprocessed to clean 1:1 square
                    if (artBitmap != null && artBitmap.width != artBitmap.height) {
                        artBitmap = cropToSquare(artBitmap)
                    }
                    cachedArtBitmap = artBitmap
                    cachedArtSongPath = currentPath
                }

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    MusicWidgetProvider.pushUpdate(this@MusicService, title, artist, isPlaying, isFav, artBitmap)
                    settingsManager.lastPlayedIsFavorite = isFav
                    updateMediaSessionLayout(player)
                    
                    // Update artwork data for Notification to ensure clean square presentation
                    val mItem = mediaItem
                    if (artBitmap != null) {
                        val stream = java.io.ByteArrayOutputStream()
                        artBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                        val byteArray = stream.toByteArray()
                        val newMeta = mItem.mediaMetadata.buildUpon()
                            .setArtworkData(byteArray, androidx.media3.common.MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                            .build()
                        val newItem = mItem.buildUpon().setMediaMetadata(newMeta).build()
                        
                        val idx = player.currentMediaItemIndex
                        if (idx != -1 && player.currentMediaItem?.mediaId == newItem.mediaId) {
                             player.replaceMediaItem(idx, newItem)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicService", "Widget update failed: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val player = mediaSession?.player
        if (player != null && intent != null && intent.action != null) {
            android.util.Log.d("MusicService", "Widget Action: ${intent.action}")
            when (intent.action) {
                MusicWidgetProvider.ACTION_PLAY_PAUSE -> {
                    if (player.isPlaying) {
                        player.pause()
                    } else {
                        if (player.mediaItemCount == 0) {
                            restoreLastSession(player, startPlaying = true)
                        } else {
                            player.play()
                        }
                    }
                }
                MusicWidgetProvider.ACTION_NEXT -> {
                    if (player.mediaItemCount == 0) {
                        restoreLastSession(player, startPlaying = true) {
                            player.seekToNextMediaItem()
                            player.play()
                        }
                    } else {
                        player.seekToNextMediaItem()
                        player.play()
                    }
                }
                MusicWidgetProvider.ACTION_PREVIOUS -> {
                    if (player.mediaItemCount == 0) {
                        restoreLastSession(player, startPlaying = true) {
                            player.seekToPreviousMediaItem()
                            player.play()
                        }
                    } else {
                        player.seekToPreviousMediaItem()
                        player.play()
                    }
                }
                MusicWidgetProvider.ACTION_FAVORITE -> {
                    val currentPath = player.currentMediaItem?.mediaId
                    if (currentPath != null && favoritesPlaylistId != -1L) {
                        serviceScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val isFav = playlistRepository.isSongInPlaylist(favoritesPlaylistId, currentPath)
                            if (isFav) {
                                playlistRepository.removeSongFromPlaylist(favoritesPlaylistId, currentPath)
                            } else {
                                // Need songId for legacy Room compatibility, but path is primary now
                                // For now, passing -1L for ID as the path-based DAO handles the lookup.
                                playlistRepository.addSongToPlaylist(favoritesPlaylistId, -1L, currentPath)
                            }
                             kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                 updateFavoriteState(!isFav)
                             }
                        }
                    } else if (player.mediaItemCount == 0) {
                        // Restore session and THEN like the song
                        restoreLastSession(player, startPlaying = false) {
                            val restoredPath = player.currentMediaItem?.mediaId
                            if (restoredPath != null && favoritesPlaylistId != -1L) {
                                serviceScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    val isFav = playlistRepository.isSongInPlaylist(favoritesPlaylistId, restoredPath)
                                    if (!isFav) {
                                        playlistRepository.addSongToPlaylist(favoritesPlaylistId, -1L, restoredPath)
                                    } else {
                                        playlistRepository.removeSongFromPlaylist(favoritesPlaylistId, restoredPath)
                                    }
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        updateFavoriteState(!isFav)
                                    }
                                }
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
                ACTION_SET_SLEEP_TIMER_END_OF_SONG -> {
                    cancelSleepTimer() // Clear any existing timer
                    stopAtEndOfCurrentSong = true
                    // Notify UI that we are in "End of Song" mode
                    val extras = android.os.Bundle().apply {
                        putLong("SLEEP_TIMER_REMAINING_MS", -1L) // -1 signifies End of Song mode
                    }
                    mediaSession?.setSessionExtras(extras)
                    android.widget.Toast.makeText(this@MusicService, "Will stop after current song", android.widget.Toast.LENGTH_SHORT).show()
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
        
        // Use our custom notification-optimized icons (scaled down)
        val shuffleIcon = if (shuffleOn) {
            R.drawable.ic_notif_shuffle_on
        } else {
            R.drawable.ic_notif_shuffle_off
        }
        
        val repeatIcon = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> R.drawable.ic_notif_repeat_one
            Player.REPEAT_MODE_ALL -> R.drawable.ic_notif_repeat_all
            else -> R.drawable.ic_notif_repeat_off
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


    private fun restoreLastSession(player: Player, startPlaying: Boolean = false, onReady: (() -> Unit)? = null) {
        serviceScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val lastPath = settingsManager.lastPlayedSongPath
                val lastPos = settingsManager.lastPlayedPosition
                
                android.util.Log.d("MusicService", "Restoring session. Last path: $lastPath, Pos: $lastPos")
                
                // Always load all songs so that Next/Previous works from the widget
                val allSongs = getAllSongsFromMediaStore()
                
                if (allSongs.isEmpty()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onReady?.invoke()
                    }
                    return@launch
                }
                
                // Find index of the last played song
                val lastIndex = if (lastPath != null) {
                    allSongs.indexOfFirst { it.mediaId == lastPath }
                } else {
                    -1
                }
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    player.setMediaItems(allSongs)
                    
                    if (lastIndex != -1) {
                        player.seekTo(lastIndex, lastPos)
                    } else {
                        // If last song not found, just start from the first one
                        player.seekTo(0, 0L)
                    }
                    
                    player.prepare()
                    if (startPlaying) {
                        player.play()
                    }
                    
                    // Small delay to ensure player state is updated before calling onReady
                    kotlinx.coroutines.delay(100)
                    onReady?.invoke()
                    
                    // Update widget to show current song info
                    updateWidget(player)
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicService", "Session restoration failed: ${e.message}")
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onReady?.invoke()
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    private fun triggerShakeShuffle(player: Player) {
        serviceScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            if (!player.shuffleModeEnabled) {
                player.shuffleModeEnabled = true
            }
            player.seekToNextMediaItem()
            com.wayne.musicdeck.utils.HapticManager.performShuffleHaptic(this@MusicService)
        }
    }

    override fun onDestroy() {
        shakeDetector?.stop()
        shakeDetector = null
        mediaSession?.run {
            saveFinalPosition(player)
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
            var remainingMillis = totalMillis
            
            val fadeDuration = 50_000L // 50 seconds fade out (10% every 5s)
            
            // Ticker for Live Countdown
            while (remainingMillis > 0) {
                // Update session extras with remaining time for UI observability
                mediaSession?.setSessionExtras(android.os.Bundle().apply {
                    putLong("SLEEP_TIMER_REMAINING_MS", remainingMillis)
                })
                
                if (remainingMillis <= fadeDuration) {
                    volumeManager.fadeOut(fadeDuration) {
                        mediaSession?.player?.pause()
                    }
                    break
                }
                
                kotlinx.coroutines.delay(1000)
                remainingMillis -= 1000
            }
            
            if (remainingMillis <= 0) {
                mediaSession?.player?.pause()
            }
            
            // Clear extras when done
            mediaSession?.setSessionExtras(android.os.Bundle.EMPTY)
            sleepTimerJob = null
        }.also { sleepTimerJob = it }
    }

    private fun cancelSleepTimer() {
        if (sleepTimerJob != null || stopAtEndOfCurrentSong) {
            android.widget.Toast.makeText(this, "Sleep timer cancelled", android.widget.Toast.LENGTH_SHORT).show()
        }
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        stopAtEndOfCurrentSong = false
        
        val extras = android.os.Bundle()
        // Removing the key means no timer
        mediaSession?.setSessionExtras(extras)
    }
    
    // softFadeOut removed in favor of VolumeManager.fadeOut for ultra-smooth rendering
    private inner class AutoPlayForwardingPlayer(player: Player) : ForwardingPlayer(player) {
        override fun pause() {
            if (settingsManager.isSunsetTransitionEnabled) {
                volumeManager.fadeOut(500) {
                    super.pause()
                    // Reset volume after pause so next play starts at full (or fades in)
                    volumeManager.resetVolume()
                }
            } else {
                super.pause()
            }
        }
        
        override fun seekToNext() {
            super.seekToNextMediaItem()
            play()
        }

        override fun seekToPrevious() {
            super.seekToPreviousMediaItem()
            play()
        }

        override fun seekToNextMediaItem() {
            super.seekToNextMediaItem()
            play()
        }

        override fun seekToPreviousMediaItem() {
            super.seekToPreviousMediaItem()
            play()
        }
    }

    // ============================
    // GOOGLE ASSISTANT MEDIA SEARCH
    // ============================

    /**
     * Searches MediaStore for songs matching a search query.
     * Fuzzy matches against title and artist.
     * Returns matched song first, followed by remaining songs for queue continuity.
     */
    private fun searchSongsFromMediaStore(query: String): MutableList<androidx.media3.common.MediaItem> {
        val results = mutableListOf<androidx.media3.common.MediaItem>()
        val allSongs = mutableListOf<SongResult>()
        val queryLower = query.lowercase()
        
        val projection = arrayOf(
            android.provider.MediaStore.Audio.Media._ID,
            android.provider.MediaStore.Audio.Media.TITLE,
            android.provider.MediaStore.Audio.Media.ARTIST,
            android.provider.MediaStore.Audio.Media.ALBUM,
            android.provider.MediaStore.Audio.Media.ALBUM_ID,
            android.provider.MediaStore.Audio.Media.DATA
        )
        
        val selection = "${android.provider.MediaStore.Audio.Media.DURATION} > 10000"
        
        contentResolver.query(
            android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            "${android.provider.MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ALBUM_ID)
            val dataCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA)
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol) ?: ""
                val artist = cursor.getString(artistCol) ?: ""
                val album = cursor.getString(albumCol) ?: ""
                val albumId = cursor.getLong(albumIdCol)
                val data = cursor.getString(dataCol) ?: ""
                
                // Score this song against the query
                val titleLower = title.lowercase()
                val artistLower = artist.lowercase()
                val combined = "$titleLower $artistLower"
                
                val score = when {
                    titleLower == queryLower -> 100          // Exact title match
                    combined.contains(queryLower) -> 80      // Title + artist contains full query
                    titleLower.contains(queryLower) -> 70    // Title contains query
                    queryLower.contains(titleLower) -> 60    // Query contains full title
                    artistLower.contains(queryLower) -> 40   // Artist matches query
                    else -> {
                        // Word-level fuzzy matching
                        val queryWords = queryLower.split(" ").filter { it.length > 2 }
                        val matchCount = queryWords.count { word ->
                            titleLower.contains(word) || artistLower.contains(word)
                        }
                        if (matchCount > 0) matchCount * 15 else 0
                    }
                }
                
                allSongs.add(SongResult(id, title, artist, album, albumId, data, score))
            }
        }
        
        // Sort by relevance score (highest first)
        allSongs.sortByDescending { it.score }
        
        // Build MediaItems: matched songs first, then the rest for queue
        for (song in allSongs) {
            if (song.score > 0 || results.isEmpty()) {
                val artUri = android.content.ContentUris.withAppendedId(
                    android.net.Uri.parse("content://media/external/audio/albumart"),
                    song.albumId
                )
                val contentUri = android.content.ContentUris.withAppendedId(
                    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    song.id
                )
                
                val mediaItem = androidx.media3.common.MediaItem.Builder()
                    .setMediaId(song.data)
                    .setUri(contentUri)
                    .setMediaMetadata(
                        androidx.media3.common.MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setAlbumTitle(song.album)
                            .setArtworkUri(artUri)
                            .build()
                    )
                    .setRequestMetadata(
                        androidx.media3.common.MediaItem.RequestMetadata.Builder()
                            .setExtras(android.os.Bundle().apply { putLong("songId", song.id) })
                            .build()
                    )
                    .build()
                
                results.add(mediaItem)
            }
        }
        
        // Only return matches (score > 0)
        android.util.Log.d("MusicService", "Search found ${allSongs.count { it.score > 0 }} matches out of ${allSongs.size} songs")
        return results
    }
    
    /**
     * Returns all songs from MediaStore as MediaItems (for generic "play music" commands).
     */
    private fun getAllSongsFromMediaStore(): MutableList<androidx.media3.common.MediaItem> {
        return searchSongsFromMediaStore("") // Returns all songs with score 0, but still builds the full list
    }
    
    private data class SongResult(
        val id: Long,
        val title: String,
        val artist: String,
        val album: String,
        val albumId: Long,
        val data: String,
        val score: Int
    )
}
