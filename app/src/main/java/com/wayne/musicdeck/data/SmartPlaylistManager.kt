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
        val scored = allSongs
            .map { song -> Pair(song, calculateChillScore(song)) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }

        return if (scored.isNotEmpty()) {
            scored.take(35)
        } else {
            // Intelligent fallback: spacious duration (>= 3m45s) and strictly no energetic markers
            allSongs.filter { song ->
                song.duration >= 225_000L && !hasEnergyMarkers(song)
            }.shuffled().take(30)
        }
    }
    
    fun getEnergyBoost(): List<Song> {
        val scored = allSongs
            .map { song -> Pair(song, calculateEnergyScore(song)) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }

        return if (scored.isNotEmpty()) {
            scored.take(35)
        } else {
            // Intelligent fallback: standard upbeat duration (2m to 3m45s) and strictly no chill/ballad markers
            allSongs.filter { song ->
                song.duration in 120_000L..225_000L && !hasChillMarkers(song)
            }.shuffled().take(30)
        }
    }

    private fun getSongSearchText(song: Song): String {
        return "${song.title} ${song.artist} ${song.album} ${song.data}".lowercase()
    }

    private fun hasEnergyMarkers(song: Song): Boolean {
        val text = getSongSearchText(song)
        return ENERGY_KEYWORDS.any { text.contains(it) }
    }

    private fun hasChillMarkers(song: Song): Boolean {
        val text = getSongSearchText(song)
        return CHILL_KEYWORDS.any { text.contains(it) }
    }

    private fun calculateEnergyScore(song: Song): Int {
        val text = getSongSearchText(song)
        var score = 0

        // Keyword matches
        for (kw in ENERGY_KEYWORDS) {
            if (text.contains(kw)) score += 4
        }
        for (kw in CHILL_KEYWORDS) {
            if (text.contains(kw)) score -= 6
        }

        // Duration heuristics
        when {
            song.duration < 45_000L -> score -= 10 // Ringtone / skit
            song.duration in 105_000L..220_000L -> score += 2 // Ideal upbeat pop/dance/rock radio edit
            song.duration > 360_000L -> if (score < 4) score -= 3 // Too long unless explicitly energetic
        }

        return score
    }

    private fun calculateChillScore(song: Song): Int {
        val text = getSongSearchText(song)
        var score = 0

        for (kw in CHILL_KEYWORDS) {
            if (text.contains(kw)) score += 4
        }
        for (kw in ENERGY_KEYWORDS) {
            if (text.contains(kw)) score -= 6
        }

        when {
            song.duration < 45_000L -> score -= 10
            song.duration >= 210_000L -> score += 2 // Spacious/ambient length
            song.duration < 120_000L -> if (score < 4) score -= 3
        }

        return score
    }

    private val ENERGY_KEYWORDS = listOf(
        "remix", "dance", "club", "edm", "techno", "house", "trance", "electro", "electronic",
        "trap", "dubstep", "dnb", "drum & bass", "drum and bass", "rock", "metal", "punk",
        "hardcore", "hardstyle", "workout", "fitness", "gym", "hype", "upbeat", "party",
        "festival", "psytrance", "jump", "speed", "fast", "energetic", "power", "boost",
        "bass", "beat", "drop", "action", "drive", "running", "rave"
    )

    private val CHILL_KEYWORDS = listOf(
        "acoustic", "lofi", "lo-fi", "piano", "ambient", "chill", "chillout", "slow", "sleep",
        "ballad", "quiet", "peace", "peaceful", "relax", "relaxing", "unplugged", "meditation",
        "soft", "calm", "mellow", "downtempo", "coffee", "study", "rain", "night", "evening",
        "serenade", "guitar", "instrumental", "zen", "soothe"
    )

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
