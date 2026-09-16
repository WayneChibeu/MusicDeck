package com.wayne.musicdeck

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.load
import coil.transform.RoundedCornersTransformation
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.wayne.musicdeck.databinding.ActivityCarModeBinding
import com.wayne.musicdeck.utils.HapticManager
import com.wayne.musicdeck.utils.ThemeHelper
import com.wayne.musicdeck.utils.setupBouncyPress
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.Locale

class CarModeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCarModeBinding
    private val viewModel: MainViewModel by viewModel()
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isUserTrackingProgress = false

    private val progressRunnable = object : Runnable {
        override fun run() {
            val controller = mediaController
            if (controller != null && controller.isPlaying && !isUserTrackingProgress) {
                val current = controller.currentPosition
                val duration = controller.duration
                if (duration > 0) {
                    val progress = ((current.toFloat() / duration.toFloat()) * 1000f).toInt()
                    binding.sbCarProgress.progress = progress
                    binding.tvCarCurrentTime.text = formatTime(current)
                    binding.tvCarTotalTime.text = formatTime(duration)
                }
            }
            handler.postDelayed(this, 500)
        }
    }

    private val voiceSearchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
            if (!spokenText.isNullOrBlank()) {
                val allSongs = viewModel.songs.value ?: emptyList()
                if (allSongs.isEmpty()) {
                    android.widget.Toast.makeText(this, "Music library is still loading...", android.widget.Toast.LENGTH_SHORT).show()
                    return@registerForActivityResult
                }

                // 1. Priority: Match Artist (e.g. "Kygo" -> queues and plays all Kygo songs)
                val artistMatches = allSongs.filter { it.artist.contains(spokenText, ignoreCase = true) }
                // 2. Title Match (e.g. "Paradise" or "Stole the Show")
                val titleMatches = allSongs.filter { it.title.contains(spokenText, ignoreCase = true) }
                // 3. Album Match
                val albumMatches = allSongs.filter { it.album.contains(spokenText, ignoreCase = true) }

                val targetList = when {
                    artistMatches.isNotEmpty() -> artistMatches
                    titleMatches.isNotEmpty() -> titleMatches
                    albumMatches.isNotEmpty() -> albumMatches
                    else -> emptyList()
                }

                if (targetList.isNotEmpty()) {
                    playTracks(targetList, 0)
                    val label = when {
                        artistMatches.isNotEmpty() -> "Playing $spokenText (${targetList.size} songs)"
                        else -> "Playing ${targetList.first().title}"
                    }
                    android.widget.Toast.makeText(this, label, android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(this, "No tracks found for \"$spokenText\"", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)

        // Keep screen awake while in car mode
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityCarModeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupButtons()
        setupSeekBar()
        initializeMediaController()

        viewModel.songs.observe(this) {
            updateTrackInfo(mediaController?.currentMediaItem)
        }
        viewModel.favorites.observe(this) {
            updateFavoriteButton()
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        binding = ActivityCarModeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupButtons()
        setupSeekBar()

        mediaController?.let { controller ->
            updateTrackInfo(controller.currentMediaItem)
            updatePlayPauseState(controller.isPlaying)
            updateShuffleButton(controller.shuffleModeEnabled)
            updateRepeatButton(controller.repeatMode)
            updateFavoriteButton()
        }
    }

    private fun setupButtons() {
        binding.btnExitCarMode.setupBouncyPress()
        binding.btnExitCarMode.setOnClickListener {
            HapticManager.performTick(this)
            finish()
        }

        binding.btnCarVoiceSearch.setupBouncyPress()
        binding.btnCarVoiceSearch.setOnClickListener {
            HapticManager.performSpringClick(this)
            startVoiceSearch()
        }

        binding.btnCarPlayPause.setupBouncyPress()
        binding.btnCarPlayPause.setOnClickListener {
            HapticManager.performSpringClick(this)
            val controller = mediaController ?: return@setOnClickListener
            if (controller.isPlaying) {
                controller.pause()
            } else {
                controller.play()
            }
        }

        binding.btnCarPrevious.setupBouncyPress()
        binding.btnCarPrevious.setOnClickListener {
            HapticManager.performSpringClick(this)
            mediaController?.seekToPreviousMediaItem()
        }

        binding.btnCarNext.setupBouncyPress()
        binding.btnCarNext.setOnClickListener {
            HapticManager.performSpringClick(this)
            mediaController?.seekToNextMediaItem()
        }

        binding.btnCarRewind10.setupBouncyPress()
        binding.btnCarRewind10.setOnClickListener {
            HapticManager.performTick(this)
            val controller = mediaController ?: return@setOnClickListener
            val target = (controller.currentPosition - 10000L).coerceAtLeast(0L)
            controller.seekTo(target)
            binding.tvCarCurrentTime.text = formatTime(target)
        }

        binding.btnCarForward10.setupBouncyPress()
        binding.btnCarForward10.setOnClickListener {
            HapticManager.performTick(this)
            val controller = mediaController ?: return@setOnClickListener
            val duration = controller.duration.coerceAtLeast(0L)
            val target = (controller.currentPosition + 10000L).coerceAtMost(duration)
            controller.seekTo(target)
            binding.tvCarCurrentTime.text = formatTime(target)
        }

        binding.btnCarShuffle.setupBouncyPress()
        binding.btnCarShuffle.setOnClickListener {
            HapticManager.performTick(this)
            val controller = mediaController ?: return@setOnClickListener
            controller.shuffleModeEnabled = !controller.shuffleModeEnabled
            updateShuffleButton(controller.shuffleModeEnabled)
        }

        binding.btnCarFavorite.setupBouncyPress()
        binding.btnCarFavorite.setOnClickListener {
            HapticManager.performSpringClick(this)
            val controller = mediaController ?: return@setOnClickListener
            val currentPath = controller.currentMediaItem?.mediaId ?: return@setOnClickListener
            val song = viewModel.songs.value?.find { it.data == currentPath } ?: return@setOnClickListener
            viewModel.toggleFavorite(song)
        }

        binding.btnCarRepeat.setupBouncyPress()
        binding.btnCarRepeat.setOnClickListener {
            HapticManager.performTick(this)
            val controller = mediaController ?: return@setOnClickListener
            val nextMode = when (controller.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            controller.repeatMode = nextMode
            updateRepeatButton(nextMode)
        }

        binding.btnCarFavorites.setupBouncyPress()
        binding.btnCarFavorites.setOnClickListener {
            HapticManager.performSpringClick(this)
            val favs = viewModel.favorites.value
            if (!favs.isNullOrEmpty()) {
                playTracks(favs, 0)
                android.widget.Toast.makeText(this, "Playing Favorites (${favs.size} songs)", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(this, "No favorite songs yet", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun playTracks(songs: List<Song>, startIndex: Int = 0) {
        val controller = mediaController ?: return
        if (songs.isEmpty() || startIndex < 0 || startIndex >= songs.size) return

        val mediaItems = songs.map { song ->
            val customCoverPath = song.data.let { path ->
                val prefs = getSharedPreferences("custom_covers", android.content.Context.MODE_PRIVATE)
                prefs.getString(path, null)
            }
            val artUri = if (customCoverPath != null) {
                android.net.Uri.fromFile(java.io.File(customCoverPath))
            } else if (song.albumId > 0 && song.album != "Unknown Album") {
                android.content.ContentUris.withAppendedId(
                    android.net.Uri.parse("content://media/external/audio/album_art"),
                    song.albumId
                )
            } else {
                song.uri
            }

            MediaItem.Builder()
                .setMediaId(song.data)
                .setUri(song.uri)
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
        }

        controller.setMediaItems(mediaItems)
        controller.seekTo(startIndex, 0)
        controller.prepare()
        controller.play()
    }

    private fun setupSeekBar() {
        binding.sbCarProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val controller = mediaController ?: return
                    val duration = controller.duration
                    if (duration > 0) {
                        val seekMs = (duration * (progress / 1000f)).toLong()
                        binding.tvCarCurrentTime.text = formatTime(seekMs)
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserTrackingProgress = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserTrackingProgress = false
                val controller = mediaController ?: return
                val duration = controller.duration
                if (duration > 0 && seekBar != null) {
                    val targetMs = (duration * (seekBar.progress / 1000f)).toLong()
                    controller.seekTo(targetMs)
                }
            }
        })
    }

    private fun initializeMediaController() {
        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                val controller = controllerFuture?.get() ?: return@addListener
                mediaController = controller
                controller.addListener(playerListener)
                viewModel.initializeController()

                updateTrackInfo(controller.currentMediaItem)
                updatePlayPauseState(controller.isPlaying)
                updateShuffleButton(controller.shuffleModeEnabled)
                updateRepeatButton(controller.repeatMode)
                updateFavoriteButton()

                handler.post(progressRunnable)
            } catch (e: Exception) {
                android.util.Log.e("CarModeActivity", "Failed to connect MediaController: ${e.message}")
            }
        }, MoreExecutors.directExecutor())
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateTrackInfo(mediaItem)
            updateFavoriteButton()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlayPauseState(isPlaying)
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            updateShuffleButton(shuffleModeEnabled)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            updateRepeatButton(repeatMode)
        }
    }

    private fun updateTrackInfo(mediaItem: MediaItem?) {
        val title = mediaItem?.mediaMetadata?.title?.toString() ?: "No Track Playing"
        val artist = mediaItem?.mediaMetadata?.artist?.toString() ?: "MusicDeck"
        val album = mediaItem?.mediaMetadata?.albumTitle?.toString()

        binding.tvCarTrackTitle.text = title
        binding.tvCarTrackArtist.text = if (!album.isNullOrBlank()) "$artist • $album" else artist

        loadArtwork(mediaItem)
        updateFavoriteButton()

        val controller = mediaController
        if (controller != null && controller.duration > 0) {
            binding.tvCarTotalTime.text = formatTime(controller.duration)
        }
    }

    private fun loadArtwork(mediaItem: MediaItem?) {
        val currentPath = mediaItem?.mediaId
        val song = if (!currentPath.isNullOrBlank()) viewModel.songs.value?.find { it.data == currentPath } else null

        val customCoverPath = song?.data?.let { path ->
            val prefs = getSharedPreferences("custom_covers", android.content.Context.MODE_PRIVATE)
            prefs.getString(path, null)
        }

        val imageData: Any = if (customCoverPath != null && java.io.File(customCoverPath).exists()) {
            java.io.File(customCoverPath)
        } else if (!currentPath.isNullOrBlank() && java.io.File(currentPath).exists()) {
            java.io.File(currentPath)
        } else if (song != null && java.io.File(song.data).exists()) {
            java.io.File(song.data)
        } else if (song != null && song.albumId > 0 && song.album != "Unknown Album") {
            android.content.ContentUris.withAppendedId(
                android.net.Uri.parse("content://media/external/audio/album_art"),
                song.albumId
            )
        } else {
            mediaItem?.mediaMetadata?.artworkUri ?: R.drawable.default_album_art
        }

        binding.ivCarAlbumArt.load(imageData) {
            crossfade(true)
            placeholder(R.drawable.default_album_art)
            error(R.drawable.default_album_art)
            transformations(RoundedCornersTransformation(24f))
        }
    }

    private fun updateFavoriteButton() {
        val currentPath = mediaController?.currentMediaItem?.mediaId
        val isFav = if (!currentPath.isNullOrBlank()) {
            viewModel.favorites.value?.any { it.data == currentPath } == true
        } else {
            false
        }

        binding.btnCarFavorite.setImageResource(
            if (isFav) R.drawable.ic_favorite else R.drawable.ic_favorite_border
        )
        val tintColor = if (isFav) {
            ContextCompat.getColor(this, R.color.colorRose)
        } else {
            0xFFFFFFFF.toInt() // 100% Solid White
        }
        binding.btnCarFavorite.imageTintList = android.content.res.ColorStateList.valueOf(tintColor)
    }

    private fun updatePlayPauseState(isPlaying: Boolean) {
        binding.ivCarPlayPause.setImageResource(
            if (isPlaying) R.drawable.ic_pause_rounded else R.drawable.ic_play_rounded
        )
    }

    private fun updateShuffleButton(enabled: Boolean) {
        val color = if (enabled) {
            com.google.android.material.color.MaterialColors.getColor(
                binding.btnCarShuffle,
                com.google.android.material.R.attr.colorPrimary,
                ContextCompat.getColor(this, android.R.color.white)
            )
        } else {
            0xFFFFFFFF.toInt() // 100% Solid White
        }
        binding.btnCarShuffle.imageTintList = android.content.res.ColorStateList.valueOf(color)
        binding.btnCarShuffle.setImageResource(
            if (enabled) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle_off
        )
    }

    private fun updateRepeatButton(repeatMode: Int) {
        val isEnabled = repeatMode != Player.REPEAT_MODE_OFF
        val color = if (isEnabled) {
            com.google.android.material.color.MaterialColors.getColor(
                binding.btnCarRepeat,
                com.google.android.material.R.attr.colorPrimary,
                ContextCompat.getColor(this, android.R.color.white)
            )
        } else {
            0xFFFFFFFF.toInt() // 100% Solid White
        }
        binding.btnCarRepeat.imageTintList = android.content.res.ColorStateList.valueOf(color)
        val iconRes = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
            Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat_all
            else -> R.drawable.ic_repeat_off
        }
        binding.btnCarRepeat.setImageResource(iconRes)
    }

    private fun startVoiceSearch() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a song title or artist to play")
            }
            voiceSearchLauncher.launch(intent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Voice search not supported on this device", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(progressRunnable)
        mediaController?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
