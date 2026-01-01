package com.wayne.musicdeck

import android.content.ContentUris
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation

class ArtistAdapter(
    private val onArtistClick: (Artist) -> Unit
) : ListAdapter<Artist, ArtistAdapter.ArtistViewHolder>(ArtistDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArtistViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_artist, parent, false)
        return ArtistViewHolder(view)
    }

    override fun onBindViewHolder(holder: ArtistViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ArtistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivArt: ImageView = itemView.findViewById(R.id.ivArtistArt)
        private val tvName: TextView = itemView.findViewById(R.id.tvArtistName)
        private val tvCount: TextView = itemView.findViewById(R.id.tvSongCount)

        fun bind(artist: Artist) {
            tvName.text = artist.name
            tvCount.text = "${artist.songCount} songs"

            val albumArtUri = ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                artist.albumArtId
            )

            ivArt.load(albumArtUri) {
                crossfade(true)
                placeholder(R.drawable.default_album_art)
                error(R.drawable.default_album_art)
                transformations(RoundedCornersTransformation(8f))
            }

            itemView.setOnClickListener { onArtistClick(artist) }
        }
    }

    class ArtistDiffCallback : DiffUtil.ItemCallback<Artist>() {
        override fun areItemsTheSame(oldItem: Artist, newItem: Artist): Boolean =
            oldItem.name == newItem.name

        override fun areContentsTheSame(oldItem: Artist, newItem: Artist): Boolean =
            oldItem == newItem
    }
}
