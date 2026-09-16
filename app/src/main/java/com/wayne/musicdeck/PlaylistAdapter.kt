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
                binding.btnMore.visibility = android.view.View.GONE
                binding.ivSingleArt.clearColorFilter()
                
                // 13dp inside padding so the 24dp vector is centered and perfectly sized within 56dp CardView
                val pad = (13 * binding.root.resources.displayMetrics.density).toInt()
                binding.ivSingleArt.setPadding(pad, pad, pad, pad)
                binding.ivSingleArt.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE

                when (playlist.id) {
                    com.wayne.musicdeck.data.SmartPlaylistManager.ID_RECENTLY_ADDED -> {
                        binding.ivSingleArt.setImageResource(R.drawable.ic_music_note)
                        binding.ivSingleArt.setColorFilter(android.graphics.Color.parseColor("#4ADE80"))
                        binding.ivSingleArt.setBackgroundColor(android.graphics.Color.parseColor("#264ADE80"))
                    }
                    com.wayne.musicdeck.data.SmartPlaylistManager.ID_HEAVY_ROTATION -> {
                        binding.ivSingleArt.setImageResource(R.drawable.ic_fire)
                        binding.ivSingleArt.setColorFilter(android.graphics.Color.parseColor("#FB923C"))
                        binding.ivSingleArt.setBackgroundColor(android.graphics.Color.parseColor("#26FB923C"))
                    }
                    com.wayne.musicdeck.data.SmartPlaylistManager.ID_FORGOTTEN_GEMS -> {
                        binding.ivSingleArt.setImageResource(R.drawable.ic_gem)
                        binding.ivSingleArt.setColorFilter(android.graphics.Color.parseColor("#C084FC"))
                        binding.ivSingleArt.setBackgroundColor(android.graphics.Color.parseColor("#26C084FC"))
                    }
                    com.wayne.musicdeck.data.SmartPlaylistManager.ID_CHILL_MODE -> {
                        binding.ivSingleArt.setImageResource(R.drawable.ic_sunset)
                        binding.ivSingleArt.setColorFilter(android.graphics.Color.parseColor("#38BDF8"))
                        binding.ivSingleArt.setBackgroundColor(android.graphics.Color.parseColor("#2638BDF8"))
                    }
                    com.wayne.musicdeck.data.SmartPlaylistManager.ID_ENERGY_BOOST -> {
                        binding.ivSingleArt.setImageResource(R.drawable.ic_bolt)
                        binding.ivSingleArt.setColorFilter(android.graphics.Color.parseColor("#FACC15"))
                        binding.ivSingleArt.setBackgroundColor(android.graphics.Color.parseColor("#26FACC15"))
                    }
                    else -> {
                        binding.ivSingleArt.setImageResource(R.drawable.ic_music_note)
                        binding.ivSingleArt.setColorFilter(binding.root.context.getColor(R.color.colorNeon))
                        binding.ivSingleArt.setBackgroundColor(android.graphics.Color.parseColor("#22000000"))
                    }
                }
            } else if (playlist.imagePath != null) {
                binding.ivSingleArt.setPadding(0, 0, 0, 0)
                binding.ivSingleArt.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                binding.ivSingleArt.setBackgroundColor(android.graphics.Color.parseColor("#22000000"))
                binding.ivSingleArt.clearColorFilter()
                binding.btnMore.visibility = android.view.View.VISIBLE
                binding.ivSingleArt.load(java.io.File(playlist.imagePath)) {
                     crossfade(true)
                     error(R.drawable.ic_launcher_background) // fallback
                }
            } else {
                val folderPad = (10 * binding.root.resources.displayMetrics.density).toInt()
                binding.ivSingleArt.setPadding(folderPad, folderPad, folderPad, folderPad)
                binding.ivSingleArt.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                binding.ivSingleArt.setBackgroundColor(android.graphics.Color.parseColor("#22000000"))
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
