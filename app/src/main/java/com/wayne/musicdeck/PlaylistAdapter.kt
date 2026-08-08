package com.wayne.musicdeck

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wayne.musicdeck.data.Playlist
import com.wayne.musicdeck.databinding.ItemPlaylistBinding
import coil.load
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject

class PlaylistAdapter(
    private val coroutineScope: CoroutineScope,
    private val getPreviewPaths: suspend (Long) -> List<String>,
    private val onPlaylistClick: (Playlist) -> Unit,
    private val onPlaylistMenuClick: (Playlist) -> Unit
) : ListAdapter<Playlist, PlaylistAdapter.PlaylistViewHolder>(PlaylistDiffCallback()) {

    private val settingsManager: com.wayne.musicdeck.utils.SettingsManager by inject(com.wayne.musicdeck.utils.SettingsManager::class.java)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val binding = ItemPlaylistBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PlaylistViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        val playlist = getItem(position)
        holder.bind(playlist)
    }

    inner class PlaylistViewHolder(private val binding: ItemPlaylistBinding) : RecyclerView.ViewHolder(binding.root) {
        private var currentJob: Job? = null
        
        fun bind(playlist: Playlist) {
            currentJob?.cancel()
            val isSmart = com.wayne.musicdeck.data.SmartPlaylistManager.isSmartPlaylist(playlist.id)
            binding.tvTitle.text = playlist.name
            binding.tvArtist.text = if (isSmart) "Smart Auto-List" else "Playlist"
            
            binding.collageGrid.visibility = android.view.View.GONE
            binding.ivSingleArt.visibility = android.view.View.VISIBLE
            
            if (isSmart) {
                binding.ivSingleArt.clearColorFilter()
                binding.ivSingleArt.setImageResource(R.drawable.ic_auto_fix)
                binding.ivSingleArt.setColorFilter(binding.root.context.getColor(R.color.colorNeon))
                binding.btnMore.visibility = android.view.View.GONE
            } else if (playlist.imagePath != null) {
                binding.ivSingleArt.clearColorFilter()
                binding.btnMore.visibility = android.view.View.VISIBLE
                binding.ivSingleArt.load(java.io.File(playlist.imagePath)) {
                     crossfade(true)
                     error(R.drawable.ic_launcher_background) // fallback
                }
            } else {
                binding.btnMore.visibility = android.view.View.VISIBLE
                binding.ivSingleArt.clearColorFilter()
                binding.ivSingleArt.setImageResource(R.drawable.ic_folder)
                binding.ivSingleArt.setColorFilter(binding.root.context.getColor(R.color.teal_200))
                
                if (settingsManager.isPlaylistCollageEnabled) {
                    // Fetch previews for collage
                    currentJob = coroutineScope.launch(Dispatchers.Main) {
                        val paths = withContext(Dispatchers.IO) { getPreviewPaths(playlist.id) }
                        if (paths.size >= 4) {
                            binding.ivSingleArt.visibility = android.view.View.GONE
                            binding.collageGrid.visibility = android.view.View.VISIBLE
                            
                            binding.ivCollage1.load(java.io.File(paths[0])) { error(R.drawable.ic_launcher_background) }
                            binding.ivCollage2.load(java.io.File(paths[1])) { error(R.drawable.ic_launcher_background) }
                            binding.ivCollage3.load(java.io.File(paths[2])) { error(R.drawable.ic_launcher_background) }
                            binding.ivCollage4.load(java.io.File(paths[3])) { error(R.drawable.ic_launcher_background) }
                        }
                    }
                }
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
