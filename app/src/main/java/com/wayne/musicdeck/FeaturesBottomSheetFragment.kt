package com.wayne.musicdeck

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.koin.android.ext.android.inject

class FeaturesBottomSheetFragment : BottomSheetDialogFragment() {
    
    private val settingsManager: com.wayne.musicdeck.utils.SettingsManager by inject()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_features, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val switchInsights = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switchInsights)
        val switchSmartPlaylists = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switchSmartPlaylists)
        val switchPlaylistCollage = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switchPlaylistCollage)
        val switchSongNotes = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switchSongNotes)

        // Init values
        switchInsights.isChecked = settingsManager.isInsightsEnabled
        switchSmartPlaylists.isChecked = settingsManager.isSmartPlaylistsEnabled
        switchPlaylistCollage.isChecked = settingsManager.isPlaylistCollageEnabled
        switchSongNotes.isChecked = settingsManager.isSongNotesEnabled

        // Listeners
        switchInsights.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.isInsightsEnabled = isChecked
        }
        view.findViewById<View>(R.id.menuInsights).setOnClickListener {
            switchInsights.isChecked = !switchInsights.isChecked
        }

        switchSmartPlaylists.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.isSmartPlaylistsEnabled = isChecked
        }
        view.findViewById<View>(R.id.menuSmartPlaylists).setOnClickListener {
            switchSmartPlaylists.isChecked = !switchSmartPlaylists.isChecked
        }

        switchPlaylistCollage.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.isPlaylistCollageEnabled = isChecked
        }
        view.findViewById<View>(R.id.menuPlaylistCollage).setOnClickListener {
            switchPlaylistCollage.isChecked = !switchPlaylistCollage.isChecked
        }

        switchSongNotes.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.isSongNotesEnabled = isChecked
        }
        view.findViewById<View>(R.id.menuSongNotes).setOnClickListener {
            switchSongNotes.isChecked = !switchSongNotes.isChecked
        }
    }
}
