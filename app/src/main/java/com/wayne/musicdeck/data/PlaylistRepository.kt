package com.wayne.musicdeck.data

class PlaylistRepository(private val playlistDao: PlaylistDao) {

    suspend fun createPlaylist(name: String): Long {
        val playlist = Playlist(name = name)
        return playlistDao.createPlaylist(playlist)
    }

    suspend fun getAllPlaylists(): List<Playlist> {
        return playlistDao.getAllPlaylists()
    }
    
    suspend fun updatePlaylist(playlist: Playlist) {
        playlistDao.updatePlaylist(playlist)
    }
    
    suspend fun deletePlaylist(playlist: Playlist) {
        playlistDao.deletePlaylist(playlist)
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) {
        // Get current count to determine order
        val count = playlistDao.getSongCountForPlaylist(playlistId)
        val playlistSong = PlaylistSong(
            playlistId = playlistId,
            songId = songId,
            orderIndex = count
        )
        playlistDao.addSongToPlaylist(playlistSong)
    }

    suspend fun getSongsForPlaylist(playlistId: Long): List<PlaylistSong> {
        return playlistDao.getSongsForPlaylist(playlistId)
    }

    fun getSongsForPlaylistLive(playlistId: Long): androidx.lifecycle.LiveData<List<PlaylistSong>> {
        return playlistDao.getSongsForPlaylistLive(playlistId)
    }

    suspend fun getSongCountForPlaylist(playlistId: Long): Int {
        return playlistDao.getSongCountForPlaylist(playlistId)
    }
    
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        playlistDao.removeSongFromPlaylist(playlistId, songId)
    }

    suspend fun reorderPlaylist(playlistId: Long, fromPos: Int, toPos: Int) {
        val songs = playlistDao.getSongsForPlaylist(playlistId).toMutableList()
        if (fromPos < 0 || fromPos >= songs.size || toPos < 0 || toPos >= songs.size) return
        
        val item = songs.removeAt(fromPos)
        songs.add(toPos, item)
        
        // Update indices and save
        songs.forEachIndexed { index, playlistSong ->
            if (playlistSong.orderIndex != index) {
                // Only update changed ones
                playlistDao.updatePlaylistSong(playlistSong.copy(orderIndex = index))
            }
        }
    }

    suspend fun getOrCreateFavoritesPlaylist(): Playlist {
        // Try to find one named "Favorites"
        val existing = playlistDao.getAllPlaylists().find { it.name == "Favorites" }
        if (existing != null) return existing
        
        // Create if not exists
        val id = playlistDao.createPlaylist(Playlist(name = "Favorites", createdAt = System.currentTimeMillis()))
        return Playlist(id = id, name = "Favorites", createdAt = System.currentTimeMillis())
    }
    suspend fun isSongInPlaylist(playlistId: Long, songId: Long): Boolean {
        // Efficient enough for now
        return playlistDao.getSongsForPlaylist(playlistId).any { it.songId == songId }
    }
}
