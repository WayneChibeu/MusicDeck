package com.wayne.musicdeck

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.imageview.ShapeableImageView
import com.wayne.musicdeck.data.PlayHistoryEntry

class HistoryAdapter(
    private val history: List<Pair<PlayHistoryEntry, Song>>,
    private val onSongClick: (Song) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivCover: ShapeableImageView = view.findViewById(R.id.ivCoverArt)
        val tvTitle: TextView = view.findViewById(R.id.tvRowName)
        val tvTime: TextView = view.findViewById(R.id.tvRowCount)
        val progress: View = view.findViewById(R.id.rowProgress) // Hide this
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_insight_song_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (entry, song) = history[position]
        
        holder.progress.visibility = View.GONE // Not needed for history
        holder.tvTitle.text = "${song.title} • ${song.artist}"
        
        // Format relative time (e.g., "2 hours ago")
        val timeStr = DateUtils.getRelativeTimeSpanString(
            entry.timestamp,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        )
        holder.tvTime.text = timeStr
        
        holder.ivCover.loadSongCover(song)
        
        holder.itemView.setOnClickListener {
            onSongClick(song)
        }
    }

    override fun getItemCount() = history.size
}
