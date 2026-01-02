package com.wayne.musicdeck

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class SongActionBottomSheet : BottomSheetDialogFragment() {
    
    companion object {
        private const val ARG_SONG_ID = "song_id"
        private const val ARG_SONG_TITLE = "song_title"
        private const val ARG_SONG_ARTIST = "song_artist"
        private const val ARG_ALBUM_ID = "album_id"
        private const val ARG_SHOW_REMOVE = "show_remove"
        
        fun newInstance(
            songId: Long,
            title: String,
            artist: String,
            albumId: Long,
            showRemoveFromPlaylist: Boolean = false
        ): SongActionBottomSheet {
            return SongActionBottomSheet().apply {
                arguments = Bundle().apply {
                    putLong(ARG_SONG_ID, songId)
                    putString(ARG_SONG_TITLE, title)
                    putString(ARG_SONG_ARTIST, artist)
                    putLong(ARG_ALBUM_ID, albumId)
                    putBoolean(ARG_SHOW_REMOVE, showRemoveFromPlaylist)
                }
            }
        }
    }
    
    var onActionSelected: ((String) -> Unit)? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.TransparentBottomSheetDialog)
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_song_actions, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val title = arguments?.getString(ARG_SONG_TITLE) ?: ""
        val artist = arguments?.getString(ARG_SONG_ARTIST) ?: ""
        val albumId = arguments?.getLong(ARG_ALBUM_ID) ?: 0L
        val showRemove = arguments?.getBoolean(ARG_SHOW_REMOVE) ?: false
        
        // Set song info
        view.findViewById<TextView>(R.id.tvSongTitle).text = title
        view.findViewById<TextView>(R.id.tvSongArtist).text = artist
        
        // Load album art
        val albumArtUri = android.content.ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            albumId
        )
        view.findViewById<ImageView>(R.id.ivSongArt).load(albumArtUri) {
            crossfade(true)
            placeholder(R.drawable.default_album_art)
            error(R.drawable.default_album_art)
            transformations(RoundedCornersTransformation(12f))
        }
        
        // Toggle remove vs add to playlist based on context
        val addToPlaylist = view.findViewById<View>(R.id.actionAddToPlaylist)
        val removeFromPlaylist = view.findViewById<View>(R.id.actionRemoveFromPlaylist)
        
        if (showRemove) {
            addToPlaylist.visibility = View.GONE
            removeFromPlaylist.visibility = View.VISIBLE
        } else {
            addToPlaylist.visibility = View.VISIBLE
            removeFromPlaylist.visibility = View.GONE
        }
        
        // Setup click listeners with haptic feedback
        setupAction(view, R.id.actionAddToPlaylist, "add_to_playlist")
        setupAction(view, R.id.actionRemoveFromPlaylist, "remove_from_playlist")
        setupAction(view, R.id.actionShare, "share")
        setupAction(view, R.id.actionRingtone, "ringtone")
        setupAction(view, R.id.actionEdit, "edit")
        setupAction(view, R.id.actionDetails, "details")
        setupAction(view, R.id.actionDelete, "delete")
        
        // Edit option is now enabled
        // view.findViewById<View>(R.id.actionEdit)?.visibility = View.VISIBLE
    }
    
    private fun setupAction(view: View, viewId: Int, action: String) {
        view.findViewById<View>(viewId).setOnClickListener {
            // Haptic feedback - safe call
            try {
                com.wayne.musicdeck.utils.HapticManager.performSpringClick(requireContext())
            } catch (e: Exception) {
                // Ignore haptic failures
            }
            // Invoke callback BEFORE dismiss to avoid context issues
            onActionSelected?.invoke(action)
            dismiss()
        }
    }
}
