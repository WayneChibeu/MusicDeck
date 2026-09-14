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
            if (!spokenText.isNullOrBlank()) {
                val songs = viewModel.songs.value ?: emptyList()
                val match = songs.find { 
                    it.title.contains(spokenText, ignoreCase = true) ||
                    it.artist.contains(spokenText, ignoreCase = true)
                }
                if (match != null) {
                    viewModel.playSong(match)
                } else if (songs.isNotEmpty()) {
                    val shuffled = songs.shuffled()
                    viewModel.playPlaylist(shuffled, 0)
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
                viewModel.playPlaylist(favs, 0)
            }
        }

        binding.btnCarFreshArrivals.setupBouncyPress()
        binding.btnCarFreshArrivals.setOnClickListener {
            HapticManager.performSpringClick(this)
            viewModel.getPlaylistSongs(com.wayne.musicdeck.data.SmartPlaylistManager.ID_RECENTLY_ADDED)
                .observe(this) { songs ->
                    if (!songs.isNullOrEmpty()) {
                        viewModel.playPlaylist(songs, 0)
                    }
                }
        }
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

                updateTrackInfo(controller.currentMediaItem)
                updatePlayPauseState(controller.isPlaying)
                updateShuffleButton(controller.shuffleModeEnabled)
                updateRepeatButton(controller.repeatMode)

                handler.post(progressRunnable)
            } catch (e: Exception) {
                android.util.Log.e("CarModeActivity", "Failed to connect MediaController: ${e.message}")
            }
        }, MoreExecutors.directExecutor())
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateTrackInfo(mediaItem)
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
        val artworkUri = mediaItem?.mediaMetadata?.artworkUri

        binding.tvCarTrackTitle.text = title
        binding.tvCarTrackArtist.text = if (!album.isNullOrBlank()) "$artist • $album" else artist

        binding.ivCarAlbumArt.load(artworkUri) {
            crossfade(true)
            placeholder(R.drawable.ic_launcher_foreground)
            error(R.drawable.ic_launcher_foreground)
            transformations(RoundedCornersTransformation(32f))
        }

        val controller = mediaController
        if (controller != null && controller.duration > 0) {
            binding.tvCarTotalTime.text = formatTime(controller.duration)
        }
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
            0x80FFFFFF.toInt()
        }
        binding.btnCarShuffle.imageTintList = android.content.res.ColorStateList.valueOf(color)
        binding.btnCarShuffle.setImageResource(
            if (enabled) R.drawable.ic_notif_shuffle_on else R.drawable.ic_notif_shuffle_off
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
            0x80FFFFFF.toInt()
        }
        binding.btnCarRepeat.imageTintList = android.content.res.ColorStateList.valueOf(color)
        val iconRes = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> R.drawable.ic_notif_repeat_one
            Player.REPEAT_MODE_ALL -> R.drawable.ic_notif_repeat_all
            else -> R.drawable.ic_notif_repeat_off
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
