package com.wayne.musicdeck

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.slider.Slider
import com.wayne.musicdeck.databinding.FragmentPlayerBottomSheetBinding
import java.util.Locale

class PlayerBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentPlayerBottomSheetBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel
    private var isTracking = false

    // Playback mode: 0=Off, 1=Single Loop, 2=Shuffle, 3=Playlist Loop
    private var playbackMode = 0
    
    // Track which view is showing
    private var isLyricsViewActive = false
    
    // Lyrics Adapter
    private val lyricsAdapter = LyricsAdapter()
    
    // Lyric file picker
    private val lyricFilePicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val currentSong = viewModel.mediaController.value?.currentMediaItem?.mediaId?.toLongOrNull()?.let { id ->
                viewModel.songs.value?.find { song -> song.id == id }
            }
            currentSong?.let { song ->
                viewModel.setLyricFile(song, it)
                loadLyrics()
            }
        }
    }

    private val updateProgressAction = object : Runnable {
        override fun run() {
            if (_binding == null) return
            val player = viewModel.mediaController.value ?: return
            if (player.isPlaying && !isTracking) {
                if (player.duration > 0) {
                    val progress = (player.currentPosition.toFloat() / player.duration * 1000f)
                    binding.seekBar.progress = progress.toInt().coerceIn(0, 1000)
                }
                binding.tvCurrentTime.text = formatTime(player.currentPosition)
                
                // Update synced lyrics
                if (isLyricsViewActive) {
                    val newIndex = lyricsAdapter.updateTime(player.currentPosition)
                    if (newIndex != -1) {
                        smoothScrollToCenter(newIndex)
                    }
                }
            }
            if (_binding != null) {
                binding.seekBar.postDelayed(this, 200) // Faster updates for smoother syncing
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Setup RecyclerView
        setupLyricsRecyclerView()
        
        // Setup view switching
        setupViewSwitching()
        
        // Swipe Gestures
        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                if (Math.abs(diffX) > Math.abs(diffY)) { // Horizontal swipe
                    if (Math.abs(diffX) > 100 && Math.abs(velocityX) > 100) {
                        if (diffX > 0) {
                            // Right Swipe -> Show Cover
                            showCoverView()
                        } else {
                            // Left Swipe -> Show Lyric
                            showLyricsView()
                        }
                        return true
                    }
                }
                return false
            }
        })
        
        val touchListener = View.OnTouchListener { _, event -> 
            if (gestureDetector.onTouchEvent(event)) true else false
        }
        
        // Robust RecyclerView Gesture Handling
        binding.rvLyrics.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                gestureDetector.onTouchEvent(e)
                return false // Pass through to RecyclerView for scrolling
            }
        })

        binding.coverView.setOnTouchListener(touchListener)
        binding.lyricView.setOnTouchListener(touchListener)
        binding.root.setOnTouchListener(touchListener)
        


        viewModel.mediaController.observe(viewLifecycleOwner) { player ->
            player?.let { 
                setupPlayer(it)
                SleepTimerBottomSheet.setPauseCallback { it.pause() }
            }
        }

        // Collapse buttons
        binding.btnCollapse.setOnClickListener { dismiss() }

        // Menu buttons
        binding.btnMenu.setOnClickListener {
            PlayerMenuBottomSheet.newInstance().show(parentFragmentManager, "PlayerMenu")
        }
        
        // Queue button
        binding.btnQueue.setOnClickListener {
            QueueBottomSheet().show(parentFragmentManager, "Queue")
        }
        
        // Favorites
        viewModel.favorites.observe(viewLifecycleOwner) { favorites ->
            val currentId = viewModel.mediaController.value?.currentMediaItem?.mediaId?.toLongOrNull() ?: return@observe
            val isFav = favorites.any { it.id == currentId }
            updateFavoriteIcon(isFav)
        }
        
        binding.btnFavorite.setOnClickListener {
            val currentId = viewModel.mediaController.value?.currentMediaItem?.mediaId?.toLongOrNull() ?: return@setOnClickListener
            val song = viewModel.songs.value?.find { it.id == currentId }
            if (song != null) {
               viewModel.toggleFavorite(song)
            } else {
                Toast.makeText(context, "Error: Song not found", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Mini player (removed in new layout but kept for safety if layout changes)
        // binding.btnMiniPlayPause.setOnClickListener... 
        
        // Manual lyric file picker
        binding.btnManuallySpecifyLyric.setOnClickListener {
            lyricFilePicker.launch("*/*")
        }
    }
    
    private fun setupLyricsRecyclerView() {
        binding.rvLyrics.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = lyricsAdapter
        }
    }
    
    private fun smoothScrollToCenter(position: Int) {
        val smoothScroller = object : LinearSmoothScroller(context) {
            override fun calculateDtToFit(viewStart: Int, viewEnd: Int, boxStart: Int, boxEnd: Int, snapPreference: Int): Int {
                return (boxStart + (boxEnd - boxStart) / 2) - (viewStart + (viewEnd - viewStart) / 2)
            }
        }
        smoothScroller.targetPosition = position
        binding.rvLyrics.layoutManager?.startSmoothScroll(smoothScroller)
    }
    
    private fun setupViewSwitching() {
        // Initial state
        binding.btnCover.isSelected = true
        binding.btnLyric.isSelected = false
        showCoverView() // default
        
        binding.btnCover.setOnClickListener {
            if (!it.isSelected) {
                showCoverView()
            }
        }
        
        binding.btnLyric.setOnClickListener {
            if (!it.isSelected) {
                showLyricsView()
            }
        }
    }
    
    private fun showLyricsView() {
        isLyricsViewActive = true
        binding.coverView.visibility = View.GONE
        binding.lyricView.visibility = View.VISIBLE
        
        binding.btnCover.isSelected = false
        binding.btnLyric.isSelected = true
        
        loadLyrics()
    }
    
    private fun showCoverView() {
        isLyricsViewActive = false
        binding.lyricView.visibility = View.GONE
        binding.coverView.visibility = View.VISIBLE
        
        binding.btnCover.isSelected = true
        binding.btnLyric.isSelected = false
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog ?: return
        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        
        val displayMetrics = resources.displayMetrics
        val height = displayMetrics.heightPixels
        
        bottomSheet.layoutParams?.height = height
        bottomSheet.requestLayout()
        
        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.peekHeight = height
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true
        behavior.isDraggable = false // Restrict swipe-down dismissal
    }

    private fun setupPlayer(player: Player) {
        if (_binding == null) return
        
        updateMetadata(player.currentMediaItem)
        
        binding.tvTotalTime.text = formatTime(player.duration)
        if (player.duration > 0) {
            val progress = (player.currentPosition.toFloat() / player.duration * 1000f)
            binding.seekBar.progress = progress.toInt().coerceIn(0, 1000)
        }
        binding.seekBar.post(updateProgressAction)

        binding.btnPlayPause.setOnClickListener {
            if (player.isPlaying) player.pause() else player.play()
        }
        binding.btnPrev.setOnClickListener { player.seekToPrevious() }
        binding.btnNext.setOnClickListener { player.seekToNext() }
        
        // Repeat button
        binding.btnRepeat.setOnClickListener {
            playbackMode = (playbackMode + 1) % 4
            
            when (playbackMode) {
                0 -> {
                    player.repeatMode = Player.REPEAT_MODE_OFF
                    player.shuffleModeEnabled = false
                    Toast.makeText(context, "Repeat Off", Toast.LENGTH_SHORT).show()
                }
                1 -> {
                    player.repeatMode = Player.REPEAT_MODE_ONE
                    player.shuffleModeEnabled = false
                    Toast.makeText(context, "Single Loop", Toast.LENGTH_SHORT).show()
                }
                2 -> {
                    player.repeatMode = Player.REPEAT_MODE_OFF
                    player.shuffleModeEnabled = true
                    Toast.makeText(context, "Shuffle", Toast.LENGTH_SHORT).show()
                }
                3 -> {
                    player.repeatMode = Player.REPEAT_MODE_ALL
                    player.shuffleModeEnabled = false
                    Toast.makeText(context, "Playlist Loop", Toast.LENGTH_SHORT).show()
                }
            }
            updatePlaybackModeIcon()
        }

        // SeekBar listeners
        val visibleThumb = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.seek_thumb)
        val transparentThumb = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        
        binding.seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = player.duration
                    if (duration > 0) {
                        binding.tvCurrentTime.text = formatTime((progress / 1000f * duration).toLong())
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {
                isTracking = true
                // Phantom Animation: Show thumb
                if (seekBar != null) {
                    seekBar.thumb = visibleThumb
                }
            }

            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                isTracking = false
                // Phantom Animation: Hide thumb
                if (seekBar != null) {
                    seekBar.thumb = transparentThumb
                    
                    val duration = player.duration
                    if (duration > 0) {
                        val seekPos = (seekBar.progress / 1000f * duration).toLong()
                        player.seekTo(seekPos)
                    }
                }
            }
        })

        // Determine initial playback mode
        playbackMode = when {
            player.repeatMode == Player.REPEAT_MODE_ONE -> 1
            player.shuffleModeEnabled -> 2
            player.repeatMode == Player.REPEAT_MODE_ALL -> 3
            else -> 0
        }

        updatePlayPauseIcon(player.isPlaying)
        updatePlaybackModeIcon()
        
        player.addListener(playerListener)
    }
    
    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (_binding == null) return
            updateMetadata(mediaItem)
            val player = viewModel.mediaController.value ?: return
            binding.tvTotalTime.text = formatTime(player.duration)
            
            val currentId = mediaItem?.mediaId?.toLongOrNull()
            if (currentId != null) {
                val isFav = viewModel.favorites.value?.any { it.id == currentId } == true
                updateFavoriteIcon(isFav)
            }
            
            if (isLyricsViewActive) {
                loadLyrics()
            }
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (_binding == null) return
            updatePlayPauseIcon(isPlaying)
            if (isPlaying) binding.seekBar.post(updateProgressAction)
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (_binding == null) return
            val player = viewModel.mediaController.value ?: return
            if (playbackState == Player.STATE_READY) {
                binding.tvTotalTime.text = formatTime(player.duration)
            }
        }
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            if (_binding == null) return
            val player = viewModel.mediaController.value ?: return
            playbackMode = when {
                player.repeatMode == Player.REPEAT_MODE_ONE -> 1
                shuffleModeEnabled -> 2
                player.repeatMode == Player.REPEAT_MODE_ALL -> 3
                else -> 0
            }
            updatePlaybackModeIcon()
        }
        override fun onRepeatModeChanged(repeatMode: Int) {
            if (_binding == null) return
            val player = viewModel.mediaController.value ?: return
            playbackMode = when {
                repeatMode == Player.REPEAT_MODE_ONE -> 1
                player.shuffleModeEnabled -> 2
                repeatMode == Player.REPEAT_MODE_ALL -> 3
                else -> 0
            }
            updatePlaybackModeIcon()
        }
    }

    private fun updateMetadata(mediaItem: MediaItem?) {
        if (_binding == null) return
        val title = mediaItem?.mediaMetadata?.title?.toString() ?: "Not Playing"
        val artist = mediaItem?.mediaMetadata?.artist?.toString() ?: ""
        
        binding.tvFullTitle.text = title
        binding.tvFullArtist.text = artist
        
        val currentId = mediaItem?.mediaId?.toLongOrNull()
        var embeddedArt: ByteArray? = null
        val context = context
        
        if (currentId != null && context != null) {
            val uri = android.content.ContentUris.withAppendedId(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                currentId
            )
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                embeddedArt = retriever.embeddedPicture
                retriever.release()
            } catch (e: Exception) {
               // ignore
            }
        }
        
        if (embeddedArt != null) {
            binding.ivFullArt.load(embeddedArt) {
                crossfade(true)
                transformations(RoundedCornersTransformation(32f))
            }
        } else {
            binding.ivFullArt.load(mediaItem?.mediaMetadata?.artworkUri) {
                crossfade(true)
                placeholder(R.drawable.default_album_art)
                error(R.drawable.default_album_art)
                transformations(RoundedCornersTransformation(32f))
            }
        }
    }

    private fun updatePlayPauseIcon(isPlaying: Boolean) {
        if (_binding == null) return
        val icon = if (isPlaying) androidx.media3.ui.R.drawable.exo_icon_pause else androidx.media3.ui.R.drawable.exo_icon_play
        binding.btnPlayPause.setImageResource(icon)
    }

    private fun updatePlaybackModeIcon() {
        if (_binding == null) return
        
        val icon = when (playbackMode) {
            1 -> androidx.media3.ui.R.drawable.exo_icon_repeat_one
            2 -> androidx.media3.ui.R.drawable.exo_icon_shuffle_on
            3 -> androidx.media3.ui.R.drawable.exo_icon_repeat_all
            else -> androidx.media3.ui.R.drawable.exo_icon_repeat_off
        }
        binding.btnRepeat.setImageResource(icon)
        
        val isActive = playbackMode != 0
        val tintColor = if (isActive) {
            com.google.android.material.color.MaterialColors.getColor(
                requireContext(),
                com.google.android.material.R.attr.colorPrimary,
                0
            )
        } else {
            com.google.android.material.color.MaterialColors.getColor(
                requireContext(),
                com.google.android.material.R.attr.colorOnSurface,
                0
            )
        }
        binding.btnRepeat.imageTintList = android.content.res.ColorStateList.valueOf(tintColor)
    }

    private fun formatTime(ms: Long): String {
        if (ms <= 0) return "0:00"
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding?.seekBar?.removeCallbacks(updateProgressAction)
        viewModel.mediaController.value?.removeListener(playerListener)
        _binding = null
    }
    
    private fun updateFavoriteIcon(isFavorite: Boolean) {
        if (_binding == null) return
        binding.btnFavorite.setImageResource(
            if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border
        )
        val tintColor = if (isFavorite) {
            com.google.android.material.color.MaterialColors.getColor(
                requireContext(),
                com.google.android.material.R.attr.colorPrimary,
                0
            )
        } else {
            com.google.android.material.color.MaterialColors.getColor(
                requireContext(),
                com.google.android.material.R.attr.colorOnSurface,
                0
            )
        }
        binding.btnFavorite.imageTintList = android.content.res.ColorStateList.valueOf(tintColor)
    }
    
    private fun loadLyrics() {
        val currentId = viewModel.mediaController.value?.currentMediaItem?.mediaId?.toLongOrNull()
        val song = currentId?.let { id -> viewModel.songs.value?.find { it.id == id } }
        
        if (song == null) {
            binding.noLyricPlaceholder.visibility = View.VISIBLE
            binding.rvLyrics.visibility = View.GONE
            return
        }
        
        // Check if lyrics exist
        if (viewModel.hasLyrics(song.id)) {
            // Load and parse lyrics
            val lyrics = viewModel.parseLyrics(song.id)
            if (lyrics.isNotEmpty()) {
                lyricsAdapter.submitList(lyrics)
                binding.noLyricPlaceholder.visibility = View.GONE
                binding.rvLyrics.visibility = View.VISIBLE
                
                // Seek to current
                val player = viewModel.mediaController.value
                if (player != null) {
                    val index = lyricsAdapter.updateTime(player.currentPosition)
                    if (index != -1) {
                        smoothScrollToCenter(index)
                    }
                }
            } else {
                binding.noLyricPlaceholder.visibility = View.VISIBLE
                binding.rvLyrics.visibility = View.GONE
            }
        } else {
            // No lyrics - show placeholder
            binding.noLyricPlaceholder.visibility = View.VISIBLE
            binding.rvLyrics.visibility = View.GONE
        }
    }
}
