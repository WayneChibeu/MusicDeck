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
        val now = System.currentTimeMillis()
        return playCounts.sortedByDescending { pc ->
            val daysSinceLastPlay = kotlin.math.max(0L, (now - pc.lastPlayed) / TimeUnit.DAYS.toMillis(1))
            // 5% decay per day
            pc.playCount.toDouble() * Math.pow(0.95, daysSinceLastPlay.toDouble())
        }
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
    
    fun getChillMode(): List<Song> {
        // Heuristic: Songs longer than 4 minutes (often more atmospheric/chill in some genres)
        return allSongs.filter { it.duration > 240000L }
            .shuffled()
            .take(30)
    }
    
    fun getEnergyBoost(): List<Song> {
        // Heuristic: Songs shorter than 3 minutes (often fast-paced/upbeat)
        return allSongs.filter { it.duration < 180000L }
            .shuffled()
            .take(30)
    }

    companion object {
        const val ID_RECENTLY_ADDED = -100L
        const val ID_HEAVY_ROTATION = -101L
        const val ID_FORGOTTEN_GEMS = -102L
        const val ID_CHILL_MODE = -103L
        const val ID_ENERGY_BOOST = -104L
        
        fun isSmartPlaylist(id: Long): Boolean = id <= -100L
        
        fun getSmartPlaylistName(id: Long): String = when (id) {
            ID_RECENTLY_ADDED -> "Fresh Arrivals"
            ID_HEAVY_ROTATION -> "Heavy Rotation"
            ID_FORGOTTEN_GEMS -> "Forgotten Gems"
            ID_CHILL_MODE -> "Chill Mode"
            ID_ENERGY_BOOST -> "Energy Boost"
            else -> "Auto List"
        }
    }
}
