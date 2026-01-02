package com.wayne.musicdeck

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import android.content.ContentUris
import android.net.Uri
import coil.load
import coil.transform.RoundedCornersTransformation
import com.wayne.musicdeck.databinding.ItemSongBinding

class SongAdapter(
    private val onSongClick: (Song) -> Unit
) : ListAdapter<SongListItem, RecyclerView.ViewHolder>(SongListItemDiffCallback()) {

    // Callback removed - using ImageUtils directly

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_SONG = 1
    }

    var onSongMenuClick: ((Song, String) -> Unit)? = null
    var showRemoveFromPlaylistOption: Boolean = false
    var currentlyPlayingId: Long = -1L
        set(value) {
            val oldValue = field
            field = value
            if (oldValue != value) {
                // Find and refresh the old and new items
                notifyDataSetChanged() // Simple approach, or find specific positions
            }
        }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is SongListItem.Header -> VIEW_TYPE_HEADER
            is SongListItem.SongItem -> VIEW_TYPE_SONG
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_header, parent, false)
                HeaderViewHolder(view)
            }
            else -> {
                val binding = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                SongViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is SongListItem.Header -> (holder as HeaderViewHolder).bind(item.letter)
            is SongListItem.SongItem -> (holder as SongViewHolder).bind(item.song)
        }
    }

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.tvHeader)
        fun bind(letter: String) {
            textView.text = letter
        }
    }

    inner class SongViewHolder(private val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(song: Song) {
            binding.tvTitle.text = song.title
            binding.tvArtist.text = song.artist
            
            // Highlight currently playing song
            val isPlaying = song.id == currentlyPlayingId
            val context = binding.root.context
            if (isPlaying) {
                val primaryColor = com.google.android.material.color.MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, 0)
                binding.tvTitle.setTextColor(primaryColor)
                binding.tvArtist.setTextColor(primaryColor)
            } else {
                val onSurfaceColor = com.google.android.material.color.MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, 0)
                val onSurfaceVariantColor = com.google.android.material.color.MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0)
                binding.tvTitle.setTextColor(onSurfaceColor)
                binding.tvArtist.setTextColor(onSurfaceVariantColor)
            }
            
            
            // Use centralized loader for consistency (DISABLED for Plan B: Minimal List)
            // binding.ivAlbumArt.loadSongCover(song)
            
            binding.root.setOnClickListener { onSongClick(song) }
            
            // Long-press to show full menu (HeyTap style)
            binding.root.setOnLongClickListener {
                onSongMenuClick?.invoke(song, "show_menu")
                true
            }
            
            binding.btnMore.setOnClickListener {
                // Same as long-press - show the nice BottomSheet menu
                onSongMenuClick?.invoke(song, "show_menu")
            }
        }
    }

    class SongListItemDiffCallback : DiffUtil.ItemCallback<SongListItem>() {
        override fun areItemsTheSame(oldItem: SongListItem, newItem: SongListItem): Boolean {
            return when {
                oldItem is SongListItem.Header && newItem is SongListItem.Header -> oldItem.letter == newItem.letter
                oldItem is SongListItem.SongItem && newItem is SongListItem.SongItem -> oldItem.song.id == newItem.song.id
                else -> false
            }
        }
        override fun areContentsTheSame(oldItem: SongListItem, newItem: SongListItem): Boolean {
            return oldItem == newItem
        }
    }
}
