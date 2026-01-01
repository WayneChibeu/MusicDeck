package com.wayne.musicdeck

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class SongInfoBottomSheet : BottomSheetDialogFragment() {

    private val viewModel: MainViewModel by activityViewModels()
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
    
    // Custom cover picker
    private val coverImagePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            currentSong?.let { song ->
                viewModel.setCustomCover(song, it)
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
        val songId = arguments?.getLong(ARG_SONG_ID) ?: -1L
        if (songId != -1L) {
            // We can't access viewModel.songs here easily if it's LiveData and we are in onCreate (not observed yet)
            // But we can observe in onViewCreated or just grab value if available. 
            // Better to do it in onViewCreated.
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val songId = arguments?.getLong(ARG_SONG_ID) ?: -1L
        currentSong = viewModel.songs.value?.find { it.id == songId }
        
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
        
        view.findViewById<View>(R.id.itemCustomCover).setOnClickListener {
            coverImagePicker.launch("image/*")
        }
        
        view.findViewById<View>(R.id.itemLyricFile).setOnClickListener {
            lyricFilePicker.launch("*/*")
        }
    }
    
    private fun updateLyricPath() {
        val song = currentSong ?: return
        val view = view ?: return
        val tvLyricPath = view.findViewById<TextView>(R.id.tvLyricPath)
        
        val lyricPath = viewModel.getLyricPath(song.id)
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
        private const val ARG_SONG_ID = "song_id"

        fun newInstance(song: Song): SongInfoBottomSheet {
            return SongInfoBottomSheet().apply {
                arguments = Bundle().apply {
                    putLong(ARG_SONG_ID, song.id)
                }
            }
        }
    }
}
