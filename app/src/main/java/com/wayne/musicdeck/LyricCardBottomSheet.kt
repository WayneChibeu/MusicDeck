package com.wayne.musicdeck

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.wayne.musicdeck.data.LyricLine
import com.wayne.musicdeck.utils.LyricCardGenerator
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class LyricCardBottomSheet : BottomSheetDialogFragment() {

    private val viewModel: MainViewModel by activityViewModel()
    private var lyrics: List<LyricLine> = emptyList()
    private val selectedIndices = mutableSetOf<Int>()
    private var selectedTheme = LyricCardGenerator.CardTheme.VIOLET

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_lyric_card, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvSubtitle = view.findViewById<TextView>(R.id.tvSelectionSubtitle)
        val rvLyrics = view.findViewById<RecyclerView>(R.id.rvCardLyrics)
        val chipGroupThemes = view.findViewById<ChipGroup>(R.id.chipGroupThemes)
        val btnShare = view.findViewById<MaterialButton>(R.id.btnShareLyricCard)

        rvLyrics.layoutManager = LinearLayoutManager(requireContext())

        // 1. Observe current lyrics
        viewModel.lyrics.observe(viewLifecycleOwner) { lyricLines ->
            val nonBlank = lyricLines.filter { it.text.isNotBlank() }
            lyrics = nonBlank
            // Pre-select current active line or first 2 lines by default
            if (selectedIndices.isEmpty() && nonBlank.isNotEmpty()) {
                selectedIndices.add(0)
                if (nonBlank.size > 1) selectedIndices.add(1)
            }
            rvLyrics.adapter?.notifyDataSetChanged()
            updateSubtitle(tvSubtitle, btnShare)
        }

        val adapter = object : RecyclerView.Adapter<LyricSelectViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LyricSelectViewHolder {
                val itemView = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_card_lyric_select, parent, false)
                return LyricSelectViewHolder(itemView)
            }

            override fun getItemCount() = lyrics.size

            override fun onBindViewHolder(holder: LyricSelectViewHolder, position: Int) {
                val line = lyrics[position]
                val isSelected = selectedIndices.contains(position)
                holder.tvText.text = line.text
                holder.ivCheck.setImageResource(
                    if (isSelected) R.drawable.ic_checkbox_selected else R.drawable.ic_checkbox_unselected
                )

                holder.itemView.setOnClickListener {
                    if (selectedIndices.contains(position)) {
                        selectedIndices.remove(position)
                    } else {
                        if (selectedIndices.size >= 5) {
                            Toast.makeText(context, "Maximum 5 lines allowed on card", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        selectedIndices.add(position)
                    }
                    notifyItemChanged(position)
                    updateSubtitle(tvSubtitle, btnShare)
                }
            }
        }
        rvLyrics.adapter = adapter

        // 2. Setup theme picker
        chipGroupThemes.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedTheme = when (checkedIds.firstOrNull()) {
                R.id.chipThemeMidnight -> LyricCardGenerator.CardTheme.MIDNIGHT
                R.id.chipThemeRose -> LyricCardGenerator.CardTheme.ROSE
                R.id.chipThemeCyan -> LyricCardGenerator.CardTheme.CYAN
                else -> LyricCardGenerator.CardTheme.VIOLET
            }
        }

        // 3. Setup Share Click
        btnShare.setOnClickListener {
            val sortedSelected = selectedIndices.sorted().mapNotNull { lyrics.getOrNull(it)?.text }
            if (sortedSelected.isEmpty()) {
                Toast.makeText(context, "Please select at least one lyric line", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val currentPath = viewModel.mediaController.value?.currentMediaItem?.mediaId
            val currentSong = if (currentPath != null) viewModel.songs.value?.find { it.data == currentPath } else null
            if (currentSong == null) {
                Toast.makeText(context, "No active song playing", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                btnShare.isEnabled = false
                btnShare.text = "Generating Card..."
                LyricCardGenerator.generateAndShare(
                    requireActivity(),
                    currentSong,
                    sortedSelected,
                    selectedTheme
                )
                dismiss()
            }
        }

        updateSubtitle(tvSubtitle, btnShare)
    }

    private fun updateSubtitle(tvSubtitle: TextView, btnShare: MaterialButton) {
        val count = selectedIndices.size
        tvSubtitle.text = "Selected $count of 5 lines"
        btnShare.isEnabled = count > 0
        btnShare.alpha = if (count > 0) 1.0f else 0.4f
    }

    class LyricSelectViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvText: TextView = view.findViewById(R.id.tvLyricText)
        val ivCheck: ImageView = view.findViewById(R.id.ivSelectCheck)
    }

    companion object {
        fun newInstance(): LyricCardBottomSheet = LyricCardBottomSheet()
    }
}
