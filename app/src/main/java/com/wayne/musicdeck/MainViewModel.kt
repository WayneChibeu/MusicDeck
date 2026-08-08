package com.wayne.musicdeck

import android.app.Application
import android.content.ComponentName
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import androidx.lifecycle.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.wayne.musicdeck.utils.SettingsManager
import com.wayne.musicdeck.data.*

class MainViewModel(
    private val application: Application,
    private val settingsManager: SettingsManager,
    private val playlistRepository: PlaylistRepository,
    private val playCountDao: PlayCountDao,
    private val customMetadataDao: CustomMetadataDao,
    private val hiddenSongDao: HiddenSongDao,
    private val customCoverRepository: CustomCoverRepository,
    private val lyricsRepository: LyricsRepository
) : androidx.lifecycle.ViewModel() {

    private val _songs = MutableLiveData<List<Song>>()
    val songs: LiveData<List<Song>> = _songs

    // MediaController Future
    private var controllerFuture: ListenableFuture<MediaController>? = null
    val mediaController = MutableLiveData<MediaController?>()
    
    // Persistent state
    var lastPlayedSongPath: String?
        get() = settingsManager.lastPlayedSongPath
        set(value) { settingsManager.lastPlayedSongPath = value }
    var lastPlayedPosition: Long
        get() = settingsManager.lastPlayedPosition
        set(value) { settingsManager.lastPlayedPosition = value }
        
    // Pending Delete State (survives Activity recreation)
    var pendingDeleteSongId: Long? = null

    fun saveSearchQuery(query: String) {
        val history = getSearchHistory().toMutableList()
        if (history.contains(query)) history.remove(query)
        history.add(0, query) // Add to top
        if (history.size > 10) history.removeAt(history.lastIndex) // Limit to 10
        settingsManager.saveSearchQuery(history)
    }
    
    fun getSearchHistory(): List<String> {
        return settingsManager.getSearchHistory()
    }
    
    fun clearSearchHistory() {
        settingsManager.clearSearchHistory()
    }

    // Dependencies are now injected via Koin constructor injection
    
    // Hidden Songs
    private val _hiddenSongs = MutableLiveData<List<Song>>()
    val hiddenSongs: LiveData<List<Song>> = _hiddenSongs
    
    val playlists = MutableLiveData<List<com.wayne.musicdeck.data.Playlist>>()
    
    private val _favorites = androidx.lifecycle.MediatorLiveData<List<Song>>()
    val favorites: LiveData<List<Song>> = _favorites
    private var favoritesLiveDataSource: LiveData<List<com.wayne.musicdeck.data.PlaylistSong>>? = null
    
    private val _artists = MutableLiveData<List<Artist>>()
    val artists: LiveData<List<Artist>> = _artists
    
    private val _albums = MutableLiveData<List<Album>>()
    val albums: LiveData<List<Album>> = _albums
    
    private val _mostPlayed = MutableLiveData<List<Song>>()
    val mostPlayed: LiveData<List<Song>> = _mostPlayed
    
    private var favoritesPlaylistId: Long = -1

    private val contentObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            loadSongs()
        }
    }

    init {
        // Only load if permissions are likely granted (checked in Activity, but safe guard here)
        val hasPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(application, android.Manifest.permission.READ_MEDIA_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(application, android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (hasPermission) {
            loadSongs()
            try {
                application.contentResolver.registerContentObserver(
                    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    true,
                    contentObserver
                )
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to register observer: ${e.message}")
            }
        }
        
        viewModelScope.launch {
            try {
                val favPlaylist = playlistRepository.getOrCreateFavoritesPlaylist()
                favoritesPlaylistId = favPlaylist.id
                setupFavoritesObserver()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Favorites setup failed: ${e.message}")
            }
        }
    }
    
    private fun setupFavoritesObserver() {
        if (favoritesPlaylistId == -1L) return
        
        // Remove previous source if any
        favoritesLiveDataSource?.let { _favorites.removeSource(it) }
        
        // Observe the favorites playlist from database reactively
        val newSource = playlistRepository.getSongsForPlaylistLive(favoritesPlaylistId)
        favoritesLiveDataSource = newSource
        
        _favorites.addSource(newSource) { playlistSongs ->
            // Convert PlaylistSong IDs to actual Song objects
            val allSongs = originalSongs.ifEmpty { _songs.value ?: emptyList() }
            val favs = playlistSongs.mapNotNull { ps -> allSongs.find { it.id == ps.songId } }
            _favorites.value = favs
        }
    }
    
    // Keep this for manual refresh when songs list loads
    private fun refreshFavoritesList() {
        favoritesLiveDataSource?.value?.let { playlistSongs ->
            val allSongs = originalSongs.ifEmpty { _songs.value ?: emptyList() }
            val favs = playlistSongs.mapNotNull { ps -> allSongs.find { it.id == ps.songId } }
            _favorites.value = favs
        }
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            if (favoritesPlaylistId == -1L) return@launch
            
            val currentFavs = _favorites.value ?: emptyList()
            val isFav = currentFavs.any { it.data == song.data }
            
            if (isFav) {
                playlistRepository.removeSongFromPlaylist(favoritesPlaylistId, song.data)
            } else {
                playlistRepository.addSongToPlaylist(favoritesPlaylistId, song.id, song.data)
            }
        }
    }
    
    // CRUD functions for Playlists
    fun loadPlaylists() {
        viewModelScope.launch {
            // Filter out the special "Favorites" playlist from the list
            val allPlaylists = playlistRepository.getAllPlaylists()
            val filteredPlaylists = allPlaylists.filter { it.name != "Favorites" }.toMutableList()
            
            // INJECT SMART PLAYLISTS
            if (settingsManager.isSmartPlaylistsEnabled) {
                filteredPlaylists.add(0, com.wayne.musicdeck.data.Playlist(com.wayne.musicdeck.data.SmartPlaylistManager.ID_ENERGY_BOOST, com.wayne.musicdeck.data.SmartPlaylistManager.getSmartPlaylistName(com.wayne.musicdeck.data.SmartPlaylistManager.ID_ENERGY_BOOST), null, System.currentTimeMillis()))
                filteredPlaylists.add(0, com.wayne.musicdeck.data.Playlist(com.wayne.musicdeck.data.SmartPlaylistManager.ID_CHILL_MODE, com.wayne.musicdeck.data.SmartPlaylistManager.getSmartPlaylistName(com.wayne.musicdeck.data.SmartPlaylistManager.ID_CHILL_MODE), null, System.currentTimeMillis()))
                filteredPlaylists.add(0, com.wayne.musicdeck.data.Playlist(com.wayne.musicdeck.data.SmartPlaylistManager.ID_RECENTLY_ADDED, com.wayne.musicdeck.data.SmartPlaylistManager.getSmartPlaylistName(com.wayne.musicdeck.data.SmartPlaylistManager.ID_RECENTLY_ADDED), null, System.currentTimeMillis()))
                filteredPlaylists.add(1, com.wayne.musicdeck.data.Playlist(com.wayne.musicdeck.data.SmartPlaylistManager.ID_HEAVY_ROTATION, com.wayne.musicdeck.data.SmartPlaylistManager.getSmartPlaylistName(com.wayne.musicdeck.data.SmartPlaylistManager.ID_HEAVY_ROTATION), null, System.currentTimeMillis()))
                filteredPlaylists.add(2, com.wayne.musicdeck.data.Playlist(com.wayne.musicdeck.data.SmartPlaylistManager.ID_FORGOTTEN_GEMS, com.wayne.musicdeck.data.SmartPlaylistManager.getSmartPlaylistName(com.wayne.musicdeck.data.SmartPlaylistManager.ID_FORGOTTEN_GEMS), null, System.currentTimeMillis()))
            }

            playlists.postValue(filteredPlaylists)
        }
    }
    
    fun updatePlaylist(playlist: com.wayne.musicdeck.data.Playlist) {
        viewModelScope.launch {
            playlistRepository.updatePlaylist(playlist)
            loadPlaylists()
        }
    }
    
    fun createPlaylist(name: String) {
        viewModelScope.launch {
            playlistRepository.createPlaylist(name)
            loadPlaylists()
        }
    }
    
    fun addSongToPlaylist(playlistId: Long, song: Song) {
        viewModelScope.launch {
            playlistRepository.addSongToPlaylist(playlistId, song.id, song.data)
            loadPlaylists()
        }
    }
    
    
    fun getPlaylistSongs(playlistId: Long): androidx.lifecycle.LiveData<List<Song>> {
        if (com.wayne.musicdeck.data.SmartPlaylistManager.isSmartPlaylist(playlistId)) {
            val smartLive = MutableLiveData<List<Song>>()
            viewModelScope.launch(Dispatchers.IO) {
                val allPlayCounts = playCountDao.getAllPlayCounts()
                val allSongs = originalSongs.ifEmpty { loadSongsInternal() }
                val manager = com.wayne.musicdeck.data.SmartPlaylistManager(allSongs, allPlayCounts)
                val songs = when (playlistId) {
                    com.wayne.musicdeck.data.SmartPlaylistManager.ID_RECENTLY_ADDED -> manager.getRecentlyAdded()
                    com.wayne.musicdeck.data.SmartPlaylistManager.ID_HEAVY_ROTATION -> manager.getHeavyRotation()
                    com.wayne.musicdeck.data.SmartPlaylistManager.ID_FORGOTTEN_GEMS -> manager.getForgottenGems()
                    com.wayne.musicdeck.data.SmartPlaylistManager.ID_CHILL_MODE -> manager.getChillMode()
                    com.wayne.musicdeck.data.SmartPlaylistManager.ID_ENERGY_BOOST -> manager.getEnergyBoost()
                    else -> emptyList()
                }
                smartLive.postValue(songs)
            }
            return smartLive
        }
        
        return playlistRepository.getSongsForPlaylistLive(playlistId).map { playlistSongs ->
            val allSongs = originalSongs 
            playlistSongs.mapNotNull { pSong ->
                allSongs.find { it.id == pSong.songId }
            }
        }
    }

    suspend fun getPlaylistPreview(playlistId: Long): List<String> {
        return withContext(Dispatchers.IO) {
            val pSongs = playlistRepository.getSongsForPlaylist(playlistId)
            val allSongs = originalSongs.ifEmpty { loadSongsInternal() }
            val paths = mutableListOf<String>()
            
            for (ps in pSongs) {
                val song = allSongs.find { it.id == ps.songId }
                if (song != null) {
                    val customCoverPath = customCoverRepository.getCustomCover(song.data)
                    if (customCoverPath != null) {
                        paths.add(customCoverPath)
                    } else {
                        // Use song path, Coil will fetch embedded art
                        paths.add(song.data)
                    }
                }
                if (paths.size >= 4) break
            }
            paths
        }
    }

    fun playPlaylist(songs: List<Song>, startIndex: Int) {
        val controller = mediaController.value ?: return
        if (startIndex < 0 || startIndex >= songs.size) return
        
        val mediaItems = songs.map { 
            val customCoverPath = it.data.let { path -> customCoverRepository.getCustomCover(path) }
            val artUri = if (customCoverPath != null) {
                android.net.Uri.fromFile(java.io.File(customCoverPath))
            } else if (it.albumId > 0 && it.album != "Unknown Album") {
                android.content.ContentUris.withAppendedId(
                    android.net.Uri.parse("content://media/external/audio/album_art"),
                    it.albumId
                )
            } else {
                it.uri
            }

            androidx.media3.common.MediaItem.Builder()
                .setMediaId(it.data)
                .setUri(it.uri)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(it.title)
                        .setArtist(it.artist)
                        .setAlbumTitle(it.album)
                        .setArtworkUri(artUri)
                        .build()
                )
                .setRequestMetadata(
                    androidx.media3.common.MediaItem.RequestMetadata.Builder()
                        .setExtras(android.os.Bundle().apply { putLong("songId", it.id) })
                        .build()
                )
                .build()
        }

        controller.setMediaItems(mediaItems)
        controller.seekTo(startIndex, 0)
        controller.prepare()
        controller.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
        controller.play()
        
        lastPlayedSongPath = songs[startIndex].data
    }
    
    fun deletePlaylist(playlist: com.wayne.musicdeck.data.Playlist) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlist)
            loadPlaylists()
        }
    }

    fun addToQueue(songs: List<Song>) {
        val controller = mediaController.value ?: return
        val mediaItems = songs.map { 
            val customCoverPath = it.data.let { path -> customCoverRepository.getCustomCover(path) }
            val artUri = if (customCoverPath != null) {
                android.net.Uri.fromFile(java.io.File(customCoverPath))
            } else if (it.albumId > 0 && it.album != "Unknown Album") {
                android.content.ContentUris.withAppendedId(
                    android.net.Uri.parse("content://media/external/audio/album_art"),
                    it.albumId
                )
            } else {
                it.uri
            }

            androidx.media3.common.MediaItem.Builder()
                .setMediaId(it.data)
                .setUri(it.uri)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(it.title)
                        .setArtist(it.artist)
                        .setAlbumTitle(it.album)
                        .setArtworkUri(artUri)
                        .build()
                )
                .setRequestMetadata(
                    androidx.media3.common.MediaItem.RequestMetadata.Builder()
                        .setExtras(android.os.Bundle().apply { putLong("songId", it.id) })
                        .build()
                )
                .build()
        }
        controller.addMediaItems(mediaItems)
    }
    
    fun reorderPlaylist(playlistId: Long, fromPos: Int, toPos: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            playlistRepository.reorderPlaylist(playlistId, fromPos, toPos)
            // Refresh list? LiveData should be observed, but we might need to trigger reload of that specific playlist
            // But getPlaylistSongs returns a new LiveData each call?
            // Actually usually we return a LiveData dependent on DB. Room provides Observable.
            // But here getPlaylistSongs seems to do a one-shot fetch?
        }
    }
    
    fun removeSongFromPlaylist(playlistId: Long, song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            playlistRepository.removeSongFromPlaylist(playlistId, song.data)
        }
    }

    private var originalSongs = listOf<Song>()

    fun loadSongs() {
        viewModelScope.launch {
            loadSongsInternal()
        }
    }
    
    // Suspend version that can be awaited
    private suspend fun loadSongsInternal(): List<Song> {
        val songList = withContext(Dispatchers.IO) {
            val songs = mutableListOf<Song>()
            
            // Get all volume names for Android 10+ to ensure SD card is included
            val volumeNames = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                try {
                    MediaStore.getExternalVolumeNames(application)
                } catch (e: Exception) {
                    setOf(MediaStore.VOLUME_EXTERNAL)
                }
            } else {
                setOf("external")
            }

            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DATE_ADDED
            )

            // Relaxed selection: Get all audio files
            val selection = "${MediaStore.Audio.Media.DURATION} > 10000" // At least 10 seconds
            val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

            for (volumeName in volumeNames) {
                val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    MediaStore.Audio.Media.getContentUri(volumeName)
                } else {
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }

                application.contentResolver.query(
                    collection,
                    projection,
                    selection,
                    null,
                    sortOrder
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM) // Added albumColumn
                    val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                    val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val title = cursor.getString(titleColumn)
                        val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                        val album = cursor.getString(albumColumn) ?: "Unknown Album" // Added album
                        val albumId = cursor.getLong(albumIdColumn)
                        val duration = cursor.getLong(durationColumn)
                        val data = cursor.getString(dataColumn) ?: ""
                        val dateAdded = cursor.getLong(dateAddedColumn)

                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                        
                        // Avoid duplicates (same ID from multiple volumes)
                        if (songs.none { it.id == id }) {
                            songs.add(Song(id, title, artist, album, albumId, contentUri, duration, data, dateAdded))
                        }
                    }
                }
            }
            songs
        }
        
        // Apply custom metadata overrides from local database (Mapped by File Path for stability)
        val customMetadataMap = withContext(Dispatchers.IO) {
            customMetadataDao.getAllCustomMetadata().associateBy { it.filePath }
        }
        val songsWithOverrides = songList.map { song ->
            val override = customMetadataMap[song.data]
            if (override != null) {
                song.copy(
                    title = override.customTitle ?: song.title,
                    artist = override.customArtist ?: song.artist,
                    album = override.customAlbum ?: song.album
                )
            } else {
                // AUTO-FIX: Apply Smart Splitter for "Artist - Title" junk
                val rawTitle = song.title
                val rawArtist = song.artist
                val isJunkArtist = rawArtist == "<unknown>" || rawArtist.contains("@") || rawArtist.contains("topic", ignoreCase = true)
                
                if (isJunkArtist && rawTitle.contains(" - ")) {
                    val parts = rawTitle.split(" - ", limit = 2)
                    if (parts.size == 2) {
                        song.copy(
                            artist = parts[0].trim(),
                            title = parts[1].trim()
                        )
                    } else {
                        song
                    }
                } else {
                    song
                }
            }
        }
        
        // Apply default title sort
        val sortedSongs = songsWithOverrides.sortedWith(titleComparator)
        
        // Partition into visible and hidden songs
        val hiddenIds = withContext(Dispatchers.IO) {
            hiddenSongDao.getHiddenSongIds().toSet()
        }
        val (hiddenList, visibleList) = sortedSongs.partition { it.id in hiddenIds }
        
        originalSongs = visibleList
        _songs.postValue(visibleList)
        _hiddenSongs.postValue(hiddenList)
        
        // Refresh favorites now that songs are loaded
        if (favoritesPlaylistId != -1L) {
            refreshFavoritesList()
        }
        
        // Generate artists and albums lists (only from visible songs)
        generateArtistsList(visibleList)
        generateAlbumsList(visibleList)
        
        return visibleList
    }
    
    // Shared title comparator for consistent sorting
    private val titleComparator = Comparator<Song> { s1, s2 ->
        val t1 = s1.title
        val t2 = s2.title
        val c1 = t1.firstOrNull()?.uppercaseChar() ?: ' '
        val c2 = t2.firstOrNull()?.uppercaseChar() ?: ' '
        
        val isL1 = c1.isLetter()
        val isL2 = c2.isLetter()
        
        if (isL1 && !isL2) -1 // Letter comes before Non-Letter
        else if (!isL1 && isL2) 1 // Non-Letter comes after Letter
        else t1.compareTo(t2, ignoreCase = true)
    }
    
    private fun generateArtistsList(songs: List<Song>) {
        val artistMap = songs.groupBy { it.artist }
        val artistsList = artistMap.map { (artistName, artistSongs) ->
            Artist(
                name = artistName,
                songCount = artistSongs.size,
                album_artId = artistSongs.firstOrNull()?.albumId ?: 0L
            )
        }.sortedBy { it.name.lowercase() }
        _artists.postValue(artistsList)
    }
    
    private fun generateAlbumsList(songs: List<Song>) {
        val albumMap = songs.groupBy { it.albumId }
        val albumsList = albumMap.map { (albumId, albumSongs) ->
            Album(
                id = albumId,
                name = albumSongs.firstOrNull()?.album ?: "Unknown Album",
                artist = albumSongs.firstOrNull()?.artist ?: "Unknown Artist",
                songCount = albumSongs.size
            )
        }.sortedBy { it.name.lowercase() }
        _albums.postValue(albumsList)
    }

    fun filterSongs(query: String?) {
        val q = query?.trim() ?: ""
        if (q.isEmpty()) {
            _songs.value = originalSongs
        } else {
            _songs.value = originalSongs.filter { 
                it.title.contains(q, true) || it.artist.contains(q, true)
            }
        }
    }

    enum class SortOption { TITLE, ARTIST, DURATION, DATE_ADDED }
    
    fun sortSongs(option: SortOption) {
        val list = _songs.value ?: return
        
        val sorted = when (option) {
            SortOption.TITLE -> list.sortedWith(titleComparator)
            SortOption.ARTIST -> list.sortedBy { it.artist }
            SortOption.DURATION -> list.sortedByDescending { it.duration }
            SortOption.DATE_ADDED -> list.sortedByDescending { it.dateAdded }
        }
        _songs.value = sorted
        
        // Also update originalSongs if Title sort is default
        if (option == SortOption.TITLE) {
             originalSongs = originalSongs.sortedWith(titleComparator)
        }
    }

    fun initializeController() {
        if (controllerFuture != null) return // Already initializing or initialized

        try {
            val sessionToken = SessionToken(
                application,
                ComponentName(application, MusicService::class.java)
            )

            controllerFuture = MediaController.Builder(application, sessionToken).buildAsync()
            controllerFuture?.addListener(
                {
                    try {
                        val controller = controllerFuture?.get()
                        mediaController.postValue(controller)
                        if (controller != null) {
                            initLyricsSystem(controller)
                            
                            // Listen for Sleep Timer ticks from Service Extras
                            // Note: onSessionExtrasChanged is not part of Player.Listener
                            // We are leaving this commented out for now to ensure compile succeeds
                            /* controller.addListener(object : androidx.media3.common.Player.Listener {
                                override fun onSessionExtrasChanged(extras: android.os.Bundle) {
                                    val remaining = if (extras.containsKey("SLEEP_TIMER_REMAINING_MS")) {
                                        extras.getLong("SLEEP_TIMER_REMAINING_MS")
                                    } else null
                                    _sleepTimerRemainingMillis.postValue(remaining)
                                }
                            }) */
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
                MoreExecutors.directExecutor()
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val _sleepTimerRemainingMillis = MutableLiveData<Long?>(null)
    val sleepTimerRemainingMillis: LiveData<Long?> = _sleepTimerRemainingMillis

    fun playSong(song: Song) {
        val controller = mediaController.value ?: return
        val currentList = _songs.value ?: return
        val startIndex = currentList.indexOfFirst { it.id == song.id }
        
        if (startIndex == -1) return

        // Convert Song objects to MediaItems
        val mediaItems = currentList.map { it ->
            val customCoverPath = it.data.let { path -> customCoverRepository.getCustomCover(path) }
            val artUri = if (customCoverPath != null) {
                Uri.fromFile(java.io.File(customCoverPath))
            } else if (it.albumId > 0 && it.album != "Unknown Album") {
                ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/album_art"),
                    it.albumId
                )
            } else {
                // Fallback: Use the file URI itself if system album_art is unavailable or generic
                it.uri 
            }

            MediaItem.Builder()
                .setMediaId(it.data)
                .setUri(it.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(it.title)
                        .setArtist(it.artist)
                        .setArtworkUri(artUri)
                        .build()
                )
                .setRequestMetadata(
                    androidx.media3.common.MediaItem.RequestMetadata.Builder()
                        .setExtras(android.os.Bundle().apply { putLong("songId", it.id) })
                        .build()
                )
                .build()
        }

        // Set the playlist and start at the selected song
        controller.setMediaItems(mediaItems)
        controller.seekTo(startIndex, 0)
        controller.prepare()
        controller.play()
        
        // Save last played song and reset position (new song started from beginning)
        lastPlayedSongPath = song.data
        lastPlayedPosition = 0
    }
    
    fun playNext(song: Song) {
        val controller = mediaController.value ?: return
        val customCoverPath = customCoverRepository.getCustomCover(song.data)
        val artUri = if (customCoverPath != null) {
            Uri.fromFile(java.io.File(customCoverPath))
        } else if (song.albumId > 0 && song.album != "Unknown Album") {
            ContentUris.withAppendedId(Uri.parse("content://media/external/audio/album_art"), song.albumId)
        } else {
            song.uri 
        }

        val mediaItem = MediaItem.Builder()
            .setMediaId(song.data)
            .setUri(song.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setArtworkUri(artUri)
                    .build()
            )
            .setRequestMetadata(
                androidx.media3.common.MediaItem.RequestMetadata.Builder()
                    .setExtras(android.os.Bundle().apply { putLong("songId", song.id) })
                    .build()
            )
            .build()
            
        // Insert right after the current playing item
        val nextIndex = controller.currentMediaItemIndex + 1
        controller.addMediaItem(nextIndex, mediaItem)
    }

    fun addToQueue(song: Song) {
        val controller = mediaController.value ?: return
        val customCoverPath = customCoverRepository.getCustomCover(song.data)
        val artUri = if (customCoverPath != null) {
            Uri.fromFile(java.io.File(customCoverPath))
        } else if (song.albumId > 0 && song.album != "Unknown Album") {
            ContentUris.withAppendedId(Uri.parse("content://media/external/audio/album_art"), song.albumId)
        } else {
            song.uri 
        }

        val mediaItem = MediaItem.Builder()
            .setMediaId(song.data)
            .setUri(song.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setArtworkUri(artUri)
                    .build()
            )
            .setRequestMetadata(
                androidx.media3.common.MediaItem.RequestMetadata.Builder()
                    .setExtras(android.os.Bundle().apply { putLong("songId", song.id) })
                    .build()
            )
            .build()
            
        // Add to end of queue
        controller.addMediaItem(mediaItem)
    }
    
    fun playSongFromPosition(song: Song, positionMs: Long, autoPlay: Boolean = true) {
        val controller = mediaController.value ?: return
        val currentList = _songs.value ?: return
        val startIndex = currentList.indexOfFirst { it.data == song.data }
        
        if (startIndex == -1) return

        val mediaItems = currentList.map { it ->
            val customCoverPath = it.data.let { path -> customCoverRepository.getCustomCover(path) }
            val artUri = if (customCoverPath != null) {
                Uri.fromFile(java.io.File(customCoverPath))
            } else if (it.albumId > 0 && it.album != "Unknown Album") {
                ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/album_art"),
                    it.albumId
                )
            } else {
                it.uri
            }

            MediaItem.Builder()
                .setMediaId(it.data)
                .setUri(it.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(it.title)
                        .setArtist(it.artist)
                        .setArtworkUri(artUri)
                        .build()
                )
                .setRequestMetadata(
                    androidx.media3.common.MediaItem.RequestMetadata.Builder()
                        .setExtras(android.os.Bundle().apply { putLong("songId", it.id) })
                        .build()
                )
                .build()
        }

        controller.setMediaItems(mediaItems)
        controller.seekTo(startIndex, positionMs)
        controller.prepare()
        if (autoPlay) {
            controller.play()
        }
        
        lastPlayedSongPath = song.data
        
        // Remove manual increment, handled by Service Heartbeat now
    }
    
    fun savePosition() {
        val controller = mediaController.value ?: return
        lastPlayedPosition = controller.currentPosition
    }
    
    // Play Count Tracking (Mapped by File Path for stability)
    fun incrementPlayCount(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            playCountDao.ensureExists(song.data, song.id)
            playCountDao.incrementPlayCount(song.data)
        }
    }
    
    suspend fun getPlayCount(song: Song): Int {
        return playCountDao.getPlayCount(song.data) ?: 0
    }
    
    suspend fun getMostPlayedSongs(limit: Int = 20): List<Song> {
        val playCounts = playCountDao.getMostPlayed(limit)
        val allSongs = _songs.value ?: return emptyList()
        return playCounts.mapNotNull { pc -> allSongs.find { it.data == pc.filePath } }
    }
    
    fun loadMostPlayed() {
        viewModelScope.launch(Dispatchers.IO) {
            val songs = getMostPlayedSongs(20)
            _mostPlayed.postValue(songs)
        }
    }

    // --- Listening Insights Logic ---
    
    data class ListeningInsights(
        val totalPlays: Int,
        val topSongs: List<Pair<Song, Int>>,
        val topArtists: List<Pair<String, Int>>,
        val uniqueArtistsCount: Int,
        val uniqueAlbumsCount: Int
    )

    private val _insights = MutableLiveData<ListeningInsights>()
    val insights: LiveData<ListeningInsights> = _insights

    fun loadInsights() {
        viewModelScope.launch(Dispatchers.IO) {
            val allPlayCounts = playCountDao.getAllPlayCounts()
            val allSongs = originalSongs.ifEmpty { loadSongsInternal() }
            
            if (allPlayCounts.isEmpty()) return@launch

            val totalPlays = allPlayCounts.sumOf { it.playCount }
            
            // Map counts to songs
            val songCounts = allPlayCounts.mapNotNull { pc ->
                allSongs.find { it.id == pc.songId }?.let { it to pc.playCount }
            }.sortedByDescending { it.second }.take(5)

            // Aggregate by Artist
            val artistCounts = allPlayCounts.mapNotNull { pc ->
                allSongs.find { it.id == pc.songId }?.let { it.artist to pc.playCount }
            }.groupBy({ it.first }, { it.second })
                .mapValues { it.value.sum() }
                .toList()
                .sortedByDescending { it.second }
                .take(3)

            val uniqueArtists = allSongs.map { it.artist }.distinct().size
            val uniqueAlbums = allSongs.map { it.albumId }.distinct().size

            _insights.postValue(
                ListeningInsights(
                    totalPlays = totalPlays,
                    topSongs = songCounts,
                    topArtists = artistCounts,
                    uniqueArtistsCount = uniqueArtists,
                    uniqueAlbumsCount = uniqueAlbums
                )
            )
        }
    }
    
    fun getFolders(): List<SongListItem.FolderItem> {
        val allSongs = if (originalSongs.isNotEmpty()) originalSongs else _songs.value ?: emptyList()
        val groupByParent = allSongs.groupBy {
             try {
                val file = java.io.File(it.data)
                if (it.data.startsWith("/")) file.parentFile?.path ?: "Unknown" else "Unknown"
             } catch (e: Exception) {
                "Unknown"
             }
        }
        
        return groupByParent.mapNotNull { (path, songs) ->
            if (path == "Unknown") null else {
                val name = java.io.File(path).name
                SongListItem.FolderItem(name, path, songs.size)
            }
        }.sortedBy { it.name }
    }
    
    fun getSongsInFolder(path: String): List<SongListItem.SongItem> {
        val allSongs = if (originalSongs.isNotEmpty()) originalSongs else _songs.value ?: emptyList()
        return allSongs.filter { 
             try {
                val file = java.io.File(it.data)
                file.parentFile?.path == path
            } catch (e: Exception) {
                false
            }
        }.sortedBy { it.title }.map { SongListItem.SongItem(it) }
    }
    
    fun updateSongTags(song: Song, title: String, artist: String, album: String, notes: String? = null) {
        viewModelScope.launch {
            try {
                // Save to local database instead of trying to modify MediaStore
                // This works around Android 10+ restrictions
                withContext(Dispatchers.IO) {
                    val customMetadata = com.wayne.musicdeck.data.CustomMetadata(
                        filePath = song.data,
                        songId = song.id,
                        customTitle = title,
                        customArtist = artist,
                        customAlbum = album,
                        notes = notes
                    )
                    customMetadataDao.insertOrUpdate(customMetadata)
                }
                
                // Reload songs and WAIT for completion - this returns the fresh sorted list
                val freshSortedList = loadSongsInternal()
                
                // Now sync with active queue using the FRESH sorted list
                withContext(Dispatchers.Main) {
                    val updatedSong = song.copy(title = title, artist = artist, album = album)
                    syncQueueOnUpdate(updatedSong, freshSortedList)
                    
                    android.widget.Toast.makeText(application, "Tags saved! (Changes visible in app only)", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(application, "Failed to save: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    // For Android 10+ permission flow
    data class TagEditRequest(val song: Song, val title: String, val artist: String, val album: String)
    private val _tagEditPermissionRequest = MutableLiveData<TagEditRequest?>()
    val tagEditPermissionRequest: LiveData<TagEditRequest?> = _tagEditPermissionRequest
    
    fun clearTagEditPermissionRequest() {
        _tagEditPermissionRequest.value = null
    }
    
    suspend fun getSongNotes(filePath: String): String? {
        return withContext(Dispatchers.IO) {
            customMetadataDao.getCustomMetadata(filePath)?.notes
        }
    }
    
    // Force update after permission granted
    fun updateSongTagsForce(song: Song, title: String, artist: String, album: String) {
        viewModelScope.launch {
            try {
                val rowsUpdated = withContext(Dispatchers.IO) {
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
                    val values = android.content.ContentValues().apply {
                        put(MediaStore.Audio.Media.TITLE, title)
                        put(MediaStore.Audio.Media.ARTIST, artist)
                        put(MediaStore.Audio.Media.ALBUM, album)
                    }
                    application.contentResolver.update(uri, values, null, null)
                }
                
                if (rowsUpdated > 0) {
                    // Reload songs and WAIT for completion
                    val freshSortedList = loadSongsInternal()
                    
                    // Now sync with active queue using the FRESH sorted list
                    withContext(Dispatchers.Main) {
                        val updatedSong = song.copy(title = title, artist = artist, album = album)
                        syncQueueOnUpdate(updatedSong, freshSortedList)
                        android.widget.Toast.makeText(application, "Tags updated! Refresh may take a moment.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(application, "Update failed - Android may not allow modifying this file", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(application, "Failed to update: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    fun onSongDeleted(songId: Long) {
        syncQueueOnDelete(songId)
    }
    
    // --- Player Queue Synchronization ---
    
    private fun syncQueueOnDelete(songId: Long) {
        val controller = mediaController.value ?: return
        val timeline = controller.currentTimeline
        if (timeline.isEmpty) return
        
        // Find all occurrences of the song in the queue
        // We iterate backwards to avoid index shifting issues when removing multiple
        for (i in timeline.windowCount - 1 downTo 0) {
            val mediaItem = controller.getMediaItemAt(i)
            if (mediaItem.mediaId == songId.toString()) {
                controller.removeMediaItem(i)
            }
        }
    }
    
    /**
     * Sync the player queue after a song's metadata is updated.
     * 
     * @param song The song with updated metadata
     * @param freshSortedList The freshly loaded and sorted song list (from loadSongsInternal)
     */
    private fun syncQueueOnUpdate(song: Song, freshSortedList: List<Song>) {
        val controller = mediaController.value ?: return
        val timeline = controller.currentTimeline
        if (timeline.isEmpty) return
        
        // Check if we're playing the full "All Songs" list (queue size matches song count)
        val isPlayingAllSongs = timeline.windowCount == freshSortedList.size
        
        if (isPlayingAllSongs) {
            // REBUILD the entire queue to match the new sorted order
            // This is the most reliable way to ensure the queue order is correct
            
            // 1. Remember the current song and position
            val currentMediaId = controller.currentMediaItem?.mediaId
            val currentPosition = controller.currentPosition
            val wasPlaying = controller.isPlaying
            
            // 2. Build new MediaItems list in the correct sorted order
            val newMediaItems = freshSortedList.map { s ->
                val customCoverPath = customCoverRepository.getCustomCover(s.id)
                val artUri = if (customCoverPath != null) {
                    Uri.fromFile(java.io.File(customCoverPath))
                } else {
                    ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/album_art"),
                        s.albumId
                    )
                }
                
                MediaItem.Builder()
                    .setMediaId(s.data)
                    .setUri(s.uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(s.title)
                            .setArtist(s.artist)
                            .setAlbumTitle(s.album)
                            .setArtworkUri(artUri)
                            .build()
                    )
                    .build()
            }
            
            // 3. Find the new index of the currently playing song
            val newIndex = if (currentMediaId != null) {
                freshSortedList.indexOfFirst { it.data == currentMediaId }
            } else -1
            
            // 4. Replace the entire queue
            controller.setMediaItems(newMediaItems, newIndex.coerceAtLeast(0), currentPosition)
            
            // 5. Resume playback if it was playing
            if (wasPlaying) {
                controller.play()
            }
        } else {
            // Playing a playlist or subset - just update the metadata in-place
            for (i in 0 until timeline.windowCount) {
                val mediaItem = controller.getMediaItemAt(i)
                if (mediaItem.mediaId == song.data) {
                    val customCoverPath = customCoverRepository.getCustomCover(song.id)
                    val artUri = if (customCoverPath != null) {
                        Uri.fromFile(java.io.File(customCoverPath))
                    } else {
                        ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/album_art"),
                            song.albumId
                        )
                    }

                    val newMediaItem = MediaItem.Builder()
                        .setMediaId(song.data)
                        .setUri(song.uri)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(song.title)
                                .setArtist(song.artist)
                                .setAlbumTitle(song.album)
                                .setArtworkUri(artUri)
                                .build()
                        )
                        .build()
                    
                    controller.replaceMediaItem(i, newMediaItem)
                    break
                }
            }
        }
    }
    
    // Backup/Restore
    private val _backupResult = MutableLiveData<String?>()
    val backupResult: LiveData<String?> = _backupResult

    fun clearBackupResult() {
        _backupResult.value = null
    }


    fun exportBackup() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allPlaylists = playlistRepository.getAllPlaylists()
                val playlistsWithSongs = allPlaylists.map { playlist ->
                    playlist to playlistRepository.getSongsForPlaylist(playlist.id)
                }
                val json = BackupHelper.exportToJson(playlistsWithSongs)
                val success = BackupHelper.saveBackup(application, json)
                withContext(Dispatchers.Main) {
                    _backupResult.value = if (success) "Backup saved successfully!" else "Backup failed"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _backupResult.value = "Backup error: ${e.message}"
                }
            }
        }
    }
    
    fun importBackup() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = BackupHelper.loadBackup(application)
                if (json == null) {
                    withContext(Dispatchers.Main) { _backupResult.value = "No backup found" }
                    return@launch
                }
                
                val data = BackupHelper.parseFromJson(json)
                if (data == null) {
                    withContext(Dispatchers.Main) { _backupResult.value = "Invalid backup format" }
                    return@launch
                }
                
                var imported = 0
                for (playlistBackup in data.playlists) {
                    // Skip Favorites - handled separately
                    if (playlistBackup.name == "Favorites") continue
                    
                    // Create playlist
                    val playlistId = playlistRepository.createPlaylist(playlistBackup.name)
                    
                    // Add songs
                    val allSongs = _songs.value ?: emptyList()
                    for (songId in playlistBackup.songIds) {
                        val songPath = allSongs.find { it.id == songId }?.data
                        if (songPath != null) {
                            playlistRepository.addSongToPlaylist(playlistId, songId, songPath)
                        }
                    }
                    imported++
                }
                
                withContext(Dispatchers.Main) {
                    _backupResult.value = "Imported $imported playlists!"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _backupResult.value = "Import error: ${e.message}"
                }
            }
        }
    }

    // Custom Album Cover Management
    fun setCustomCover(song: Song, imageUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = application
                
                // 1. Cleanup old covers for this song
                val filesDir = context.filesDir
                val oldFiles = filesDir.listFiles { _, name -> 
                    name.startsWith("cover_${song.id}_") && name.endsWith(".jpg")
                }
                oldFiles?.forEach { it.delete() }
                
                // 2. Create new timestamped file
                val timestamp = System.currentTimeMillis()
                val fileName = "cover_${song.id}_$timestamp.jpg"
                val customCoverFile = java.io.File(filesDir, fileName)
                
                // 3. Copy image
                val inputStream = context.contentResolver.openInputStream(imageUri)
                val outputStream = java.io.FileOutputStream(customCoverFile)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()
                
                // 4. Save path to repository
                customCoverRepository.setCustomCover(song.data, customCoverFile.absolutePath)
                
                // 5. Update UI
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Custom cover updated!", android.widget.Toast.LENGTH_SHORT).show()
                    // Force refresh list to pick up new path from Prefs
                    loadSongs()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(application, "Failed to set cover: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    fun removeCustomCover(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Get current path and delete file
                val path = customCoverRepository.getCustomCover(song.data)
                if (path != null) {
                    val file = java.io.File(path)
                    file.delete()
                }
                
                // Remove from repository
                // (Need a removeCustomCover(filePath: String) in Repository)
                // For now, setting to empty to "remove" as it's a Prefs Map.
                customCoverRepository.setCustomCover(song.data, "")
                
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(application, "Custom cover removed", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(application, "Failed to remove cover", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    fun getCustomCoverPath(filePath: String): String? {
        return customCoverRepository.getCustomCover(filePath)
    }
    
    fun hasCustomCover(filePath: String): Boolean {
        return customCoverRepository.getCustomCover(filePath) != null
    }
    
    // Lyrics Management
    fun setLyricFile(song: Song, fileUri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Copy lyric file to internal storage
                val inputStream = application.contentResolver.openInputStream(fileUri)
                val lyricFile = java.io.File(application.filesDir, "manual_lyric_${song.id}.lrc")
                val outputStream = java.io.FileOutputStream(lyricFile)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()
                
                // Save path to repository
                lyricsRepository.saveLyricPath(song.data, lyricFile.absolutePath)
                
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(application, "Lyric file set!", android.widget.Toast.LENGTH_SHORT).show()
                    val controller = mediaController.value
                    if (controller?.currentMediaItem?.mediaId == song.data) {
                        loadLyricsForMediaItem(controller.currentMediaItem!!, forceRefetch = false)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(application, "Failed to set lyric file", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun saveLyricPath(filePath: String, lyricFile: java.io.File) {
        lyricsRepository.saveLyricPath(filePath, lyricFile.absolutePath)
    }

    fun getLyricPath(filePath: String): String? {
        return lyricsRepository.getLyricPath(filePath)
    }
    

    
    /**
     * Remove lyrics for a song
     */
    fun removeLyrics(filePath: String) {
        val path = lyricsRepository.getLyricPath(filePath)
        if (path != null) {
            val file = java.io.File(path)
            if (file.exists()) file.delete()
        }
        lyricsRepository.removeLyricPath(filePath)
    }
    

    
    // Get file size for a song
    fun getSongFileSize(song: Song): String {
        return try {
            val file = java.io.File(song.data)
            val sizeBytes = file.length()
            when {
                sizeBytes < 1024 -> "$sizeBytes B"
                sizeBytes < 1024 * 1024 -> String.format("%.1f KB", sizeBytes / 1024.0)
                else -> String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0))
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }

// --- Hidden Song Management ---
    
    fun hideSong(songId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                hiddenSongDao.hide(com.wayne.musicdeck.data.HiddenSong(songId = songId))
            }
            // Reload to refresh both visible and hidden lists
            loadSongsInternal()
        }
    }
    
    fun unhideSong(songId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                hiddenSongDao.unhide(songId)
            }
            // Reload to refresh both visible and hidden lists
            loadSongsInternal()
        }
    }
    
    suspend fun isSongHidden(songId: Long): Boolean {
        return hiddenSongDao.isHidden(songId)
    }
    
    override fun onCleared() {
        application.contentResolver.unregisterContentObserver(contentObserver)
        super.onCleared()
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }
    
    // --- Lyrics Sync Logic ---
    
    sealed class LyricsStatus {
        object None : LyricsStatus()
        object Loading : LyricsStatus()
        data class Success(val isSynced: Boolean) : LyricsStatus()
        data class Error(val message: String) : LyricsStatus()
        object NotFound : LyricsStatus()
    }
    
    private val _lyrics = MutableLiveData<List<com.wayne.musicdeck.data.LyricLine>>()
    val lyrics: LiveData<List<com.wayne.musicdeck.data.LyricLine>> = _lyrics
    
    private val _lyricsStatus = MutableLiveData<LyricsStatus>()
    val lyricsStatus: LiveData<LyricsStatus> = _lyricsStatus
    
    private var lyricsJob: kotlinx.coroutines.Job? = null
    
    // Called once when MediaController is ready
    fun initLyricsSystem(controller: MediaController) {
        android.util.Log.d("LyricsSys", "Initializing Lyrics System")
        
        controller.addListener(object : androidx.media3.common.Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                android.util.Log.d("LyricsSys", "Transition: ${mediaItem?.mediaId}")
                if (mediaItem != null) {
                    loadLyricsForMediaItem(mediaItem)
                } else {
                    _lyrics.postValue(emptyList())
                    _lyricsStatus.postValue(LyricsStatus.None)
                }
            }
        })
        
        // Initial load
        controller.currentMediaItem?.let { loadLyricsForMediaItem(it) }
    }
    
    fun loadLyricsForMediaItem(mediaItem: MediaItem, forceRefetch: Boolean = false) {
        val currentPath = mediaItem.mediaId // Already stable path
        if (currentPath.isEmpty()) return
        
        // CANCEL any previous loading job immediately.
        // This prevents race conditions where multiple fetches overlap.
        lyricsJob?.cancel()
        
        _lyricsStatus.postValue(LyricsStatus.Loading)
        _lyrics.postValue(emptyList())
        
        lyricsJob = viewModelScope.launch {
            try {
                android.util.Log.d("LyricsSys", "Job started for $currentPath (force=$forceRefetch)")
                
                // 1. Check for manual override FIRST
                val savedPath = lyricsRepository.getLyricPath(currentPath)
                val isManualOverride = savedPath?.contains("manual_lyric_") == true
                
                if (isManualOverride) {
                    val lines = lyricsRepository.parseLrcFile(savedPath!!)
                    if (lines.isNotEmpty()) {
                        android.util.Log.d("LyricsSys", "Loaded manual override lyrics")
                        _lyrics.postValue(lines)
                        val isSynced = lines.any { it.timeMs > 0 }
                        _lyricsStatus.postValue(LyricsStatus.Success(isSynced))
                        return@launch
                    }
                }
                
                // 2. Check for local sidecar .lrc file (Priority for non-manual)
                val localFolderLrc = lyricsRepository.findLocalLrcFile(currentPath)
                if (localFolderLrc != null) {
                    val lines = lyricsRepository.parseLrcFile(localFolderLrc)
                    if (lines.isNotEmpty()) {
                        android.util.Log.d("LyricsSys", "Loaded local folder lyrics")
                        _lyrics.postValue(lines)
                        val isSynced = lines.any { it.timeMs > 0 }
                        _lyricsStatus.postValue(LyricsStatus.Success(isSynced))
                        return@launch
                    }
                }

                // 3. Check internal cache storage (unless forcing refetch)
                if (!forceRefetch && lyricsRepository.hasLyrics(currentPath)) {
                    val path = lyricsRepository.getLyricPath(currentPath)
                    if (path != null && path.contains("manual_lyric_").not()) {
                        val lines = lyricsRepository.parseLrcFile(path)
                        if (lines.isNotEmpty()) {
                            android.util.Log.d("LyricsSys", "Loaded local app cache lyrics")
                            _lyrics.postValue(lines)
                            val isSynced = lines.any { it.timeMs > 0 }
                            _lyricsStatus.postValue(LyricsStatus.Success(isSynced))
                            return@launch
                        } else {
                            // File exist but empty/corrupt? Remove it.
                           lyricsRepository.removeLyricPath(currentPath) 
                        }
                    }
                }
                
                // 4. Fetch from API
                val title = mediaItem.mediaMetadata.title?.toString() ?: "Unknown"
                val artist = mediaItem.mediaMetadata.artist?.toString() ?: "Unknown"
                // val album = mediaItem.mediaMetadata.albumTitle?.toString() // Redundant
                val duration = mediaController.value?.duration ?: 0L
                
                if (title == "Unknown") {
                    _lyricsStatus.postValue(LyricsStatus.Error("Unknown Title"))
                    return@launch
                }
                
                // Skip very long tracks (likely mixes)
                if (duration > 15 * 60 * 1000) {
                     _lyricsStatus.postValue(LyricsStatus.NotFound)
                     return@launch
                }
                
                android.util.Log.d("LyricsSys", "Fetching API: $title")
                val result = lyricsRepository.fetchAndSaveLyrics(
                    songId = mediaItem.requestMetadata.extras?.getLong("songId") ?: -1L,
                    trackName = title,
                    artistName = artist,
                    filePath = currentPath,
                    durationMs = mediaItem.requestMetadata.extras?.getLong("duration")
                )
                
                when (result) {
                    is com.wayne.musicdeck.data.FetchResult.Success -> {
                        if (coroutineContext[kotlinx.coroutines.Job]?.isActive == true) {
                            android.util.Log.d("LyricsSys", "API Success")
                            val path = lyricsRepository.getLyricPath(currentPath)
                            if (path != null) {
                                 val lines = lyricsRepository.parseLrcFile(path)
                                 _lyrics.postValue(lines)
                                 _lyricsStatus.postValue(LyricsStatus.Success(result.isSynced))
                            } else {
                                _lyricsStatus.postValue(LyricsStatus.Error("Save failed"))
                            }
                        }
                    }
                    is com.wayne.musicdeck.data.FetchResult.NotFound -> {
                        android.util.Log.d("LyricsSys", "API NotFound")
                        _lyricsStatus.postValue(LyricsStatus.NotFound)
                    }
                    is com.wayne.musicdeck.data.FetchResult.Error -> {
                        android.util.Log.d("LyricsSys", "API Error: ${result.message}")
                        _lyricsStatus.postValue(LyricsStatus.Error(result.message))
                    }
                }
                
            } catch (e: Exception) {
               if (e is kotlinx.coroutines.CancellationException) throw e
               e.printStackTrace()
               _lyricsStatus.postValue(LyricsStatus.Error("Error: ${e.message}"))
            }
        }
    }
    
    // --- Helpers for Menu / Manual Fetch ---
    
    fun hasLyrics(filePath: String): Boolean {
        return lyricsRepository.hasLyrics(filePath)
    }
    
    fun fetchLyrics(song: Song) {
        val controller = mediaController.value ?: return
        // Match by stable path
        if (controller.currentMediaItem?.mediaId == song.data) {
            loadLyricsForMediaItem(controller.currentMediaItem!!, forceRefetch = true)
        }
    }
    
    // --- Smart Auto-Organize Logic ---

    private val _useOnlineWisdom = androidx.lifecycle.MutableLiveData<Boolean>(false)
    val useOnlineWisdom: androidx.lifecycle.LiveData<Boolean> get() = _useOnlineWisdom

    fun setUseOnlineWisdom(enabled: Boolean) {
        _useOnlineWisdom.value = enabled
        generateOrganizationSuggestions()
    }

    private val _organizationSuggestions = androidx.lifecycle.MutableLiveData<List<com.wayne.musicdeck.data.OrganizationSuggestion>>()
    val organizationSuggestions: androidx.lifecycle.LiveData<List<com.wayne.musicdeck.data.OrganizationSuggestion>> get() = _organizationSuggestions

    fun generateOrganizationSuggestions() {
        viewModelScope.launch {
            val enabled = _useOnlineWisdom.value ?: false
            val currentSongs = _songs.value ?: emptyList()
            val suggestions = mutableListOf<com.wayne.musicdeck.data.OrganizationSuggestion>()
            val lyricsApiService = com.wayne.musicdeck.data.LyricsApiService()

            withContext(Dispatchers.IO) {
                for (song in currentSongs) {
                    var suggestedTitle = song.title
                    var suggestedArtist = song.artist
                    var suggestedAlbum = song.album
                    val reasons = mutableListOf<String>()

                    // 1. Cleaning Junk (Local)
                    val junkPatterns = listOf(
                        "Official Music Video", "Official Video", "Lyric Video", "Official Audio", 
                        "Official", "Lyrics", "VEVO", "HQ", "HD", "4K", "Topic"
                    )

                    fun String.cleanJunk(): String {
                        var cleaned = this
                        for (pattern in junkPatterns) {
                            val escapedPattern = Regex.escape(pattern)
                            cleaned = cleaned.replace(Regex("(?i)\\s*[\\[\\(]?$escapedPattern[\\]\\)]?\\s*"), " ").trim()
                        }
                        if (cleaned.endsWith("VEVO", ignoreCase = true) && cleaned.length > 4) {
                            cleaned = cleaned.substring(0, cleaned.length - 4).trim()
                        }
                        return cleaned
                    }

                    val cleanedArtist = suggestedArtist.cleanJunk()
                    if (cleanedArtist != suggestedArtist) {
                        suggestedArtist = cleanedArtist
                        reasons.add("Cleaned junk from Artist")
                    }

                    val cleanedTitle = suggestedTitle.cleanJunk()
                    if (cleanedTitle != suggestedTitle) {
                        suggestedTitle = cleanedTitle
                        reasons.add("Cleaned junk from Title")
                    }

                    // 2. Path-based Deduction for Unknowns (Local)
                    val isSuspiciousArtist = suggestedArtist == "Unknown Artist" || 
                                           suggestedArtist.length < 3 ||
                                           suggestedArtist.contains("VEVO", ignoreCase = true)
                                           
                    val isSuspiciousAlbum = suggestedAlbum == "Unknown Album" || 
                                          suggestedAlbum == suggestedTitle

                    if (isSuspiciousArtist || isSuspiciousAlbum) {
                        val file = java.io.File(song.data)
                        val folder = file.parentFile
                        val parentFolder = folder?.parentFile

                        if (folder != null && folder.name != "Music" && folder.name != "Download") {
                            if (isSuspiciousAlbum) {
                                suggestedAlbum = folder.name
                                reasons.add("Guessed Album from folder")
                            }
                            if (parentFolder != null && parentFolder.name != "0" && parentFolder.name != "emulated") {
                                if (suggestedArtist == "Unknown Artist") {
                                    suggestedArtist = parentFolder.name
                                    reasons.add("Guessed Artist from parent folder")
                                }
                            }
                        }
                        
                        // Try splitting "Artist - Title" from both Filename AND the Title tag itself
                        val sourceForSplit = if (suggestedTitle.contains(" - ")) suggestedTitle else file.nameWithoutExtension
                        
                        if (suggestedArtist == "Unknown Artist" && sourceForSplit.contains(" - ")) {
                            val parts = sourceForSplit.split(" - ", limit = 2)
                            if (parts.size == 2) {
                                suggestedArtist = parts[0].trim().cleanJunk()
                                suggestedTitle = parts[1].trim().cleanJunk()
                                reasons.add("Parsed Artist and Title")
                            }
                        }
                    }

                    // 3. Online Wisdom (Web-Sync)
                    val isGuessed = reasons.any { it.contains("Guessed") }
                    val needsWebSync = enabled && (suggestedArtist == "Unknown Artist" || isGuessed || suggestedArtist.length < 3)

                    if (needsWebSync) {
                        val titleQuery = suggestedTitle.cleanJunk().trim()
                        var webMatch: com.wayne.musicdeck.data.LrclibResponse? = null
                        
                        // If artist is a guess (like Javier G), try searching title only first
                        if (isGuessed || suggestedArtist == "Unknown Artist") {
                             if (titleQuery.length > 3) {
                                 Log.d("SmartOrganize", "Searching Web by Title: $titleQuery")
                                 webMatch = lyricsApiService.searchTrackMetadata(titleQuery)
                             }
                        }
                        
                        // If title-only failed, try combined
                        if (webMatch == null) {
                            val combinedQuery = "$suggestedTitle $suggestedArtist".cleanJunk().trim()
                            if (combinedQuery.length > 5) {
                                Log.d("SmartOrganize", "Searching Web by Combined: $combinedQuery")
                                webMatch = lyricsApiService.searchTrackMetadata(combinedQuery)
                            }
                        }

                        val match = webMatch
                        if (match != null && !match.artistName.isNullOrBlank()) {
                            if (suggestedArtist != match.artistName || suggestedAlbum != match.albumName) {
                                suggestedArtist = match.artistName
                                suggestedAlbum = match.albumName ?: suggestedAlbum
                                suggestedTitle = match.trackName ?: suggestedTitle
                                reasons.add("Web-Synced (Elite Sync)")
                                Log.d("SmartOrganize", "Found Match: ${match.artistName} - ${match.trackName}")
                                kotlinx.coroutines.delay(150)
                            }
                        }
                    }

                    val suggestion = com.wayne.musicdeck.data.OrganizationSuggestion(
                        songId = song.id,
                        filePath = song.data,
                        currentTitle = song.title,
                        currentArtist = song.artist,
                        currentAlbum = song.album,
                        suggestedTitle = suggestedTitle,
                        suggestedArtist = suggestedArtist,
                        suggestedAlbum = suggestedAlbum,
                        reason = reasons.joinToString(", ")
                    )

                    if (suggestion.hasChanges) {
                        suggestions.add(suggestion)
                    }
                }
            }
            _organizationSuggestions.postValue(suggestions)
        }
    }

    fun resetOrganization() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Wipe all custom metadata to restore originals
                customMetadataDao.deleteAll()
                // Regenerate logic to show results based on originals
                generateOrganizationSuggestions()
            }
        }
    }

    fun applyOrganizationSuggestions(suggestions: List<com.wayne.musicdeck.data.OrganizationSuggestion>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                suggestions.forEach { suggestion ->
                    val metadata = com.wayne.musicdeck.data.CustomMetadata(
                        filePath = suggestion.filePath,
                        songId = suggestion.songId,
                        customTitle = suggestion.suggestedTitle,
                        customArtist = suggestion.suggestedArtist,
                        customAlbum = suggestion.suggestedAlbum
                    )
                    customMetadataDao.insertOrUpdate(metadata)
                }
            }
            _organizationSuggestions.postValue(emptyList())
            loadSongs() // Refresh library
        }
    }
}
