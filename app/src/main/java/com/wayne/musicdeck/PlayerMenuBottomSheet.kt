package com.wayne.musicdeck

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.android.ext.android.inject
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class PlayerMenuBottomSheet : BottomSheetDialogFragment() {

    private val viewModel: MainViewModel by activityViewModel()
    private val settingsManager: com.wayne.musicdeck.utils.SettingsManager by inject()
    private var currentPlaybackSpeed = 1.0f
    private val speeds = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    private var currentSpeedIndex = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_player_menu, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Cancel button
        view.findViewById<TextView>(R.id.btnCancel).setOnClickListener {
            dismiss()
        }

        // Listening History
        view.findViewById<View>(R.id.menuHistory).setOnClickListener {
            dismiss()
            HistoryBottomSheetFragment().show(parentFragmentManager, "History")
        }

        // Sleep Timer
        view.findViewById<View>(R.id.menuSleepTimer).setOnClickListener {
            dismiss()
            SleepTimerBottomSheetFragment().show(parentFragmentManager, "SleepTimer")
        }

        // Equalizer - hide if device doesn't support it
        val menuEqualizer = view.findViewById<View>(R.id.menuEqualizer)
        if (!AudioEffectManager.isSupported(requireContext())) {
            menuEqualizer.visibility = View.GONE
        } else {
            menuEqualizer.setOnClickListener {
                val player = viewModel.mediaController.value ?: return@setOnClickListener
                val controller = player as? androidx.media3.session.MediaController
                val sessionId = controller?.connectedToken?.extras?.getInt("AUDIO_SESSION_ID", 0) ?: 0
                
                if (sessionId != 0) {
                    dismiss()
                    EqualizerBottomSheet.newInstance(sessionId).show(parentFragmentManager, "Equalizer")
                } else {
                    Toast.makeText(context, "No audio session available", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Fetch Lyrics
        val menuFetchLyrics = view.findViewById<View>(R.id.menuFetchLyrics)
        val progressFetchLyrics = view.findViewById<View>(R.id.progressFetchLyrics)
        val tvFetchLyricsLabel = view.findViewById<TextView>(R.id.tvFetchLyricsLabel)
        val iconMeteredLyrics = view.findViewById<ImageView>(R.id.iconMeteredLyrics)
        
        val isMetered = com.wayne.musicdeck.utils.NetworkUtils.isOnMeteredNetwork(requireContext())
        iconMeteredLyrics?.visibility = if (isMetered) View.VISIBLE else View.GONE
        
        // Update label based on whether song already has lyrics
        val currentPath = viewModel.mediaController.value?.currentMediaItem?.mediaId
        val hasLyrics = currentPath?.let { viewModel.hasLyrics(it) } ?: false
        if (hasLyrics) {
            tvFetchLyricsLabel.text = "Re-fetch Lyrics"
        }
        
        val performLyricsFetch: (Song) -> Unit = { song ->
            progressFetchLyrics.visibility = View.VISIBLE
            tvFetchLyricsLabel.text = "Fetching..."
            viewModel.fetchLyrics(song)
        }
        
        menuFetchLyrics.setOnClickListener {
            val songPath = viewModel.mediaController.value?.currentMediaItem?.mediaId
            val song = viewModel.songs.value?.find { it.data == songPath }
            if (song != null) {
                if (isMetered && !settingsManager.skipMobileDataLyricsWarning) {
                    showMobileDataWarningDialog(requireContext()) {
                        performLyricsFetch(song)
                    }
                } else {
                    performLyricsFetch(song)
                }
            } else {
                Toast.makeText(context, "No song playing", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Observe fetch result
        viewModel.lyricsStatus.observe(viewLifecycleOwner) { status ->
            when (status) {
                is MainViewModel.LyricsStatus.Loading -> {
                    progressFetchLyrics.visibility = View.VISIBLE
                    tvFetchLyricsLabel.text = "Fetching..."
                }
                is MainViewModel.LyricsStatus.Success -> {
                    progressFetchLyrics.visibility = View.GONE
                    tvFetchLyricsLabel.text = "Re-fetch Lyrics"
                    // Don't auto-dismiss as it might strict immediately on load
                }
                is MainViewModel.LyricsStatus.NotFound -> {
                    progressFetchLyrics.visibility = View.GONE
                    tvFetchLyricsLabel.text = "Fetch Lyrics"
                    Toast.makeText(context, "Lyrics not found online", Toast.LENGTH_SHORT).show()
                }
                is MainViewModel.LyricsStatus.Error -> {
                    progressFetchLyrics.visibility = View.GONE
                    tvFetchLyricsLabel.text = "Fetch Lyrics"
                    Toast.makeText(context, status.message, Toast.LENGTH_SHORT).show()
                }
                else -> {
                    progressFetchLyrics.visibility = View.GONE
                }
            }
        }


        // Add to Playlist
        view.findViewById<View>(R.id.menuAddToPlaylist).setOnClickListener {
            val currentPath = viewModel.mediaController.value?.currentMediaItem?.mediaId
            val song = viewModel.songs.value?.find { it.data == currentPath }
            if (song != null) {
                dismiss()
                AddToPlaylistBottomSheet.newInstance(song).show(parentFragmentManager, "AddToPlaylist")
            } else {
                Toast.makeText(context, "No song playing", Toast.LENGTH_SHORT).show()
            }
        }

        // Song Info
        view.findViewById<View>(R.id.menuSongInfo).setOnClickListener {
            val currentPath = viewModel.mediaController.value?.currentMediaItem?.mediaId
            val song = viewModel.songs.value?.find { it.data == currentPath }
            if (song != null) {
                dismiss()
                SongInfoBottomSheet.newInstance(song).show(parentFragmentManager, "SongInfo")
            } else {
                Toast.makeText(context, "No song playing", Toast.LENGTH_SHORT).show()
            }
        }

        // Set Ringtone
        view.findViewById<View>(R.id.menuSetRingtone).setOnClickListener {
            val currentPath = viewModel.mediaController.value?.currentMediaItem?.mediaId
            val song = viewModel.songs.value?.find { it.data == currentPath }
            if (song != null) {
                try {
                    android.media.RingtoneManager.setActualDefaultRingtoneUri(
                        requireContext(),
                        android.media.RingtoneManager.TYPE_RINGTONE,
                        song.uri
                    )
                    android.widget.Toast.makeText(context, "Set as ringtone: ${song.title}", android.widget.Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Failed to set ringtone. Check permissions.", android.widget.Toast.LENGTH_SHORT).show()
                }
                dismiss()
            } else {
                android.widget.Toast.makeText(context, "No song playing", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        // Share
        view.findViewById<View>(R.id.menuShare).setOnClickListener {
            val currentPath = viewModel.mediaController.value?.currentMediaItem?.mediaId
            val song = viewModel.songs.value?.find { it.data == currentPath }
            if (song != null) {
                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "audio/*"
                    putExtra(android.content.Intent.EXTRA_STREAM, song.uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(android.content.Intent.createChooser(shareIntent, "Share ${song.title}"))
                dismiss()
            } else {
                android.widget.Toast.makeText(context, "No song playing", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        // Tempo & Pitch Shifter (Slowed + Reverb, Nightcore)
        val tvCurrentSpeed = view.findViewById<TextView>(R.id.tvCurrentSpeed)
        val currentTempo = settingsManager.playbackTempo
        val currentPitch = settingsManager.playbackPitchSemitones
        val isReverb = settingsManager.isReverbEnabled
        val currentPreset = settingsManager.tempoPitchPreset

        val speedText = String.format(java.util.Locale.US, "%.2fx", currentTempo)
        val pitchText = when {
            kotlin.math.abs(currentPitch) < 0.05f -> ""
            currentPitch > 0 -> " • +${String.format(java.util.Locale.US, "%.1f", currentPitch)}st"
            else -> " • ${String.format(java.util.Locale.US, "%.1f", currentPitch)}st"
        }
        val reverbText = if (isReverb) " • Reverb" else ""

        val badgeText = when {
            currentPreset.isNotEmpty() && !currentPreset.equals("Normal", ignoreCase = true) && !currentPreset.equals("Custom", ignoreCase = true) -> {
                "$currentPreset ($speedText)"
            }
            kotlin.math.abs(currentTempo - 1.0f) < 0.01f && kotlin.math.abs(currentPitch) < 0.05f && !isReverb -> "1.00x"
            else -> "$speedText$pitchText$reverbText"
        }
        tvCurrentSpeed.text = badgeText

        view.findViewById<View>(R.id.menuPlaybackSpeed).setOnClickListener {
            dismiss()
            TempoPitchBottomSheet().show(parentFragmentManager, "TempoPitch")
        }

        // Bit-Perfect USB-DAC Passthrough
        val tvUsbDacBadge = view.findViewById<TextView>(R.id.tvUsbDacBadge)
        val tvUsbDacSubtitle = view.findViewById<TextView>(R.id.tvUsbDacSubtitle)
        val dacState = com.wayne.musicdeck.audio.UsbDacManager.dacState.value
        if (dacState.isConnected) {
            tvUsbDacBadge.text = if (dacState.isPassthroughEnabled) "BIT-PERFECT" else "STANDBY"
            tvUsbDacSubtitle.text = dacState.deviceName
        } else {
            tvUsbDacBadge.text = "OFF"
            tvUsbDacSubtitle.text = "Connect USB-C DAC or interface"
        }

        view.findViewById<View>(R.id.menuUsbDac).setOnClickListener {
            dismiss()
            UsbDacBottomSheet().show(parentFragmentManager, "UsbDac")
        }

        // Dedicated Car Mode
        view.findViewById<View>(R.id.menuCarMode)?.setOnClickListener {
            dismiss()
            val intent = Intent(requireContext(), CarModeActivity::class.java)
            startActivity(intent)
        }

        // Sunset Transition Toggle
        val sunsetSwitch = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switchSunset)
        val isSunsetEnabled = settingsManager.isSunsetTransitionEnabled
        sunsetSwitch.isChecked = isSunsetEnabled
 
        sunsetSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.isSunsetTransitionEnabled = isChecked
            val msg = if (isChecked) "Sunset Transition enabled" else "Sunset Transition disabled"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.menuSunset).setOnClickListener {
            sunsetSwitch.isChecked = !sunsetSwitch.isChecked
        }

        // Crossfade Transition Toggle & Duration Slider
        val crossfadeSwitch = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switchCrossfade)
        val layoutCrossfadeDuration = view.findViewById<View>(R.id.layoutCrossfadeDuration)
        val tvCrossfadeDuration = view.findViewById<TextView>(R.id.tvCrossfadeDuration)
        val seekCrossfadeDuration = view.findViewById<androidx.appcompat.widget.AppCompatSeekBar>(R.id.seekCrossfadeDuration)

        crossfadeSwitch.isChecked = settingsManager.isCrossfadeEnabled
        layoutCrossfadeDuration.visibility = if (settingsManager.isCrossfadeEnabled) View.VISIBLE else View.GONE
        
        val initialDuration = settingsManager.crossfadeDurationSeconds.coerceIn(1, 12)
        seekCrossfadeDuration.progress = initialDuration
        tvCrossfadeDuration.text = "${initialDuration}s"

        crossfadeSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.isCrossfadeEnabled = isChecked
            layoutCrossfadeDuration.visibility = if (isChecked) View.VISIBLE else View.GONE
            val msg = if (isChecked) "Crossfade enabled (${settingsManager.crossfadeDurationSeconds}s)" else "Crossfade disabled"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }

        seekCrossfadeDuration.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                val sec = progress.coerceAtLeast(1)
                if (progress < 1) {
                    seekBar?.progress = 1
                }
                if (fromUser) {
                    settingsManager.crossfadeDurationSeconds = sec
                    tvCrossfadeDuration.text = "${sec}s"
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        view.findViewById<View>(R.id.menuCrossfade).setOnClickListener {
            crossfadeSwitch.isChecked = !crossfadeSwitch.isChecked
        }

        // Sound Check (Loudness Normalization) Toggle
        val soundCheckSwitch = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switchSoundCheck)
        soundCheckSwitch.isChecked = settingsManager.isSoundCheckEnabled

        soundCheckSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.isSoundCheckEnabled = isChecked
            if (isChecked) {
                // Apply safe hearing limit immediately if volume is high
                val audioManager = requireContext().getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
                if (audioManager != null) {
                    val maxVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                    val safeLimit = (maxVol * 0.8f).toInt()
                    val curVol = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                    if (curVol > safeLimit) {
                        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, safeLimit, android.media.AudioManager.FLAG_SHOW_UI)
                    }
                }
                // Reset LoudnessEnhancer to 0 gain - normalized output
                AudioEffectManager.setVolumeBoostGain(0, requireContext())
                Toast.makeText(context, "Sound Check on - volume normalized to safe level", Toast.LENGTH_SHORT).show()
            } else {
                // Restore user's saved volume boost
                val savedGain = AudioEffectManager.getSavedVolumeBoostGain(requireContext())
                AudioEffectManager.setVolumeBoostGain(savedGain, requireContext())
                Toast.makeText(context, "Sound Check off", Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<View>(R.id.menuSoundCheck).setOnClickListener {
            soundCheckSwitch.isChecked = !soundCheckSwitch.isChecked
        }

        // Spectrum Visualizer Toggle
        val visualizerSwitch = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switchVisualizer)
        visualizerSwitch.isChecked = settingsManager.isVisualizerEnabled

        visualizerSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.isVisualizerEnabled = isChecked
            // Update active PlayerBottomSheetFragment immediately
            (parentFragmentManager.fragments.filterIsInstance<PlayerBottomSheetFragment>().firstOrNull()
                ?: (activity as? MainActivity)?.supportFragmentManager?.fragments?.filterIsInstance<PlayerBottomSheetFragment>()?.firstOrNull())
                ?.setVisualizerEnabled(isChecked)
        }

        view.findViewById<View>(R.id.menuVisualizer).setOnClickListener {
            visualizerSwitch.isChecked = !visualizerSwitch.isChecked
        }

        // A-B Practice Looper Toggle
        val switchAbLoop = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switchAbLoop)
        switchAbLoop.isChecked = settingsManager.isAbLoopEnabled

        switchAbLoop.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.isAbLoopEnabled = isChecked
            (parentFragmentManager.fragments.filterIsInstance<PlayerBottomSheetFragment>().firstOrNull()
                ?: (activity as? MainActivity)?.supportFragmentManager?.fragments?.filterIsInstance<PlayerBottomSheetFragment>()?.firstOrNull())
                ?.applyPlayerControlVisibility()
        }

        view.findViewById<View>(R.id.menuAbLoop).setOnClickListener {
            switchAbLoop.isChecked = !switchAbLoop.isChecked
        }

        // Audio Stream Telemetry Pill Toggle
        val switchAudioBadge = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switchAudioBadge)
        switchAudioBadge.isChecked = settingsManager.isAudioBadgeEnabled

        switchAudioBadge.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.isAudioBadgeEnabled = isChecked
            (parentFragmentManager.fragments.filterIsInstance<PlayerBottomSheetFragment>().firstOrNull()
                ?: (activity as? MainActivity)?.supportFragmentManager?.fragments?.filterIsInstance<PlayerBottomSheetFragment>()?.firstOrNull())
                ?.applyPlayerControlVisibility()
        }

        view.findViewById<View>(R.id.menuAudioBadge).setOnClickListener {
            switchAudioBadge.isChecked = !switchAudioBadge.isChecked
        }

        // Waveform Scrub Bar Toggle
        val switchWaveform = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switchWaveform)
        switchWaveform.isChecked = settingsManager.isWaveformScrubBarEnabled

        switchWaveform.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.isWaveformScrubBarEnabled = isChecked
            (parentFragmentManager.fragments.filterIsInstance<PlayerBottomSheetFragment>().firstOrNull()
                ?: (activity as? MainActivity)?.supportFragmentManager?.fragments?.filterIsInstance<PlayerBottomSheetFragment>()?.firstOrNull())
                ?.applyPlayerControlVisibility()
        }

        view.findViewById<View>(R.id.menuWaveform).setOnClickListener {
            switchWaveform.isChecked = !switchWaveform.isChecked
        }
    }

    private fun formatSpeed(speed: Float): String {
        return if (speed == speed.toLong().toFloat()) {
            "${speed.toLong()}x"
        } else {
            "${speed}x"
        }
    }

    private fun showMobileDataWarningDialog(context: Context, onConfirm: () -> Unit) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_mobile_data_warning, null)
        val cbDontAsk = dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbDontAskAgain)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
            .setView(dialogView)
            .setPositiveButton("Download") { _, _ ->
                if (cbDontAsk.isChecked) {
                    settingsManager.skipMobileDataLyricsWarning = true
                }
                onConfirm()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    companion object {
        fun newInstance(): PlayerMenuBottomSheet {
            return PlayerMenuBottomSheet()
        }
    }
}
