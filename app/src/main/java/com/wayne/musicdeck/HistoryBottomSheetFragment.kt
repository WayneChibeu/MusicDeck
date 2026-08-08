package com.wayne.musicdeck

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryBottomSheetFragment : BottomSheetDialogFragment() {

    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val rvHistory = view.findViewById<RecyclerView>(R.id.rvHistory)
        
        lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) {
                com.wayne.musicdeck.data.MusicDatabase.getDatabase(requireContext()).playHistoryDao().getRecentPlays(100)
            }
            
            val allSongs = viewModel.songs.value ?: emptyList()
            
            // Map entries to songs
            val historyWithSongs = entries.mapNotNull { entry ->
                val song = allSongs.find { it.id == entry.songId }
                if (song != null) Pair(entry, song) else null
            }
            
            val adapter = HistoryAdapter(historyWithSongs) { song ->
                val songsList = historyWithSongs.map { it.second }
                val index = songsList.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                viewModel.playPlaylist(songsList, index)
                dismiss()
            }
            rvHistory.adapter = adapter
        }
    }
}
