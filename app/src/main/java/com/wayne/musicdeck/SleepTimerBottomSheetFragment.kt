package com.wayne.musicdeck

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.wayne.musicdeck.databinding.FragmentSleepTimerBinding
import com.wayne.musicdeck.utils.HapticManager
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import java.util.Locale

class SleepTimerBottomSheetFragment : BottomSheetDialogFragment() {
    
    private var _binding: FragmentSleepTimerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModel()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSleepTimerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Close / Cancel header
        binding.btnCancelHeader.setOnClickListener {
            dismiss()
        }

        // Preset Pills
        setupPresetPill(binding.btn15Min, 15)
        setupPresetPill(binding.btn30Min, 30)
        setupPresetPill(binding.btn45Min, 45)
        setupPresetPill(binding.btn60Min, 60)
        setupPresetPill(binding.btn90Min, 90)

        // Slider
        val density = resources.displayMetrics.density
        binding.sliderMinutes.apply {
            thumbRadius = (10 * density).toInt()
            thumbElevation = 2f
            haloRadius = 0
            trackHeight = (6 * density).toInt()
            isTickVisible = false
        }
        binding.sliderMinutes.addOnChangeListener { _, value, _ ->
            val mins = value.toInt()
            binding.tvCustomMinutesValue.text = "$mins minutes"
            binding.btnStartCustom.text = "Start Sleep Timer ($mins min)"
        }

        // Start Custom Button
        binding.btnStartCustom.setOnClickListener {
            val mins = binding.sliderMinutes.value.toInt()
            HapticManager.performSpringClick(requireContext())
            setTimer(mins)
        }

        // End of Current Song Hero Tile
        binding.btnEndOfSong.setOnClickListener {
            HapticManager.performSpringClick(requireContext())
            context?.startService(Intent(context, MusicService::class.java).apply {
                action = MusicService.ACTION_SET_SLEEP_TIMER_END_OF_SONG
            })
            dismiss()
        }

        // Stop Active Timer Button
        binding.btnStopActiveTimer.setOnClickListener {
            HapticManager.performSpringClick(requireContext())
            context?.startService(Intent(context, MusicService::class.java).apply {
                action = MusicService.ACTION_CANCEL_SLEEP_TIMER
            })
            binding.layoutActiveTimer.visibility = View.GONE
        }

        // Observe Session Extras for Active Timer Countdown
        viewModel.mediaController.observe(viewLifecycleOwner) { controller ->
            val extras = (controller as? androidx.media3.session.MediaController)?.sessionExtras
            val remainingMs = extras?.getLong("SLEEP_TIMER_REMAINING_MS", 0L) ?: 0L
            if (remainingMs > 0) {
                binding.layoutActiveTimer.visibility = View.VISIBLE
                val minutes = (remainingMs / 1000) / 60
                val seconds = (remainingMs / 1000) % 60
                binding.tvActiveTimerRemaining.text = String.format(Locale.getDefault(), "Stops in %02d:%02d", minutes, seconds)
            } else {
                binding.layoutActiveTimer.visibility = View.GONE
            }
        }
    }

    private fun setupPresetPill(pillView: TextView, minutes: Int) {
        pillView.setOnClickListener {
            HapticManager.performSpringClick(requireContext())
            setTimer(minutes)
        }
    }
    
    private fun setTimer(minutes: Int) {
        val intent = Intent(context, MusicService::class.java).apply {
            action = MusicService.ACTION_SET_SLEEP_TIMER
            putExtra(MusicService.EXTRA_TIMER_MINUTES, minutes)
        }
        context?.startService(intent)
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

