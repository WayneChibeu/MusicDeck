package com.wayne.musicdeck

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class QueueTouchHelperCallback(
    private val onMove: (Int, Int) -> Unit
) : ItemTouchHelper.Callback() {

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        val swipeFlags = 0 // Disable Swipe for now, or enable for remove
        return makeMovementFlags(dragFlags, swipeFlags)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        onMove(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // Not used if swipeFlags is 0
    }
    
    override fun isLongPressDragEnabled(): Boolean = false // We use handle
    override fun isItemViewSwipeEnabled(): Boolean = false
    
    // Improve drag smoothness
    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            viewHolder?.itemView?.alpha = 0.7f
            viewHolder?.itemView?.scaleX = 1.05f
            viewHolder?.itemView?.scaleY = 1.05f
        }
    }
    
    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        viewHolder.itemView.alpha = 1.0f
        viewHolder.itemView.scaleX = 1.0f
        viewHolder.itemView.scaleY = 1.0f
    }
    
    // Faster interpolation for smoother drag
    override fun getAnimationDuration(
        recyclerView: RecyclerView,
        animationType: Int,
        animateDx: Float,
        animateDy: Float
    ): Long {
        return if (animationType == ItemTouchHelper.ANIMATION_TYPE_DRAG) {
            150L // Faster than default
        } else {
            super.getAnimationDuration(recyclerView, animationType, animateDx, animateDy)
        }
    }
}
