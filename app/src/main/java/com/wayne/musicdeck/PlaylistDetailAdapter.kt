package com.wayne.musicdeck

import android.content.ContentUris
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.wayne.musicdeck.data.Playlist
// Song is in root package

class PlaylistDetailAdapter(
    var playlist: Playlist,
    private val onPlayAllClick: () -> Unit,
    private val onHeaderMenuClick: () -> Unit,
    private val onItemClick: (Song) -> Unit,
    private val onMoreClick: (Song) -> Unit
) : ListAdapter<Song, RecyclerView.ViewHolder>(SongDiffCallback()) {

    var currentlyPlayingId: Long = -1L
        set(value) {
            field = value
            notifyDataSetChanged() // efficient enough for small playlists, typically use payloads for perf
        }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_SONG = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) VIEW_TYPE_HEADER else VIEW_TYPE_SONG
    }

    override fun getItemCount(): Int {
        return super.getItemCount() + 1 // +1 for Header
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_playlist_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_playlist_song, parent, false)
            SongViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HeaderViewHolder) {
            holder.bind(playlist, currentList.size)
        } else if (holder is SongViewHolder) {
            val song = getItem(position - 1) // Offset by 1
            holder.bind(song)
        }
    }

    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val btnPlayAll: ImageButton = itemView.findViewById(R.id.btnPlayAll)
        private val tvPlayAll: TextView = itemView.findViewById(R.id.tvPlayAll) // Clickable?
        private val tvSongCount: TextView = itemView.findViewById(R.id.tvSongCount)
        private val btnHeaderMore: ImageButton = itemView.findViewById(R.id.btnHeaderMore)

        fun bind(playlist: Playlist, count: Int) {
            tvSongCount.text = "$count song(s)"
            
            val playAllAction = { onPlayAllClick() }
            btnPlayAll.setOnClickListener { playAllAction() }
            tvPlayAll.setOnClickListener { playAllAction() }
            
            btnHeaderMore.setOnClickListener { onHeaderMenuClick() }
        }
    }

    inner class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivArt: ImageView = itemView.findViewById(R.id.ivAlbumArt)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvArtist: TextView = itemView.findViewById(R.id.tvArtist)
        private val btnMore: ImageButton = itemView.findViewById(R.id.btnMore)

        fun bind(song: Song) {
            tvTitle.text = song.title
            
            // Format artist - album
            val subtitle = if (song.album != "Unknown Album") "${song.artist} - ${song.album}" else song.artist
            tvArtist.text = subtitle

            // Highlight if playing
            val isPlaying = song.id == currentlyPlayingId
            
            if (isPlaying) {
                 val primary = com.google.android.material.color.MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorPrimary, android.graphics.Color.BLACK)
                 tvTitle.setTextColor(primary)
                 tvArtist.setTextColor(primary)
            } else {
                 val onSurface = com.google.android.material.color.MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnSurface, android.graphics.Color.BLACK)
                 val onSurfaceVariant = com.google.android.material.color.MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnSurfaceVariant, android.graphics.Color.GRAY)
                 tvTitle.setTextColor(onSurface)
                 tvArtist.setTextColor(onSurfaceVariant)
            }
            
            ivArt.loadSongCover(song)
            
            btnMore.setOnClickListener { 
                onMoreClick(song)
            }
            
            itemView.setOnClickListener {
                onItemClick(song)
            }
        }
    }

    class SongDiffCallback : DiffUtil.ItemCallback<Song>() {
        override fun areItemsTheSame(oldItem: Song, newItem: Song): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Song, newItem: Song): Boolean =
            oldItem == newItem
    }
}
