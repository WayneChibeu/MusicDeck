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

    private val exportBackupLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            viewModel.exportBackup(uri)
        }
    }

    private val importBackupLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.importBackup(uri)
        }
    }

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
            viewModel.loadPlaylists()
            viewModel.loadSongs()
            android.widget.Toast.makeText(context, "Scanning library...", android.widget.Toast.LENGTH_SHORT).show()
            dismiss()
        }
        
        binding.btnBackup.setOnClickListener {
            val dateStr = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.getDefault()).format(java.util.Date())
            exportBackupLauncher.launch("MusicDeck_Playlists_$dateStr.json")
        }
        
        binding.btnRestore.setOnClickListener {
            importBackupLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
        }
        
        binding.btnTheme.setOnClickListener {
            dismiss()
            ThemeSelectionBottomSheet().show(parentFragmentManager, "theme")
        }
        
        binding.btnSmartOrganize.setOnClickListener {
            dismiss()
            SmartOrganizeBottomSheet().show(parentFragmentManager, "smart_organize")
        }
        
        binding.btnInsights.setOnClickListener {
            dismiss()
            startActivity(android.content.Intent(context, InsightsActivity::class.java))
        }

        binding.btnAbout.setOnClickListener {
            dismiss()
            LegalBottomSheetFragment().show(parentFragmentManager, "legal")
        }

        binding.btnFeatures.setOnClickListener {
            dismiss()
            FeaturesBottomSheetFragment().show(parentFragmentManager, "features")
        }
        
        // Observe backup result
        viewModel.backupResult.observe(viewLifecycleOwner) { result ->
            if (!result.isNullOrEmpty()) {
                android.widget.Toast.makeText(context, result, android.widget.Toast.LENGTH_SHORT).show()
                viewModel.clearBackupResult()
            }
        }
    }
    
    private fun showAboutDialog() {
        val ctx = context ?: return
        val version = try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        } catch (e: Exception) {
            "2.5.0"
        }
        
        val message = """
            |MusicDeck v$version
            |Made with ❤️ by the MusicDeck Team
            |
            |Lyrics are graciously powered by LRCLIB.
            |
            |Privacy Policy
            |All of your music and personal data strictly remain on your device. We do not track, collect, or share your data with any third parties. Internet access is utilized solely to provide metadata and real-time lyrics for an enhanced playback experience.
        """.trimMargin()
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
            .setTitle("About MusicDeck")
            .setMessage(message)
            .setPositiveButton("Got it!", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
