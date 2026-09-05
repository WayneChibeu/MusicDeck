package com.wayne.musicdeck

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.materialswitch.MaterialSwitch

class EqualizerBottomSheet : BottomSheetDialogFragment() {
    
    private val seekBars = mutableListOf<SeekBar>()
    private val freqLabels = mutableListOf<TextView>()
    private val gainLabels = mutableListOf<TextView>()
    private val presetPillViews = mutableMapOf<String, TextView>()
    
    // Presets: name -> array of band values (normalized 0-100)
    private val customPresets = mapOf(
        "MusicDeck Signature" to intArrayOf(62, 56, 52, 60, 68),
        "Cinema 3D" to intArrayOf(75, 50, 42, 65, 75),
        "Vocal Clarity" to intArrayOf(35, 45, 75, 70, 50),
        "Night Warmth" to intArrayOf(55, 50, 48, 45, 40),
        "Live Stage" to intArrayOf(65, 52, 55, 65, 75),
        "Flat" to intArrayOf(50, 50, 50, 50, 50),
        "Bass" to intArrayOf(85, 75, 40, 50, 60),
        "Classical" to intArrayOf(65, 60, 50, 55, 60),
        "Dance" to intArrayOf(75, 40, 50, 60, 65),
        "Folk" to intArrayOf(60, 50, 50, 55, 60),
        "Heavy Metal" to intArrayOf(70, 60, 55, 75, 80),
        "Hip Hop" to intArrayOf(80, 65, 45, 55, 75),
        "Jazz" to intArrayOf(60, 50, 50, 55, 65),
        "Pop" to intArrayOf(55, 65, 70, 60, 55),
        "Rock" to intArrayOf(70, 60, 50, 60, 70)
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_equalizer, container, false)
    }

    override fun onStart() {
        super.onStart()
        // Configure bottom sheet to prevent accidental dismissal during slider manipulation
        val dialog = dialog as? BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            val behavior = BottomSheetBehavior.from(it)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val eq = AudioEffectManager.getEqualizer()
        
        if (eq == null) {
            val errorMsg = AudioEffectManager.lastInitError ?: "Equalizer not available on this device"
            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
            dismiss()
            return
        }
        
        try {
            setupEqualizer(view)
            setupBassBoost(view)
            setupVolumeBoost(view)
            setupVirtualizer(view)
            setupExtremeBass(view)
            setupPresets(view)
            setupSwitch(view)
            setupResetButton(view)
            setupPresetScrollProtection(view)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to initialize EQ UI: ${e.message}", Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachTouchDisallow(v: View) {
        v.setOnTouchListener { target, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    disallowParentIntercept(target, true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    disallowParentIntercept(target, false)
                }
            }
            false
        }
    }

    /**
     * Walk the full parent chain to prevent the BottomSheet from
     * intercepting horizontal scrolls as dismiss gestures.
     */
    private fun disallowParentIntercept(view: View, disallow: Boolean) {
        var parent = view.parent
        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow)
            parent = parent.parent
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPresetScrollProtection(view: View) {
        val scrollView = view.findViewById<android.widget.HorizontalScrollView>(R.id.scrollPresets) ?: return
        val touchSlop = android.view.ViewConfiguration.get(requireContext()).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var isHorizontalScroll = false

        scrollView.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    isHorizontalScroll = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = Math.abs(event.x - downX)
                    val dy = Math.abs(event.y - downY)
                    if (!isHorizontalScroll && dx > touchSlop && dx > dy) {
                        isHorizontalScroll = true
                    }
                    if (isHorizontalScroll) {
                        disallowParentIntercept(v, true)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isHorizontalScroll) {
                        disallowParentIntercept(v, false)
                    }
                    isHorizontalScroll = false
                }
            }
            false // Let HorizontalScrollView handle the scroll itself
        }
    }
    
    private fun setupEqualizer(view: View) {
        val eq = AudioEffectManager.getEqualizer() ?: return
        
        val bandCount = eq.numberOfBands.toInt()
        val minLevel = eq.bandLevelRange[0]
        val maxLevel = eq.bandLevelRange[1]
        val range = maxLevel - minLevel
        
        seekBars.clear()
        freqLabels.clear()
        gainLabels.clear()

        val sb1 = view.findViewById<SeekBar>(R.id.seekBand1)
        val sb2 = view.findViewById<SeekBar>(R.id.seekBand2)
        val sb3 = view.findViewById<SeekBar>(R.id.seekBand3)
        val sb4 = view.findViewById<SeekBar>(R.id.seekBand4)
        val sb5 = view.findViewById<SeekBar>(R.id.seekBand5)

        val fl1 = view.findViewById<TextView>(R.id.labelBand1)
        val fl2 = view.findViewById<TextView>(R.id.labelBand2)
        val fl3 = view.findViewById<TextView>(R.id.labelBand3)
        val fl4 = view.findViewById<TextView>(R.id.labelBand4)
        val fl5 = view.findViewById<TextView>(R.id.labelBand5)

        val gl1 = view.findViewById<TextView>(R.id.tvGainBand1)
        val gl2 = view.findViewById<TextView>(R.id.tvGainBand2)
        val gl3 = view.findViewById<TextView>(R.id.tvGainBand3)
        val gl4 = view.findViewById<TextView>(R.id.tvGainBand4)
        val gl5 = view.findViewById<TextView>(R.id.tvGainBand5)

        seekBars.addAll(listOf(sb1, sb2, sb3, sb4, sb5))
        freqLabels.addAll(listOf(fl1, fl2, fl3, fl4, fl5))
        gainLabels.addAll(listOf(gl1, gl2, gl3, gl4, gl5))
        
        // Touch interception protection for bands container
        view.findViewById<View>(R.id.eqBandsContainer)?.let { attachTouchDisallow(it) }

        for (i in 0 until minOf(bandCount, 5)) {
            val centerFreq = eq.getCenterFreq(i.toShort()) / 1000
            val formattedFreq = if (centerFreq >= 1000) {
                String.format("%.1f kHz", centerFreq / 1000f)
            } else {
                "$centerFreq Hz"
            }
            freqLabels[i].text = formattedFreq
            
            seekBars[i].max = 100
            
            val currentLevel = eq.getBandLevel(i.toShort())
            val progress = ((currentLevel - minLevel) * 100 / range)
            seekBars[i].progress = progress
            
            // Format dynamic dB gain label
            updateGainLabel(i, currentLevel)

            // Prevent touch drag conflict
            attachTouchDisallow(seekBars[i])
            
            val bandIndex = i
            seekBars[i].setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        AudioEffectManager.setBandLevel(bandIndex.toShort(), progress, requireContext())
                        val level = (minLevel + (progress * range / 100)).toShort()
                        updateGainLabel(bandIndex, level)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    seekBar?.parent?.requestDisallowInterceptTouchEvent(true)
                }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    seekBar?.parent?.requestDisallowInterceptTouchEvent(false)
                }
            })
        }
    }

    private fun updateGainLabel(bandIndex: Int, levelMilliBels: Short) {
        if (bandIndex !in gainLabels.indices) return
        val dB = levelMilliBels / 100
        val text = if (dB > 0) "+$dB dB" else "$dB dB"
        gainLabels[bandIndex].text = text
    }
    
    private fun setupBassBoost(view: View) {
        val bb = AudioEffectManager.getBassBoost() ?: return
        val seekBassBoost = view.findViewById<SeekBar>(R.id.seekBassBoost)
        val tvBassBoostLevel = view.findViewById<TextView>(R.id.tvBassBoostLevel)

        if (!bb.strengthSupported) {
            seekBassBoost.isEnabled = false
            return
        }
        
        attachTouchDisallow(seekBassBoost)

        val currentStrength = bb.roundedStrength // 0 - 1000
        val progress = currentStrength.toInt()
        
        seekBassBoost.progress = progress
        tvBassBoostLevel.text = "${progress / 10}%"
        
        seekBassBoost.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    AudioEffectManager.setBassBoostStrength(progress, requireContext())
                    tvBassBoostLevel.text = "${progress / 10}%"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                seekBar?.parent?.requestDisallowInterceptTouchEvent(true)
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.parent?.requestDisallowInterceptTouchEvent(false)
            }
        })
    }

    private fun setupVolumeBoost(view: View) {
        val seekVolume = view.findViewById<SeekBar>(R.id.seekVolumeBoost) ?: return
        val tvVolumeLevel = view.findViewById<TextView>(R.id.tvVolumeBoostLevel) ?: return
        
        attachTouchDisallow(seekVolume)
        
        val currentGain = AudioEffectManager.getSavedVolumeBoostGain(requireContext())
        seekVolume.progress = currentGain
        val db = currentGain / 100
        val dbDec = (currentGain % 100) / 10
        tvVolumeLevel.text = "+$db.$dbDec dB"

        var lastAppliedGain = currentGain
        val throttleHandler = android.os.Handler(android.os.Looper.getMainLooper())
        var pendingRunnable: Runnable? = null

        seekVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val curDb = progress / 100
                    val curDec = (progress % 100) / 10
                    tvVolumeLevel.text = "+$curDb.$curDec dB"

                    // Smooth parameter stepping: step at 25mB intervals or 30ms throttle to prevent zipper noise
                    if (Math.abs(progress - lastAppliedGain) >= 25) {
                        pendingRunnable?.let { throttleHandler.removeCallbacks(it) }
                        lastAppliedGain = progress
                        AudioEffectManager.setVolumeBoostGain(progress, requireContext(), saveToPrefs = false)
                    } else {
                        pendingRunnable?.let { throttleHandler.removeCallbacks(it) }
                        val runnable = Runnable {
                            lastAppliedGain = progress
                            AudioEffectManager.setVolumeBoostGain(progress, requireContext(), saveToPrefs = false)
                        }
                        pendingRunnable = runnable
                        throttleHandler.postDelayed(runnable, 30)
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                seekBar?.parent?.requestDisallowInterceptTouchEvent(true)
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.parent?.requestDisallowInterceptTouchEvent(false)
                pendingRunnable?.let { throttleHandler.removeCallbacks(it) }
                val finalProgress = seekBar?.progress ?: return
                // Persist to disk only on release to eliminate I/O pauses during playback
                AudioEffectManager.setVolumeBoostGain(finalProgress, requireContext(), saveToPrefs = true)
            }
        })
    }

    private fun setupVirtualizer(view: View) {
        val seekVirtualizer = view.findViewById<SeekBar>(R.id.seekVirtualizer) ?: return
        val tvVirtualizerLevel = view.findViewById<TextView>(R.id.tvVirtualizerLevel) ?: return
        
        attachTouchDisallow(seekVirtualizer)
        
        val currentStrength = AudioEffectManager.getSavedVirtualizerStrength(requireContext())
        seekVirtualizer.progress = currentStrength
        tvVirtualizerLevel.text = "${currentStrength / 10}%"

        var lastAppliedStrength = currentStrength
        val throttleHandler = android.os.Handler(android.os.Looper.getMainLooper())
        var pendingRunnable: Runnable? = null

        seekVirtualizer.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    tvVirtualizerLevel.text = "${progress / 10}%"

                    if (Math.abs(progress - lastAppliedStrength) >= 20) {
                        pendingRunnable?.let { throttleHandler.removeCallbacks(it) }
                        lastAppliedStrength = progress
                        AudioEffectManager.setVirtualizerStrength(progress, requireContext(), saveToPrefs = false)
                    } else {
                        pendingRunnable?.let { throttleHandler.removeCallbacks(it) }
                        val runnable = Runnable {
                            lastAppliedStrength = progress
                            AudioEffectManager.setVirtualizerStrength(progress, requireContext(), saveToPrefs = false)
                        }
                        pendingRunnable = runnable
                        throttleHandler.postDelayed(runnable, 30)
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                seekBar?.parent?.requestDisallowInterceptTouchEvent(true)
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.parent?.requestDisallowInterceptTouchEvent(false)
                pendingRunnable?.let { throttleHandler.removeCallbacks(it) }
                val finalProgress = seekBar?.progress ?: return
                AudioEffectManager.setVirtualizerStrength(finalProgress, requireContext(), saveToPrefs = true)
            }
        })
    }

    private fun setupSwitch(view: View) {
        val switch = view.findViewById<MaterialSwitch>(R.id.switchEq)
        val tvEqStatus = view.findViewById<TextView>(R.id.tvEqStatus)
        val eq = AudioEffectManager.getEqualizer()
        
        val isEnabled = eq?.enabled == true
        switch.isChecked = isEnabled
        tvEqStatus.text = if (isEnabled) "Effects Active" else "Effects Disabled (Bypassed)"
        
        switch.setOnCheckedChangeListener { _, isChecked ->
            AudioEffectManager.setEqEnabled(isChecked, requireContext())
            tvEqStatus.text = if (isChecked) "Effects Active" else "Effects Disabled (Bypassed)"
            val status = if (isChecked) "ON" else "OFF"
            Toast.makeText(context, "Equalizer $status", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupExtremeBass(view: View) {
        val switchExtreme = view.findViewById<MaterialSwitch>(R.id.switchExtremeBass)
        val isExtreme = AudioEffectManager.isExtremeBassEnabled(requireContext())
        switchExtreme.isChecked = isExtreme

        switchExtreme.setOnCheckedChangeListener { _, isChecked ->
            AudioEffectManager.setExtremeBassEnabled(isChecked, requireContext())
            if (isChecked) {
                Toast.makeText(context, "Extreme Bass Mode Activated", Toast.LENGTH_SHORT).show()
            }
            view.post {
                setupEqualizer(view)
                setupBassBoost(view)
            }
        }
    }
    
    private fun setupPresets(view: View) {
        val container = view.findViewById<LinearLayout>(R.id.presetChipsContainer) ?: return
        container.removeAllViews()
        presetPillViews.clear()

        val rawPreset = AudioEffectManager.getSavedPreset(requireContext())
        val savedPreset = if (rawPreset.equals("Normal", ignoreCase = true)) "Flat" else rawPreset
        val context = requireContext()

        customPresets.keys.forEach { presetName ->
            val pill = TextView(context).apply {
                text = presetName
                textSize = 13f
                setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = dpToPx(8)
                }
                layoutParams = params
                isClickable = true
                isFocusable = true
                
                setOnClickListener {
                    selectPresetPill(presetName)
                    applyPreset(presetName)
                }
            }
            container.addView(pill)
            presetPillViews[presetName] = pill
        }

        selectPresetPill(savedPreset)
    }

    private fun selectPresetPill(selectedPreset: String) {
        presetPillViews.forEach { (name, pill) ->
            if (name.equals(selectedPreset, ignoreCase = true)) {
                pill.setBackgroundResource(R.drawable.bg_preset_pill_active)
                pill.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            } else {
                pill.setBackgroundResource(R.drawable.bg_preset_pill_inactive)
                pill.setTextColor(ContextCompat.getColor(requireContext(), R.color.textSecondary))
            }
        }
    }
    
    private fun applyPreset(presetName: String) {
        val targetPreset = if (presetName.equals("Normal", ignoreCase = true)) "Flat" else presetName
        val values = customPresets[targetPreset] ?: return
        val eq = AudioEffectManager.getEqualizer()
        val minLevel = eq?.bandLevelRange?.get(0) ?: -1500
        val maxLevel = eq?.bandLevelRange?.get(1) ?: 1500
        val range = maxLevel - minLevel
        
        for (i in 0 until minOf(values.size, seekBars.size)) {
            seekBars[i].progress = values[i]
            AudioEffectManager.setBandLevel(i.toShort(), values[i], requireContext())
            val level = (minLevel + (values[i] * range / 100)).toShort()
            updateGainLabel(i, level)
        }
        
        if (AudioEffectManager.isExtremeBassEnabled(requireContext())) {
            AudioEffectManager.applyExtremeBass()
            view?.post {
                setupEqualizer(view ?: return@post)
                setupBassBoost(view ?: return@post)
            }
        }

        // Custom branded preset spatial profiles
        when (presetName) {
            "Cinema 3D" -> {
                AudioEffectManager.setVirtualizerStrength(600, requireContext())
                view?.findViewById<SeekBar>(R.id.seekVirtualizer)?.progress = 600
                view?.findViewById<TextView>(R.id.tvVirtualizerLevel)?.text = "60%"
            }
            "MusicDeck Signature" -> {
                AudioEffectManager.setVirtualizerStrength(200, requireContext())
                view?.findViewById<SeekBar>(R.id.seekVirtualizer)?.progress = 200
                view?.findViewById<TextView>(R.id.tvVirtualizerLevel)?.text = "20%"
            }
            "Live Stage" -> {
                AudioEffectManager.setVirtualizerStrength(450, requireContext())
                view?.findViewById<SeekBar>(R.id.seekVirtualizer)?.progress = 450
                view?.findViewById<TextView>(R.id.tvVirtualizerLevel)?.text = "45%"
            }
        }
        
        AudioEffectManager.savePreset(presetName, requireContext())
    }

    private fun setupResetButton(view: View) {
        view.findViewById<ImageView>(R.id.btnResetEq)?.setOnClickListener {
            selectPresetPill("Flat")
            applyPreset("Flat")
            
            // Reset bass boost
            val seekBassBoost = view.findViewById<SeekBar>(R.id.seekBassBoost)
            val tvBassBoostLevel = view.findViewById<TextView>(R.id.tvBassBoostLevel)
            seekBassBoost?.progress = 0
            tvBassBoostLevel?.text = "0%"
            AudioEffectManager.setBassBoostStrength(0, requireContext())

            // Reset volume boost
            val seekVolumeBoost = view.findViewById<SeekBar>(R.id.seekVolumeBoost)
            val tvVolumeBoostLevel = view.findViewById<TextView>(R.id.tvVolumeBoostLevel)
            seekVolumeBoost?.progress = 0
            tvVolumeBoostLevel?.text = "+0.0 dB"
            AudioEffectManager.setVolumeBoostGain(0, requireContext())

            // Reset virtualizer
            val seekVirtualizer = view.findViewById<SeekBar>(R.id.seekVirtualizer)
            val tvVirtualizerLevel = view.findViewById<TextView>(R.id.tvVirtualizerLevel)
            seekVirtualizer?.progress = 0
            tvVirtualizerLevel?.text = "0%"
            AudioEffectManager.setVirtualizerStrength(0, requireContext())
            
            // Turn off extreme bass
            val switchExtreme = view.findViewById<MaterialSwitch>(R.id.switchExtremeBass)
            if (switchExtreme?.isChecked == true) {
                switchExtreme.isChecked = false
                AudioEffectManager.setExtremeBassEnabled(false, requireContext())
            }
            
            Toast.makeText(context, "Audio effects reset to Flat", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        seekBars.clear()
        freqLabels.clear()
        gainLabels.clear()
        presetPillViews.clear()
    }
    
    companion object {
        fun newInstance(audioSessionId: Int): EqualizerBottomSheet {
            return EqualizerBottomSheet().apply {
                arguments = Bundle().apply {
                    putInt("audioSessionId", audioSessionId)
                }
            }
        }
    }
}
