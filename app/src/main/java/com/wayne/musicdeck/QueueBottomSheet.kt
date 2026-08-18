package com.wayne.musicdeck

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class QueueBottomSheet : BottomSheetDialogFragment() {

    private val viewModel: MainViewModel by activityViewModel()
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
                isDragging = true
                itemTouchHelper.startDrag(viewHolder)
            },
            onRemoveClick = { position ->
                val player = viewModel.mediaController.value ?: return@QueueAdapter
                try {
                    player.removeMediaItem(position)
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
        
        val callback = QueueTouchHelperCallback(
            onMoveLocally = { fromPos, toPos ->
                adapter.moveItemLocally(fromPos, toPos)
            },
            onDragComplete = { startPos, endPos ->
                isDragging = false
                val player = viewModel.mediaController.value ?: return@QueueTouchHelperCallback
                try {
                    player.moveMediaItem(startPos, endPos)
                } catch (e: Exception) {
                    // Ignore
                }
            }
        )
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
                    if (!isDragging) {
                        recyclerView.scrollToPosition(player.currentMediaItemIndex)
                    }
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
        adapter.submitItems(items)
        adapter.currentPlayingIndex = player.currentMediaItemIndex
    }
}
