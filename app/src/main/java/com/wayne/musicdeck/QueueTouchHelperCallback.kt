package com.wayne.musicdeck

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.wayne.musicdeck.utils.HapticManager

class QueueTouchHelperCallback(
    private val onMoveLocally: (Int, Int) -> Unit,
    private val onDragComplete: (Int, Int) -> Unit = { _, _ -> }
) : ItemTouchHelper.Callback() {

    private var dragStartPos: Int = -1
    private var dragEndPos: Int = -1

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        val swipeFlags = 0
        return makeMovementFlags(dragFlags, swipeFlags)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val fromPos = viewHolder.bindingAdapterPosition
        val toPos = target.bindingAdapterPosition
        if (fromPos != RecyclerView.NO_POSITION && toPos != RecyclerView.NO_POSITION) {
            dragEndPos = toPos
            onMoveLocally(fromPos, toPos)
            return true
        }
        return false
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
    }
    
    override fun isLongPressDragEnabled(): Boolean = false
    override fun isItemViewSwipeEnabled(): Boolean = false
    
    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
            dragStartPos = viewHolder.bindingAdapterPosition
            dragEndPos = dragStartPos
            
            viewHolder.itemView.apply {
                elevation = 16f
                animate().scaleX(1.03f).scaleY(1.03f).alpha(0.92f).setDuration(120).start()
                HapticManager.performSpringClick(context)
            }
        }
    }
    
    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        viewHolder.itemView.apply {
            elevation = 0f
            animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(120).start()
        }
        
        if (dragStartPos != -1 && dragEndPos != -1 && dragStartPos != dragEndPos) {
            onDragComplete(dragStartPos, dragEndPos)
        }
        dragStartPos = -1
        dragEndPos = -1
    }
    
    override fun getAnimationDuration(
        recyclerView: RecyclerView,
        animationType: Int,
        animateDx: Float,
        animateDy: Float
    ): Long = 180L
}

