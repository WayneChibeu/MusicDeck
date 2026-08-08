package com.wayne.musicdeck

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import coil.load
import coil.transform.RoundedCornersTransformation
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import org.koin.androidx.viewmodel.ext.android.viewModel

class InsightsActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_insights)

        // Setup Toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val tvTotalPlays = findViewById<TextView>(R.id.tvTotalPlays)
        val tvUniqueArtists = findViewById<TextView>(R.id.tvUniqueArtists)
        val tvUniqueAlbums = findViewById<TextView>(R.id.tvUniqueAlbums)
        val tvStreak = findViewById<TextView>(R.id.tvStreak)
        val tvWeeklyPlays = findViewById<TextView>(R.id.tvWeeklyPlays)
        val containerTopSongs = findViewById<LinearLayout>(R.id.containerTopSongs)
        val containerTopArtists = findViewById<LinearLayout>(R.id.containerTopArtists)
        val btnShareInsights = findViewById<MaterialButton>(R.id.btnShareInsights)

        var shareStatsText = "I've been listening to a lot of music! 🎧\n"

        lifecycleScope.launch {
            val db = com.wayne.musicdeck.data.MusicDatabase.getDatabase(this@InsightsActivity)
            val dao = db.playHistoryDao()
            
            val sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
            val weeklyPlays = withContext(Dispatchers.IO) { dao.getPlaysSince(sevenDaysAgo) }
            tvWeeklyPlays.text = weeklyPlays.toString()
            
            // Calculate streak
            val playDays = withContext(Dispatchers.IO) { dao.getDistinctPlayDays() }
            var currentStreak = 0
            val todayDay = System.currentTimeMillis() / 86400000
            
            var expectedDay = todayDay
            for (day in playDays) {
                if (day == expectedDay) {
                    currentStreak++
                    expectedDay--
                } else if (day == expectedDay - 1 && currentStreak == 0) {
                    // Didn't play today yet, but streak started yesterday
                    currentStreak++
                    expectedDay = day - 1
                } else {
                    break
                }
            }
            tvStreak.text = "$currentStreak Days"
        }

        viewModel.insights.observe(this) { insights ->
            tvTotalPlays.text = insights.totalPlays.toString()
            tvUniqueArtists.text = insights.uniqueArtistsCount.toString()
            tvUniqueAlbums.text = insights.uniqueAlbumsCount.toString()

            shareStatsText = "🎵 My MusicDeck Diary 🎵\n" +
                    "Total Plays: ${insights.totalPlays}\n" +
                    "Unique Artists: ${insights.uniqueArtistsCount}\n" +
                    "Top Song: ${insights.topSongs.firstOrNull()?.first?.title ?: "N/A"}\n" +
                    "Top Artist: ${insights.topArtists.firstOrNull()?.first ?: "N/A"}\n"

            // Populate Top Songs with SMART MATCH for Album Art
            containerTopSongs.removeAllViews()
            val maxPlaysSongs = insights.topSongs.firstOrNull()?.second ?: 1
            
            // Get current local songs to match against
            val localSongs = viewModel.songs.value ?: emptyList()

            insights.topSongs.forEach { (songEntity, count) ->
                val rowView = LayoutInflater.from(this).inflate(R.layout.item_insight_song_row, containerTopSongs, false)
                rowView.findViewById<TextView>(R.id.tvRowName).text = songEntity.title
                rowView.findViewById<TextView>(R.id.tvRowCount).text = "${count} plays"
                
                val progressBar = rowView.findViewById<ProgressBar>(R.id.rowProgress)
                progressBar.max = maxPlaysSongs
                progressBar.progress = count
                
                val ivCover = rowView.findViewById<ImageView>(R.id.ivCoverArt)
                
                // SMART MATCH: Find if the song still exists on the device
                val localMatch = localSongs.find { it.title.equals(songEntity.title, ignoreCase = true) && it.artist.equals(songEntity.artist, ignoreCase = true) }
                
                if (localMatch != null) {
                    ivCover.load(java.io.File(localMatch.data)) {
                        crossfade(true)
                        transformations(RoundedCornersTransformation(16f))
                        error(R.drawable.default_album_art)
                    }
                } else {
                    ivCover.setImageResource(R.drawable.default_album_art)
                }

                containerTopSongs.addView(rowView)
            }

            // Populate Top Artists (Reuse layout, but use default art)
            containerTopArtists.removeAllViews()
            val maxPlaysArtists = insights.topArtists.firstOrNull()?.second ?: 1
            insights.topArtists.forEach { (artistName, count) ->
                val rowView = LayoutInflater.from(this).inflate(R.layout.item_insight_song_row, containerTopArtists, false)
                rowView.findViewById<TextView>(R.id.tvRowName).text = artistName
                rowView.findViewById<TextView>(R.id.tvRowCount).text = "${count} plays"
                
                val progressBar = rowView.findViewById<ProgressBar>(R.id.rowProgress)
                progressBar.max = maxPlaysArtists
                progressBar.progress = count
                
                val ivCover = rowView.findViewById<ImageView>(R.id.ivCoverArt)
                ivCover.setImageResource(R.drawable.default_album_art) // Placeholder
                
                containerTopArtists.addView(rowView)
            }
        }

        btnShareInsights.setOnClickListener {
            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, shareStatsText)
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, null)
            startActivity(shareIntent)
        }

        viewModel.loadInsights()
    }
}
