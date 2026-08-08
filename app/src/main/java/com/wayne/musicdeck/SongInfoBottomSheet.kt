package com.wayne.musicdeck

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
class SongInfoBottomSheet : BottomSheetDialogFragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private val settingsManager: com.wayne.musicdeck.utils.SettingsManager by inject()
    private var currentSong: Song? = null
    
    // Lyric file picker
    private val lyricFilePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            currentSong?.let { song ->
                viewModel.setLyricFile(song, it)
                updateLyricPath()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_song_info, container, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val songPath = arguments?.getString(ARG_SONG_PATH)
        if (songPath != null) {
            // Find song by path
            currentSong = viewModel.songs.value?.find { it.data == songPath }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val songPath = arguments?.getString(ARG_SONG_PATH)
        if (currentSong == null && songPath != null) {
            currentSong = viewModel.songs.value?.find { it.data == songPath }
        }
        
        val song = currentSong
        
        if (song == null) {
            dismiss()
            return
        }
        
        // Header
        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            dismiss()
        }
        
        // Populate fields
        view.findViewById<TextView>(R.id.tvSongTitle).text = song.title
        view.findViewById<TextView>(R.id.tvArtist).text = song.artist
        view.findViewById<TextView>(R.id.tvAlbum).text = song.album
        view.findViewById<TextView>(R.id.tvDuration).text = formatDuration(song.duration)
        view.findViewById<TextView>(R.id.tvFileSize).text = viewModel.getSongFileSize(song)
        view.findViewById<TextView>(R.id.tvFilePath).text = song.data
        
        // Load Notes
        // Load Notes
        val itemNotes = view.findViewById<View>(R.id.itemNotes)
        val tvNotes = view.findViewById<TextView>(R.id.tvNotes)
        val divNotes = view.findViewById<View>(R.id.divNotes)
        
        if (settingsManager.isSongNotesEnabled) {
            lifecycleScope.launch {
                val notes = viewModel.getSongNotes(song.data)
                if (!notes.isNullOrEmpty()) {
                    itemNotes.visibility = View.VISIBLE
                    divNotes.visibility = View.VISIBLE
                    tvNotes.text = notes
                }
            }
        }
        
        // Update lyric path
        updateLyricPath()
        
        // Click handlers
        view.findViewById<View>(R.id.itemSong).setOnClickListener {
            android.widget.Toast.makeText(context, song.title, android.widget.Toast.LENGTH_SHORT).show()
        }
        
        view.findViewById<View>(R.id.itemArtist).setOnClickListener {
            android.widget.Toast.makeText(context, song.artist, android.widget.Toast.LENGTH_SHORT).show()
        }
        
        view.findViewById<View>(R.id.itemAlbum).setOnClickListener {
            android.widget.Toast.makeText(context, song.album, android.widget.Toast.LENGTH_SHORT).show()
        }
        
        view.findViewById<View>(R.id.itemLyricFile).setOnClickListener {
            lyricFilePicker.launch("*/*")
        }
    }
    
    private fun updateLyricPath() {
        val song = currentSong ?: return
        val view = view ?: return
        val tvLyricPath = view.findViewById<TextView>(R.id.tvLyricPath)
        
        val lyricPath = viewModel.getLyricPath(song.data)
        tvLyricPath.text = if (lyricPath != null) {
            lyricPath.substringAfterLast("/")
        } else {
            "No lyric file"
        }
    }
    
    private fun formatDuration(durationMs: Long): String {
        val seconds = (durationMs / 1000) % 60
        val minutes = (durationMs / 1000) / 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    companion object {
        private const val ARG_SONG_PATH = "song_path"

        fun newInstance(song: Song): SongInfoBottomSheet {
            return SongInfoBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_SONG_PATH, song.data)
                }
            }
        }
    }
}
