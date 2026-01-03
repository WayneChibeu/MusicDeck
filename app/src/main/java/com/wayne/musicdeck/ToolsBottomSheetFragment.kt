package com.wayne.musicdeck

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import androidx.fragment.app.activityViewModels

class ToolsBottomSheetFragment : BottomSheetDialogFragment() {
    
    private var _binding: com.wayne.musicdeck.databinding.FragmentToolsBinding? = null // Layout needed
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = com.wayne.musicdeck.databinding.FragmentToolsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.btnRescan.setOnClickListener { 
            viewModel.loadPlaylists() // Or loadSongs()
            // MainViewModel has loadSongs which is triggered by Observer, but we can force it
            // Actually viewModel.loadSongs() is public? 
            // In step 6000: fun loadSongs() is public.
             viewModel.loadSongs()
             android.widget.Toast.makeText(context, "Scanning library...", android.widget.Toast.LENGTH_SHORT).show()
             dismiss()
        }
        
        binding.btnBackup.setOnClickListener {
            viewModel.exportBackup()
        }
        
        binding.btnRestore.setOnClickListener {
             viewModel.importBackup()
        }
        
        binding.btnTheme.setOnClickListener {
            dismiss()
            ThemeSelectionBottomSheet().show(parentFragmentManager, "theme")
        }
        
        // Observe backup result
        viewModel.backupResult.observe(viewLifecycleOwner) { result ->
            if (!result.isNullOrEmpty()) {
                android.widget.Toast.makeText(context, result, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
