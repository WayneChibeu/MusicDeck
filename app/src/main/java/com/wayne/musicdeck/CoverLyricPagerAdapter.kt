package com.wayne.musicdeck

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation

class CoverLyricPagerAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var coverArtBytes: ByteArray? = null
    private var coverArtUri: android.net.Uri? = null
    private var lyricsText: String = "No lyrics available\n\n♫\n\nSwipe right to see cover"
    private var songTitle: String = ""

    companion object {
        const val TYPE_COVER = 0
        const val TYPE_LYRICS = 1
    }

    override fun getItemCount(): Int = 2

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) TYPE_COVER else TYPE_LYRICS
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_COVER) {
            val view = inflater.inflate(R.layout.item_pager_cover, parent, false)
            CoverViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_pager_lyrics, parent, false)
            LyricsViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is CoverViewHolder -> holder.bind(coverArtBytes, coverArtUri)
            is LyricsViewHolder -> holder.bind(lyricsText, songTitle)
        }
    }

    fun updateCover(bytes: ByteArray?, uri: android.net.Uri?) {
        coverArtBytes = bytes
        coverArtUri = uri
        notifyItemChanged(0)
    }

    fun updateLyrics(text: String, title: String) {
        lyricsText = text
        songTitle = title
        notifyItemChanged(1)
    }

    class CoverViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ivCover: ImageView = view.findViewById(R.id.ivPagerCover)

        fun bind(bytes: ByteArray?, uri: android.net.Uri?) {
            if (bytes != null) {
                ivCover.load(bytes) {
                    crossfade(true)
                    transformations(RoundedCornersTransformation(32f))
                }
            } else {
                ivCover.load(uri) {
                    crossfade(true)
                    placeholder(R.drawable.default_album_art)
                    error(R.drawable.default_album_art)
                    transformations(RoundedCornersTransformation(32f))
                }
            }
        }
    }

    class LyricsViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvLyrics: TextView = view.findViewById(R.id.tvPagerLyrics)

        fun bind(text: String, title: String) {
            tvLyrics.text = text
        }
    }
}
