package com.wayne.musicdeck.utils

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.FileProvider
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.transform.RoundedCornersTransformation
import com.wayne.musicdeck.R
import com.wayne.musicdeck.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object LyricCardGenerator {

    enum class CardTheme(val backgroundRes: Int, val displayName: String) {
        VIOLET(R.drawable.bg_share_card_canvas, "Violet"),
        MIDNIGHT(R.drawable.bg_card_midnight, "Midnight"),
        ROSE(R.drawable.bg_card_rose, "Rose"),
        CYAN(R.drawable.bg_card_cyan, "Cyan")
    }

    suspend fun generateAndShare(
        activity: Activity,
        song: Song?,
        lyricLines: List<String>,
        theme: CardTheme
    ) = withContext(Dispatchers.Main) {
        if (song == null || lyricLines.isEmpty()) return@withContext

        val inflater = LayoutInflater.from(activity)
        val cardView = inflater.inflate(R.layout.layout_shareable_lyric_card, null, false)

        val cardRoot = cardView.findViewById<View>(R.id.cardRoot)
        val tvCardTitle = cardView.findViewById<TextView>(R.id.tvCardTitle)
        val tvCardArtist = cardView.findViewById<TextView>(R.id.tvCardArtist)
        val tvCardLyrics = cardView.findViewById<TextView>(R.id.tvCardLyrics)
        val ivCardCover = cardView.findViewById<ImageView>(R.id.ivCardCover)

        cardRoot.setBackgroundResource(theme.backgroundRes)
        tvCardTitle.text = song.title
        tvCardArtist.text = song.artist
        tvCardLyrics.text = lyricLines.joinToString("\n")

        // Load Album Art synchronously for Bitmap capture
        try {
            val imageLoader = coil.Coil.imageLoader(activity)
            val request = ImageRequest.Builder(activity)
                .data(File(song.data))
                .transformations(RoundedCornersTransformation(16f))
                .allowHardware(false)
                .build()
            val result = imageLoader.execute(request)
            if (result is SuccessResult) {
                ivCardCover.setImageDrawable(result.drawable)
            } else if (song.albumId > 0) {
                val albumArtUri = android.content.ContentUris.withAppendedId(
                    android.net.Uri.parse("content://media/external/audio/albumart"),
                    song.albumId
                )
                val uriRequest = ImageRequest.Builder(activity)
                    .data(albumArtUri)
                    .transformations(RoundedCornersTransformation(16f))
                    .allowHardware(false)
                    .build()
                val uriResult = imageLoader.execute(uriRequest)
                if (uriResult is SuccessResult) {
                    ivCardCover.setImageDrawable(uriResult.drawable)
                }
            }
        } catch (_: Exception) {}

        // Measure & Layout off-screen card (1080px wide for crystal-clear social sharing)
        val widthSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        cardView.measure(widthSpec, heightSpec)
        val totalWidth = 1080
        val totalHeight = cardView.measuredHeight
        cardView.layout(0, 0, totalWidth, totalHeight)

        // Render to Bitmap
        val bitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        cardView.draw(canvas)

        // Save Bitmap to cache & share
        withContext(Dispatchers.IO) {
            try {
                val cacheDir = File(activity.cacheDir, "lyric_cards").apply { mkdirs() }
                val cardFile = File(cacheDir, "musicdeck_lyrics_${System.currentTimeMillis()}.png")
                FileOutputStream(cardFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                val contentUri = FileProvider.getUriForFile(
                    activity,
                    "${activity.packageName}.fileprovider",
                    cardFile
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_TEXT, "♫ \"${song.title}\" — ${song.artist}\n#MusicDeck")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                withContext(Dispatchers.Main) {
                    activity.startActivity(Intent.createChooser(shareIntent, "Share Lyric Card"))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(activity, "Failed to export card: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
