package com.wayne.musicdeck

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class SearchBottomSheet : BottomSheetDialogFragment() {

    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: SongAdapter
    
    private lateinit var etSearch: EditText
    private lateinit var chipGroupHistory: ChipGroup
    private lateinit var tvNoHistory: TextView
    private lateinit var historyHeader: LinearLayout
    private lateinit var rvSearchResults: RecyclerView
    
    var onSongClick: ((Song) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        
        // Find views
        etSearch = view.findViewById(R.id.etSearch)
        chipGroupHistory = view.findViewById(R.id.chipGroupHistory)
        tvNoHistory = view.findViewById(R.id.tvNoHistory)
        historyHeader = view.findViewById(R.id.historyHeader)
        rvSearchResults = view.findViewById(R.id.rvSearchResults)
        
        val btnBack = view.findViewById<ImageButton>(R.id.btnBack)
        val btnSearch = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSearch)
        val btnClearHistory = view.findViewById<ImageButton>(R.id.btnClearHistory)
        
        // Setup adapter
        adapter = SongAdapter { song ->
            onSongClick?.invoke(song)
            dismiss()
        }
        rvSearchResults.layoutManager = LinearLayoutManager(context)
        rvSearchResults.adapter = adapter
        
        // Load search history
        loadSearchHistory()
        
        // Back button
        btnBack.setOnClickListener { dismiss() }
        
        // Search button
        btnSearch.setOnClickListener { performSearch() }
        
        // Search on keyboard action
        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else false
        }
        
        // Clear history
        btnClearHistory.setOnClickListener {
            viewModel.clearSearchHistory()
            loadSearchHistory()
            android.widget.Toast.makeText(context, "History cleared", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        // Focus and show keyboard
        etSearch.requestFocus()
    }
    
    override fun onStart() {
        super.onStart()
        // Make it full screen
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            val behavior = BottomSheetBehavior.from(it)
            val height = resources.displayMetrics.heightPixels
            behavior.peekHeight = height
            it.layoutParams.height = height
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
        }
    }
    
    private fun loadSearchHistory() {
        val history = viewModel.getSearchHistory()
        chipGroupHistory.removeAllViews()
        
        if (history.isEmpty()) {
            tvNoHistory.visibility = View.VISIBLE
            chipGroupHistory.visibility = View.GONE
        } else {
            tvNoHistory.visibility = View.GONE
            chipGroupHistory.visibility = View.VISIBLE
            
            history.forEach { query ->
                val chip = Chip(requireContext()).apply {
                    text = query
                    isClickable = true
                    isCheckable = false
                    setOnClickListener {
                        etSearch.setText(query)
                        performSearch()
                    }
                }
                chipGroupHistory.addView(chip)
            }
        }
    }
    
    private fun performSearch() {
        val query = etSearch.text.toString().trim()
        if (query.isEmpty()) {
            android.widget.Toast.makeText(context, "Enter a search term", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        // Save to history
        viewModel.saveSearchQuery(query)
        
        // Filter songs
        val allSongs = viewModel.songs.value ?: emptyList()
        val results = allSongs.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.artist.contains(query, ignoreCase = true) ||
            it.album.contains(query, ignoreCase = true)
        }
        
        // Show results
        if (results.isEmpty()) {
            android.widget.Toast.makeText(context, "No results found", android.widget.Toast.LENGTH_SHORT).show()
            adapter.submitList(emptyList())
        } else {
            // Submit as SongListItem (without headers for search results)
            adapter.submitList(results.map { SongListItem.SongItem(it) })
        }
        
        // Hide history section
        historyHeader.visibility = View.GONE
        chipGroupHistory.visibility = View.GONE
        tvNoHistory.visibility = View.GONE
        
        // Refresh history for next time
        loadSearchHistory()
    }
    
    companion object {
        fun newInstance(): SearchBottomSheet {
            return SearchBottomSheet()
        }
    }
}
