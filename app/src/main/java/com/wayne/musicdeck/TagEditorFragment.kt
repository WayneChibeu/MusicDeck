package com.wayne.musicdeck

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.android.ext.android.inject

class TagEditorFragment : BottomSheetDialogFragment() {

    private val viewModel: MainViewModel by activityViewModel()
    private val settingsManager: com.wayne.musicdeck.utils.SettingsManager by inject()
    private var songId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        songId = arguments?.getLong(ARG_SONG_ID) ?: -1L
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_tag_editor, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etTitle = view.findViewById<TextInputEditText>(R.id.etTitle)
        val etArtist = view.findViewById<TextInputEditText>(R.id.etArtist)
        val etAlbum = view.findViewById<TextInputEditText>(R.id.etAlbum)
        val tilNotes = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilNotes)
        val etNotes = view.findViewById<TextInputEditText>(R.id.etNotes)
        val btnSave = view.findViewById<Button>(R.id.btnSave)
        
        if (!settingsManager.isSongNotesEnabled) {
            tilNotes.visibility = android.view.View.GONE
        }

        // Load song details
        val song = viewModel.songs.value?.find { it.id == songId }
        if (song != null) {
            etTitle.setText(song.title)
            etArtist.setText(song.artist)
            etAlbum.setText(song.album)
            
            lifecycleScope.launch {
                val notes = viewModel.getSongNotes(song.data)
                if (notes != null) {
                    etNotes.setText(notes)
                }
            }
        } else {
            dismiss()
            return
        }

        btnSave.setOnClickListener {
            val element = viewModel.songs.value?.find { it.id == songId } ?: return@setOnClickListener
            val newTitle = etTitle.text.toString().trim()
            val newArtist = etArtist.text.toString().trim()
            val newAlbum = etAlbum.text.toString().trim()
            val newNotes = etNotes.text.toString().trim().takeIf { it.isNotEmpty() }
            
            viewModel.updateSongTags(element, newTitle, newArtist, newAlbum, newNotes)
            dismiss()
        }
    }

    companion object {
        private const val ARG_SONG_ID = "song_id"
        fun newInstance(songId: Long): TagEditorFragment {
            val fragment = TagEditorFragment()
            val args = Bundle()
            args.putLong(ARG_SONG_ID, songId)
            fragment.arguments = args
            return fragment
        }
    }
}
