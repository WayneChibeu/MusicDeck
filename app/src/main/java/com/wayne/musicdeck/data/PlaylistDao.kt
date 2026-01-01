package com.wayne.musicdeck.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface PlaylistDao {
    // Playlist Operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun createPlaylist(playlist: Playlist): Long

    @androidx.room.Update
    suspend fun updatePlaylist(playlist: Playlist)

    @Query("SELECT * FROM playlists ORDER BY name ASC")
    suspend fun getAllPlaylists(): List<Playlist>

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)
    
    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylistById(playlistId: Long)

    // Song Operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongToPlaylist(playlistSong: PlaylistSong)

    @androidx.room.Update
    suspend fun updatePlaylistSong(playlistSong: PlaylistSong)

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY orderIndex ASC")
    suspend fun getSongsForPlaylist(playlistId: Long): List<PlaylistSong>

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY orderIndex ASC")
    fun getSongsForPlaylistLive(playlistId: Long): androidx.lifecycle.LiveData<List<PlaylistSong>>
    
    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun getSongCountForPlaylist(playlistId: Long): Int

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)
}
