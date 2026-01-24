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
                            // Right Swipe -> Show Cover (sync toggle)
                            selectButton(true)
                        } else {
                            // Left Swipe -> Show Lyric (sync toggle)
                            selectButton(false)
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
        // Initial state - Cover is checked by default
        selectButton(true)
        
        // Click listeners - always call selectButton to enforce mutual exclusion
        // MaterialButton with checkable=true auto-toggles before onClick, so we must
        // unconditionally set the correct state in selectButton()
        binding.btnCover.setOnClickListener {
            selectButton(true)
        }
        
        binding.btnLyric.setOnClickListener {
            selectButton(false)
        }
    }
    
    private fun selectButton(isCover: Boolean) {
        // Enforce mutual exclusion for radio behavior
        binding.btnCover.isChecked = isCover
        binding.btnLyric.isChecked = !isCover
        
        if (isCover) {
            showCoverView()
        } else {
            showLyricsView()
        }
    }
    
    private fun showLyricsView() {
        if (isLyricsViewActive) return
        isLyricsViewActive = true
        
        // Prepare incoming view ONLY if not already visible to avoid snapping mid-animation
        if (binding.lyricView.visibility != View.VISIBLE) {
            binding.lyricView.translationX = 100f // Slide in from right
            binding.lyricView.alpha = 0f
            binding.lyricView.visibility = View.VISIBLE
        }
        
        // Animate cover out (slide left + fade)
        binding.coverView.animate()
            .alpha(0f)
            .translationX(-100f)
            .setDuration(300)
            .setInterpolator(android.view.animation.DecelerateInterpolator(2f)) // Smooth deceleration
            .withEndAction {
                binding.coverView.visibility = View.GONE
                binding.coverView.translationX = 0f // Reset
            }
            .start()
        
        // Animate lyrics in (slide to center + fade in)
        binding.lyricView.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(300)
            .setInterpolator(android.view.animation.DecelerateInterpolator(2f))
            .withEndAction(null) // Ensure no GONE action persists
            .start()
        
        loadLyrics()
    }
    
    private fun showCoverView() {
        if (!isLyricsViewActive && binding.coverView.visibility == View.VISIBLE) return
        isLyricsViewActive = false
        
        // Prepare incoming view ONLY if not already visible
        if (binding.coverView.visibility != View.VISIBLE) {
            binding.coverView.translationX = -100f // Slide in from left
            binding.coverView.alpha = 0f
            binding.coverView.visibility = View.VISIBLE
        }
        
        // Animate lyrics out (slide right + fade)
        binding.lyricView.animate()
            .alpha(0f)
            .translationX(100f)
            .setDuration(300)
            .setInterpolator(android.view.animation.DecelerateInterpolator(2f))
            .withEndAction {
                binding.lyricView.visibility = View.GONE
                binding.lyricView.translationX = 0f // Reset
            }
            .start()
        
        // Animate cover in (slide to center + fade in)
        binding.coverView.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(300)
            .setInterpolator(android.view.animation.DecelerateInterpolator(2f))
            .withEndAction(null)
            .start()
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog ?: return
        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        
        // Force full screen height
        val displayMetrics = resources.displayMetrics
        val height = displayMetrics.heightPixels
        
        bottomSheet.layoutParams?.height = ViewGroup.LayoutParams.MATCH_PARENT
        bottomSheet.requestLayout()
        
        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.peekHeight = height
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true
        behavior.isDraggable = false // Restrict swipe-down dismissal
        
        setupEdgeToEdge(dialog)
    }
    
    private fun setupEdgeToEdge(dialog: BottomSheetDialog) {
        val window = dialog.window ?: return
        
        // Make navigation, status bars transparent
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        
        // Disable contrast enforcement to prevent system scrims (Android 10+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        
        // Request edge-to-edge layout
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Handle insets (padding for status bar/nav bar)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            
            // Add top padding for status bar (so header isn't hidden)
            // Add bottom padding for nav bar (so controls aren't hidden)
            view.setPadding(0, 0, 0, 0)
            binding.headerView.setPadding(
                binding.headerView.paddingLeft,
                insets.top, // Only pad the header top
                binding.headerView.paddingRight,
                binding.headerView.paddingBottom
            )
            
            // Add bottom padding to container to avoid nav bar overlap
            // We apply this to the containers inside the root, not the root itself (which has background)
            binding.coverView.setPadding(
                binding.coverView.paddingLeft,
                binding.coverView.paddingTop,
                binding.coverView.paddingRight,
                insets.bottom + 16.dpToPx() // Original 16dp + nav bar height
            )
            
            binding.lyricView.setPadding(
                binding.lyricView.paddingLeft,
                binding.lyricView.paddingTop,
                binding.lyricView.paddingRight,
                insets.bottom + 16.dpToPx()
            )
            
            androidx.core.view.WindowInsetsCompat.CONSUMED
        }
    }
    
    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

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
        
        // Favorite button
        binding.btnFavorite.setOnClickListener {
            val currentId = player.currentMediaItem?.mediaId?.toLongOrNull()
            if (currentId != null) {
                val song = viewModel.songs.value?.find { it.id == currentId }
                if (song != null) {
                    val wasFavorite = viewModel.favorites.value?.any { it.id == currentId } == true
                    viewModel.toggleFavorite(song)
                    updateFavoriteIcon(!wasFavorite, animate = true)
                }
            }
        }
        
        // Set initial favorite state
        val initialId = player.currentMediaItem?.mediaId?.toLongOrNull()
        if (initialId != null) {
            val isFav = viewModel.favorites.value?.any { it.id == initialId } == true
            updateFavoriteIcon(isFav)
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
        val ctx = context ?: return
        
        if (currentId != null) {
            val uri = android.content.ContentUris.withAppendedId(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                currentId
            )
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(ctx, uri)
                embeddedArt = retriever.embeddedPicture
                retriever.release()
            } catch (e: Exception) {
               // ignore
            }
        }
        
        // Load image with Palette extraction
        val imageData: Any = embeddedArt ?: mediaItem?.mediaMetadata?.artworkUri ?: R.drawable.default_album_art
        
        val request = coil.request.ImageRequest.Builder(ctx)
            .data(imageData)
            .crossfade(true)
            .allowHardware(false) // Required for Palette
            .target(
                onSuccess = { result ->
                    binding.ivFullArt.setImageDrawable(result)
                    // Extract colors for dynamic background
                    val bitmap = (result as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        androidx.palette.graphics.Palette.from(bitmap).generate { palette ->
                            applyDynamicBackground(palette)
                        }
                    }
                },
                onError = {
                    binding.ivFullArt.setImageResource(R.drawable.default_album_art)
                    applyDynamicBackground(null)
                }
            )
            .transformations(RoundedCornersTransformation(32f))
            .build()
        coil.ImageLoader(ctx).enqueue(request)
    }
    
    private fun applyDynamicBackground(palette: androidx.palette.graphics.Palette?) {
        val ctx = context ?: return
        val defaultColor = com.google.android.material.color.MaterialColors.getColor(
            ctx, com.google.android.material.R.attr.colorSurface, android.graphics.Color.BLACK
        )
        
        // Get the most dominant/prominent color
        // Priority: Dominant -> DarkVibrant -> Vibrant -> Default
        val primaryColor = palette?.getDominantColor(
            palette.getDarkVibrantColor(
                palette.getVibrantColor(defaultColor)
            )
        ) ?: defaultColor
        
        // Create a 'whole' gradient: Primary Color -> Slightly Darker Primary Color
        // This avoids fading to black and keeps the color strong throughout
        
        // Use the primary color for both start and end to create a solid look
        // Or a very subtle shift if desired, but user asked for "whole"
        val gradient = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(primaryColor, primaryColor)
        )
        
        // Apply to root view
        binding.root.background = gradient
        
        // Tint header to match logic matches root top color
        // Use semi-transparent version or just transparent since root handles likely
        binding.headerView.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
    }
    


    private fun updatePlayPauseIcon(isPlaying: Boolean) {
        if (_binding == null) return
        val icon = if (isPlaying) androidx.media3.ui.R.drawable.exo_icon_pause else androidx.media3.ui.R.drawable.exo_icon_play
        binding.btnPlayPause.setImageResource(icon)
    }

    private fun updatePlaybackModeIcon() {
        if (_binding == null) return
        
        val icon = when (playbackMode) {
            1 -> R.drawable.ic_repeat_one
            2 -> R.drawable.ic_shuffle_on
            3 -> R.drawable.ic_repeat_all
            else -> R.drawable.ic_repeat_off
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
    
    private fun updateFavoriteIcon(isFavorite: Boolean, animate: Boolean = false) {
        if (_binding == null) return
        binding.btnFavorite.setImageResource(
            if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border
        )
        val tintColor = if (isFavorite) {
            requireContext().getColor(R.color.colorRose)
        } else {
            com.google.android.material.color.MaterialColors.getColor(
                requireContext(),
                com.google.android.material.R.attr.colorOnSurface,
                0
            )
        }
        binding.btnFavorite.imageTintList = android.content.res.ColorStateList.valueOf(tintColor)
        
        // Heart explosion animation when favoriting
        if (animate && isFavorite) {
            playHeartExplosionAnimation()
        }
    }
    
    private fun playHeartExplosionAnimation() {
        val btn = binding.btnFavorite
        
        // Scale up animation
        val scaleUp = android.view.animation.ScaleAnimation(
            1f, 1.4f, 1f, 1.4f,
            android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
            android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 150
            fillAfter = true
        }
        
        // Scale down with bounce
        val scaleDown = android.view.animation.ScaleAnimation(
            1.4f, 1f, 1.4f, 1f,
            android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
            android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 200
            interpolator = android.view.animation.OvershootInterpolator(3f)
            fillAfter = true
        }
        
        val animSet = android.view.animation.AnimationSet(false)
        scaleDown.startOffset = 150
        animSet.addAnimation(scaleUp)
        animSet.addAnimation(scaleDown)
        
        btn.startAnimation(animSet)
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
