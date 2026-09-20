package com.wayne.musicdeck

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
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
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.transform.RoundedCornersTransformation
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.wayne.musicdeck.data.LyricLine
import com.wayne.musicdeck.utils.LyricCardGenerator
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import java.io.File

class LyricCardBottomSheet : BottomSheetDialogFragment() {

    private val viewModel: MainViewModel by activityViewModel()
    private var lyrics: List<LyricLine> = emptyList()
    private val selectedIndices = mutableSetOf<Int>()
    private var selectedTheme = LyricCardGenerator.CardTheme.ALBUM_MATCH
    private var cachedCoverBitmap: Bitmap? = null

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

        // Live Preview Views
        val cardPreviewContainer = view.findViewById<View>(R.id.cardPreviewContainer)
        val ivPreviewCover = view.findViewById<ImageView>(R.id.ivPreviewCover)
        val tvPreviewTitle = view.findViewById<TextView>(R.id.tvPreviewTitle)
        val tvPreviewArtist = view.findViewById<TextView>(R.id.tvPreviewArtist)
        val tvPreviewQuote = view.findViewById<TextView>(R.id.tvPreviewQuote)
        val tvPreviewLyrics = view.findViewById<TextView>(R.id.tvPreviewLyrics)

        fun updatePreviewTheme() {
            if (selectedTheme == LyricCardGenerator.CardTheme.ALBUM_MATCH) {
                val dynamicTheme = LyricCardGenerator.createDynamicTheme(requireContext(), cachedCoverBitmap)
                cardPreviewContainer.background = dynamicTheme.backgroundDrawable
                tvPreviewQuote.setTextColor(dynamicTheme.accentColor)
            } else {
                cardPreviewContainer.setBackgroundResource(selectedTheme.backgroundRes)
                tvPreviewQuote.setTextColor(selectedTheme.accentColor)
            }
        }

        fun updatePreviewLyrics() {
            val sortedSelected = selectedIndices.sorted().mapNotNull { lyrics.getOrNull(it)?.text }
            tvPreviewLyrics.text = if (sortedSelected.isNotEmpty()) {
                sortedSelected.joinToString("\n")
            } else {
                "Select lyrics below to preview..."
            }
        }

        // Initialize preview theme
        updatePreviewTheme()

        // Populate song metadata & artwork in preview
        val currentPath = viewModel.mediaController.value?.currentMediaItem?.mediaId
        val currentSong = if (currentPath != null) viewModel.songs.value?.find { it.data == currentPath } else null
        if (currentSong != null) {
            tvPreviewTitle.text = currentSong.title
            tvPreviewArtist.text = currentSong.artist
            
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val loader = requireContext().imageLoader
                    val request = ImageRequest.Builder(requireContext())
                        .data(File(currentSong.data))
                        .transformations(RoundedCornersTransformation(16f))
                        .allowHardware(false)
                        .build()
                    val result = loader.execute(request)
                    if (result is SuccessResult) {
                        ivPreviewCover.setImageDrawable(result.drawable)
                        cachedCoverBitmap = (result.drawable as? BitmapDrawable)?.bitmap
                        updatePreviewTheme()
                    }
                } catch (_: Exception) {}
            }
        }

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
            updatePreviewLyrics()
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
                    updatePreviewLyrics()
                }
            }
        }
        rvLyrics.adapter = adapter

        // 2. Setup theme picker
        chipGroupThemes.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedTheme = when (checkedIds.firstOrNull()) {
                R.id.chipThemeAlbum -> LyricCardGenerator.CardTheme.ALBUM_MATCH
                R.id.chipThemeMidnight -> LyricCardGenerator.CardTheme.MIDNIGHT
                R.id.chipThemeRose -> LyricCardGenerator.CardTheme.ROSE
                R.id.chipThemeCyan -> LyricCardGenerator.CardTheme.CYAN
                R.id.chipThemeViolet -> LyricCardGenerator.CardTheme.VIOLET
                else -> LyricCardGenerator.CardTheme.ALBUM_MATCH
            }
            updatePreviewTheme()
        }

        // 3. Setup Share Click
        btnShare.setOnClickListener {
            val sortedSelected = selectedIndices.sorted().mapNotNull { lyrics.getOrNull(it)?.text }
            if (sortedSelected.isEmpty()) {
                Toast.makeText(context, "Please select at least one lyric line", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val curSong = if (currentPath != null) viewModel.songs.value?.find { it.data == currentPath } else null
            if (curSong == null) {
                Toast.makeText(context, "No active song playing", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                btnShare.isEnabled = false
                btnShare.text = "Generating Card..."
                LyricCardGenerator.generateAndShare(
                    requireActivity(),
                    curSong,
                    sortedSelected,
                    selectedTheme
                )
                dismiss()
            }
        }

        updateSubtitle(tvSubtitle, btnShare)
        updatePreviewLyrics()
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
