package com.wayne.musicdeck

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class QueueBottomSheet : BottomSheetDialogFragment() {

    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: QueueAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvQueueCount: TextView
    private lateinit var itemTouchHelper: ItemTouchHelper

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_queue, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        
        tvQueueCount = view.findViewById(R.id.tvQueueCount)
        recyclerView = view.findViewById(R.id.rvQueue)
        recyclerView.layoutManager = LinearLayoutManager(context)
        
        setupAdapter()
        setupObserver()
        
        view.findViewById<View>(R.id.btnClearQueue).setOnClickListener {
             viewModel.mediaController.value?.clearMediaItems()
             Toast.makeText(context, "Queue cleared", Toast.LENGTH_SHORT).show()
             dismiss()
        }
    }
    
    // Track local changes to prevent jitter during drag
    private var isDragging = false

    private fun setupAdapter() {
        adapter = QueueAdapter(
            onStartDrag = { viewHolder ->
                itemTouchHelper.startDrag(viewHolder)
            },
            onRemoveClick = { position ->
                val player = viewModel.mediaController.value ?: return@QueueAdapter
                // Don't remove currently playing if it's the only one, or handle gracefully
                try {
                    player.removeMediaItem(position)
                    
                    // Update list manually to reflect immediate change if observer is slow
                    // Actually observer should be fast enough.
                } catch (e: Exception) {
                    Toast.makeText(context, "Error removing item", Toast.LENGTH_SHORT).show()
                }
            },
            onItemClick = { position ->
                viewModel.mediaController.value?.seekTo(position, 0)
                dismiss()
            }
        )
        recyclerView.adapter = adapter
        
        val callback = QueueTouchHelperCallback { fromPos, toPos ->
            isDragging = true
            val player = viewModel.mediaController.value ?: return@QueueTouchHelperCallback
            
            try {
                // Move in player
                player.moveMediaItem(fromPos, toPos)
                
                // Move in adapter list locally to look smooth
                // Note: submitting list to ListAdapter might reset if we don't do this carefully
                // However, ListAdapter + DiffCallback handles it usually.
                // But for move, we want instant visual feedback. 
                // Since ListAdapter is async, we might rely on player update.
                // For now, let's rely on the Player.Listener update which happens immediately.
            } catch (e: Exception) {
                // Ignore
            }
            isDragging = false // Actually should wait for drop?
        }
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun setupObserver() {
        viewModel.mediaController.observe(viewLifecycleOwner) { player ->
            if (player == null) return@observe
            
            // Initial load
            updateQueue(player)
            
            // Listen for queue changes
            player.addListener(object : androidx.media3.common.Player.Listener {
                override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                    if (!isDragging) {
                        updateQueue(player)
                    }
                }
                
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    adapter.currentPlayingIndex = player.currentMediaItemIndex
                    recyclerView.scrollToPosition(player.currentMediaItemIndex)
                }
            })
        }
    }
    
    private fun updateQueue(player: androidx.media3.common.Player) {
        val itemCount = player.mediaItemCount
        tvQueueCount.text = "$itemCount songs"
        
        val items = mutableListOf<MediaItem>()
        for (i in 0 until itemCount) {
            items.add(player.getMediaItemAt(i))
        }
        adapter.submitList(items)
        adapter.currentPlayingIndex = player.currentMediaItemIndex
    }
}
