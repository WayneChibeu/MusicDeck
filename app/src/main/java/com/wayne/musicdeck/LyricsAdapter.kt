package com.wayne.musicdeck

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.wayne.musicdeck.data.LyricLine

class LyricsAdapter : RecyclerView.Adapter<LyricsAdapter.LyricViewHolder>() {

    private var lyrics: List<LyricLine> = emptyList()
    private var activeLineIndex = -1
    
    // Cache colors
    private var activeColor: Int = Color.WHITE
    private var inactiveColor: Int = Color.GRAY
    private var activeTextSize = 24f
    private var inactiveTextSize = 18f

    fun submitList(newLyrics: List<LyricLine>) {
        lyrics = newLyrics
        activeLineIndex = -1
        notifyDataSetChanged()
    }

    fun updateTime(timeMs: Long): Int {
        if (lyrics.isEmpty()) return -1

        // Find the line that corresponds to current time
        // Use lastOrNull matching the condition
        var newIndex = -1
        for (i in lyrics.indices) {
            if (lyrics[i].timeMs <= timeMs) {
                newIndex = i
            } else {
                break
            }
        }

        if (newIndex != activeLineIndex) {
            val oldIndex = activeLineIndex
            activeLineIndex = newIndex
            
            if (oldIndex in lyrics.indices) notifyItemChanged(oldIndex)
            if (activeLineIndex in lyrics.indices) notifyItemChanged(activeLineIndex)
            
            return activeLineIndex
        }
        
        return -1 // No change
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LyricViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_lyric_line, parent, false)
            
        // Resolve theme colors
        resolveColors(parent.context)
        
        return LyricViewHolder(view)
    }
    
    private fun resolveColors(context: Context) {
        val typedValue = TypedValue()
        
        // Active color (OnSurface)
        context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
        activeColor = typedValue.data
        
        // Inactive color (OnSurfaceVariant)
        context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
        inactiveColor = typedValue.data
    }

    override fun onBindViewHolder(holder: LyricViewHolder, position: Int) {
        val line = lyrics[position]
        holder.tvLyric.text = line.text
        
        if (position == activeLineIndex) {
            holder.tvLyric.setTextColor(activeColor)
            holder.tvLyric.textSize = activeTextSize
            holder.tvLyric.alpha = 1.0f
            holder.tvLyric.setTypeface(null, android.graphics.Typeface.BOLD)
        } else {
            holder.tvLyric.setTextColor(inactiveColor)
            holder.tvLyric.textSize = inactiveTextSize
            holder.tvLyric.alpha = 0.6f
            holder.tvLyric.setTypeface(null, android.graphics.Typeface.NORMAL)
        }
    }

    override fun getItemCount(): Int = lyrics.size

    class LyricViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvLyric: TextView = itemView.findViewById(R.id.tvLyricLine)
    }
}
