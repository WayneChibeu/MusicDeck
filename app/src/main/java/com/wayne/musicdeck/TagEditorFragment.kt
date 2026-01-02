package com.wayne.musicdeck

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.TextInputEditText

class TagEditorFragment : BottomSheetDialogFragment() {

    private lateinit var viewModel: MainViewModel
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
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        val etTitle = view.findViewById<TextInputEditText>(R.id.etTitle)
        val etArtist = view.findViewById<TextInputEditText>(R.id.etArtist)
        val etAlbum = view.findViewById<TextInputEditText>(R.id.etAlbum)
        val btnSave = view.findViewById<Button>(R.id.btnSave)

        // Load song details
        val song = viewModel.songs.value?.find { it.id == songId }
        if (song != null) {
            etTitle.setText(song.title)
            etArtist.setText(song.artist)
            etAlbum.setText(song.album)
        } else {
            dismiss()
            return
        }

        btnSave.setOnClickListener {
            val element = viewModel.songs.value?.find { it.id == songId } ?: return@setOnClickListener
            val newTitle = etTitle.text.toString().trim()
            val newArtist = etArtist.text.toString().trim()
            val newAlbum = etAlbum.text.toString().trim()
            
            viewModel.updateSongTags(element, newTitle, newArtist, newAlbum)
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
