package com.wayne.musicdeck.data

import org.json.JSONArray
import org.json.JSONObject

data class EQPreset(
    val name: String,
    val bands: IntArray, // 5 bands (normalized 0-100)
    val bassBoost: Int = 0, // 0-1000
    val virtualizer: Int = 0, // 0-1000
    val crossfeed: Int = 0, // 0-1000 (Bauer Binaural DSP)
    val crossfeedMode: Int = 2, // 1: Meier, 2: Bauer, 3: Chu Moy
    val extremeBass: Boolean = false,
    val volumeBoost: Int = 0, // gain in mB
    val author: String = "MusicDeck User",
    val timestamp: Long = System.currentTimeMillis(),
    val version: Int = 1
) {
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("format", FORMAT_IDENTIFIER)
        obj.put("version", version)
        obj.put("name", name)
        obj.put("author", author)
        obj.put("timestamp", timestamp)
        
        val bandsArray = JSONArray()
        bands.forEach { bandsArray.put(it) }
        obj.put("bands", bandsArray)
        
        obj.put("bassBoost", bassBoost)
        obj.put("virtualizer", virtualizer)
        obj.put("crossfeed", crossfeed)
        obj.put("crossfeedMode", crossfeedMode)
        obj.put("extremeBass", extremeBass)
        obj.put("volumeBoost", volumeBoost)
        return obj.toString(2)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EQPreset
        return name == other.name && bands.contentEquals(other.bands)
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + bands.contentHashCode()
        return result
    }

    companion object {
        const val FORMAT_IDENTIFIER = "MusicDeck.EQPreset"
        const val EXTENSION = ".deck"

        fun fromJson(jsonStr: String): EQPreset? {
            return try {
                val obj = JSONObject(jsonStr)
                val format = obj.optString("format", "")
                if (format != FORMAT_IDENTIFIER && !obj.has("bands")) {
                    return null
                }
                val name = obj.optString("name", "Imported Preset")
                val author = obj.optString("author", "Community")
                val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                val version = obj.optInt("version", 1)
                
                val bandsJson = obj.getJSONArray("bands")
                val bands = IntArray(minOf(bandsJson.length(), 5)) { i ->
                    bandsJson.getInt(i).coerceIn(0, 100)
                }
                if (bands.isEmpty()) return null

                val bassBoost = obj.optInt("bassBoost", 0).coerceIn(0, 1000)
                val virtualizer = obj.optInt("virtualizer", 0).coerceIn(0, 1000)
                val crossfeed = obj.optInt("crossfeed", 0).coerceIn(0, 1000)
                val crossfeedMode = obj.optInt("crossfeedMode", 2).coerceIn(1, 3)
                val extremeBass = obj.optBoolean("extremeBass", false)
                val volumeBoost = obj.optInt("volumeBoost", 0)

                EQPreset(
                    name = name,
                    bands = bands,
                    bassBoost = bassBoost,
                    virtualizer = virtualizer,
                    crossfeed = crossfeed,
                    crossfeedMode = crossfeedMode,
                    extremeBass = extremeBass,
                    volumeBoost = volumeBoost,
                    author = author,
                    timestamp = timestamp,
                    version = version
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
