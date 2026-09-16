package com.wayne.musicdeck

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
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
import com.wayne.musicdeck.data.EQPreset
import com.wayne.musicdeck.utils.EQPresetManager
import com.wayne.musicdeck.views.EqualizerGraphView

class EqualizerBottomSheet : BottomSheetDialogFragment() {
    
    private var eqGraphView: EqualizerGraphView? = null
    private val seekBars = mutableListOf<SeekBar>()
    private val freqLabels = mutableListOf<TextView>()
    private val gainLabels = mutableListOf<TextView>()
    private val presetPillViews = mutableMapOf<String, TextView>()
    
    private val importPresetLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { handleImportedUri(it) }
    }

    // Built-in presets: name -> array of band values (normalized 0-100)
    private val builtInPresets = linkedMapOf(
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

    private val userPresets = mutableMapOf<String, EQPreset>()

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
        
        if (eq == null && !AudioEffectManager.isInitialized()) {
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
            setupCrossfeed(view)
            setupKaraoke(view)
            setupExtremeBass(view)
            setupPresets(view)
            setupImportExportButtons(view)
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
        val eq = AudioEffectManager.getEqualizer()
        val defaultCenterFreqs = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")
        val prefs = requireContext().getSharedPreferences("eq_prefs", Context.MODE_PRIVATE)
        
        val bandCount = eq?.numberOfBands?.toInt() ?: 5
        val minLevel: Short = eq?.bandLevelRange?.get(0) ?: -1500
        val maxLevel: Short = eq?.bandLevelRange?.get(1) ?: 1500
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

        // Setup Interactive Parametric EQ Graph
        val eqGraph = view.findViewById<EqualizerGraphView>(R.id.eqGraphView)
        eqGraphView = eqGraph
        if (eqGraph != null) {
            val initialGains = FloatArray(5) { b ->
                val p = prefs.getInt("eq_band_$b", 50)
                (p - 50) * 0.3f
            }
            eqGraph.setAllGains(initialGains)
            eqGraph.setEqEnabled(AudioEffectManager.isEqEnabled(requireContext()))

            eqGraph.onBandGainChanged = { bandIndex, _, progress ->
                if (bandIndex in seekBars.indices) {
                    seekBars[bandIndex].progress = progress
                    AudioEffectManager.setBandLevel(bandIndex.toShort(), progress, requireContext())
                    val level = (minLevel + (progress * range / 100)).toShort()
                    updateGainLabel(bandIndex, level)
                }
            }
        }

        for (i in 0 until minOf(bandCount, 5)) {
            val formattedFreq = if (eq != null && i < eq.numberOfBands) {
                val centerFreq = eq.getCenterFreq(i.toShort()) / 1000
                if (centerFreq >= 1000) {
                    String.format("%.1f kHz", centerFreq / 1000f)
                } else {
                    "$centerFreq Hz"
                }
            } else {
                defaultCenterFreqs[i]
            }
            freqLabels[i].text = formattedFreq
            
            seekBars[i].max = 100
            
            val progress = prefs.getInt("eq_band_$i", 50)
            seekBars[i].progress = progress
            
            // Format dynamic dB gain label
            val currentLevel = (minLevel + (progress * range / 100)).toShort()
            updateGainLabel(i, currentLevel)

            // Prevent touch drag conflict
            attachTouchDisallow(seekBars[i])
            
            val bandIndex = i
            seekBars[i].setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val gainDb = (progress - 50) * 0.3f
                        eqGraphView?.setBandGain(bandIndex, gainDb)
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
        val seekBassBoost = view.findViewById<SeekBar>(R.id.seekBassBoost) ?: return
        val tvBassBoostLevel = view.findViewById<TextView>(R.id.tvBassBoostLevel) ?: return
        
        attachTouchDisallow(seekBassBoost)

        val prefs = requireContext().getSharedPreferences("eq_prefs", Context.MODE_PRIVATE)
        val progress = prefs.getInt("bass_boost_strength", 0)
        
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

    private fun setupCrossfeed(view: View) {
        val seekCrossfeed = view.findViewById<SeekBar>(R.id.seekCrossfeed) ?: return
        val tvCrossfeedLevel = view.findViewById<TextView>(R.id.tvCrossfeedLevel) ?: return
        val chipSubtle = view.findViewById<TextView>(R.id.chipCrossfeedSubtle) ?: return
        val chipStandard = view.findViewById<TextView>(R.id.chipCrossfeedStandard) ?: return
        val chipStudio = view.findViewById<TextView>(R.id.chipCrossfeedStudio) ?: return

        attachTouchDisallow(seekCrossfeed)

        val context = requireContext()
        val currentStrength = AudioEffectManager.getSavedCrossfeedStrength(context)
        var currentMode = AudioEffectManager.getSavedCrossfeedMode(context)

        seekCrossfeed.progress = currentStrength
        updateCrossfeedLevelText(tvCrossfeedLevel, currentStrength, currentMode)
        updateCrossfeedChips(currentMode, chipSubtle, chipStandard, chipStudio)

        chipSubtle.setOnClickListener {
            currentMode = 1
            AudioEffectManager.setCrossfeedMode(1, requireContext())
            if (seekCrossfeed.progress == 0) {
                seekCrossfeed.progress = 350
                AudioEffectManager.setCrossfeedStrength(350, requireContext())
            }
            updateCrossfeedChips(1, chipSubtle, chipStandard, chipStudio)
            updateCrossfeedLevelText(tvCrossfeedLevel, seekCrossfeed.progress, 1)
        }

        chipStandard.setOnClickListener {
            currentMode = 2
            AudioEffectManager.setCrossfeedMode(2, requireContext())
            if (seekCrossfeed.progress == 0) {
                seekCrossfeed.progress = 650
                AudioEffectManager.setCrossfeedStrength(650, requireContext())
            }
            updateCrossfeedChips(2, chipSubtle, chipStandard, chipStudio)
            updateCrossfeedLevelText(tvCrossfeedLevel, seekCrossfeed.progress, 2)
        }

        chipStudio.setOnClickListener {
            currentMode = 3
            AudioEffectManager.setCrossfeedMode(3, requireContext())
            if (seekCrossfeed.progress == 0) {
                seekCrossfeed.progress = 1000
                AudioEffectManager.setCrossfeedStrength(1000, requireContext())
            }
            updateCrossfeedChips(3, chipSubtle, chipStandard, chipStudio)
            updateCrossfeedLevelText(tvCrossfeedLevel, seekCrossfeed.progress, 3)
        }

        var lastAppliedStrength = currentStrength
        val throttleHandler = android.os.Handler(android.os.Looper.getMainLooper())
        var pendingRunnable: Runnable? = null

        seekCrossfeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    updateCrossfeedLevelText(tvCrossfeedLevel, progress, currentMode)

                    if (Math.abs(progress - lastAppliedStrength) >= 20) {
                        pendingRunnable?.let { throttleHandler.removeCallbacks(it) }
                        lastAppliedStrength = progress
                        AudioEffectManager.setCrossfeedStrength(progress, requireContext(), saveToPrefs = false)
                    } else {
                        pendingRunnable?.let { throttleHandler.removeCallbacks(it) }
                        val runnable = Runnable {
                            lastAppliedStrength = progress
                            AudioEffectManager.setCrossfeedStrength(progress, requireContext(), saveToPrefs = false)
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
                AudioEffectManager.setCrossfeedStrength(finalProgress, requireContext(), saveToPrefs = true)
            }
        })
    }

    private fun updateCrossfeedLevelText(tv: TextView, progress: Int, mode: Int) {
        if (progress == 0) {
            tv.text = "Off"
        } else {
            val modeName = when (mode) {
                1 -> "Meier"
                3 -> "Chu Moy"
                else -> "Bauer"
            }
            tv.text = "$modeName ${progress / 10}%"
        }
    }

    private fun updateCrossfeedChips(activeMode: Int, chipSubtle: TextView, chipStandard: TextView, chipStudio: TextView) {
        val activeRes = R.drawable.bg_preset_pill_active
        val inactiveRes = R.drawable.bg_preset_pill_inactive
        val activeColor = ContextCompat.getColor(requireContext(), R.color.white)
        val inactiveColor = ContextCompat.getColor(requireContext(), R.color.textSecondary)

        chipSubtle.setBackgroundResource(if (activeMode == 1) activeRes else inactiveRes)
        chipSubtle.setTextColor(if (activeMode == 1) activeColor else inactiveColor)

        chipStandard.setBackgroundResource(if (activeMode == 2) activeRes else inactiveRes)
        chipStandard.setTextColor(if (activeMode == 2) activeColor else inactiveColor)

        chipStudio.setBackgroundResource(if (activeMode == 3) activeRes else inactiveRes)
        chipStudio.setTextColor(if (activeMode == 3) activeColor else inactiveColor)
    }

    private fun setupSwitch(view: View) {
        val switch = view.findViewById<MaterialSwitch>(R.id.switchEq) ?: return
        val tvEqStatus = view.findViewById<TextView>(R.id.tvEqStatus) ?: return
        val isEnabled = AudioEffectManager.isEqEnabled(requireContext())
        switch.isChecked = isEnabled
        tvEqStatus.text = if (isEnabled) "DeckAcoustix DSP • Active" else "DeckAcoustix DSP • Bypassed"
        
        switch.setOnCheckedChangeListener { _, isChecked ->
            AudioEffectManager.setEqEnabled(isChecked, requireContext())
            tvEqStatus.text = if (isChecked) "DeckAcoustix DSP • Active" else "DeckAcoustix DSP • Bypassed"
            eqGraphView?.setEqEnabled(isChecked)
            val status = if (isChecked) "ON" else "OFF"
            Toast.makeText(context, "Equalizer $status", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupKaraoke(view: View) {
        val switchKaraoke = view.findViewById<MaterialSwitch>(R.id.switchKaraoke) ?: return
        val isKaraoke = AudioEffectManager.isKaraokeEnabled(requireContext())
        switchKaraoke.isChecked = isKaraoke

        switchKaraoke.setOnCheckedChangeListener { _, isChecked ->
            AudioEffectManager.setKaraokeEnabled(isChecked, requireContext())
            val status = if (isChecked) "ON" else "OFF"
            Toast.makeText(context, "Karaoke Mode $status", Toast.LENGTH_SHORT).show()
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
        userPresets.clear()

        val context = requireContext()
        val rawPreset = AudioEffectManager.getSavedPreset(context)
        val savedPreset = if (rawPreset.equals("Normal", ignoreCase = true)) "Flat" else rawPreset

        // 1. User custom presets (.deck imports / saved profiles)
        val savedCustom = EQPresetManager.getSavedCustomPresets(context)
        savedCustom.forEach { preset ->
            userPresets[preset.name] = preset
            val pill = createPresetPill(context, preset.name, isCustom = true)
            container.addView(pill)
            presetPillViews[preset.name] = pill
        }

        // 2. Built-in presets
        builtInPresets.keys.forEach { presetName ->
            val pill = createPresetPill(context, presetName, isCustom = false)
            container.addView(pill)
            presetPillViews[presetName] = pill
        }

        selectPresetPill(savedPreset)
    }

    private fun createPresetPill(context: Context, presetName: String, isCustom: Boolean): TextView {
        return TextView(context).apply {
            text = if (isCustom) "$presetName (Custom)" else presetName
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

            if (isCustom) {
                setOnLongClickListener {
                    showCustomPresetOptionsDialog(presetName)
                    true
                }
            }
        }
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

        val customPreset = userPresets[targetPreset]
        val values = customPreset?.bands ?: builtInPresets[targetPreset] ?: return

        val eq = AudioEffectManager.getEqualizer()
        val minLevel = eq?.bandLevelRange?.get(0) ?: -1500
        val maxLevel = eq?.bandLevelRange?.get(1) ?: 1500
        val range = maxLevel - minLevel
        
        val targetGains = FloatArray(minOf(values.size, 5)) { i ->
            (values[i] - 50) * 0.3f
        }
        eqGraphView?.animateToGains(targetGains)

        for (i in 0 until minOf(values.size, seekBars.size)) {
            seekBars[i].progress = values[i]
            AudioEffectManager.setBandLevel(i.toShort(), values[i], requireContext())
            val level = (minLevel + (values[i] * range / 100)).toShort()
            updateGainLabel(i, level)
        }

        if (customPreset != null) {
            // Apply parameters from imported / saved .deck profile
            if (customPreset.bassBoost > 0) {
                AudioEffectManager.setBassBoostStrength(customPreset.bassBoost, requireContext())
                view?.findViewById<SeekBar>(R.id.seekBassBoost)?.progress = customPreset.bassBoost
                view?.findViewById<TextView>(R.id.tvBassBoostLevel)?.text = "${customPreset.bassBoost / 10}%"
            }
            if (customPreset.virtualizer > 0) {
                AudioEffectManager.setVirtualizerStrength(customPreset.virtualizer, requireContext())
                view?.findViewById<SeekBar>(R.id.seekVirtualizer)?.progress = customPreset.virtualizer
                view?.findViewById<TextView>(R.id.tvVirtualizerLevel)?.text = "${customPreset.virtualizer / 10}%"
            }
            if (customPreset.extremeBass != AudioEffectManager.isExtremeBassEnabled(requireContext())) {
                AudioEffectManager.setExtremeBassEnabled(customPreset.extremeBass, requireContext())
                view?.findViewById<MaterialSwitch>(R.id.switchExtremeBass)?.isChecked = customPreset.extremeBass
            }
            if (customPreset.crossfeed > 0) {
                AudioEffectManager.setCrossfeedMode(customPreset.crossfeedMode, requireContext())
                AudioEffectManager.setCrossfeedStrength(customPreset.crossfeed, requireContext())
                view?.findViewById<SeekBar>(R.id.seekCrossfeed)?.progress = customPreset.crossfeed
                view?.findViewById<TextView>(R.id.tvCrossfeedLevel)?.let {
                    updateCrossfeedLevelText(it, customPreset.crossfeed, customPreset.crossfeedMode)
                }
                val chipSubtle = view?.findViewById<TextView>(R.id.chipCrossfeedSubtle)
                val chipStandard = view?.findViewById<TextView>(R.id.chipCrossfeedStandard)
                val chipStudio = view?.findViewById<TextView>(R.id.chipCrossfeedStudio)
                if (chipSubtle != null && chipStandard != null && chipStudio != null) {
                    updateCrossfeedChips(customPreset.crossfeedMode, chipSubtle, chipStandard, chipStudio)
                }
            } else {
                AudioEffectManager.setCrossfeedStrength(0, requireContext())
                view?.findViewById<SeekBar>(R.id.seekCrossfeed)?.progress = 0
                view?.findViewById<TextView>(R.id.tvCrossfeedLevel)?.text = "Off"
            }
        } else {
            // Built-in preset spatial profiles
            when (presetName) {
                "Cinema 3D" -> {
                    AudioEffectManager.setVirtualizerStrength(600, requireContext())
                    view?.findViewById<SeekBar>(R.id.seekVirtualizer)?.progress = 600
                    view?.findViewById<TextView>(R.id.tvVirtualizerLevel)?.text = "60%"
                    AudioEffectManager.setCrossfeedMode(1, requireContext())
                    AudioEffectManager.setCrossfeedStrength(300, requireContext())
                    view?.findViewById<SeekBar>(R.id.seekCrossfeed)?.progress = 300
                    view?.findViewById<TextView>(R.id.tvCrossfeedLevel)?.let { updateCrossfeedLevelText(it, 300, 1) }
                }
                "MusicDeck Signature" -> {
                    AudioEffectManager.setVirtualizerStrength(200, requireContext())
                    view?.findViewById<SeekBar>(R.id.seekVirtualizer)?.progress = 200
                    view?.findViewById<TextView>(R.id.tvVirtualizerLevel)?.text = "20%"
                    AudioEffectManager.setCrossfeedMode(2, requireContext())
                    AudioEffectManager.setCrossfeedStrength(450, requireContext())
                    view?.findViewById<SeekBar>(R.id.seekCrossfeed)?.progress = 450
                    view?.findViewById<TextView>(R.id.tvCrossfeedLevel)?.let { updateCrossfeedLevelText(it, 450, 2) }
                }
                "Live Stage" -> {
                    AudioEffectManager.setVirtualizerStrength(450, requireContext())
                    view?.findViewById<SeekBar>(R.id.seekVirtualizer)?.progress = 450
                    view?.findViewById<TextView>(R.id.tvVirtualizerLevel)?.text = "45%"
                    AudioEffectManager.setCrossfeedMode(2, requireContext())
                    AudioEffectManager.setCrossfeedStrength(500, requireContext())
                    view?.findViewById<SeekBar>(R.id.seekCrossfeed)?.progress = 500
                    view?.findViewById<TextView>(R.id.tvCrossfeedLevel)?.let { updateCrossfeedLevelText(it, 500, 2) }
                }
                "Flat" -> {
                    AudioEffectManager.setCrossfeedStrength(0, requireContext())
                    view?.findViewById<SeekBar>(R.id.seekCrossfeed)?.progress = 0
                    view?.findViewById<TextView>(R.id.tvCrossfeedLevel)?.text = "Off"
                }
            }

            val chipSubtle = view?.findViewById<TextView>(R.id.chipCrossfeedSubtle)
            val chipStandard = view?.findViewById<TextView>(R.id.chipCrossfeedStandard)
            val chipStudio = view?.findViewById<TextView>(R.id.chipCrossfeedStudio)
            if (chipSubtle != null && chipStandard != null && chipStudio != null) {
                updateCrossfeedChips(AudioEffectManager.getSavedCrossfeedMode(requireContext()), chipSubtle, chipStandard, chipStudio)
            }
        }
        
        if (AudioEffectManager.isExtremeBassEnabled(requireContext())) {
            AudioEffectManager.applyExtremeBass()
            view?.post {
                setupEqualizer(view ?: return@post)
                setupBassBoost(view ?: return@post)
            }
        }

        AudioEffectManager.savePreset(presetName, requireContext())
    }

    private fun setupImportExportButtons(view: View) {
        view.findViewById<View>(R.id.btnImportDeckPreset)?.setOnClickListener {
            try {
                importPresetLauncher.launch("*/*")
            } catch (e: Exception) {
                Toast.makeText(context, "Cannot open file picker: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<View>(R.id.btnExportDeckPreset)?.setOnClickListener {
            showExportPresetDialog()
        }
    }

    private fun handleImportedUri(uri: Uri) {
        val ctx = context ?: return
        val preset = EQPresetManager.importPresetFromUri(ctx, uri)
        if (preset != null) {
            val v = view ?: return
            setupPresets(v)
            selectPresetPill(preset.name)
            applyPreset(preset.name)
            Toast.makeText(ctx, "Imported '${preset.name}' (.deck)!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(ctx, "Failed to import: Not a valid .deck file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showExportPresetDialog() {
        val ctx = context ?: return
        val input = android.widget.EditText(ctx).apply {
            hint = "e.g. Sony XM5 Punch, Vocal Boost"
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.GRAY)
            val pad = dpToPx(14)
            setPadding(pad, pad, pad, pad)
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_preset_pill_inactive)
            isSingleLine = true
        }

        val container = FrameLayout(ctx).apply {
            val padH = dpToPx(24)
            val padV = dpToPx(12)
            setPadding(padH, padV, padH, padV)
            addView(input)
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
            .setTitle("Export EQ Preset (.deck)")
            .setMessage("Save your current 5-band EQ curve and DSP settings to export and share with friends.")
            .setView(container)
            .setPositiveButton("Save & Share") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { "Custom EQ" }
                val currentBands = IntArray(seekBars.size) { i -> seekBars[i].progress }
                val bassBoost = AudioEffectManager.getBassBoostStrength(ctx)
                val virtualizer = AudioEffectManager.getSavedVirtualizerStrength(ctx)
                val crossfeed = AudioEffectManager.getSavedCrossfeedStrength(ctx)
                val crossfeedMode = AudioEffectManager.getSavedCrossfeedMode(ctx)
                val extremeBass = AudioEffectManager.isExtremeBassEnabled(ctx)
                val volumeBoost = AudioEffectManager.getSavedVolumeBoostGain(ctx)

                val preset = EQPreset(
                    name = name,
                    bands = currentBands,
                    bassBoost = bassBoost,
                    virtualizer = virtualizer,
                    crossfeed = crossfeed,
                    crossfeedMode = crossfeedMode,
                    extremeBass = extremeBass,
                    volumeBoost = volumeBoost
                )

                EQPresetManager.saveCustomPreset(ctx, preset)
                view?.let {
                    setupPresets(it)
                    selectPresetPill(name)
                    applyPreset(name)
                }

                EQPresetManager.sharePreset(ctx, preset)
            }
            .setNeutralButton("Save Only") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { "Custom EQ" }
                val currentBands = IntArray(seekBars.size) { i -> seekBars[i].progress }
                val bassBoost = AudioEffectManager.getBassBoostStrength(ctx)
                val virtualizer = AudioEffectManager.getSavedVirtualizerStrength(ctx)
                val crossfeed = AudioEffectManager.getSavedCrossfeedStrength(ctx)
                val crossfeedMode = AudioEffectManager.getSavedCrossfeedMode(ctx)
                val extremeBass = AudioEffectManager.isExtremeBassEnabled(ctx)
                val volumeBoost = AudioEffectManager.getSavedVolumeBoostGain(ctx)

                val preset = EQPreset(
                    name = name,
                    bands = currentBands,
                    bassBoost = bassBoost,
                    virtualizer = virtualizer,
                    crossfeed = crossfeed,
                    crossfeedMode = crossfeedMode,
                    extremeBass = extremeBass,
                    volumeBoost = volumeBoost
                )

                EQPresetManager.saveCustomPreset(ctx, preset)
                view?.let {
                    setupPresets(it)
                    selectPresetPill(name)
                    applyPreset(name)
                }
                Toast.makeText(ctx, "Saved preset '$name'!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCustomPresetOptionsDialog(presetName: String) {
        val ctx = context ?: return
        val preset = userPresets[presetName] ?: return
        val options = arrayOf("Share Preset (.deck)", "Delete Preset")

        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
            .setTitle(presetName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> EQPresetManager.sharePreset(ctx, preset)
                    1 -> {
                        EQPresetManager.deleteCustomPreset(ctx, presetName)
                        view?.let { setupPresets(it) }
                        selectPresetPill("Flat")
                        applyPreset("Flat")
                        Toast.makeText(ctx, "Deleted '$presetName'", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun setupResetButton(view: View) {
        view.findViewById<ImageView>(R.id.btnResetEq)?.setOnClickListener {
            selectPresetPill("Flat")
            applyPreset("Flat")
            eqGraphView?.animateToGains(FloatArray(5) { 0f })
            
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

            // Reset crossfeed
            val seekCrossfeed = view.findViewById<SeekBar>(R.id.seekCrossfeed)
            val tvCrossfeedLevel = view.findViewById<TextView>(R.id.tvCrossfeedLevel)
            seekCrossfeed?.progress = 0
            tvCrossfeedLevel?.text = "Off"
            AudioEffectManager.setCrossfeedStrength(0, requireContext())
            AudioEffectManager.setCrossfeedMode(2, requireContext())
            val chipSubtle = view.findViewById<TextView>(R.id.chipCrossfeedSubtle)
            val chipStandard = view.findViewById<TextView>(R.id.chipCrossfeedStandard)
            val chipStudio = view.findViewById<TextView>(R.id.chipCrossfeedStudio)
            if (chipSubtle != null && chipStandard != null && chipStudio != null) {
                updateCrossfeedChips(2, chipSubtle, chipStandard, chipStudio)
            }
            
            // Turn off extreme bass
            val switchExtreme = view.findViewById<MaterialSwitch>(R.id.switchExtremeBass)
            if (switchExtreme?.isChecked == true) {
                switchExtreme.isChecked = false
                AudioEffectManager.setExtremeBassEnabled(false, requireContext())
            }

            // Turn off karaoke mode
            val switchKaraoke = view.findViewById<MaterialSwitch>(R.id.switchKaraoke)
            if (switchKaraoke?.isChecked == true) {
                switchKaraoke.isChecked = false
                AudioEffectManager.setKaraokeEnabled(false, requireContext())
            }
            
            Toast.makeText(context, "Audio effects reset to Flat", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    override fun onResume() {
        super.onResume()
        eqGraphView?.setRtaActive(true)
    }

    override fun onPause() {
        super.onPause()
        eqGraphView?.setRtaActive(false)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        eqGraphView?.setRtaActive(false)
        eqGraphView = null
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
