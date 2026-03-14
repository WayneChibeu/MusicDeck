package com.wayne.musicdeck.data

import com.wayne.musicdeck.Song
import java.util.concurrent.TimeUnit

class SmartPlaylistManager(
    private val allSongs: List<Song>,
    private val playCounts: List<SongPlayCount>
) {
    fun getRecentlyAdded(): List<Song> {
        val sevenDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        return allSongs.filter { it.dateAdded * 1000L > sevenDaysAgo }
            .sortedByDescending { it.dateAdded }
            .take(50)
    }

    fun getHeavyRotation(): List<Song> {
        return playCounts.sortedByDescending { it.playCount }
            .take(30)
            .mapNotNull { pc -> allSongs.find { it.id == pc.songId } }
    }

    fun getForgottenGems(): List<Song> {
        // High play count but hasn't been played in 2 weeks
        val twoWeeksAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(14)
        return playCounts.filter { it.playCount > 5 && it.lastPlayed < twoWeeksAgo }
            .sortedByDescending { it.playCount }
            .take(20)
            .mapNotNull { pc -> allSongs.find { it.id == pc.songId } }
    }

    companion object {
        const val ID_RECENTLY_ADDED = -100L
        const val ID_HEAVY_ROTATION = -101L
        const val ID_FORGOTTEN_GEMS = -102L
        
        fun isSmartPlaylist(id: Long): Boolean = id <= -100L
        
        fun getSmartPlaylistName(id: Long): String = when (id) {
            ID_RECENTLY_ADDED -> "Fresh Arrivals"
            ID_HEAVY_ROTATION -> "Heavy Rotation"
            ID_FORGOTTEN_GEMS -> "Forgotten Gems"
            else -> "Auto List"
        }
    }
}
