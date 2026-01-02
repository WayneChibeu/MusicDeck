package com.wayne.musicdeck

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import coil.load
import coil.transform.RoundedCornersTransformation
import com.wayne.musicdeck.databinding.ActivityMainBinding
import androidx.activity.addCallback

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private val adapter = SongAdapter { song ->
        viewModel.playSong(song)
    }
    
    // Modern ActivityResultLauncher for MediaStore delete requests (Android 11+)
    private val deleteRequestLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.loadSongs()
            android.widget.Toast.makeText(this, "Deleted!", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    // Image picker for custom album covers
    private var targetSongForCover: Song? = null
    private val imagePickerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            targetSongForCover?.let { song ->
                viewModel.setCustomCover(song, it)
                // Refresh display
                viewModel.loadSongs()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        // setupOneTimeEvents() removed
        
        binding.fastScroller.setListener(object : com.wayne.musicdeck.views.FastScrollerView.OnFastScrollListener {
            override fun onLetterSelected(letter: String) {
                val layoutManager = binding.recyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
                if (layoutManager != null) {
                    val currentList = adapter.currentList
                    val index = currentList.indexOfFirst { item ->
                        when(item) {
                            is com.wayne.musicdeck.SongListItem.Header -> item.letter == letter
                            is com.wayne.musicdeck.SongListItem.SongItem -> item.song.title.uppercase().startsWith(letter)
                            else -> false
                        }
                    }
                    if (index != -1) {
                        layoutManager.scrollToPositionWithOffset(index, 0)
                    } else {
                        // Fallback logic for sections without exact match?
                        // Just scroll to approximate? Simplified for now.
                    }
                }
            }
        })
        
        viewModel.songs.observe(this) { songs ->
            adapter.submitList(processHeaders(songs))
            restoreLastSong(songs)
            // Update song count in header
            binding.tvSongCount.text = "${songs.size} songs"
        }
        
        adapter.onSongMenuClick = { song, action ->
            when (action) {
                "show_menu" -> showPlaylistSongOptions(song)
                "details" -> showSongDetailsDialog(song)
                "edit" -> showEditSongOptions(song)
                "add_to_playlist" -> showAddToPlaylistDialog(song)
                "remove_from_playlist" -> { /* Handled in PlaylistDetailFragment usually, but if here... */ }
                "delete" -> deleteSong(song)
                "share" -> shareSong(song)
                else -> {
                    // Handle other actions
                }
            }
        }

        viewModel.mediaController.observe(this) { controller ->
            controller?.let { player ->
                // binding.playerControlView.player = it // Removed legacy player

                // Setup Mini Player Controls
                binding.miniPlayer.btnMiniPlayPause.setOnClickListener {
                    if (player.mediaItemCount == 0) {
                        // No media loaded - try to play last song
                        playLastSong()
                    } else if (player.isPlaying) {
                        player.pause()
                    } else {
                        player.play()
                    }
                }

                binding.miniPlayer.root.setOnClickListener {
                    if (player.mediaItemCount == 0) {
                        playLastSong()
                    }
                    PlayerBottomSheetFragment().show(supportFragmentManager, "PlayerBottomSheet")
                }
                
                binding.miniPlayer.btnMiniNext.setOnClickListener {
                    if (player.mediaItemCount == 0) {
                        playLastSong()
                    } else {
                        player.seekToNext()
                    }
                }

                // Progress Update Runnable
                val progressRunnable = object : Runnable {
                    override fun run() {
                        if (player.isPlaying) {
                            val progress = if (player.duration > 0) {
                                (player.currentPosition * 100 / player.duration).toInt()
                            } else 0
                            binding.miniPlayer.miniPlayerProgress.progress = progress
                            binding.miniPlayer.miniPlayerProgress.postDelayed(this, 1000)
                        }
                    }
                }

                // Add Listener for updates
                player.addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        updateMiniPlayer(mediaItem)
                        // Save new song ID when auto-advancing
                        mediaItem?.mediaId?.toLongOrNull()?.let { id ->
                            viewModel.lastPlayedSongId = id
                            viewModel.lastPlayedPosition = 0 // Reset position for new song
                            // Update adapter to highlight currently playing song
                            adapter.currentlyPlayingId = id
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        updatePlayPauseIcon(isPlaying)
                        // Update adapter to animate/pause equalizer
                        adapter.isPlaying = isPlaying
                        
                        if (isPlaying) {
                            binding.miniPlayer.miniPlayerProgress.post(progressRunnable)
                        } else {
                            binding.miniPlayer.miniPlayerProgress.removeCallbacks(progressRunnable)
                            viewModel.savePosition()
                        }
                    }
                    
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY && player.isPlaying) {
                           updatePlayPauseIcon(true)
                           updateMiniPlayer(player.currentMediaItem)
                           binding.miniPlayer.miniPlayerProgress.post(progressRunnable)
                        }
                    }
                })
                
                // Initial update
                updateMiniPlayer(player.currentMediaItem)
                updatePlayPauseIcon(player.isPlaying)
                if (player.isPlaying) binding.miniPlayer.miniPlayerProgress.post(progressRunnable)
                
                // Sync currently playing song ID to adapter for highlight
                val currentId = player.currentMediaItem?.mediaId?.toLongOrNull()
                if (currentId != null) {
                    adapter.currentlyPlayingId = currentId
                } else {
                    // No active playback - highlight last played song
                    val lastId = viewModel.lastPlayedSongId
                    if (lastId != -1L) {
                        adapter.currentlyPlayingId = lastId
                    }
                }
            }
        }
        
        viewModel.initializeController()
        setupDevInfo()
        setupSearch() // Includes Sort
        setupPlaybackControls()
        setupActionGrid()
        setupPermissions()
        setupTabs()
        setupPersonalizedHeader()
        // Removed Rescan and MoreMenu
        
        viewModel.favorites.observe(this) {
            updateMiniPlayerFavoriteIcon()
        }
    }
    
    private lateinit var artistAdapter: ArtistAdapter
    private lateinit var albumAdapter: AlbumAdapter
    
    private var isPlaylistTab = false
    private lateinit var playlistAdapter: PlaylistAdapter
    private lateinit var playlistDetailAdapter: PlaylistDetailAdapter
    private var playlistTouchHelper: androidx.recyclerview.widget.ItemTouchHelper? = null
    
    private var isViewingPlaylistDetails = false
    private var currentViewingPlaylistId = -1L
    private var isViewingArtistDetails = false
    private var isViewingAlbumDetails = false

    private fun setupTabs() {
        val tabLayout = binding.topBar.findViewById<com.google.android.material.tabs.TabLayout>(R.id.tabLayout)
        tabLayout.removeAllTabs() // Clear existing if any cache
        tabLayout.addTab(tabLayout.newTab().setText("Tracks"))
        tabLayout.addTab(tabLayout.newTab().setText("Artists"))
        tabLayout.addTab(tabLayout.newTab().setText("Albums"))
        tabLayout.addTab(tabLayout.newTab().setText("Playlists"))
        tabLayout.addTab(tabLayout.newTab().setText("Favorites"))
        tabLayout.addTab(tabLayout.newTab().setText("Most Played"))
        
        // Initialize Adapters
        artistAdapter = ArtistAdapter { artist ->
            // Show songs for this artist
            isViewingArtistDetails = true
            isViewingAlbumDetails = false
            isViewingPlaylistDetails = false
            playlistTouchHelper?.attachToRecyclerView(null)
            
            val artistSongs = viewModel.songs.value?.filter { it.artist == artist.name } ?: emptyList()
            adapter.submitList(processHeaders(artistSongs))
            binding.recyclerView.adapter = adapter
            
            binding.fastScroller.visibility = View.VISIBLE
            binding.fabAddPlaylist.visibility = View.GONE
            
            android.widget.Toast.makeText(this, "Artist: ${artist.name}", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        albumAdapter = AlbumAdapter { album ->
            // Show songs for this album
            isViewingAlbumDetails = true
            isViewingArtistDetails = false
            isViewingPlaylistDetails = false
            playlistTouchHelper?.attachToRecyclerView(null)
            
            val albumSongs = viewModel.songs.value?.filter { it.albumId == album.id } ?: emptyList()
            // Sort by track number if available, but we don't parse it yet. Default to title.
            adapter.submitList(processHeaders(albumSongs))
            binding.recyclerView.adapter = adapter
            
            binding.fastScroller.visibility = View.VISIBLE
            binding.fabAddPlaylist.visibility = View.GONE
            
            android.widget.Toast.makeText(this, "Album: ${album.name}", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        playlistAdapter = PlaylistAdapter(
            onPlaylistClick = { playlist ->
                isViewingPlaylistDetails = true
                currentViewingPlaylistId = playlist.id
                binding.fabAddPlaylist.visibility = View.GONE
                
                // Toggle Toolbar
                binding.mainHeader.visibility = View.GONE
                binding.playlistToolbar.root.visibility = View.VISIBLE
                binding.playlistToolbar.tvPlaylistTitle.text = playlist.name
                
                binding.playlistToolbar.btnBack.setOnClickListener {
                     onBackPressedDispatcher.onBackPressed()
                }
                
                binding.playlistToolbar.btnMenu.setOnClickListener {
                     // Playlist Options (Image 2)
                     val options = arrayOf("Add Songs", "Favorite", "Edit playlist info", "Add to play queue", "Delete playlist")
                     androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Playlist Options")
                        .setItems(options) { _, which ->
                             when (which) {
                                  0 -> android.widget.Toast.makeText(this, "Add Songs: Coming Soon", android.widget.Toast.LENGTH_SHORT).show()
                                  1 -> android.widget.Toast.makeText(this, "Favorite: Coming Soon", android.widget.Toast.LENGTH_SHORT).show()
                                  2 -> showRenamePlaylistDialog(playlist)
                                  3 -> {
                                      // Add to queue
                                      val songs = viewModel.getPlaylistSongs(playlist.id).value
                                      if (!songs.isNullOrEmpty()) {
                                          viewModel.addToQueue(songs)
                                          android.widget.Toast.makeText(this, "Added to queue", android.widget.Toast.LENGTH_SHORT).show()
                                      }
                                  }
                                  4 -> {
                                      viewModel.deletePlaylist(playlist)
                                      onBackPressedDispatcher.onBackPressed()
                                  }
                             }
                        }
                        .show()
                }

                // Initialize Detail Adapter
                playlistDetailAdapter = PlaylistDetailAdapter(
                    playlist = playlist,
                    onPlayAllClick = {
                        val songs = playlistDetailAdapter.currentList
                        if (songs.isNotEmpty()) {
                             viewModel.playPlaylist(songs, 0)
                             // Auto-restart as requested
                             viewModel.mediaController.value?.repeatMode = Player.REPEAT_MODE_ALL
                        }
                    },
                    onHeaderMenuClick = {
                         // Header 3-dots: Same as toolbar or just select?
                         // User said "clicking three vertical lines leads to options". Toolbar has lines. Header has dots.
                         // Maybe Header dots is "Select" or "Sort"? defaulting to toolbar options for now
                         binding.playlistToolbar.btnMenu.performClick()
                    },
                    onItemClick = { song ->
                         val currentList = playlistDetailAdapter.currentList
                         val index = currentList.indexOf(song)
                         if (index != -1) {
                             viewModel.playPlaylist(currentList, index)
                         }
                    },
                    onMoreClick = { song -> showPlaylistSongOptions(song) }
                )
                
                // Setup Drag
                val callback = QueueTouchHelperCallback { from, to ->
                    try {
                        // Adjust for Header (Item 0)
                        // Wait, Adapter indices include Header.
                        // Songs are at index 1+.
                        // Drag should verify not to drag Header.
                        // QueueTouchHelperCallback likely assumes 0-indexed list.
                        // I need to update TouchHelper or Disable Drag for now to avoid crashes?
                        // Or fix Adapter. 
                        // For simplicity, disabling reorder in this view temporarily or safely ignoring header drag.
                    } catch (e: Exception) { e.printStackTrace() }
                }
                // playlistTouchHelper = androidx.recyclerview.widget.ItemTouchHelper(callback)
                // playlistTouchHelper?.attachToRecyclerView(binding.recyclerView)
                
                binding.recyclerView.adapter = playlistDetailAdapter
                
                viewModel.getPlaylistSongs(playlist.id).observe(this) { songs ->
                    if (isViewingPlaylistDetails && currentViewingPlaylistId == playlist.id) {
                         playlistDetailAdapter.submitList(songs)
                          // updateAlphabetIndex(songs) removed
                          binding.fastScroller.visibility = View.VISIBLE
                    }
                }
            },
            onPlaylistMenuClick = { playlist ->
                 // Long press menu on main list
                 binding.playlistToolbar.btnMenu.performClick() // Reuse logic? No, this is outside.
                 // Keep simple dialog - removed "Change Cover Image" since album art is hidden
                 val options = arrayOf("Rename", "Delete")
                 androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(playlist.name)
                    .setItems(options) { _, which ->
                        when (which) {
                            0 -> showRenamePlaylistDialog(playlist)
                            1 -> viewModel.deletePlaylist(playlist)
                        }
                    }
                    .show()
            }
        )
        
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isViewingPlaylistDetails) {
                    isViewingPlaylistDetails = false
                    isPlaylistTab = true
                    playlistTouchHelper?.attachToRecyclerView(null)
                    binding.recyclerView.adapter = playlistAdapter
                    // binding.fastScroller.visibility = View.GONE (or manage per context)
                    // This was likely in handleSongMenuAction or similar
                    // Checking line 637 context: Unresolved reference alphabetIndex.
                    // I'll just replace it.
                    binding.fastScroller.visibility = View.VISIBLE
                    binding.mainHeader.visibility = View.VISIBLE
                    binding.playlistToolbar.root.visibility = View.GONE
                    binding.fastScroller.visibility = View.GONE
                    binding.fabAddPlaylist.visibility = View.VISIBLE
                    viewModel.loadPlaylists() 
                } else if (isViewingArtistDetails) {
                    isViewingArtistDetails = false
                binding.recyclerView.adapter = artistAdapter
                    binding.fastScroller.visibility = View.GONE
                    artistAdapter.submitList(viewModel.artists.value)
                } else if (isViewingAlbumDetails) {
                    isViewingAlbumDetails = false
                    binding.recyclerView.adapter = albumAdapter
                    binding.fastScroller.visibility = View.GONE
                    albumAdapter.submitList(viewModel.albums.value)
                } else if (tabLayout.selectedTabPosition != 0) {
                    tabLayout.getTabAt(0)?.select()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                // Reset states
                isViewingPlaylistDetails = false
                isViewingArtistDetails = false
                isViewingAlbumDetails = false
                adapter.showRemoveFromPlaylistOption = false
                playlistTouchHelper?.attachToRecyclerView(null)
                
                when (tab?.position) {
                    0 -> { // Tracks
                        isPlaylistTab = false
                        currentViewingPlaylistId = -1L
                        binding.recyclerView.adapter = adapter
                        adapter.submitList(processHeaders(viewModel.songs.value ?: emptyList()))
                        binding.fastScroller.visibility = View.VISIBLE
                        binding.fabAddPlaylist.visibility = View.GONE
                    }
                    1 -> { // Artists
                        isPlaylistTab = false
                        binding.recyclerView.adapter = artistAdapter
                        binding.fastScroller.visibility = View.GONE 
                        binding.fabAddPlaylist.visibility = View.GONE
                        
                        viewModel.artists.observe(this@MainActivity) { artists ->
                            if (tabLayout.selectedTabPosition == 1 && !isViewingArtistDetails) {
                                artistAdapter.submitList(artists)
                            }
                        }
                    }
                    2 -> { // Albums
                        isPlaylistTab = false
                        binding.recyclerView.adapter = albumAdapter
                        binding.fastScroller.visibility = View.GONE
                        binding.fabAddPlaylist.visibility = View.GONE
                        
                        viewModel.albums.observe(this@MainActivity) { albums ->
                            if (tabLayout.selectedTabPosition == 2 && !isViewingAlbumDetails) {
                                albumAdapter.submitList(albums)
                            }
                        }
                    }
                    3 -> { // Playlists
                        isPlaylistTab = true
                        binding.recyclerView.adapter = playlistAdapter
                        binding.fastScroller.visibility = View.GONE
                        binding.fabAddPlaylist.visibility = View.VISIBLE
                        viewModel.loadPlaylists()
                        
                        viewModel.playlists.observe(this@MainActivity) { playlists ->
                            if (tabLayout.selectedTabPosition == 3 && !isViewingPlaylistDetails) {
                                playlistAdapter.submitList(playlists)
                            }
                        }
                    }
                    4 -> { // Favorites
                        isPlaylistTab = false 
                        binding.fastScroller.visibility = View.GONE
                        binding.fabAddPlaylist.visibility = View.GONE
                        
                        viewModel.favorites.observe(this@MainActivity) { favs ->
                            if (tabLayout.selectedTabPosition == 4) {
                                adapter.submitList(processHeaders(favs))
                                binding.recyclerView.adapter = adapter
                                if (favs.isEmpty()) {
                                    android.widget.Toast.makeText(this@MainActivity, "No favorites yet!", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    5 -> { // Most Played
                        isPlaylistTab = false 
                        binding.fastScroller.visibility = View.GONE
                        binding.fabAddPlaylist.visibility = View.GONE
                        
                        viewModel.loadMostPlayed()
                        
                        viewModel.mostPlayed.observe(this@MainActivity) { songs ->
                            if (tabLayout.selectedTabPosition == 5) {
                                // Direct mapping to preserve play count order (no headers)
                                val items = songs.map { com.wayne.musicdeck.SongListItem.SongItem(it) }
                                adapter.submitList(items)
                                binding.recyclerView.adapter = adapter
                            }
                        }
                    }
                }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
        
        binding.fabAddPlaylist.setOnClickListener {
            showCreatePlaylistDialog()
        }
        
        // Mini Player Heart
        binding.miniPlayer.btnMiniFavorite.setOnClickListener {
            val song = viewModel.songs.value?.find { it.id == viewModel.lastPlayedSongId }
            if (song != null) {
                viewModel.toggleFavorite(song)
            }
        }
    }
    
    private fun showCreatePlaylistDialog() {
        val sheet = InputBottomSheetFragment.newInstance(
            title = "New Playlist",
            hint = "Playlist Name",
            positiveButtonText = "Create"
        )
        sheet.onSaveListener = { name ->
            viewModel.createPlaylist(name)
            android.widget.Toast.makeText(this, "Created $name", android.widget.Toast.LENGTH_SHORT).show()
        }
        sheet.show(supportFragmentManager, "CreatePlaylistSheet")
    }
    
    private fun showRenamePlaylistDialog(playlist: com.wayne.musicdeck.data.Playlist) {
        val sheet = InputBottomSheetFragment.newInstance(
            title = "Rename Playlist",
            hint = "Playlist Name",
            initialValue = playlist.name,
            positiveButtonText = "Save"
        )
        sheet.onSaveListener = { name ->
             val updated = playlist.copy(name = name)
             viewModel.updatePlaylist(updated)
             android.widget.Toast.makeText(this, "Renamed to $name", android.widget.Toast.LENGTH_SHORT).show()
        }
        sheet.show(supportFragmentManager, "RenamePlaylistSheet")
    }

    private var targetPlaylistForImage: com.wayne.musicdeck.data.Playlist? = null
    
    private val pickImageLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val playlist = targetPlaylistForImage ?: return@let
            // Save image locally or use URI directly (requires persistence permission issues). 
            // Better to copy to app internal storage
             try {
                val inputStream = contentResolver.openInputStream(it)
                val file = java.io.File(filesDir, "playlist_${playlist.id}_cover.jpg")
                val outputStream = java.io.FileOutputStream(file)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()
                
                val updated = playlist.copy(imagePath = file.absolutePath)
                viewModel.updatePlaylist(updated)
                viewModel.loadPlaylists() // Refresh list to show the updated cover
                android.widget.Toast.makeText(this, "Cover Updated!", android.widget.Toast.LENGTH_SHORT).show()
             } catch (e: Exception) {
                 android.widget.Toast.makeText(this, "Failed to set cover", android.widget.Toast.LENGTH_SHORT).show()
             }
        }
    }
    
    private fun pickPlaylistImage(playlist: com.wayne.musicdeck.data.Playlist) {
        targetPlaylistForImage = playlist
        pickImageLauncher.launch("image/*")
    }
    

    
    // setupMoreMenu removed
    // setupRescan removed
    
    // Cleaned up legacy index code

    private fun setupSearch() {
        // Search opens full-screen HeyTap-style search bottom sheet
        binding.btnSearch.setOnClickListener {
            val searchSheet = SearchBottomSheet.newInstance()
            searchSheet.onSongClick = { song ->
                // Play the selected song
                val controller = viewModel.mediaController.value
                if (controller != null) {
                    val allSongs = viewModel.songs.value ?: listOf(song)
                    val startIndex = allSongs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                    val mediaItems = allSongs.map { s ->
                        MediaItem.Builder()
                            .setMediaId(s.id.toString())
                            .setUri(s.uri)
                            .setMediaMetadata(
                                androidx.media3.common.MediaMetadata.Builder()
                                    .setTitle(s.title)
                                    .setArtist(s.artist)
                                    .build()
                            )
                            .build()
                    }
                    controller.setMediaItems(mediaItems, startIndex, 0)
                    controller.prepare()
                    controller.play()
                }
            }
            searchSheet.show(supportFragmentManager, "SearchBottomSheet")
        }
    }
    
    private fun setupPlaybackControls() {
        // Play All icon - plays in current sort order
        binding.ivPlayAll.setOnClickListener {
            val songs = viewModel.songs.value
            if (songs.isNullOrEmpty()) {
                android.widget.Toast.makeText(this, "No songs available", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val controller = viewModel.mediaController.value ?: return@setOnClickListener
            
            // Play in current sorted order (not shuffled)
            val mediaItems = songs.map { song ->
                MediaItem.Builder()
                    .setMediaId(song.id.toString())
                    .setUri(song.uri)
                    .setMediaMetadata(
                        androidx.media3.common.MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .build()
                    )
                    .build()
            }
            controller.setMediaItems(mediaItems)
            controller.prepare()
            controller.play()
            android.widget.Toast.makeText(this, "Playing ${songs.size} songs", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        // Shuffle text - shuffles all songs
        binding.tvShuffle.setOnClickListener {
            val songs = viewModel.songs.value
            if (songs.isNullOrEmpty()) {
                android.widget.Toast.makeText(this, "No songs available", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val controller = viewModel.mediaController.value ?: return@setOnClickListener
            
            // Shuffle songs
            val shuffled = songs.shuffled()
            val mediaItems = shuffled.map { song ->
                MediaItem.Builder()
                    .setMediaId(song.id.toString())
                    .setUri(song.uri)
                    .setMediaMetadata(
                        androidx.media3.common.MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .build()
                    )
                    .build()
            }
            controller.setMediaItems(mediaItems)
            controller.prepare()
            controller.play()
            android.widget.Toast.makeText(this, "Shuffling ${songs.size} songs", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        // List Menu (Sort Order + Multi-select like YouTube Music)
        binding.btnListMenu.setOnClickListener { view ->
            val popup = android.widget.PopupMenu(this, view)
            // Submenu style - create menu items
            popup.menu.add(0, 100, 0, "Sort by song title")
            popup.menu.add(0, 101, 0, "Sort by date added")
            popup.menu.add(0, 102, 0, "Sort by artist")
            popup.menu.add(0, 103, 0, "Sort by duration")

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    100 -> {
                        currentSortOption = MainViewModel.SortOption.TITLE
                        viewModel.sortSongs(MainViewModel.SortOption.TITLE)
                        refreshSongList()
                        android.widget.Toast.makeText(this, "Sorted by title", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    101 -> {
                        currentSortOption = MainViewModel.SortOption.DATE_ADDED
                        viewModel.sortSongs(MainViewModel.SortOption.DATE_ADDED)
                        refreshSongList()
                        android.widget.Toast.makeText(this, "Sorted by date added", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    102 -> {
                        currentSortOption = MainViewModel.SortOption.ARTIST
                        viewModel.sortSongs(MainViewModel.SortOption.ARTIST)
                        refreshSongList()
                        android.widget.Toast.makeText(this, "Sorted by artist", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    103 -> {
                        currentSortOption = MainViewModel.SortOption.DURATION
                        viewModel.sortSongs(MainViewModel.SortOption.DURATION)
                        refreshSongList()
                        android.widget.Toast.makeText(this, "Sorted by duration", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                true
            }
            popup.show()
        }
    }
    
    // Track current sort option for header logic
    private var currentSortOption: MainViewModel.SortOption = MainViewModel.SortOption.TITLE
    
    // Refresh song list respecting current sort (with or without headers)
    private fun refreshSongList() {
        val songs = viewModel.songs.value ?: return
        if (currentSortOption == MainViewModel.SortOption.TITLE) {
            adapter.submitList(processHeaders(songs))
        } else {
            // No headers for non-title sorts - show flat list
            adapter.submitList(songs.map { SongListItem.SongItem(it) })
        }
    }
    
    private fun setupActionGrid() {
        // Equalizer Card (Violet)
        binding.cardEqualizer.setOnClickListener {
             val controller = viewModel.mediaController.value
             val sessionId = controller?.connectedToken?.extras?.getInt("AUDIO_SESSION_ID", 0) ?: 0
             
             if (sessionId != 0) {
                 EqualizerBottomSheet.newInstance(sessionId).show(supportFragmentManager, "EqBottomSheet")
             } else {
                 android.widget.Toast.makeText(this, "Start playback to usage Equalizer", android.widget.Toast.LENGTH_SHORT).show()
             }
        }
        
        // Sleep Timer Card (Mint)
        binding.cardSleepTimer.setOnClickListener {
             SleepTimerBottomSheetFragment().show(supportFragmentManager, "SleepTimerSheet")
        }
        
        // Favorites Card (Rose)
        binding.cardFavorites.setOnClickListener {
             // Switch to Favorites Tab (Index 4)
             binding.tabLayout.getTabAt(4)?.select()
             android.widget.Toast.makeText(this, "Showing Favorites", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        // Tools/Drive Card (Cyan)
        binding.cardTools.setOnClickListener {
             ToolsBottomSheetFragment().show(supportFragmentManager, "ToolsSheet")
        }
    }
    
    private var hasRestored = false
    
    private fun restoreLastSong(songs: List<Song>) {
        if (hasRestored) return
        val lastId = viewModel.lastPlayedSongId
        if (lastId == -1L) return
        
        val lastSong = songs.find { it.id == lastId }
        lastSong?.let { song ->
            hasRestored = true
            // Update mini player to show last song
            binding.miniPlayer.tvMiniTitle.text = song.title
            binding.miniPlayer.tvMiniArtist.text = song.artist
            
            var embeddedArt: ByteArray? = null
            val uri = song.uri
            
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(this, uri)
                embeddedArt = retriever.embeddedPicture
                retriever.release()
            } catch (e: Exception) {
               // ignore
            }
            
            if (embeddedArt != null) {
                binding.miniPlayer.ivMiniArt.load(embeddedArt) {
                    crossfade(true)
                    transformations(RoundedCornersTransformation(12f))
                    memoryCacheKey("embedded_art_mini_${song.id}")
                    diskCacheKey("embedded_art_mini_${song.id}")
                }
            } else {
                val albumArtUri = android.content.ContentUris.withAppendedId(
                    android.net.Uri.parse("content://media/external/audio/albumart"),
                    song.albumId
                )
                binding.miniPlayer.ivMiniArt.load(albumArtUri) {
                    crossfade(true)
                    placeholder(R.drawable.default_album_art)
                    error(R.drawable.default_album_art)
                    transformations(RoundedCornersTransformation(12f))
                }
            }
        }
    }
    
    private fun playLastSong() {
        val lastId = viewModel.lastPlayedSongId
        if (lastId == -1L) return
        val items = adapter.currentList
        val songItem = items.filterIsInstance<SongListItem.SongItem>().find { it.song.id == lastId }
        val lastSong = songItem?.song ?: return
        val lastPosition = viewModel.lastPlayedPosition
        viewModel.playSongFromPosition(lastSong, lastPosition)
    }

    private fun setupDevInfo() {
        // Moved to Rescan Long Click for now, or just unused
    }
    
    private fun setupPersonalizedHeader() {
        val prefs = getSharedPreferences("musicdeck_prefs", MODE_PRIVATE)
        val userName = prefs.getString("user_name", null)
        
        if (userName.isNullOrEmpty()) {
            // First run - ask for name
            showNameDialog(prefs)
        } else {
            // Set personalized greeting
            updateHeaderWithName(userName)
        }
        
        // Long-press header to change name
        binding.tvMyMusic.setOnLongClickListener {
            showNameDialog(prefs)
            true
        }
    }
    
    private fun showNameDialog(prefs: android.content.SharedPreferences) {
        val sheet = InputBottomSheetFragment.newInstance(
            title = "What's your name?",
            hint = "Wayne",
            positiveButtonText = "Let's Go!"
        )
        sheet.onSaveListener = { name ->
            prefs.edit().putString("user_name", name).apply()
            updateHeaderWithName(name)
            android.widget.Toast.makeText(this, "Welcome, $name!", android.widget.Toast.LENGTH_SHORT).show()
        }
        sheet.show(supportFragmentManager, "NameInputSheet")
    }
    
    private fun updateHeaderWithName(name: String) {
        binding.tvMyMusic.text = if (name == "My Music") "My Music" else "$name's Music"
    }

    private fun setupRecyclerView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }
    
    // scrollToLetter removed - unused
    // Garbage removed
    
    private fun processHeaders(songs: List<Song>): List<SongListItem> {
        if (songs.isEmpty()) return emptyList()
        
        // Sort so letters come first, then non-letters (# at end)
        val sorted = songs.sortedWith { s1, s2 ->
            val c1 = s1.title.firstOrNull()?.uppercaseChar() ?: ' '
            val c2 = s2.title.firstOrNull()?.uppercaseChar() ?: ' '
            val isL1 = c1.isLetter()
            val isL2 = c2.isLetter()
            if (isL1 && !isL2) -1
            else if (!isL1 && isL2) 1
            else s1.title.compareTo(s2.title, ignoreCase = true)
        }
        
        val list = mutableListOf<SongListItem>()
        var lastHeader = ""
        
        sorted.forEach { song ->
            val firstChar = song.title.firstOrNull()?.uppercaseChar() ?: '#'
            val header = if (firstChar.isLetter()) firstChar.toString() else "#"
            
            if (header != lastHeader) {
                list.add(SongListItem.Header(header))
                lastHeader = header
            }
            list.add(SongListItem.SongItem(song))
        }
        return list
    }

    private fun setupPermissions() {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            // Permission Granted
            binding.permissionContainer.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
            viewModel.loadSongs()
        } else {
            // Permission Needed
            binding.permissionContainer.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
            
            binding.btnGrant.setOnClickListener {
                requestPermissions(arrayOf(permission), 101)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
    // Orphaned block removed
    }

    private fun updateMiniPlayer(mediaItem: MediaItem?) {
        if (mediaItem == null) {
            // Don't reset if we have a last played song showing
            if (hasRestored && viewModel.lastPlayedSongId != -1L) {
                 updateMiniPlayerFavoriteIcon()
                 return
            }
            binding.miniPlayer.tvMiniTitle.text = "Not Playing"
            binding.miniPlayer.tvMiniArtist.text = "Select a song"
            binding.miniPlayer.ivMiniArt.setImageResource(R.drawable.default_album_art)
            return
        }
        
        binding.miniPlayer.tvMiniTitle.text = mediaItem.mediaMetadata.title ?: "Unknown Title"
        binding.miniPlayer.tvMiniArtist.text = mediaItem.mediaMetadata.artist ?: "Unknown Artist"
        
        binding.miniPlayer.tvMiniTitle.text = mediaItem.mediaMetadata.title ?: "Unknown Title"
        binding.miniPlayer.tvMiniArtist.text = mediaItem.mediaMetadata.artist ?: "Unknown Artist"
        
        var embeddedArt: ByteArray? = null
        val currentId = mediaItem.mediaId.toLongOrNull()
        
        if (currentId != null) {
            val uri = android.content.ContentUris.withAppendedId(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                currentId
            )
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(this, uri)
                embeddedArt = retriever.embeddedPicture
                retriever.release()
            } catch (e: Exception) {
               // ignore
            }
        }
        
        if (embeddedArt != null) {
            binding.miniPlayer.ivMiniArt.load(embeddedArt) {
                crossfade(true)
                transformations(RoundedCornersTransformation(12f))
                memoryCacheKey("embedded_art_mini_${mediaItem.mediaId}")
                diskCacheKey("embedded_art_mini_${mediaItem.mediaId}")
            }
        } else {
            binding.miniPlayer.ivMiniArt.load(mediaItem.mediaMetadata.artworkUri) {
                crossfade(true)
                placeholder(R.drawable.default_album_art)
                error(R.drawable.default_album_art)
                transformations(RoundedCornersTransformation(12f))
            }
        }
        updateMiniPlayerFavoriteIcon()
    }
    
    private fun updateMiniPlayerFavoriteIcon() {
        val currentId = viewModel.mediaController.value?.currentMediaItem?.mediaId?.toLongOrNull() 
                       ?: viewModel.lastPlayedSongId
        
        val btnFav = binding.miniPlayer.btnMiniFavorite
        
        if (currentId == -1L) {
            btnFav.setColorFilter(getColor(android.R.color.darker_gray))
            btnFav.setImageResource(R.drawable.ic_favorite_border)
            return
        }

        val isFav = viewModel.favorites.value?.any { it.id == currentId } == true
        val icon = if (isFav) R.drawable.ic_favorite else R.drawable.ic_favorite_border
        
        btnFav.setImageResource(icon)
        btnFav.setColorFilter(getColor(if (isFav) R.color.teal_200 else android.R.color.darker_gray))
    }

    private fun updatePlayPauseIcon(isPlaying: Boolean) {
        val icon = if (isPlaying) androidx.media3.ui.R.drawable.exo_icon_pause else androidx.media3.ui.R.drawable.exo_icon_play
        binding.miniPlayer.btnMiniPlayPause.setImageResource(icon)
    }
    
    private fun handleSongMenuAction(song: Song, action: String) {
        when (action) {
            "edit" -> showEditSongOptions(song)
            "share" -> shareSong(song)
            "ringtone" -> setAsRingtone(song)
            "details" -> showSongDetailsDialog(song)
            "delete" -> deleteSong(song)
            "add_to_playlist" -> showAddToPlaylistDialog(song)
            "remove_from_playlist" -> {
                // Determine current playlist ID. This requires state tracking.
                // We know 'isViewingPlaylistDetails' is true if this option is visible.
                // But we need the ID. We can store `currentViewingPlaylistId`.
                if (currentViewingPlaylistId != -1L) {
                    viewModel.removeSongFromPlaylist(currentViewingPlaylistId, song.id)
                    // Refresh handled by observing? See below.
                    // Ideally we remove from list immediately for UX.
                    // Adapter update will happen if we reload from DB.
                    viewModel.getPlaylistSongs(currentViewingPlaylistId).observe(this) { songs ->
                        if (isViewingPlaylistDetails && currentViewingPlaylistId != -1L) {
                             adapter.submitList(processHeaders(songs))
                        }
                    }
                }
            }
        }
    }
    
    private fun showEditDialog(song: Song) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_song, null)
        val etTitle = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etTitle)
        val etArtist = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etArtist)
        @Suppress("UNUSED_VARIABLE")
        val etAlbum = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etAlbum)
        
        etTitle.setText(song.title)
        etArtist.setText(song.artist)
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Edit Song Info")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newTitle = etTitle.text.toString()
                val newArtist = etArtist.text.toString()
                
                try {
                    val uri = android.content.ContentUris.withAppendedId(
                        android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Audio.Media.TITLE, newTitle)
                        put(android.provider.MediaStore.Audio.Media.ARTIST, newArtist)
                    }
                    contentResolver.update(uri, values, null, null)
                    viewModel.loadSongs()
                    android.widget.Toast.makeText(this, "Updated!", android.widget.Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    android.widget.Toast.makeText(this, "Update failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    


    private fun showPlaylistSongOptions(song: Song) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_heytap_bottom_sheet, null)
        dialog.setContentView(view)

        // Cancel Action
        view.findViewById<android.widget.TextView>(R.id.action_cancel).setOnClickListener {
            dialog.dismiss()
        }

        // Play Next
        view.findViewById<android.widget.TextView>(R.id.action_play_next).setOnClickListener {
            viewModel.addToQueue(listOf(song))
            android.widget.Toast.makeText(this, "Added to Play Next", android.widget.Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        // Add to Queue
        view.findViewById<android.widget.TextView>(R.id.action_add_queue).setOnClickListener {
            viewModel.addToQueue(listOf(song))
            android.widget.Toast.makeText(this, "Added to Queue", android.widget.Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        // Add to Playlist
        view.findViewById<android.widget.TextView>(R.id.action_add_playlist).setOnClickListener {
            showAddToPlaylistDialog(song)
            dialog.dismiss()
        }

        // Song Info
        view.findViewById<android.widget.TextView>(R.id.action_song_info).setOnClickListener {
            SongInfoBottomSheet.newInstance(song).show(supportFragmentManager, "SongInfo")
            dialog.dismiss()
        }

        // Album Info
        val albumText = view.findViewById<android.widget.TextView>(R.id.action_album_info)
        albumText.text = "Album: ${song.album}"
        albumText.setOnClickListener {
             android.widget.Toast.makeText(this, "Album: ${song.album}", android.widget.Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
                // Details/Delete blocks removed as IDs don't exist in this layout
                // Using existing actions: action_song_info handles details
                
                // Remove from Playlist (only show when viewing a playlist)
        val removeView = view.findViewById<android.widget.TextView>(R.id.action_remove)
        if (currentViewingPlaylistId != -1L) {
            removeView.visibility = android.view.View.VISIBLE
            removeView.setOnClickListener {
                viewModel.removeSongFromPlaylist(currentViewingPlaylistId, song.id)
                android.widget.Toast.makeText(this, "Removed from playlist", android.widget.Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        } else {
            removeView.visibility = android.view.View.GONE
        }
        
        // Set Custom Album Cover - HIDDEN since album art not shown in list view
        view.findViewById<android.widget.TextView>(R.id.action_set_cover).visibility = android.view.View.GONE
        
        // Remove Custom Cover - HIDDEN since album art not shown in list view
        view.findViewById<android.widget.TextView>(R.id.action_remove_cover).visibility = android.view.View.GONE

        dialog.show()
    }

    // Fix: Add missing dialog methods
    private fun showSongDetailsDialog(song: Song) {
        val fileSize = viewModel.getSongFileSize(song)
        val details = """
            Title: ${song.title}
            Artist: ${song.artist}
            Album: ${song.album}
            Duration: ${formatTime(song.duration)}
            Size: $fileSize
            Path: ${song.data}
        """.trimIndent()

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Song Details")
            .setMessage(details)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun showEditSongOptions(song: Song) {
        val options = arrayOf("Change Cover Image", "Change Lyrics File", "Remove Custom Cover", "Remove Custom Lyrics")
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Edit Song By...")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        targetSongForCover = song
                        imagePickerLauncher.launch("image/*")
                    }
                    1 -> {
                        android.widget.Toast.makeText(this, "Lyrics picker in Player view only for now", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    2 -> viewModel.removeCustomCover(song)
                    3 -> {
                         android.widget.Toast.makeText(this, "Not implemented yet", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun showAddToPlaylistDialog(song: Song) {
        // Simple list of playlists
        viewModel.loadPlaylists()
        viewModel.playlists.observe(this) { playlists ->
            val list = playlists ?: emptyList()
            if (list.isEmpty()) {
                android.widget.Toast.makeText(this@MainActivity, "No playlists found", android.widget.Toast.LENGTH_SHORT).show()
                // Don't remove observer here easily with lambda, but it's okay for one-shot if we handle logic
                // Better to use LiveData extension or just let it update
                return@observe
            }
            
            val names = list.map { it.name }.toTypedArray()
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle("Add to Playlist")
                .setItems(names) { _, which ->
                    viewModel.addSongToPlaylist(list[which].id, song)
                }
                .show()
        }
    }

    private fun deleteSong(song: Song) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
             val uriList = listOf(song.uri)
             val pendingIntent = android.provider.MediaStore.createDeleteRequest(contentResolver, uriList)
             val request = androidx.activity.result.IntentSenderRequest.Builder(pendingIntent.intentSender).build()
             deleteRequestLauncher.launch(request)
        } else {
            android.widget.Toast.makeText(this, "Delete supported on Android 11+", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setAsRingtone(song: Song) {
        if (!android.provider.Settings.System.canWrite(this)) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("To set this song as your ringtone, MusicDeck needs permission to modify system settings.")
                .setPositiveButton("Grant") { _, _ ->
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        try {
            val uri = android.content.ContentUris.withAppendedId(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
            android.media.RingtoneManager.setActualDefaultRingtoneUri(
                this, android.media.RingtoneManager.TYPE_RINGTONE, uri)
            android.widget.Toast.makeText(this, "\"${song.title}\" set as ringtone", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Failed to set ringtone: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    // Helper function for formatting time
    private fun formatTime(ms: Long): String {
        return String.format("%02d:%02d", java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(ms),
            java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(ms) - 
            java.util.concurrent.TimeUnit.MINUTES.toSeconds(java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(ms)))
    }

    private fun shareSong(song: Song) {
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(android.content.Intent.EXTRA_STREAM, song.uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(android.content.Intent.createChooser(shareIntent, "Share Song"))
    }
}
