package com.wayne.musicdeck

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.materialswitch.MaterialSwitch

class EqualizerBottomSheet : BottomSheetDialogFragment() {
    
    private val seekBars = mutableListOf<SeekBar>()
    private val freqLabels = mutableListOf<TextView>()
    
    // Presets: name -> array of band values (normalized 0-100)
    private val customPresets = mapOf(
        "Flat" to intArrayOf(50, 50, 50, 50, 50),
        "Bass Boost" to intArrayOf(80, 70, 50, 50, 50),
        "Treble Boost" to intArrayOf(50, 50, 50, 70, 80),
        "Rock" to intArrayOf(70, 60, 50, 60, 70),
        "Pop" to intArrayOf(55, 65, 70, 60, 55),
        "Jazz" to intArrayOf(60, 55, 50, 55, 65),
        "Classical" to intArrayOf(65, 60, 50, 55, 60),
        "Vocal Boost" to intArrayOf(45, 50, 70, 60, 45)
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_equalizer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val eq = AudioEffectManager.getEqualizer()
        
        if (eq == null) {
            Toast.makeText(context, "Equalizer not initialized", Toast.LENGTH_SHORT).show()
            dismiss()
            return
        }
        
        try {
            setupEqualizer(view)
            setupBassBoost(view)
            setupPresets(view)
            setupSwitch(view)
            
            // UI state is restored in setup methods by reading from AudioEffectManager/Prefs
            
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to initialize EQ UI: ${e.message}", Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }
    
    private fun setupEqualizer(view: View) {
        val eq = AudioEffectManager.getEqualizer() ?: return
        
        val bandCount = eq.numberOfBands.toInt()
        val minLevel = eq.bandLevelRange[0]
        val maxLevel = eq.bandLevelRange[1]
        val range = maxLevel - minLevel
        
        // Find the seek bars and labels
        val seekBar1 = view.findViewById<SeekBar>(R.id.seekBand1)
        val seekBar2 = view.findViewById<SeekBar>(R.id.seekBand2)
        val seekBar3 = view.findViewById<SeekBar>(R.id.seekBand3)
        val seekBar4 = view.findViewById<SeekBar>(R.id.seekBand4)
        val seekBar5 = view.findViewById<SeekBar>(R.id.seekBand5)
        
        val label1 = view.findViewById<TextView>(R.id.labelBand1)
        val label2 = view.findViewById<TextView>(R.id.labelBand2)
        val label3 = view.findViewById<TextView>(R.id.labelBand3)
        val label4 = view.findViewById<TextView>(R.id.labelBand4)
        val label5 = view.findViewById<TextView>(R.id.labelBand5)
        
        seekBars.addAll(listOf(seekBar1, seekBar2, seekBar3, seekBar4, seekBar5))
        freqLabels.addAll(listOf(label1, label2, label3, label4, label5))
        
        // Configure each band
        for (i in 0 until minOf(bandCount, 5)) {
            val freq = eq.getCenterFreq(i.toShort()) / 1000 // Convert to Hz
            val freqText = if (freq >= 1000) "${freq / 1000}kHz" else "${freq}Hz"
            freqLabels[i].text = freqText
            
            seekBars[i].max = 100
            
            // Get current level or saved level
            // Since AudioEffectManager restores on init, eq.getBandLevel should be correct
            val currentLevel = eq.getBandLevel(i.toShort())
            val progress = ((currentLevel - minLevel) * 100 / range)
            seekBars[i].progress = progress
            
            val bandIndex = i
            seekBars[i].setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        AudioEffectManager.setBandLevel(bandIndex.toShort(), progress, requireContext())
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
    }
    
    private fun setupBassBoost(view: View) {
        val bb = AudioEffectManager.getBassBoost() ?: return
        if (!bb.strengthSupported) {
            view.findViewById<View>(R.id.seekBassBoost).isEnabled = false
            return
        }
        
        val seekBassBoost = view.findViewById<SeekBar>(R.id.seekBassBoost)
        val tvBassBoostLevel = view.findViewById<TextView>(R.id.tvBassBoostLevel)
        
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

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupSwitch(view: View) {
        val switch = view.findViewById<MaterialSwitch>(R.id.switchEq)
        val eq = AudioEffectManager.getEqualizer()
        
        switch.isChecked = eq?.enabled == true
        
        switch.setOnCheckedChangeListener { _, isChecked ->
            AudioEffectManager.setEqEnabled(isChecked, requireContext())
            val status = if (isChecked) "ON" else "OFF"
            Toast.makeText(context, "Effects $status", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupPresets(view: View) {
        val spinner = view.findViewById<Spinner>(R.id.spinnerPresets)
        val presetNames = customPresets.keys.toList()
        
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, presetNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        
        // Restore saved preset selection
        val savedPreset = AudioEffectManager.getSavedPreset(requireContext())
        val savedIndex = presetNames.indexOf(savedPreset)
        if (savedIndex >= 0) {
            spinner.setSelection(savedIndex, false)
        }
        
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val presetName = presetNames[position]
                // Only apply if user triggers it (spinner initial layout might fire this?)
                // Actually, simple_spinner usually fires onItemSelected on setup.
                // We check if it matches saved.
                val currentSaved = AudioEffectManager.getSavedPreset(requireContext())
                if (presetName != currentSaved) {
                     applyPreset(presetName)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun applyPreset(presetName: String) {
        val values = customPresets[presetName] ?: return
        
        // Update UI logic
        for (i in 0 until minOf(values.size, seekBars.size)) {
            seekBars[i].progress = values[i]
            // Update Manager
            AudioEffectManager.setBandLevel(i.toShort(), values[i], requireContext())
        }
        AudioEffectManager.savePreset(presetName, requireContext())
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        seekBars.clear()
        freqLabels.clear()
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
