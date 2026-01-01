package com.wayne.musicdeck

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation

class QueueAdapter(
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onRemoveClick: (Int) -> Unit,
    private val onItemClick: (Int) -> Unit
) : ListAdapter<MediaItem, QueueAdapter.QueueViewHolder>(MediaItemDiffCallback()) {

    // Track currently playing index to highlight it
    var currentPlayingIndex: Int = -1
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_queue_song, parent, false)
        return QueueViewHolder(view)
    }

    override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class QueueViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivArt: ImageView = itemView.findViewById(R.id.ivArt)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvArtist: TextView = itemView.findViewById(R.id.tvArtist)
        private val btnDrag: ImageView = itemView.findViewById(R.id.btnDrag)
        private val btnRemove: ImageButton = itemView.findViewById(R.id.btnRemove)
        private val playingIndicator: View = itemView.findViewById(R.id.playingIndicator)

        fun bind(item: MediaItem, position: Int) {
            tvTitle.text = item.mediaMetadata.title ?: "Unknown"
            tvArtist.text = item.mediaMetadata.artist ?: "Unknown"

            // Highlight if playing
            val isPlaying = position == currentPlayingIndex
            if (isPlaying) {
                val primary = com.google.android.material.color.MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorPrimary, android.graphics.Color.BLACK)
                tvTitle.setTextColor(primary)
                tvArtist.setTextColor(primary)
                playingIndicator.visibility = View.VISIBLE
            } else {
                val onSurface = com.google.android.material.color.MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnSurface, android.graphics.Color.BLACK)
                val onSurfaceVariant = com.google.android.material.color.MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnSurfaceVariant, android.graphics.Color.GRAY)
                tvTitle.setTextColor(onSurface)
                tvArtist.setTextColor(onSurfaceVariant)
                playingIndicator.visibility = View.INVISIBLE
            }
            
            ivArt.load(item.mediaMetadata.artworkUri) {
                crossfade(true)
                placeholder(R.drawable.default_album_art)
                error(R.drawable.default_album_art)
                transformations(RoundedCornersTransformation(24f))
            }

            btnDrag.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    onStartDrag(this)
                }
                false
            }
            
            btnRemove.setOnClickListener { 
                onRemoveClick(bindingAdapterPosition)
            }
            
            itemView.setOnClickListener {
                onItemClick(bindingAdapterPosition)
            }
        }
    }

    class MediaItemDiffCallback : DiffUtil.ItemCallback<MediaItem>() {
        override fun areItemsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean =
            oldItem.mediaId == newItem.mediaId

        override fun areContentsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean =
            oldItem.mediaMetadata.title == newItem.mediaMetadata.title 
            && oldItem.mediaMetadata.artist == newItem.mediaMetadata.artist
    }
}
