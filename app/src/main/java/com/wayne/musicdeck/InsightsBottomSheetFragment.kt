package com.wayne.musicdeck

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class InsightsBottomSheetFragment : BottomSheetDialogFragment() {

    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_insights, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvTotalPlays = view.findViewById<TextView>(R.id.tvTotalPlays)
        val tvUniqueArtists = view.findViewById<TextView>(R.id.tvUniqueArtists)
        val tvUniqueAlbums = view.findViewById<TextView>(R.id.tvUniqueAlbums)
        val containerTopSongs = view.findViewById<LinearLayout>(R.id.containerTopSongs)
        val containerTopArtists = view.findViewById<LinearLayout>(R.id.containerTopArtists)

        viewModel.insights.observe(viewLifecycleOwner) { insights ->
            tvTotalPlays.text = insights.totalPlays.toString()
            tvUniqueArtists.text = insights.uniqueArtistsCount.toString()
            tvUniqueAlbums.text = insights.uniqueAlbumsCount.toString()

            // Populate Top Songs
            containerTopSongs.removeAllViews()
            val maxPlays = insights.topSongs.firstOrNull()?.second ?: 1
            insights.topSongs.forEach { (song, count) ->
                val songView = layoutInflater.inflate(R.layout.item_insight_row, containerTopSongs, false)
                songView.findViewById<TextView>(R.id.tvRowName).text = song.title
                songView.findViewById<TextView>(R.id.tvRowCount).text = "${count} plays"
                
                val progressBar = songView.findViewById<ProgressBar>(R.id.rowProgress)
                progressBar.max = maxPlays
                progressBar.progress = count
                
                containerTopSongs.addView(songView)
            }

            // Populate Top Artists
            containerTopArtists.removeAllViews()
            insights.topArtists.forEach { (artist, count) ->
                val artistView = layoutInflater.inflate(R.layout.item_insight_row, containerTopArtists, false)
                artistView.findViewById<TextView>(R.id.tvRowName).text = artist
                artistView.findViewById<TextView>(R.id.tvRowCount).text = "${count} plays"
                
                // Hide progress bar for artists to keep it clean, or use it?
                // Let's use it for visual consistency
                val progressBar = artistView.findViewById<ProgressBar>(R.id.rowProgress)
                progressBar.max = insights.topArtists.firstOrNull()?.second ?: 1
                progressBar.progress = count
                
                containerTopArtists.addView(artistView)
            }
        }

        viewModel.loadInsights()
    }
}
