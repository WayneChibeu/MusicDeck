package com.wayne.musicdeck.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
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

    enum class CardTheme(val backgroundRes: Int, val accentColor: Int, val displayName: String) {
        ALBUM_MATCH(0, 0, "Album Match"),
        VIOLET(R.drawable.bg_card_violet, 0xFFA78BFA.toInt(), "Violet"),
        MIDNIGHT(R.drawable.bg_card_midnight, 0xFF60A5FA.toInt(), "Midnight"),
        ROSE(R.drawable.bg_card_rose, 0xFFF472B6.toInt(), "Rose"),
        CYAN(R.drawable.bg_card_cyan, 0xFF22D3EE.toInt(), "Cyan")
    }

    data class ExtractedTheme(
        val backgroundDrawable: GradientDrawable,
        val accentColor: Int
    )

    fun createDynamicTheme(context: Context, bitmap: Bitmap?): ExtractedTheme {
        val density = context.resources.displayMetrics.density
        if (bitmap == null) {
            val gd = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF3B186B.toInt(), 0xFF220D45.toInt(), 0xFF0F0621.toInt())
            ).apply {
                cornerRadius = 28f * density
                setStroke((1.5f * density).toInt(), 0x4DA78BFA.toInt())
            }
            return ExtractedTheme(gd, 0xFFA78BFA.toInt())
        }

        val palette = Palette.from(bitmap).generate()
        val darkVibrant = palette.getDarkVibrantColor(0)
        val darkMuted = palette.getDarkMutedColor(0)
        val vibrant = palette.getVibrantColor(0)
        val dominant = palette.getDominantColor(0)
        val lightVibrant = palette.getLightVibrantColor(0)

        val baseColor = when {
            darkVibrant != 0 -> darkVibrant
            darkMuted != 0 -> darkMuted
            vibrant != 0 -> darkenColor(vibrant, 0.45f)
            dominant != 0 -> darkenColor(dominant, 0.40f)
            else -> 0xFF3B186B.toInt()
        }

        val startColor = darkenColor(baseColor, 0.90f)
        val centerColor = darkenColor(baseColor, 0.60f)
        val endColor = darkenColor(baseColor, 0.25f)

        val accent = when {
            lightVibrant != 0 -> lightVibrant
            vibrant != 0 -> vibrant
            else -> 0xFFA78BFA.toInt()
        }
        val stroke = ColorUtils.setAlphaComponent(accent, 0x4D)

        val gd = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(startColor, centerColor, endColor)
        ).apply {
            cornerRadius = 28f * density
            setStroke((1.5f * density).toInt(), stroke)
        }

        return ExtractedTheme(gd, accent)
    }

    private fun darkenColor(color: Int, factor: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] *= factor
        return Color.HSVToColor(hsv)
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
        val tvCardQuote = cardView.findViewById<TextView>(R.id.tvCardQuote)
        val ivCardCover = cardView.findViewById<ImageView>(R.id.ivCardCover)

        tvCardTitle.text = song.title
        tvCardArtist.text = song.artist
        tvCardLyrics.text = lyricLines.joinToString("\n")

        var loadedBitmap: Bitmap? = null

        // Load Album Art synchronously for Bitmap capture & color extraction
        try {
            val imageLoader = coil.Coil.imageLoader(activity)
            val request = ImageRequest.Builder(activity)
                .data(File(song.data))
                .transformations(RoundedCornersTransformation(20f))
                .allowHardware(false)
                .build()
            val result = imageLoader.execute(request)
            if (result is SuccessResult) {
                ivCardCover.setImageDrawable(result.drawable)
                loadedBitmap = (result.drawable as? BitmapDrawable)?.bitmap
            } else if (song.albumId > 0) {
                val albumArtUri = android.content.ContentUris.withAppendedId(
                    android.net.Uri.parse("content://media/external/audio/albumart"),
                    song.albumId
                )
                val uriRequest = ImageRequest.Builder(activity)
                    .data(albumArtUri)
                    .transformations(RoundedCornersTransformation(20f))
                    .allowHardware(false)
                    .build()
                val uriResult = imageLoader.execute(uriRequest)
                if (uriResult is SuccessResult) {
                    ivCardCover.setImageDrawable(uriResult.drawable)
                    loadedBitmap = (uriResult.drawable as? BitmapDrawable)?.bitmap
                }
            }
        } catch (_: Exception) {}

        if (theme == CardTheme.ALBUM_MATCH) {
            val dynamicTheme = createDynamicTheme(activity, loadedBitmap)
            cardRoot.background = dynamicTheme.backgroundDrawable
            tvCardQuote?.setTextColor(dynamicTheme.accentColor)
        } else {
            cardRoot.setBackgroundResource(theme.backgroundRes)
            tvCardQuote?.setTextColor(theme.accentColor)
        }

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
