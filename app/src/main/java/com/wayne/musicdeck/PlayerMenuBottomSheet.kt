package com.wayne.musicdeck

import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class PlayerMenuBottomSheet : BottomSheetDialogFragment() {

    private lateinit var viewModel: MainViewModel
    private var currentPlaybackSpeed = 1.0f
    private val speeds = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    private var currentSpeedIndex = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
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

        // Sleep Timer
        view.findViewById<View>(R.id.menuSleepTimer).setOnClickListener {
            dismiss()
            SleepTimerBottomSheet().show(parentFragmentManager, "SleepTimer")
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

        // Add to Playlist
        view.findViewById<View>(R.id.menuAddToPlaylist).setOnClickListener {
            val currentId = viewModel.mediaController.value?.currentMediaItem?.mediaId?.toLongOrNull()
            val song = viewModel.songs.value?.find { it.id == currentId }
            if (song != null) {
                dismiss()
                AddToPlaylistBottomSheet.newInstance(song).show(parentFragmentManager, "AddToPlaylist")
            } else {
                Toast.makeText(context, "No song playing", Toast.LENGTH_SHORT).show()
            }
        }

        // Song Info
        view.findViewById<View>(R.id.menuSongInfo).setOnClickListener {
            val currentId = viewModel.mediaController.value?.currentMediaItem?.mediaId?.toLongOrNull()
            val song = viewModel.songs.value?.find { it.id == currentId }
            if (song != null) {
                dismiss()
                SongInfoBottomSheet.newInstance(song).show(parentFragmentManager, "SongInfo")
            } else {
                Toast.makeText(context, "No song playing", Toast.LENGTH_SHORT).show()
            }
        }

        // Set Ringtone
        view.findViewById<View>(R.id.menuSetRingtone).setOnClickListener {
            val currentId = viewModel.mediaController.value?.currentMediaItem?.mediaId?.toLongOrNull()
            val song = viewModel.songs.value?.find { it.id == currentId }
            if (song != null) {
                try {
                    android.media.RingtoneManager.setActualDefaultRingtoneUri(
                        requireContext(),
                        android.media.RingtoneManager.TYPE_RINGTONE,
                        song.uri
                    )
                    Toast.makeText(context, "Set as ringtone: ${song.title}", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to set ringtone. Check permissions.", Toast.LENGTH_SHORT).show()
                }
                dismiss()
            } else {
                Toast.makeText(context, "No song playing", Toast.LENGTH_SHORT).show()
            }
        }

        // Share
        view.findViewById<View>(R.id.menuShare).setOnClickListener {
            val currentId = viewModel.mediaController.value?.currentMediaItem?.mediaId?.toLongOrNull()
            val song = viewModel.songs.value?.find { it.id == currentId }
            if (song != null) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "audio/*"
                    putExtra(Intent.EXTRA_STREAM, song.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Share ${song.title}"))
                dismiss()
            } else {
                Toast.makeText(context, "No song playing", Toast.LENGTH_SHORT).show()
            }
        }

        // Playback Speed
        val tvCurrentSpeed = view.findViewById<TextView>(R.id.tvCurrentSpeed)
        val player = viewModel.mediaController.value
        if (player != null) {
            currentPlaybackSpeed = player.playbackParameters.speed
            currentSpeedIndex = speeds.indexOfFirst { it == currentPlaybackSpeed }.takeIf { it >= 0 } ?: 2
            tvCurrentSpeed.text = formatSpeed(currentPlaybackSpeed)
        }
        
        view.findViewById<View>(R.id.menuPlaybackSpeed).setOnClickListener {
            val p = viewModel.mediaController.value ?: return@setOnClickListener
            currentSpeedIndex = (currentSpeedIndex + 1) % speeds.size
            val newSpeed = speeds[currentSpeedIndex]
            p.setPlaybackSpeed(newSpeed)
            tvCurrentSpeed.text = formatSpeed(newSpeed)
            Toast.makeText(context, "Speed: ${formatSpeed(newSpeed)}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatSpeed(speed: Float): String {
        return if (speed == speed.toLong().toFloat()) {
            "${speed.toLong()}x"
        } else {
            "${speed}x"
        }
    }

    companion object {
        fun newInstance(): PlayerMenuBottomSheet {
            return PlayerMenuBottomSheet()
        }
    }
}
