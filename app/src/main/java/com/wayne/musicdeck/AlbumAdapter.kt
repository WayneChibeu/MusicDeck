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

class AlbumAdapter(
    private val onAlbumClick: (Album) -> Unit
) : ListAdapter<Album, AlbumAdapter.AlbumViewHolder>(AlbumDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_album, parent, false)
        return AlbumViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AlbumViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivArt: ImageView = itemView.findViewById(R.id.ivAlbumArt)
        private val tvName: TextView = itemView.findViewById(R.id.tvAlbumName)
        private val tvArtist: TextView = itemView.findViewById(R.id.tvAlbumArtist)
        private val tvCount: TextView = itemView.findViewById(R.id.tvSongCount)

        fun bind(album: Album) {
            tvName.text = album.name
            tvArtist.text = album.artist
            tvCount.text = "${album.songCount} songs"

            // If it's "Unknown Album", the generic ID might point to a folder's first song art.
            // We'll rely on common MediaStore ID for now, but ensure Coil treats it individually.
            val albumArtUri = if (album.name == "Unknown Album") {
                // For unknown album, we could potentially try to load from a specific song in that group
                // But simplified for now: stick to MediaStore then fallback to embedded in future revisions if needed.
                ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    album.id
                )
            } else {
                ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    album.id
                )
            }

            ivArt.load(albumArtUri) {
                crossfade(true)
                placeholder(R.drawable.default_album_art)
                error(R.drawable.default_album_art)
                // Use a different cache key if it's the unknown album group to avoid sharing art
                if (album.name == "Unknown Album") {
                    memoryCacheKey("unknown_album_${album.id}")
                }
                transformations(RoundedCornersTransformation(8f))
            }

            itemView.setOnClickListener { onAlbumClick(album) }
        }
    }

    class AlbumDiffCallback : DiffUtil.ItemCallback<Album>() {
        override fun areItemsTheSame(oldItem: Album, newItem: Album): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Album, newItem: Album): Boolean =
            oldItem == newItem
    }
}
