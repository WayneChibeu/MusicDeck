package com.wayne.musicdeck

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class PlaylistOptionsBottomSheet : BottomSheetDialogFragment() {

    var onAction: ((String) -> Unit)? = null
    private var playlistName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playlistName = arguments?.getString(ARG_NAME) ?: "Playlist"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_playlist_options, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        view.findViewById<TextView>(R.id.tvPlaylistTitle).text = playlistName

        view.findViewById<View>(R.id.action_rename).setOnClickListener {
            onAction?.invoke("rename")
            dismiss()
        }
        
        view.findViewById<View>(R.id.action_queue).setOnClickListener {
            onAction?.invoke("queue")
            dismiss()
        }
        
        view.findViewById<View>(R.id.action_delete).setOnClickListener {
            onAction?.invoke("delete")
            dismiss()
        }
    }

    companion object {
        private const val ARG_NAME = "playlist_name"
        fun newInstance(name: String): PlaylistOptionsBottomSheet {
            val fragment = PlaylistOptionsBottomSheet()
            val args = Bundle()
            args.putString(ARG_NAME, name)
            fragment.arguments = args
            return fragment
        }
    }
}
