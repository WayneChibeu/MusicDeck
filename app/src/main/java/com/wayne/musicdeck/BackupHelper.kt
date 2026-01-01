package com.wayne.musicdeck

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import com.wayne.musicdeck.data.Playlist
import com.wayne.musicdeck.data.PlaylistSong
import java.io.File

object BackupHelper {
    
    data class BackupData(
        val playlists: List<PlaylistBackup>
    )
    
    data class PlaylistBackup(
        val name: String,
        val songIds: List<Long>
    )
    
    fun exportToJson(playlists: List<Pair<Playlist, List<PlaylistSong>>>): String {
        val jsonArray = JSONArray()
        
        for ((playlist, songs) in playlists) {
            val playlistJson = JSONObject().apply {
                put("name", playlist.name)
                put("songs", JSONArray(songs.map { it.songId }))
            }
            jsonArray.put(playlistJson)
        }
        
        val root = JSONObject().apply {
            put("version", 1)
            put("app", "MusicDeck")
            put("exportDate", System.currentTimeMillis())
            put("playlists", jsonArray)
        }
        
        return root.toString(2)
    }
    
    fun parseFromJson(json: String): BackupData? {
        return try {
            val root = JSONObject(json)
            val playlistsArray = root.getJSONArray("playlists")
            
            val playlists = mutableListOf<PlaylistBackup>()
            for (i in 0 until playlistsArray.length()) {
                val playlistJson = playlistsArray.getJSONObject(i)
                val name = playlistJson.getString("name")
                val songsArray = playlistJson.getJSONArray("songs")
                
                val songIds = mutableListOf<Long>()
                for (j in 0 until songsArray.length()) {
                    songIds.add(songsArray.getLong(j))
                }
                
                playlists.add(PlaylistBackup(name, songIds))
            }
            
            BackupData(playlists)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    fun getBackupFile(context: Context): File {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(dir, "musicdeck_backup.json")
    }
    
    fun saveBackup(context: Context, json: String): Boolean {
        return try {
            getBackupFile(context).writeText(json)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    fun loadBackup(context: Context): String? {
        return try {
            val file = getBackupFile(context)
            if (file.exists()) file.readText() else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
