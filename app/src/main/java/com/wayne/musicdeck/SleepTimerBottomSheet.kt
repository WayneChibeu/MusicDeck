package com.wayne.musicdeck

import android.content.Context
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.media3.session.MediaController
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip

class SleepTimerBottomSheet : BottomSheetDialogFragment() {
    
    companion object {
        private var countdownTimer: CountDownTimer? = null
        var remainingTimeMs: Long = 0L
            private set
        
        // Store a callback to pause playback
        private var pauseCallback: (() -> Unit)? = null
        
        fun setPauseCallback(callback: (() -> Unit)?) {
            pauseCallback = callback
        }
        
        fun cancelTimer() {
            countdownTimer?.cancel()
            countdownTimer = null
            remainingTimeMs = 0L
        }
        
        fun isTimerActive(): Boolean = remainingTimeMs > 0L
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_sleep_timer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val btn15 = view.findViewById<android.widget.Button>(R.id.btn15Min)
        val btn30 = view.findViewById<android.widget.Button>(R.id.btn30Min)
        val btn45 = view.findViewById<android.widget.Button>(R.id.btn45Min)
        val btn60 = view.findViewById<android.widget.Button>(R.id.btn60Min)
        val btnStop = view.findViewById<android.widget.Button>(R.id.btnStop)
        
        btn15.setOnClickListener { startTimer(15) }
        btn30.setOnClickListener { startTimer(30) }
        btn45.setOnClickListener { startTimer(45) }
        btn60.setOnClickListener { startTimer(60) }
        
        btnStop.setOnClickListener {
            cancelTimer()
            Toast.makeText(context, "Sleep timer cancelled", Toast.LENGTH_SHORT).show()
            dismiss()
        }
        
        // Custom timer input
        val etCustom = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etCustomMinutes)
        val btnSetCustom = view.findViewById<android.widget.Button>(R.id.btnSetCustom)
        
        btnSetCustom.setOnClickListener {
            val minutes = etCustom.text.toString().toIntOrNull()
            if (minutes != null && minutes > 0) {
                startTimer(minutes)
            } else {
                Toast.makeText(context, "Enter a valid number of minutes", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Update cancel button visibility
        btnStop.visibility = if (isTimerActive()) View.VISIBLE else View.GONE
    }

    private fun startTimer(minutes: Int) {
        // Cancel any existing timer
        cancelTimer()
        
        val durationMs = minutes * 60 * 1000L
        remainingTimeMs = durationMs
        
        val ctx = context
        
        countdownTimer = object : CountDownTimer(durationMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                remainingTimeMs = millisUntilFinished
            }

            override fun onFinish() {
                remainingTimeMs = 0L
                // Pause playback via callback
                pauseCallback?.invoke()
                ctx?.let {
                    Toast.makeText(it, "Sleep timer ended - playback paused", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
        
        Toast.makeText(context, "Sleep timer set for $minutes minutes", Toast.LENGTH_SHORT).show()
        dismiss()
    }
}
