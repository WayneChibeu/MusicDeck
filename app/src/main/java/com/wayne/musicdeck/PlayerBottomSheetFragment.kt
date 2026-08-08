package com.wayne.musicdeck

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
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
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import com.wayne.musicdeck.utils.setupBouncyPress
import android.view.HapticFeedbackConstants
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import org.koin.android.ext.android.inject
import android.graphics.Color
import android.widget.LinearLayout
import com.wayne.musicdeck.utils.SettingsManager

class PlayerBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentPlayerBottomSheetBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModel()
    private val settingsManager: SettingsManager by inject()
    private var isTracking = false

    // Playback mode: 0=Off, 1=Single Loop, 2=Shuffle, 3=Playlist Loop
    private var playbackMode = 0
    
    // Track which view is showing
    private var isLyricsViewActive = false
    
    // Lyrics Adapter
    private val lyricsAdapter = LyricsAdapter()
    
    // Stored as class field so we can clean it up in onDestroyView
    private var hideScrubberRunnable: Runnable? = null
    
    // Lyric file picker
    private val lyricFilePicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val currentPath = viewModel.mediaController.value?.currentMediaItem?.mediaId
            val currentSong = currentPath?.let { path ->
                viewModel.songs.value?.find { song -> song.data == path }
            }
            currentSong?.let { song ->
                viewModel.setLyricFile(song, it)
            }
        }
    }

    private val updateProgressAction = object : Runnable {
        override fun run() {
            if (_binding == null) return
            val player = viewModel.mediaController.value ?: return
            if (player.isPlaying && !isTracking) {
                // Breathing animation sync
                startBreathingAnimation()
                if (player.duration > 0) {
                    val progress = (player.currentPosition.toFloat() / player.duration * 1000f)
                    binding.seekBar.progress = progress.toInt().coerceIn(0, 1000)
                }
                binding.tvCurrentTime.text = formatTime(player.currentPosition)
                
                // Update synced lyrics
                if (isLyricsViewActive) {
                    // 30ms look-ahead is the "Golden Ratio" for sync
                    val newIndex = lyricsAdapter.updateTime(player.currentPosition + 30)
                    if (newIndex != -1) {
                        smoothScrollToCenter(newIndex)
                    }
                }
            }
            if (_binding != null) {
                binding.seekBar.postDelayed(this, 100) // 100ms for butter-smooth sync
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        
        // Initial text size from settings
        lyricsAdapter.setFontSizeIndex(settingsManager.lyricFontSizeIndex)
        
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
                    if (Math.abs(diffX) > 150 && Math.abs(velocityX) > 150) {
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
        
        var startX = 0f
        var startY = 0f
        var isHorizontalLock = false
        var isVerticalLock = false
        val touchSlop = ViewConfiguration.get(requireContext()).scaledTouchSlop
        
        val touchListener = View.OnTouchListener { v, event -> 
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    isHorizontalLock = false
                    isVerticalLock = false
                    gestureDetector.onTouchEvent(event)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = Math.abs(event.rawX - startX)
                    val dy = Math.abs(event.rawY - startY)
                    
                    if (!isHorizontalLock && !isVerticalLock) {
                        if (dx > touchSlop && dx > dy) {
                            isHorizontalLock = true
                            v.parent.requestDisallowInterceptTouchEvent(true)
                        } else if (dy > touchSlop && dy > dx) {
                            isVerticalLock = true
                            // Let parent (BottomSheetBehavior) intercept for dismissal
                        }
                    } else if (isHorizontalLock) {
                        v.parent.requestDisallowInterceptTouchEvent(true)
                    }
                    
                    gestureDetector.onTouchEvent(event)
                }
                else -> {
                    gestureDetector.onTouchEvent(event)
                }
            }
            true // Consuming touch so we handle the entire gesture
        }
        
        // Robust RecyclerView Gesture Handling
        binding.rvLyrics.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            private var downX = 0f
            private var downY = 0f
            
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                gestureDetector.onTouchEvent(e)
                val slop = android.view.ViewConfiguration.get(rv.context).scaledTouchSlop
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = e.x
                        downY = e.y
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = Math.abs(e.x - downX)
                        val dy = Math.abs(e.y - downY)
                        if (dx > slop && dx > dy) {
                            // Horizontal dragging detected. 
                            // Return true to intercept the touch and cancel the ghost click on the lyrics!
                            return true
                        }
                    }
                }
                return false // Pass through to RecyclerView for vertical scrolling or children for clicking
            }
            
            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                // Ensure the gesture detector gets the rest of the flick!
                gestureDetector.onTouchEvent(e)
            }
        })

        binding.coverView.setOnTouchListener(touchListener)
        binding.lyricView.setOnTouchListener(touchListener)
        binding.root.setOnTouchListener(touchListener)
        


        viewModel.mediaController.observe(viewLifecycleOwner) { player ->
            player?.let { 
                setupPlayer(it)
            }
        }
        
        // Tap-to-Seek logic for Lyrics
        lyricsAdapter.onItemClickListener = { line ->
            val player = viewModel.mediaController.value
            if (player != null) {
                player.seekTo(line.timeMs)
                binding.rvLyrics.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }
        
        // Apply Bouncy Touches to all primary controls
        binding.btnPlayPause.setupBouncyPress()
        binding.btnPrev.setupBouncyPress()
        binding.btnNext.setupBouncyPress()
        binding.btnFavorite.setupBouncyPress()
        binding.btnRepeat.setupBouncyPress()
        binding.btnQueue.setupBouncyPress()
        binding.btnMenu.setupBouncyPress()
        binding.btnCollapse.setupBouncyPress()

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
            val currentPath = viewModel.mediaController.value?.currentMediaItem?.mediaId ?: return@observe
            val isFav = favorites.any { it.data == currentPath }
            updateFavoriteIcon(isFav)
        }
        
        binding.btnFavorite.setOnClickListener {
            val currentPath = viewModel.mediaController.value?.currentMediaItem?.mediaId ?: return@setOnClickListener
            val song = viewModel.songs.value?.find { it.data == currentPath }
            if (song != null) {
               viewModel.toggleFavorite(song)
            } else {
                android.widget.Toast.makeText(context, "Error: Song not found", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        // Library Update Listener: Refresh metadata if song list loads after transition
        viewModel.songs.observe(viewLifecycleOwner) { 
            viewModel.mediaController.value?.let { player ->
                updateMetadata(player.currentMediaItem)
            }
        }
        
        // Mini player (removed in new layout but kept for safety if layout changes)
        // binding.btnMiniPlayPause.setOnClickListener... 
        
        // Manual lyric file picker
        binding.btnManuallySpecifyLyric.setOnClickListener {
            lyricFilePicker.launch("*/*")
        }
        
        // Desktop Lyrics Toggle
        binding.btnDesktopLyrics.setOnClickListener {
            showDesktopLyricsPopup()
        }
        
        // Lyrics Observers
        viewModel.lyrics.observe(viewLifecycleOwner) { lines ->
            if (_binding == null) return@observe
            lyricsAdapter.submitList(lines)
            
            // Auto-scroll to current position if we just loaded lyrics
            val player = viewModel.mediaController.value
            if (player != null && lines.isNotEmpty()) {
                val index = lyricsAdapter.updateTime(player.currentPosition)
                if (index != -1) {
                    smoothScrollToCenter(index)
                }
            }
        }
        
        viewModel.lyricsStatus.observe(viewLifecycleOwner) { status ->
            if (_binding == null) return@observe
            // Reset visibilities first to avoid flickering/overlapping
            
            when (status) {
                is MainViewModel.LyricsStatus.Loading -> {
                    binding.lyricsLoadingContainer.visibility = View.VISIBLE
                    binding.rvLyrics.visibility = View.GONE
                    binding.noLyricPlaceholder.visibility = View.GONE
                }
                
                is MainViewModel.LyricsStatus.Success -> {
                    binding.lyricsLoadingContainer.visibility = View.GONE
                    binding.rvLyrics.visibility = View.VISIBLE
                    binding.noLyricPlaceholder.visibility = View.GONE
                }
                
                is MainViewModel.LyricsStatus.NotFound -> {
                    binding.lyricsLoadingContainer.visibility = View.GONE
                    binding.rvLyrics.visibility = View.GONE
                    binding.noLyricPlaceholder.visibility = View.VISIBLE
                    // Reset error text if needed?
                }
                
                is MainViewModel.LyricsStatus.Error -> {
                    binding.lyricsLoadingContainer.visibility = View.GONE
                    binding.rvLyrics.visibility = View.GONE
                    binding.noLyricPlaceholder.visibility = View.VISIBLE
                    android.widget.Toast.makeText(context, "Lyrics error: ${status.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
                
                is MainViewModel.LyricsStatus.None -> {
                     binding.lyricsLoadingContainer.visibility = View.GONE
                     binding.rvLyrics.visibility = View.GONE
                     binding.noLyricPlaceholder.visibility = View.VISIBLE
                }
            }
        }
    }
    
    private fun setupLyricsRecyclerView() {
        binding.rvLyrics.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = lyricsAdapter
            
            hideScrubberRunnable = Runnable {
                val b = _binding ?: return@Runnable
                b.scrollTimestampContainer.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction { 
                        _binding?.scrollTimestampContainer?.visibility = View.GONE 
                    }
                    .start()
            }
            
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    val b = _binding ?: return
                    val runnable = hideScrubberRunnable ?: return
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        b.scrollTimestampContainer.removeCallbacks(runnable)
                        if (b.scrollTimestampContainer.visibility != View.VISIBLE) {
                            b.scrollTimestampContainer.visibility = View.VISIBLE
                            b.scrollTimestampContainer.animate().alpha(1f).setDuration(200).start()
                        }
                    } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        // Fade out after 3 seconds
                        b.scrollTimestampContainer.postDelayed(runnable, 3000)
                    }
                }

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    val b = _binding ?: return
                    if (recyclerView.scrollState == RecyclerView.SCROLL_STATE_DRAGGING || recyclerView.scrollState == RecyclerView.SCROLL_STATE_SETTLING) {
                        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                        val firstVisible = lm.findFirstVisibleItemPosition()
                        val lastVisible = lm.findLastVisibleItemPosition()
                        
                        if (firstVisible != RecyclerView.NO_POSITION && lastVisible != RecyclerView.NO_POSITION) {
                            val centerPosition = (firstVisible + lastVisible) / 2
                            val lyric = lyricsAdapter.getLyricAt(centerPosition)
                            if (lyric != null) {
                                b.tvScrollTimestamp.text = formatTime(lyric.timeMs)
                            }
                        }
                    }
                }
            })
        }
    }
    
    /**
     * Manually syncs lyrics UI visibility based on current ViewModel status.
     * Needed because LiveData observer only fires on VALUE CHANGE, not on re-read.
     * Call this when switching to lyrics tab to ensure UI is correct.
     */
    private fun syncLyricsUI() {
        val status = viewModel.lyricsStatus.value ?: MainViewModel.LyricsStatus.None
        
        when (status) {
            is MainViewModel.LyricsStatus.Loading -> {
                binding.lyricsLoadingContainer.visibility = View.VISIBLE
                binding.rvLyrics.visibility = View.GONE
                binding.noLyricPlaceholder.visibility = View.GONE
            }
            
            is MainViewModel.LyricsStatus.Success -> {
                binding.lyricsLoadingContainer.visibility = View.GONE
                binding.rvLyrics.visibility = View.VISIBLE
                binding.noLyricPlaceholder.visibility = View.GONE
                
                // Re-submit lyrics to adapter in case it's stale
                viewModel.lyrics.value?.let { lines ->
                    lyricsAdapter.submitList(lines)
                }
            }
            
            is MainViewModel.LyricsStatus.NotFound,
            is MainViewModel.LyricsStatus.Error,
            is MainViewModel.LyricsStatus.None -> {
                binding.lyricsLoadingContainer.visibility = View.GONE
                binding.rvLyrics.visibility = View.GONE
                binding.noLyricPlaceholder.visibility = View.VISIBLE
            }
        }
    }
    
    private fun smoothScrollToCenter(position: Int) {
        val ctx = context ?: return  // Fragment detached — bail out
        val smoothScroller = object : LinearSmoothScroller(ctx) {
            override fun calculateDtToFit(viewStart: Int, viewEnd: Int, boxStart: Int, boxEnd: Int, snapPreference: Int): Int {
                return (boxStart + (boxEnd - boxStart) / 2) - (viewStart + (viewEnd - viewStart) / 2)
            }
        }
        smoothScroller.targetPosition = position
        _binding?.rvLyrics?.layoutManager?.startSmoothScroll(smoothScroller)
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
                _binding?.coverView?.visibility = View.GONE
                _binding?.coverView?.translationX = 0f // Reset
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
        
        // Hide toggle when in lyrics view to avoid visual clutter
        binding.toggleWrapper.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                _binding?.toggleWrapper?.visibility = View.INVISIBLE
            }
            .start()
        
        // Dim background slightly for lyrics
        binding.lyricsDimmer.animate()
            .alpha(0.35f)
            .setDuration(400)
            .start()
            
        // Sync lyrics UI based on current status (observer only fires on CHANGE)
        syncLyricsUI()
        updateScreenOnState()
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
                _binding?.lyricView?.visibility = View.GONE
                _binding?.lyricView?.translationX = 0f // Reset
            }
            .start()
        
        binding.coverView.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(300)
            .setInterpolator(android.view.animation.DecelerateInterpolator(2f))
            .withEndAction(null)
            .start()
            
        // Remove background dimming
        binding.lyricsDimmer.animate()
            .alpha(0f)
            .setDuration(300)
            .start()
        
        // Show toggle when returning to cover view
        binding.toggleWrapper.visibility = View.VISIBLE
        binding.toggleWrapper.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
            
        updateScreenOnState()
    }

    private fun updateScreenOnState() {
        if (_binding == null) return
        val isPlaying = viewModel.mediaController.value?.isPlaying == true
        binding.root.keepScreenOn = isLyricsViewActive && isPlaying
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
        behavior.isDraggable = true // Allow swipe-down dismissal
        
        setupEdgeToEdge(dialog)

        // Proactive Refresh: Ensure metadata is current when returning from background
        viewModel.mediaController.value?.let { player ->
            updateMetadata(player.currentMediaItem)
        }
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

        // AGGRESSIVE IMMERSIVE MODE: Force layout behind system bars
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        
        // Request edge-to-edge layout (still good practice)
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
            playHaptic(it)
            if (player.isPlaying) player.pause() else player.play()
        }
        binding.btnPrev.setOnClickListener {
            playHaptic(it)
            player.seekToPrevious()
        }
        binding.btnNext.setOnClickListener {
            playHaptic(it)
            player.seekToNext()
        }
        
        // Repeat button
        binding.btnRepeat.setOnClickListener {
            playHaptic(it)
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
            playHaptic(it)
            val currentPath = player.currentMediaItem?.mediaId ?: return@setOnClickListener
            val song = viewModel.songs.value?.find { it.data == currentPath } ?: return@setOnClickListener
            
            val wasFavorite = viewModel.favorites.value?.any { it.data == currentPath } == true
            viewModel.toggleFavorite(song)
            updateFavoriteIcon(!wasFavorite, animate = true)
        }
        
        // Set initial favorite state
        val initialPath = player.currentMediaItem?.mediaId
        if (initialPath != null) {
            val isFav = viewModel.favorites.value?.any { it.data == initialPath } == true
            updateFavoriteIcon(isFav)
        }

        // SeekBar listeners
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
                // Thumb stays visible - no more phantom animation to preserve dynamic coloring
            }

            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                isTracking = false
                
                if (seekBar != null) {
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
        updateScreenOnState()
        
        player.addListener(playerListener)
    }
    
    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (_binding == null) return
            updateMetadata(mediaItem)
            val player = viewModel.mediaController.value ?: return
            binding.tvTotalTime.text = formatTime(player.duration)
            
            val currentPath = mediaItem?.mediaId
            val isFav = viewModel.favorites.value?.any { it.data == currentPath } == true
            updateFavoriteIcon(isFav)
            // Lyrics are now observed automatically via viewModel.lyrics logic
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (_binding == null) return
            updatePlayPauseIcon(isPlaying)
            if (isPlaying) {
                binding.seekBar.post(updateProgressAction)
                startBreathingAnimation()
            } else {
                stopBreathingAnimation()
            }
            updateScreenOnState()
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
        
        val currentPath = mediaItem?.mediaId
        val ctx = context ?: return
        
        // Resolve song object for deeper metadata (albumId, custom art)
        val song = if (currentPath != null) viewModel.songs.value?.find { it.data == currentPath } else null
        
        // Smart art resolution logic (sync with ImageUtils)
        val customCoverPath = song?.data?.let { path -> 
            val prefs = ctx.getSharedPreferences("custom_covers", android.content.Context.MODE_PRIVATE)
            prefs.getString(path, null)
        }
        
        val imageData: Any = if (customCoverPath != null) {
            java.io.File(customCoverPath)
        } else if (song != null) {
            java.io.File(song.data) // PRIORITY: Load as File so custom CoilAudioFetcher intercepts it!
        } else {
            mediaItem?.mediaMetadata?.artworkUri ?: R.drawable.default_album_art
        }
        
        val request = coil.request.ImageRequest.Builder(ctx)
            .data(imageData)
            .crossfade(true)
            .allowHardware(false) // Required for Palette
            .placeholder(R.drawable.default_album_art)
            .error(R.drawable.default_album_art)
            .target(
                onSuccess = { result ->
                    _binding?.ivFullArt?.setImageDrawable(result)
                    // Extract colors for dynamic background
                    val bitmap = (result as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        androidx.palette.graphics.Palette.from(bitmap).generate { palette ->
                            if (_binding != null) applyDynamicBackground(palette)
                        }
                    }
                },
                onError = {
                    _binding?.ivFullArt?.setImageResource(R.drawable.default_album_art)
                    if (_binding != null) {
                        applyDynamicBackground(null)
                        updateSeekBarColor(android.graphics.Color.WHITE)
                    }
                }
            )
            .transformations(RoundedCornersTransformation(32f))
            .build()
        
        // CRITICAL: We must use the global Coil ImageLoader which has our custom MP3 Audio Fetcher attached!
        // Instantiating a new ImageLoader() bypasses our initialization in MusicApp.kt.
        coil.Coil.imageLoader(ctx).enqueue(request)
    }
    
    private fun isColorDark(color: Int): Boolean {
        return androidx.core.graphics.ColorUtils.calculateLuminance(color) < 0.4
    }
    
    private fun darkenColor(color: Int, factor: Float = 0.5f): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)
        hsv[2] *= factor // Reduce brightness
        return android.graphics.Color.HSVToColor(hsv)
    }
    
    /** Soften a bright color for use as a pleasant light background. */
    private fun softenColor(color: Int, saturationFactor: Float = 0.7f): Int {
        val hsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(color, hsl)
        hsl[1] *= saturationFactor  // Reduce saturation to avoid neon-bright backgrounds
        hsl[2] = hsl[2].coerceIn(0.75f, 0.90f)  // Keep lightness in a pleasant range
        return androidx.core.graphics.ColorUtils.HSLToColor(hsl)
    }
    
    /** Derive a rich, dark accent from a bright dominant color (e.g., bright yellow → deep gold). */
    private fun deepenColor(color: Int): Int {
        val hsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(color, hsl)
        hsl[1] = (hsl[1] * 1.5f).coerceAtMost(1f)  // Boost saturation aggressively for richness
        hsl[2] = 0.22f  // Dark enough to guarantee contrast against light backgrounds
        return androidx.core.graphics.ColorUtils.HSLToColor(hsl)
    }

    private fun applyDynamicBackground(palette: androidx.palette.graphics.Palette?) {
        val ctx = context ?: return
        val defaultColor = com.google.android.material.color.MaterialColors.getColor(
            ctx, com.google.android.material.R.attr.colorSurface, android.graphics.Color.BLACK
        )
        
        if (palette == null) {
            applyMeshBackground(defaultColor, darkenColor(defaultColor, 0.7f), false)
            updateSeekBarColor(android.graphics.Color.WHITE, false)
            return
        }
        
        // Extract all palette swatches
        val dominant = palette.getDominantColor(android.graphics.Color.TRANSPARENT)
        val darkVibrant = palette.getDarkVibrantColor(android.graphics.Color.TRANSPARENT)
        val darkMuted = palette.getDarkMutedColor(android.graphics.Color.TRANSPARENT)
        val vibrant = palette.getVibrantColor(android.graphics.Color.TRANSPARENT)
        val lightVibrant = palette.getLightVibrantColor(android.graphics.Color.TRANSPARENT)
        
        // Flagship Apple Music / Spotify approach:
        // Always maintain a rich, ambient dark/deep background so white text, white icons,
        // and white lyrics pop with perfect contrast on every single album cover.
        val dominantLuminance = if (dominant != android.graphics.Color.TRANSPARENT) {
            androidx.core.graphics.ColorUtils.calculateLuminance(dominant)
        } else 0.0
        
        val selectedColor = when {
            dominant != android.graphics.Color.TRANSPARENT && dominantLuminance < 0.35 -> dominant
            dominant != android.graphics.Color.TRANSPARENT -> deepenColor(dominant)
            darkVibrant != android.graphics.Color.TRANSPARENT -> darkVibrant
            darkMuted != android.graphics.Color.TRANSPARENT -> darkMuted
            vibrant != android.graphics.Color.TRANSPARENT -> darkenColor(vibrant, 0.5f)
            else -> defaultColor
        }
        
        // Secondary color for rich vertical gradient depth
        val secondaryColor = when {
            darkMuted != android.graphics.Color.TRANSPARENT && darkMuted != selectedColor -> darkMuted
            darkVibrant != android.graphics.Color.TRANSPARENT && darkVibrant != selectedColor -> darkVibrant
            else -> darkenColor(selectedColor, 0.7f)
        }
        
        applyMeshBackground(selectedColor, secondaryColor, false)
        
        // Helper to choose accent color with good contrast against selectedColor
        fun getHighContrastAccent(bg: Int, candidates: List<Int>, fallback: Int): Int {
            for (color in candidates) {
                if (color == android.graphics.Color.TRANSPARENT) continue
                if (androidx.core.graphics.ColorUtils.calculateContrast(color, bg) >= 3.0) return color
            }
            return fallback
        }
        
        val accentColor = getHighContrastAccent(
            selectedColor,
            listOf(lightVibrant, vibrant),
            android.graphics.Color.WHITE
        )
        
        updateSeekBarColor(accentColor, false)
        // Lyrics: Always crisp white for flagship readability and visual unity
        lyricsAdapter.activeColor = android.graphics.Color.WHITE
        updateTextColors(true)
    }
    
    private fun updateTextColors(useLightText: Boolean) {
        val textColor = if (useLightText) android.graphics.Color.WHITE else android.graphics.Color.BLACK
        val secondaryColor = if (useLightText) 0xB3FFFFFF.toInt() else 0xB3000000.toInt() // 70% opacity
        
        // Update title and artist
        binding.tvFullTitle.setTextColor(textColor)
        binding.tvFullArtist.setTextColor(secondaryColor)
        
        // Update time labels
        binding.tvCurrentTime.setTextColor(secondaryColor)
        binding.tvTotalTime.setTextColor(secondaryColor)
        
        // Update header icons (exclude Favorite, it manages its own color)
        val iconTint = android.content.res.ColorStateList.valueOf(textColor)
        binding.btnCollapse.imageTintList = iconTint
        binding.btnMenu.imageTintList = iconTint
        binding.btnPlayPause.imageTintList = iconTint
        binding.btnPrev.imageTintList = iconTint
        binding.btnNext.imageTintList = iconTint
        binding.btnQueue.imageTintList = iconTint
        
        // Re-apply repeat icon color based on new dynamic theme colors
        updatePlaybackModeIcon()
    }
    
    private fun applyMeshBackground(primary: Int, secondary: Int, isLightMode: Boolean = false) {
        if (isLightMode) {
            // Light mode: keep the entire gradient in the same warm color family
            // Use HSL to darken while preserving hue (HSV-based darkenColor strips warmth)
            val hsl = FloatArray(3)
            androidx.core.graphics.ColorUtils.colorToHSL(primary, hsl)
            // Mid-tone: slightly deeper and more saturated
            val midHsl = hsl.copyOf()
            midHsl[1] = (midHsl[1] * 1.1f).coerceAtMost(1f)
            midHsl[2] = (midHsl[2] * 0.85f).coerceAtLeast(0.55f)
            val midColor = androidx.core.graphics.ColorUtils.HSLToColor(midHsl)
            // Bottom: noticeably deeper but still in-family
            val bottomHsl = hsl.copyOf()
            bottomHsl[1] = (bottomHsl[1] * 1.2f).coerceAtMost(1f)
            bottomHsl[2] = (bottomHsl[2] * 0.65f).coerceAtLeast(0.35f)
            val bottomColor = androidx.core.graphics.ColorUtils.HSLToColor(bottomHsl)
            
            val gradient = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(primary, midColor, bottomColor)
            )
            binding.root.background = gradient
        } else {
            // Dark mode: diagonal gradient fading to black (existing behavior)
            val gradient = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(primary, secondary, android.graphics.Color.BLACK)
            )
            binding.root.background = gradient
        }
        binding.headerView.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
    }
    
    private fun updateSeekBarColor(color: Int, isLightMode: Boolean = false) {
        // Tint thumb
        binding.seekBar.thumb.setTint(color)
        
        // Tint progress and adapt track background for visibility
        val progressDrawable = binding.seekBar.progressDrawable
        if (progressDrawable is android.graphics.drawable.LayerDrawable) {
            val progressLayer = progressDrawable.findDrawableByLayerId(android.R.id.progress)
            progressLayer?.setTint(color)
            // Adaptive track: 15% black on light backgrounds, 15% white on dark backgrounds
            val trackColor = if (isLightMode) 0x26000000.toInt() else 0x26FFFFFF.toInt()
            val bgLayer = progressDrawable.findDrawableByLayerId(android.R.id.background)
            bgLayer?.setTint(trackColor)
        } else {
            // Fallback if not a LayerDrawable (shouldn't happen with our XML)
            progressDrawable.setTint(color)
        }
        
        // Tint Play/Pause Ring
        binding.playPauseContainer.background?.setTint(color)
    }
    


    private fun updatePlayPauseIcon(isPlaying: Boolean) {
        if (_binding == null) return
        val icon = if (isPlaying) R.drawable.ic_pause_rounded else R.drawable.ic_play_rounded
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
        
        // Active = Theme Accent Color (colorPrimary)
        // Inactive = Solid white/black (colorOnSurface)
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
        // Cancel all view animations FIRST while binding is still valid
        _binding?.coverView?.animate()?.cancel()
        _binding?.lyricView?.animate()?.cancel()
        _binding?.toggleWrapper?.animate()?.cancel()
        _binding?.lyricsDimmer?.animate()?.cancel()
        _binding?.scrollTimestampContainer?.animate()?.cancel()
        
        // Stop breathing animation
        stopBreathingAnimation()
        
        // Remove all pending Runnables (BEFORE super.onDestroyView detaches views)
        _binding?.seekBar?.removeCallbacks(updateProgressAction)
        hideScrubberRunnable?.let { runnable ->
            _binding?.scrollTimestampContainer?.removeCallbacks(runnable)
        }
        // Also remove from the main handler as a safety net
        _binding?.seekBar?.handler?.removeCallbacks(updateProgressAction)
        
        // Remove player listener
        viewModel.mediaController.value?.removeListener(playerListener)
        
        // Null out binding before super to prevent any lingering callbacks
        _binding = null
        
        // Call super LAST — this detaches views from the window hierarchy
        super.onDestroyView()
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

    private var breathingAnimator: ValueAnimator? = null

    private fun startBreathingAnimation() {
        if (breathingAnimator != null && breathingAnimator!!.isRunning) return
        if (_binding == null) return
        
        breathingAnimator = ValueAnimator.ofFloat(1.0f, 1.03f).apply {
            duration = 1800 // Lively, enthusiastic pulse
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                if (_binding != null) {
                    val scale = animator.animatedValue as Float
                    binding.ivFullArt.scaleX = scale
                    binding.ivFullArt.scaleY = scale
                }
            }
            start()
        }
    }

    private fun stopBreathingAnimation() {
        breathingAnimator?.cancel()
        if (_binding != null) {
            binding.ivFullArt.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(500)
                .start()
        }
    }

    private fun playHaptic(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    @Suppress("DEPRECATION")
    private fun isFloatingLyricServiceRunning(): Boolean {
        val manager = requireContext().getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (FloatingLyricService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun showDesktopLyricsPopup() {
        val ctx = requireContext()
        val isRunning = isFloatingLyricServiceRunning()
        
        // Custom vertical layout for the popup
        val container = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
        }
        
        // Option 1: Toggle Desktop Lyrics
        val toggleText = if (isRunning) "Turn off Desktop Lyrics" else "Turn on Desktop Lyrics"
        val toggleIcon = if (isRunning) R.drawable.ic_visibility_off else R.drawable.ic_visibility
        val toggleOption = createPopupOption(ctx, toggleText, toggleIcon)
        container.addView(toggleOption)
        
        // Option 2: Change font size
        val fontOption = createPopupOption(ctx, "Change font size", R.drawable.ic_edit)
        container.addView(fontOption)
        
        // Create PopupWindow
        val popupWindow = android.widget.PopupWindow(container, 180.dpToPx(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popupWindow.elevation = 24.dpToPx().toFloat()
        popupWindow.setBackgroundDrawable(androidx.core.content.ContextCompat.getDrawable(ctx, R.drawable.bg_dialog_modern))
        
        // Wire click handlers
        toggleOption.setOnClickListener {
            popupWindow.dismiss()
            if (isRunning) {
                FloatingLyricService.stop(ctx)
                Toast.makeText(ctx, "Desktop Lyrics disabled", Toast.LENGTH_SHORT).show()
            } else {
                if (android.provider.Settings.canDrawOverlays(ctx)) {
                    FloatingLyricService.start(ctx)
                    Toast.makeText(ctx, "Desktop Lyrics enabled", Toast.LENGTH_SHORT).show()
                } else {
                    val intent = android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:${ctx.packageName}")
                    )
                    startActivity(intent)
                    Toast.makeText(ctx, "Please grant overlay permission", Toast.LENGTH_LONG).show()
                }
            }
        }
        
        fontOption.setOnClickListener {
            popupWindow.dismiss()
            showFontSizeBottomSheet()
        }
        
        // Show above the icon. We offset it so it appears centered above the anchor.
        popupWindow.showAsDropDown(binding.btnDesktopLyrics, -130.dpToPx(), -140.dpToPx())
    }
    
    private fun createPopupOption(ctx: android.content.Context, text: String, iconRes: Int): android.widget.LinearLayout {
        return android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())
            isClickable = true
            isFocusable = true
            
            // Ripple background
            val rippleAttrs = intArrayOf(android.R.attr.selectableItemBackground)
            val typedArray = ctx.obtainStyledAttributes(rippleAttrs)
            background = typedArray.getDrawable(0)
            typedArray.recycle()
            
            // Icon
            val icon = android.widget.ImageView(ctx).apply {
                setImageResource(iconRes)
                layoutParams = android.widget.LinearLayout.LayoutParams(20.dpToPx(), 20.dpToPx()).apply {
                    marginEnd = 12.dpToPx()
                }
                imageTintList = android.content.res.ColorStateList.valueOf(0xB3FFFFFF.toInt()) // 70% white
            }
            addView(icon)
            
            // Text
            val label = android.widget.TextView(ctx).apply {
                this.text = text
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 14f
            }
            addView(label)
        }
    }

    private fun showFontSizeBottomSheet() {
        val dialog = BottomSheetDialog(requireContext(), R.style.TransparentBottomSheetDialog)
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 32.dpToPx(), 24.dpToPx(), 32.dpToPx())
            background = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.bg_bottom_sheet_glassmorphic)
        }
        
        // Title
        val title = android.widget.TextView(requireContext()).apply {
            text = "Change font size"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, 32.dpToPx())
            layoutParams = lp
        }
        container.addView(title)
        
        // Slider
        val slider = Slider(requireContext()).apply {
            valueFrom = 0f
            valueTo = 4f
            stepSize = 1f
            value = settingsManager.lyricFontSizeIndex.toFloat()
            
            // Premium Polish: Clean pill look without tick marks
            isTickVisible = false
            trackHeight = 12.dpToPx()
            thumbRadius = 14.dpToPx()
            haloRadius = 0 
            
            addOnChangeListener { _, value, _ ->
                val index = value.toInt()
                settingsManager.lyricFontSizeIndex = index
                lyricsAdapter.setFontSizeIndex(index)
            }
        }
        container.addView(slider)
        
        // Labels
        val labelsLayout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 8.dpToPx(), 0, 0)
            layoutParams = lp
        }
        val labels = listOf("Small", "Default", "Medium", "Large", "Extra")
        labels.forEach { labelStr ->
            val v = android.widget.TextView(requireContext()).apply {
                text = labelStr
                textSize = 10f
                setTextColor(0x80FFFFFF.toInt())
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            labelsLayout.addView(v)
        }
        container.addView(labelsLayout)
        
        // Finish Button
        val btnFinish = com.google.android.material.button.MaterialButton(requireContext()).apply {
            text = "Finish"
            setTextColor(0xFFFA6650.toInt())
            background = null
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 24.dpToPx(), 0, 0)
            layoutParams = lp
            setOnClickListener { dialog.dismiss() }
        }
        container.addView(btnFinish)
        
        dialog.setContentView(container)
        dialog.show()
    }

}

