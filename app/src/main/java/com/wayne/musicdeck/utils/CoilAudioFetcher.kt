package com.wayne.musicdeck.utils

import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CoilAudioFetcher(
    private val data: File,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(data.absolutePath)
            val picture = retriever.embeddedPicture
            retriever.release()

            if (picture != null) {
                // Parse embedded bytes into a UI-ready Bitmap
                val bitmap = BitmapFactory.decodeByteArray(picture, 0, picture.size)
                if (bitmap != null) {
                    return@withContext DrawableResult(
                        drawable = BitmapDrawable(options.context.resources, bitmap),
                        isSampled = false,
                        dataSource = DataSource.DISK
                    )
                }
            }
        } catch (e: Exception) {
            // Silently fail if file is locked or corrupt. Coil will use placeholder.
            e.printStackTrace()
        }
        return@withContext null
    }

    class Factory : Fetcher.Factory<File> {
        override fun create(data: File, options: Options, imageLoader: ImageLoader): Fetcher? {
            val ext = data.extension.lowercase()
            // Only intercept audio files
            if (ext == "mp3" || ext == "flac" || ext == "m4a" || ext == "ogg" || ext == "wav" || 
                ext == "opus" || ext == "webm" || ext == "aac" || ext == "mkv" || ext == "mp4") {
                return CoilAudioFetcher(data, options)
            }
            return null // Let Coil handle regular images
        }
    }
}
