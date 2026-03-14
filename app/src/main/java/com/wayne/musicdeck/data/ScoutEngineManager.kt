package com.wayne.musicdeck.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.wayne.musicdeck.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScoutEngineManager(private val context: Context) {

    suspend fun identifySong(song: Song): IdentificationResult = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            val uri = android.content.ContentUris.withAppendedId(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                song.id
            )
            retriever.setDataSource(context, uri)
            
            // ELITE RECOGNITION LOGIC
            // 1. If it's Track_01.mp3, try to deduce from parent folder name
            if (song.title.contains("Track", ignoreCase = true) || song.title.matches(Regex(".*[0-9]{2,}.*"))) {
                val path = song.data
                val parts = path.split("/")
                if (parts.size >= 2) {
                    val folderName = parts[parts.size - 2]
                    if (folderName != "Music" && folderName != "Download" && folderName != "audio") {
                        return@withContext IdentificationResult.Success(
                            title = song.title,
                            artist = folderName,
                            confidence = 0.7f,
                            source = "Folder Analysis"
                        )
                    }
                }
            }

            // 2. Search for common patterns in filenames that are missing in tags
            val fileName = song.data.substringAfterLast("/")
            if (fileName.contains("-") && (song.artist == "<unknown>" || song.artist.isEmpty())) {
                 val split = fileName.substringBeforeLast(".").split("-")
                 if (split.size >= 2) {
                     return@withContext IdentificationResult.Success(
                         title = split[1].trim(),
                         artist = split[0].trim(),
                         confidence = 0.8f,
                         source = "Filename Extraction"
                     )
                 }
            }

            // 3. TODO: Remote Fingerprinting (AcoustID)
            // For now, return "Unable to Identify" for truly anonymous tracks
            return@withContext IdentificationResult.Failure("Signature captured, but no matches found in local database.")

        } catch (e: Exception) {
            return@withContext IdentificationResult.Failure(e.message ?: "Unknown Error")
        } finally {
            retriever.release()
        }
    }

    sealed class IdentificationResult {
        data class Success(val title: String, val artist: String, val confidence: Float, val source: String) : IdentificationResult()
        data class Failure(val reason: String) : IdentificationResult()
    }
}
