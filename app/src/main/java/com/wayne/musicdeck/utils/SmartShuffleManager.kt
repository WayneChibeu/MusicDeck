package com.wayne.musicdeck.utils

import kotlin.math.max

object SmartShuffleManager {

    /**
     * Smart Anti-Repeat Shuffle Algorithm:
     * 1. Inspects the last-played timestamp of every item using both SettingsManager recent history
     *    and the Room PlayCount database.
     * 2. Items not heard in the cooldown window (default 24h) are considered "fresh / cold".
     * 3. Items heard recently are considered "cooldown / warm" and pushed towards the back of the queue,
     *    ordered oldest-played first.
     * 4. If almost all items have been played recently (e.g. small playlist or binge session),
     *    the least-recently played half is treated as the fresh pool, guaranteeing no recent repeats.
     * 5. If [currentlyPlayingPath] is provided, it is kept at index 0 or excluded from being picked next.
     */
    fun <T> smartShuffle(
        items: List<T>,
        getPath: (T) -> String,
        settingsManager: SettingsManager,
        playCountMap: Map<String, Long> = emptyMap(),
        currentlyPlayingPath: String? = null,
        cooldownWindowMs: Long = 24 * 60 * 60 * 1000L // 24 hours
    ): List<T> {
        if (items.size <= 2) {
            return if (currentlyPlayingPath != null) {
                items.sortedBy { if (getPath(it) == currentlyPlayingPath) 0 else 1 }
            } else {
                items.shuffled()
            }
        }

        val now = System.currentTimeMillis()
        val recentMap = settingsManager.getAllRecentPlays()

        // Extract currently playing item if present so it doesn't get relocated or repeated
        val currentItem = if (currentlyPlayingPath != null) items.find { getPath(it) == currentlyPlayingPath } else null
        val candidates = if (currentItem != null) items.filter { it != currentItem } else items

        // Partition candidates by last-played timestamp
        val scored = candidates.map { item ->
            val path = getPath(item)
            val lastPlayed = max(
                recentMap[path] ?: 0L,
                max(settingsManager.getSongLastPlayed(path), playCountMap[path] ?: 0L)
            )
            item to lastPlayed
        }

        // Fresh / cold: not played within cooldown window (or never played, timestamp == 0)
        val cutoff = now - cooldownWindowMs
        var freshPool = scored.filter { it.second < cutoff }.map { it.first }
        var recentPool = scored.filter { it.second >= cutoff }.sortedBy { it.second }.map { it.first }

        // Adaptive Fallback: If fresh pool is too small (e.g. user listens to the same small playlist repeatedly),
        // take the oldest 50% of the scored list as the fresh pool so the user still gets a varied experience.
        if (freshPool.size < max(1, candidates.size / 3)) {
            val sortedByOldest = scored.sortedBy { it.second }
            val splitIndex = candidates.size / 2
            freshPool = sortedByOldest.take(splitIndex).map { it.first }
            recentPool = sortedByOldest.drop(splitIndex).map { it.first }
        }

        // Shuffle the fresh pool thoroughly with random entropy
        val shuffledFresh = freshPool.shuffled()

        // Recent pool: songs are ordered so the ones heard furthest back in time come earlier,
        // but with small local jitter so it's not strictly deterministic
        val orderedRecent = jitterRecent(recentPool)

        val result = mutableListOf<T>()
        if (currentItem != null) {
            result.add(currentItem)
        }
        result.addAll(shuffledFresh)
        result.addAll(orderedRecent)

        return result
    }

    /**
     * Adds mild local perturbation to the recent pool so that songs from 12 hours ago
     * don't always play in the strict chronological sequence they were originally played.
     */
    private fun <T> jitterRecent(list: List<T>): List<T> {
        if (list.size <= 3) return list
        val chunks = list.chunked(3)
        return chunks.flatMap { it.shuffled() }
    }
}
