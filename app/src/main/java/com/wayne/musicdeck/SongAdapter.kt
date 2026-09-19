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
        private const val VIEW_TYPE_FOLDER = 2
    }

    var onFolderClick: ((SongListItem.FolderItem) -> Unit)? = null
    var onSongMenuClick: ((Song, String) -> Unit)? = null
    var showRemoveFromPlaylistOption: Boolean = false
    var currentlyPlayingPath: String? = null
        set(value) {
            val oldValue = field
            field = value
            if (oldValue != value) {
                notifyDataSetChanged()
            }
        }
    
    var isPlaying: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    // --- HeyTap Multi-Select Mode ---
    var isSelectionMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                if (!value) {
                    selectedSongIds.clear()
                }
                notifyDataSetChanged()
                notifySelectionChanged()
            }
        }

    val selectedSongIds = mutableSetOf<Long>()
    var onSelectionChanged: ((selectedCount: Int, totalSongsCount: Int) -> Unit)? = null

    private fun notifySelectionChanged() {
        val totalSongs = currentList.count { it is SongListItem.SongItem }
        onSelectionChanged?.invoke(selectedSongIds.size, totalSongs)
    }

    fun toggleSelection(song: Song) {
        if (selectedSongIds.contains(song.id)) {
            selectedSongIds.remove(song.id)
        } else {
            selectedSongIds.add(song.id)
        }
        notifyDataSetChanged()
        notifySelectionChanged()
    }

    fun selectAll() {
        currentList.forEach { item ->
            if (item is SongListItem.SongItem) {
                selectedSongIds.add(item.song.id)
            }
        }
        notifyDataSetChanged()
        notifySelectionChanged()
    }

    fun deselectAll() {
        selectedSongIds.clear()
        notifyDataSetChanged()
        notifySelectionChanged()
    }

    fun getSelectedSongs(): List<Song> {
        val list = mutableListOf<Song>()
        currentList.forEach { item ->
            if (item is SongListItem.SongItem && selectedSongIds.contains(item.song.id)) {
                list.add(item.song)
            }
        }
        return list
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is SongListItem.Header -> VIEW_TYPE_HEADER
            is SongListItem.SongItem -> VIEW_TYPE_SONG
            is SongListItem.FolderItem -> VIEW_TYPE_FOLDER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_header, parent, false)
                HeaderViewHolder(view)
            }
            VIEW_TYPE_FOLDER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_folder, parent, false)
                FolderViewHolder(view)
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
            is SongListItem.FolderItem -> (holder as FolderViewHolder).bind(item)
        }
    }

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.tvHeader)
        fun bind(letter: String) {
            textView.text = letter
        }
    }
    
    inner class FolderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvFolderName)
        private val tvCount: TextView = itemView.findViewById(R.id.tvSongCount)
        fun bind(item: SongListItem.FolderItem) {
            tvName.text = item.name
            tvCount.text = "${item.count} songs"
            itemView.setOnClickListener { onFolderClick?.invoke(item) }
        }
    }

    inner class SongViewHolder(private val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(song: Song) {
            binding.tvTitle.text = song.title
            binding.tvArtist.text = song.artist
            
            // Highlight currently playing song using stable path
            val isCurrentSong = song.data == currentlyPlayingPath
            val context = binding.root.context
            if (isCurrentSong) {
                val primaryColor = com.google.android.material.color.MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, 0)
                binding.tvTitle.setTextColor(primaryColor)
                binding.tvArtist.setTextColor(primaryColor)
                
                // Show equalizer
                binding.ivEqualizer.visibility = android.view.View.VISIBLE
                val animDrawable = binding.ivEqualizer.drawable as? android.graphics.drawable.AnimationDrawable
                
                // Animate only if player is playing
                if (this@SongAdapter.isPlaying) {
                    if (animDrawable?.isRunning == false) animDrawable.start()
                } else {
                    if (animDrawable?.isRunning == true) animDrawable.stop()
                    // Optional: Reset to first frame so it looks static
                    animDrawable?.selectDrawable(0)
                }
            } else {
                val onSurfaceColor = com.google.android.material.color.MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, 0)
                val onSurfaceVariantColor = com.google.android.material.color.MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0)
                binding.tvTitle.setTextColor(onSurfaceColor)
                binding.tvArtist.setTextColor(onSurfaceVariantColor)
                
                // Hide equalizer
                binding.ivEqualizer.visibility = android.view.View.GONE
                val animDrawable = binding.ivEqualizer.drawable as? android.graphics.drawable.AnimationDrawable
                animDrawable?.stop()
            }
            
            
            // Use centralized loader for consistency (DISABLED for Plan B: Minimal List)
            binding.ivAlbumArt.loadSongCover(song)

            if (isSelectionMode) {
                binding.btnMore.visibility = View.GONE
                binding.ivEqualizer.visibility = View.GONE
                binding.ivSelectionCheck.visibility = View.VISIBLE

                val isSelected = selectedSongIds.contains(song.id)
                binding.ivSelectionCheck.setImageResource(
                    if (isSelected) R.drawable.ic_checkbox_selected else R.drawable.ic_checkbox_unselected
                )

                binding.root.setOnClickListener {
                    toggleSelection(song)
                }
                binding.root.setOnLongClickListener(null)
                binding.btnMore.setOnClickListener(null)
            } else {
                binding.ivSelectionCheck.visibility = View.GONE
                binding.btnMore.visibility = View.VISIBLE

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
