package com.wayne.musicdeck

import android.app.Application
import android.content.ComponentName
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
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

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _songs = MutableLiveData<List<Song>>()
    val songs: LiveData<List<Song>> = _songs

    // MediaController Future
    private var controllerFuture: ListenableFuture<MediaController>? = null
    val mediaController = MutableLiveData<MediaController?>()
    
    // Persistent state
    private val prefs = application.getSharedPreferences("musicdeck_prefs", android.content.Context.MODE_PRIVATE)
    var lastPlayedSongId: Long
        get() = prefs.getLong("last_song_id", -1)
        set(value) = prefs.edit().putLong("last_song_id", value).apply()
    var lastPlayedPosition: Long
        get() = prefs.getLong("last_position", 0)
        set(value) = prefs.edit().putLong("last_position", value).apply()

    fun saveSearchQuery(query: String) {
        val history = getSearchHistory().toMutableList()
        if (history.contains(query)) history.remove(query)
        history.add(0, query) // Add to top
        if (history.size > 10) history.removeAt(history.lastIndex) // Limit to 10
        val str = history.joinToString("|||")
        prefs.edit().putString("search_history", str).apply()
    }
    
    fun getSearchHistory(): List<String> {
        val str = prefs.getString("search_history", "") ?: ""
        return if (str.isEmpty()) emptyList() else str.split("|||")
    }
    
    fun clearSearchHistory() {
        prefs.edit().remove("search_history").apply()
    }

    // Database & Repository
    private val database = com.wayne.musicdeck.data.MusicDatabase.getDatabase(application)
    private val playlistRepository = com.wayne.musicdeck.data.PlaylistRepository(database.playlistDao())
    private val playCountDao = database.playCountDao()
    private val customCoverRepository = com.wayne.musicdeck.data.CustomCoverRepository(application)
    private val lyricsRepository = com.wayne.musicdeck.data.LyricsRepository(application)
    private val customMetadataDao = database.customMetadataDao()
    
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
        loadSongs()
        getApplication<Application>().contentResolver.registerContentObserver(
            android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )
        viewModelScope.launch {
            val favPlaylist = playlistRepository.getOrCreateFavoritesPlaylist()
            favoritesPlaylistId = favPlaylist.id
            // Set up reactive observation of favorites
            setupFavoritesObserver()
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
            val isFav = currentFavs.any { it.id == song.id }
            
            if (isFav) {
                playlistRepository.removeSongFromPlaylist(favoritesPlaylistId, song.id)
            } else {
                playlistRepository.addSongToPlaylist(favoritesPlaylistId, song.id)
            }
            // No need to manually refresh - MediatorLiveData observes database changes automatically
        }
    }
    
    // CRUD functions for Playlists
    fun loadPlaylists() {
        viewModelScope.launch {
            // Filter out the special "Favorites" playlist from the list
            val allPlaylists = playlistRepository.getAllPlaylists()
            val filteredPlaylists = allPlaylists.filter { it.name != "Favorites" }
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
            playlistRepository.addSongToPlaylist(playlistId, song.id)
            android.widget.Toast.makeText(getApplication(), "Added to playlist", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    
    fun getPlaylistSongs(playlistId: Long): androidx.lifecycle.LiveData<List<Song>> {
        return playlistRepository.getSongsForPlaylistLive(playlistId).map { playlistSongs ->
            val allSongs = originalSongs 
            playlistSongs.mapNotNull { pSong ->
                allSongs.find { it.id == pSong.songId }
            }
        }
    }

    fun playPlaylist(songs: List<Song>, startIndex: Int) {
        val controller = mediaController.value ?: return
        if (startIndex < 0 || startIndex >= songs.size) return
        
        val mediaItems = songs.map { 
            androidx.media3.common.MediaItem.Builder()
                .setMediaId(it.id.toString())
                .setUri(it.uri)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(it.title)
                        .setArtist(it.artist)
                        .setArtworkUri(
                            android.content.ContentUris.withAppendedId(
                                android.net.Uri.parse("content://media/external/audio/albumart"),
                                it.albumId
                            )
                        )
                        .build()
                )
                .build()
        }

        controller.setMediaItems(mediaItems)
        controller.seekTo(startIndex, 0)
        controller.prepare()
        controller.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
        controller.play()
        
        lastPlayedSongId = songs[startIndex].id
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
            androidx.media3.common.MediaItem.Builder()
                .setMediaId(it.id.toString())
                .setUri(it.uri)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(it.title)
                        .setArtist(it.artist)
                        .setAlbumTitle(it.album)
                        .setArtworkUri(
                            android.content.ContentUris.withAppendedId(
                                android.net.Uri.parse("content://media/external/audio/albumart"),
                                it.albumId
                            )
                        )
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
    
    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            playlistRepository.removeSongFromPlaylist(playlistId, songId)
        }
    }

    private var originalSongs = listOf<Song>()

    fun loadSongs() {
        viewModelScope.launch {
            val songList = withContext(Dispatchers.IO) {
                val songs = mutableListOf<Song>()
                
                // Get all volume names for Android 10+ to ensure SD card is included
                val volumeNames = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    try {
                        MediaStore.getExternalVolumeNames(getApplication())
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

                    getApplication<Application>().contentResolver.query(
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
            
            // Apply custom metadata overrides from local database
            val customMetadataMap = customMetadataDao.getAllCustomMetadata().associateBy { it.songId }
            val songsWithOverrides = songList.map { song ->
                val override = customMetadataMap[song.id]
                if (override != null) {
                    song.copy(
                        title = override.customTitle ?: song.title,
                        artist = override.customArtist ?: song.artist,
                        album = override.customAlbum ?: song.album
                    )
                } else {
                    song
                }
            }
            
            originalSongs = songsWithOverrides
            _songs.postValue(songsWithOverrides)
            
            // Refresh favorites now that songs are loaded
            if (favoritesPlaylistId != -1L) {
                refreshFavoritesList()
            }
            
            // Generate artists and albums lists
            generateArtistsList(songsWithOverrides)
            generateAlbumsList(songsWithOverrides)
        }
    }
    
    private fun generateArtistsList(songs: List<Song>) {
        val artistMap = songs.groupBy { it.artist }
        val artistsList = artistMap.map { (artistName, artistSongs) ->
            Artist(
                name = artistName,
                songCount = artistSongs.size,
                albumArtId = artistSongs.firstOrNull()?.albumId ?: 0L
            )
        }.sortedBy { it.name.lowercase() }
        _artists.postValue(artistsList)
    }
    
    private fun generateAlbumsList(songs: List<Song>) {
        val albumMap = songs.groupBy { it.albumId }
        val albumsList = albumMap.map { (albumId, albumSongs) ->
            Album(
                id = albumId,
                name = albumSongs.firstOrNull()?.title?.substringBefore(" - ") ?: "Unknown Album",
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
        
        val titleComparator = Comparator<Song> { s1, s2 ->
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
                getApplication(),
                ComponentName(getApplication(), MusicService::class.java)
            )

            controllerFuture = MediaController.Builder(getApplication(), sessionToken).buildAsync()
            controllerFuture?.addListener(
                {
                    try {
                        val controller = controllerFuture?.get()
                        mediaController.postValue(controller)
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

    fun playSong(song: Song) {
        val controller = mediaController.value ?: return
        val currentList = _songs.value ?: return
        val startIndex = currentList.indexOfFirst { it.id == song.id }
        
        if (startIndex == -1) return

        // Convert Song objects to MediaItems
        val mediaItems = currentList.map { 
            val customCoverPath = customCoverRepository.getCustomCover(it.id)
            val artUri = if (customCoverPath != null) {
                Uri.fromFile(java.io.File(customCoverPath))
            } else {
                ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    it.albumId
                )
            }

            MediaItem.Builder()
                .setMediaId(it.id.toString())
                .setUri(it.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(it.title)
                        .setArtist(it.artist)
                        .setArtworkUri(artUri)
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
        lastPlayedSongId = song.id
        lastPlayedPosition = 0
    }
    
    fun playSongFromPosition(song: Song, positionMs: Long) {
        val controller = mediaController.value ?: return
        val currentList = _songs.value ?: return
        val startIndex = currentList.indexOfFirst { it.id == song.id }
        
        if (startIndex == -1) return

        val mediaItems = currentList.map { 
            MediaItem.Builder()
                .setMediaId(it.id.toString())
                .setUri(it.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(it.title)
                        .setArtist(it.artist)
                        .setArtworkUri(
                            ContentUris.withAppendedId(
                                Uri.parse("content://media/external/audio/albumart"),
                                it.albumId
                            )
                        )
                        .build()
                )
                .build()
        }

        controller.setMediaItems(mediaItems)
        controller.seekTo(startIndex, positionMs)
        controller.prepare()
        controller.play()
        
        lastPlayedSongId = song.id
        
        // Track play count
        incrementPlayCount(song.id)
    }
    
    fun savePosition() {
        val controller = mediaController.value ?: return
        lastPlayedPosition = controller.currentPosition
    }
    
    // Play Count Tracking
    fun incrementPlayCount(songId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            playCountDao.ensureExists(songId)
            playCountDao.incrementPlayCount(songId)
        }
    }
    
    suspend fun getPlayCount(songId: Long): Int {
        return playCountDao.getPlayCount(songId) ?: 0
    }
    
    suspend fun getMostPlayedSongs(limit: Int = 20): List<Song> {
        val playCounts = playCountDao.getMostPlayed(limit)
        val allSongs = _songs.value ?: return emptyList()
        return playCounts.mapNotNull { pc -> allSongs.find { it.id == pc.songId } }
    }
    
    fun loadMostPlayed() {
        viewModelScope.launch(Dispatchers.IO) {
            val songs = getMostPlayedSongs(20)
            _mostPlayed.postValue(songs)
        }
    }
    
    fun getFolders(): List<SongListItem.FolderItem> {
        val allSongs = _songs.value ?: emptyList()
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
        val allSongs = _songs.value ?: emptyList()
        return allSongs.filter { 
             try {
                val file = java.io.File(it.data)
                file.parentFile?.path == path
            } catch (e: Exception) {
                false
            }
        }.sortedBy { it.title }.map { SongListItem.SongItem(it) }
    }
    
    fun updateSongTags(song: Song, title: String, artist: String, album: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Save to local database instead of trying to modify MediaStore
                // This works around Android 10+ restrictions
                val customMetadata = com.wayne.musicdeck.data.CustomMetadata(
                    songId = song.id,
                    customTitle = title,
                    customArtist = artist,
                    customAlbum = album
                )
                customMetadataDao.insertOrUpdate(customMetadata)
                
                // Reload songs to apply the new override
                loadSongs()
                
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(getApplication(), "Tags saved! (Changes visible in app only)", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(getApplication(), "Failed to save: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
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
    
    // Force update after permission granted
    fun updateSongTagsForce(song: Song, title: String, artist: String, album: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
                val values = android.content.ContentValues().apply {
                    put(MediaStore.Audio.Media.TITLE, title)
                    put(MediaStore.Audio.Media.ARTIST, artist)
                    put(MediaStore.Audio.Media.ALBUM, album)
                }
                
                val rowsUpdated = getApplication<Application>().contentResolver.update(uri, values, null, null)
                
                if (rowsUpdated > 0) {
                    loadSongs()
                    withContext(Dispatchers.Main) {
                         android.widget.Toast.makeText(getApplication(), "Tags updated! Refresh may take a moment.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(getApplication(), "Update failed - Android may not allow modifying this file", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(getApplication(), "Failed to update: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    // Backup/Restore
    private val _backupResult = MutableLiveData<String?>()
    val backupResult: LiveData<String?> = _backupResult
    
    fun exportBackup() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allPlaylists = playlistRepository.getAllPlaylists()
                val playlistsWithSongs = allPlaylists.map { playlist ->
                    playlist to playlistRepository.getSongsForPlaylist(playlist.id)
                }
                val json = BackupHelper.exportToJson(playlistsWithSongs)
                val success = BackupHelper.saveBackup(getApplication(), json)
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
                val json = BackupHelper.loadBackup(getApplication())
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
                    for (songId in playlistBackup.songIds) {
                        playlistRepository.addSongToPlaylist(playlistId, songId)
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
                val context = getApplication<Application>()
                
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
                customCoverRepository.saveCustomCover(song.id, customCoverFile.absolutePath)
                
                // 5. Update UI
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Custom cover updated!", android.widget.Toast.LENGTH_SHORT).show()
                    // Force refresh list to pick up new path from Prefs
                    loadSongs()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(getApplication(), "Failed to set cover: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    fun removeCustomCover(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Get current path and delete file
                val path = customCoverRepository.getCustomCover(song.id)
                if (path != null) {
                    val file = java.io.File(path)
                    file.delete()
                }
                
                // Remove from repository
                customCoverRepository.removeCustomCover(song.id)
                
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(getApplication(), "Custom cover removed", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(getApplication(), "Failed to remove cover", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    fun getCustomCoverPath(songId: Long): String? {
        return customCoverRepository.getCustomCover(songId)
    }
    
    fun hasCustomCover(songId: Long): Boolean {
        return customCoverRepository.hasCustomCover(songId)
    }
    
    // Lyrics Management
    fun setLyricFile(song: Song, fileUri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Copy lyric file to internal storage
                val inputStream = getApplication<Application>().contentResolver.openInputStream(fileUri)
                val lyricFile = java.io.File(getApplication<Application>().filesDir, "lyric_${song.id}.lrc")
                val outputStream = java.io.FileOutputStream(lyricFile)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()
                
                // Save path to repository
                lyricsRepository.saveLyricPath(song.id, lyricFile.absolutePath)
                
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(getApplication(), "Lyric file set!", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(getApplication(), "Failed to set lyric file", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    fun getLyricPath(songId: Long): String? {
        return lyricsRepository.getLyricPath(songId)
    }
    
    fun hasLyrics(songId: Long): Boolean {
        return lyricsRepository.hasLyrics(songId)
    }
    
    fun parseLyrics(songId: Long): List<com.wayne.musicdeck.data.LyricLine> {
        val path = lyricsRepository.getLyricPath(songId) ?: return emptyList()
        return lyricsRepository.parseLrcFile(path)
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

    override fun onCleared() {
        getApplication<Application>().contentResolver.unregisterContentObserver(contentObserver)
        super.onCleared()
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }
}
