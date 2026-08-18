package com.wayne.musicdeck.utils

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.FileProvider
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.transform.RoundedCornersTransformation
import com.wayne.musicdeck.MainViewModel
import com.wayne.musicdeck.R
import com.wayne.musicdeck.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object InsightsShareHelper {

    suspend fun generateAndShareCard(
        activity: Activity,
        insights: MainViewModel.ListeningInsights,
        streakDays: Int,
        localSongs: List<Song>
    ) {
        withContext(Dispatchers.Main) {
            val inflater = LayoutInflater.from(activity)
            val cardView = inflater.inflate(R.layout.layout_insights_share_card, null, false)

            val tvHeroTitle = cardView.findViewById<TextView>(R.id.tvHeroTitle)
            val tvHeroArtist = cardView.findViewById<TextView>(R.id.tvHeroArtist)
            val tvHeroPlays = cardView.findViewById<TextView>(R.id.tvHeroPlays)
            val ivHeroCover = cardView.findViewById<ImageView>(R.id.ivHeroCover)

            val tvStatTotalPlays = cardView.findViewById<TextView>(R.id.tvStatTotalPlays)
            val tvStatStreak = cardView.findViewById<TextView>(R.id.tvStatStreak)
            val tvStatArtists = cardView.findViewById<TextView>(R.id.tvStatArtists)

            val tvRank1Title = cardView.findViewById<TextView>(R.id.tvRank1Title)
            val tvRank1Artist = cardView.findViewById<TextView>(R.id.tvRank1Artist)
            val tvRank1Plays = cardView.findViewById<TextView>(R.id.tvRank1Plays)
            val rowRank1 = cardView.findViewById<View>(R.id.rowRank1)

            val tvRank2Title = cardView.findViewById<TextView>(R.id.tvRank2Title)
            val tvRank2Artist = cardView.findViewById<TextView>(R.id.tvRank2Artist)
            val tvRank2Plays = cardView.findViewById<TextView>(R.id.tvRank2Plays)
            val rowRank2 = cardView.findViewById<View>(R.id.rowRank2)

            val tvRank3Title = cardView.findViewById<TextView>(R.id.tvRank3Title)
            val tvRank3Artist = cardView.findViewById<TextView>(R.id.tvRank3Artist)
            val tvRank3Plays = cardView.findViewById<TextView>(R.id.tvRank3Plays)
            val rowRank3 = cardView.findViewById<View>(R.id.rowRank3)

            val tvArtistPill1 = cardView.findViewById<TextView>(R.id.tvArtistPill1)
            val tvArtistPill2 = cardView.findViewById<TextView>(R.id.tvArtistPill2)
            val tvArtistPill3 = cardView.findViewById<TextView>(R.id.tvArtistPill3)

            // 1. Bind Hero Top Song
            val topSongEntry = insights.topSongs.firstOrNull()
            if (topSongEntry != null) {
                val (songEntity, count) = topSongEntry
                tvHeroTitle.text = songEntity.title
                tvHeroArtist.text = songEntity.artist
                tvHeroPlays.text = "$count plays"

                // Load Hero Cover Art synchronously with CoilAudioFetcher & MediaStore fallbacks
                val match = localSongs.find {
                    it.title.equals(songEntity.title, ignoreCase = true) &&
                    it.artist.equals(songEntity.artist, ignoreCase = true)
                } ?: localSongs.find { it.id == songEntity.id }

                if (match != null) {
                    try {
                        val imageLoader = coil.Coil.imageLoader(activity)
                        // Try 1: Embedded audio metadata via CoilAudioFetcher
                        val request = ImageRequest.Builder(activity)
                            .data(File(match.data))
                            .transformations(RoundedCornersTransformation(20f))
                            .allowHardware(false)
                            .build()
                        var result = imageLoader.execute(request)
                        
                        // Try 2: MediaStore album art URI if embedded was empty
                        if (result !is SuccessResult) {
                            val albumArtUri = android.content.ContentUris.withAppendedId(
                                android.net.Uri.parse("content://media/external/audio/albumart"),
                                match.albumId
                            )
                            val uriRequest = ImageRequest.Builder(activity)
                                .data(albumArtUri)
                                .transformations(RoundedCornersTransformation(20f))
                                .allowHardware(false)
                                .build()
                            result = imageLoader.execute(uriRequest)
                        }

                        if (result is SuccessResult) {
                            ivHeroCover.setImageDrawable(result.drawable)
                        } else {
                            ivHeroCover.setImageResource(R.drawable.default_album_art)
                        }
                    } catch (e: Exception) {
                        ivHeroCover.setImageResource(R.drawable.default_album_art)
                    }
                } else {
                    ivHeroCover.setImageResource(R.drawable.default_album_art)
                }
            } else {
                tvHeroTitle.text = "No plays yet"
                tvHeroArtist.text = "Start listening to track your journey!"
                tvHeroPlays.text = "0 plays"
                ivHeroCover.setImageResource(R.drawable.default_album_art)
            }

            // 2. Bind Stats
            tvStatTotalPlays.text = insights.totalPlays.toString()
            tvStatStreak.text = "$streakDays Days"
            tvStatArtists.text = insights.uniqueArtistsCount.toString()

            // 3. Bind Top 3 Leaderboard
            val topSongs = insights.topSongs
            if (topSongs.isNotEmpty()) {
                val song1 = topSongs[0]
                tvRank1Title.text = song1.first.title
                tvRank1Artist.text = song1.first.artist
                tvRank1Plays.text = "${song1.second} plays"
                rowRank1.visibility = View.VISIBLE
            } else {
                rowRank1.visibility = View.GONE
            }

            if (topSongs.size > 1) {
                val song2 = topSongs[1]
                tvRank2Title.text = song2.first.title
                tvRank2Artist.text = song2.first.artist
                tvRank2Plays.text = "${song2.second} plays"
                rowRank2.visibility = View.VISIBLE
            } else {
                rowRank2.visibility = View.GONE
            }

            if (topSongs.size > 2) {
                val song3 = topSongs[2]
                tvRank3Title.text = song3.first.title
                tvRank3Artist.text = song3.first.artist
                tvRank3Plays.text = "${song3.second} plays"
                rowRank3.visibility = View.VISIBLE
            } else {
                rowRank3.visibility = View.GONE
            }

            // 4. Bind Top Artists Pills
            val topArtists = insights.topArtists
            if (topArtists.isNotEmpty()) {
                tvArtistPill1.text = "1. ${topArtists[0].first}"
                tvArtistPill1.visibility = View.VISIBLE
            } else {
                tvArtistPill1.visibility = View.GONE
            }

            if (topArtists.size > 1) {
                tvArtistPill2.text = "2. ${topArtists[1].first}"
                tvArtistPill2.visibility = View.VISIBLE
            } else {
                tvArtistPill2.visibility = View.GONE
            }

            if (topArtists.size > 2) {
                tvArtistPill3.text = "3. ${topArtists[2].first}"
                tvArtistPill3.visibility = View.VISIBLE
            } else {
                tvArtistPill3.visibility = View.GONE
            }

            // 5. Render View onto High-Resolution Bitmap
            val density = activity.resources.displayMetrics.density
            val targetWidthPx = (380 * density).toInt().coerceAtLeast(1080)
            val widthSpec = View.MeasureSpec.makeMeasureSpec(targetWidthPx, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

            cardView.measure(widthSpec, heightSpec)
            val targetHeightPx = cardView.measuredHeight
            cardView.layout(0, 0, targetWidthPx, targetHeightPx)

            val bitmap = Bitmap.createBitmap(targetWidthPx, targetHeightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            cardView.draw(canvas)

            // 6. Save Bitmap to Cache and Fire Share Intent
            val cacheFile = File(activity.cacheDir, "musicdeck_insights_card.png")
            withContext(Dispatchers.IO) {
                FileOutputStream(cacheFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }

            val contentUri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                cacheFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, "Check out my MusicDeck listening stats!")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            activity.startActivity(Intent.createChooser(shareIntent, "Share Insights Card"))
        }
    }
}
