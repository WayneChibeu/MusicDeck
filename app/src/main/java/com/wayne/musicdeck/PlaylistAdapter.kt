package com.wayne.musicdeck

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wayne.musicdeck.data.Playlist
import com.wayne.musicdeck.databinding.ItemSongBinding
import coil.load

class PlaylistAdapter(
    private val onPlaylistClick: (Playlist) -> Unit,
    private val onPlaylistMenuClick: (Playlist) -> Unit
) : ListAdapter<Playlist, PlaylistAdapter.PlaylistViewHolder>(PlaylistDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val binding = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PlaylistViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        val playlist = getItem(position)
        holder.bind(playlist)
    }

    inner class PlaylistViewHolder(private val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(playlist: Playlist) {
            val isSmart = com.wayne.musicdeck.data.SmartPlaylistManager.isSmartPlaylist(playlist.id)
            binding.tvTitle.text = playlist.name
            binding.tvArtist.text = if (isSmart) "Smart Auto-List" else "Playlist"
            
            if (isSmart) {
                binding.ivAlbumArt.clearColorFilter()
                binding.ivAlbumArt.setImageResource(R.drawable.ic_auto_fix)
                binding.ivAlbumArt.setColorFilter(binding.root.context.getColor(R.color.colorNeon))
                binding.btnMore.visibility = android.view.View.GONE
            } else if (playlist.imagePath != null) {
                binding.ivAlbumArt.clearColorFilter()
                binding.btnMore.visibility = android.view.View.VISIBLE
                binding.ivAlbumArt.load(java.io.File(playlist.imagePath)) {
                     crossfade(true)
                     transformations(coil.transform.RoundedCornersTransformation(8f))
                     error(R.drawable.ic_launcher_background) // fallback
                }
            } else {
                // Default folder icon
                binding.btnMore.visibility = android.view.View.VISIBLE
                binding.ivAlbumArt.load(R.drawable.ic_folder) // Reset to avoid old image
                binding.ivAlbumArt.setImageResource(R.drawable.ic_folder)
                binding.ivAlbumArt.setColorFilter(binding.root.context.getColor(R.color.teal_200))
            }

            binding.root.setOnClickListener {
                onPlaylistClick(playlist)
            }
            
            // Re-purpose the options menu for delete, etc.
            binding.btnMore.setOnClickListener {
                onPlaylistMenuClick(playlist)
            }
        }
    }

    class PlaylistDiffCallback : DiffUtil.ItemCallback<Playlist>() {
        override fun areItemsTheSame(oldItem: Playlist, newItem: Playlist): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Playlist, newItem: Playlist): Boolean {
            return oldItem == newItem
        }
    }
}
