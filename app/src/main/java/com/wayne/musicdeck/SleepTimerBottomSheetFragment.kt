package com.wayne.musicdeck

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.wayne.musicdeck.databinding.FragmentSleepTimerBinding // Need to create layout!
// I'll use standard view references if binding not generated yet, or creating layout first.
// I'll create layout next step. For now code assumes binding.

class SleepTimerBottomSheetFragment : BottomSheetDialogFragment() {
    
    private var _binding: com.wayne.musicdeck.databinding.FragmentSleepTimerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = com.wayne.musicdeck.databinding.FragmentSleepTimerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.btn15Min.setOnClickListener { setTimer(15) }
        binding.btn30Min.setOnClickListener { setTimer(30) }
        binding.btn45Min.setOnClickListener { setTimer(45) }
        binding.btn60Min.setOnClickListener { setTimer(60) }
        binding.btnStop.setOnClickListener { 
            context?.startService(Intent(context, MusicService::class.java).apply {
                action = MusicService.ACTION_CANCEL_SLEEP_TIMER
            })
            dismiss()
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
