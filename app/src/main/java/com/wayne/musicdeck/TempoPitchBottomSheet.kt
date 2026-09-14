/*
 * Copyright (c) 2026 Wayne Chibeu. All rights reserved.
 * MusicDeck Audio DSP Engine - Studio Independent Tempo & Pitch Shifter.
 */

package com.wayne.musicdeck

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.materialswitch.MaterialSwitch
import com.wayne.musicdeck.utils.HapticManager
import com.wayne.musicdeck.utils.SettingsManager
import org.koin.android.ext.android.inject
import java.util.Locale

class TempoPitchBottomSheet : BottomSheetDialogFragment() {

    private val settingsManager: SettingsManager by inject()

    private var currentTempo: Float = 1.0f
    private var currentPitchSemitones: Float = 0.0f
    private var isReverbEnabled: Boolean = false
    private var reverbRoomSize: Float = 0.75f
    private var reverbDamping: Float = 0.40f
    private var reverbWetLevel: Float = 0.35f

    private val presetPills = mutableMapOf<String, TextView>()

    data class TempoPitchPreset(
        val name: String,
        val tempo: Float,
        val pitchSemitones: Float,
        val reverbEnabled: Boolean,
        val roomSize: Float = 0.75f,
        val damping: Float = 0.40f,
        val wetLevel: Float = 0.35f
    )

    private val presets = listOf(
        TempoPitchPreset("Normal", 1.00f, 0.0f, false),
        TempoPitchPreset("Slowed + Reverb", 0.85f, -2.0f, true, 0.80f, 0.40f, 0.38f),
        TempoPitchPreset("Nightcore", 1.25f, 3.0f, false),
        TempoPitchPreset("Sped Up", 1.18f, 2.0f, false),
        TempoPitchPreset("Lofi Chill", 0.90f, -1.0f, true, 0.65f, 0.55f, 0.28f)
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_tempo_pitch, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load persisted states
        currentTempo = settingsManager.playbackTempo
        currentPitchSemitones = settingsManager.playbackPitchSemitones
        isReverbEnabled = settingsManager.isReverbEnabled
        reverbRoomSize = settingsManager.reverbRoomSize
        reverbDamping = settingsManager.reverbDamping
        reverbWetLevel = settingsManager.reverbWetLevel

        setupHeader(view)
        setupPresets(view)
        setupTempoControls(view)
        setupPitchControls(view)
        setupReverbControls(view)
    }

    private fun setupHeader(view: View) {
        view.findViewById<ImageView>(R.id.btnResetTempoPitch).setOnClickListener {
            HapticManager.performHeavyClick(requireContext())
            applyPreset("Normal", view)
        }
    }

    private fun setupPresets(view: View) {
        val container = view.findViewById<LinearLayout>(R.id.presetChipsContainer) ?: return
        container.removeAllViews()
        presetPills.clear()

        val savedPreset = settingsManager.tempoPitchPreset
        val context = requireContext()

        presets.forEach { preset ->
            val pill = TextView(context).apply {
                text = preset.name
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
                    HapticManager.performSpringClick(context)
                    applyPreset(preset.name, view)
                }
            }
            container.addView(pill)
            presetPills[preset.name] = pill
        }

        updatePresetPillSelection(savedPreset)
    }

    private fun applyPreset(presetName: String, view: View) {
        val preset = presets.firstOrNull { it.name.equals(presetName, ignoreCase = true) } ?: return

        currentTempo = preset.tempo
        currentPitchSemitones = preset.pitchSemitones
        isReverbEnabled = preset.reverbEnabled
        reverbRoomSize = preset.roomSize
        reverbDamping = preset.damping
        reverbWetLevel = preset.wetLevel

        settingsManager.tempoPitchPreset = preset.name

        // Update UI controls
        val seekTempo = view.findViewById<SeekBar>(R.id.seekTempo)
        val tvTempoValue = view.findViewById<TextView>(R.id.tvTempoValue)
        val tempoProgress = ((currentTempo * 100f).toInt() - 50).coerceIn(0, 150)
        seekTempo.progress = tempoProgress
        tvTempoValue.text = String.format(Locale.US, "%.2fx", currentTempo)

        val seekPitch = view.findViewById<SeekBar>(R.id.seekPitch)
        val tvPitchValue = view.findViewById<TextView>(R.id.tvPitchValue)
        val pitchProgress = ((currentPitchSemitones / 0.5f) + 12f).toInt().coerceIn(0, 24)
        seekPitch.progress = pitchProgress
        tvPitchValue.text = formatPitch(currentPitchSemitones)

        val switchReverb = view.findViewById<MaterialSwitch>(R.id.switchReverb)
        val layoutReverbControls = view.findViewById<View>(R.id.layoutReverbControls)
        switchReverb.isChecked = isReverbEnabled
        layoutReverbControls.visibility = if (isReverbEnabled) View.VISIBLE else View.GONE

        val seekReverbWet = view.findViewById<SeekBar>(R.id.seekReverbWet)
        val tvReverbWet = view.findViewById<TextView>(R.id.tvReverbWetLevel)
        val wetProg = (reverbWetLevel * 100f).toInt().coerceIn(0, 100)
        seekReverbWet.progress = wetProg
        tvReverbWet.text = "${wetProg}%"

        val seekReverbRoom = view.findViewById<SeekBar>(R.id.seekReverbRoom)
        val tvReverbRoom = view.findViewById<TextView>(R.id.tvReverbRoomSize)
        val roomProg = (reverbRoomSize * 100f).toInt().coerceIn(0, 100)
        seekReverbRoom.progress = roomProg
        tvReverbRoom.text = "${roomProg}%"

        val seekReverbDamp = view.findViewById<SeekBar>(R.id.seekReverbDamping)
        val tvReverbDamp = view.findViewById<TextView>(R.id.tvReverbDamping)
        val dampProg = (reverbDamping * 100f).toInt().coerceIn(0, 100)
        seekReverbDamp.progress = dampProg
        tvReverbDamp.text = "${dampProg}%"

        updatePresetPillSelection(preset.name)
        dispatchTempoPitchUpdate()
        dispatchReverbUpdate()
    }

    private fun updatePresetPillSelection(selectedName: String) {
        presetPills.forEach { (name, pill) ->
            if (name.equals(selectedName, ignoreCase = true)) {
                pill.setBackgroundResource(R.drawable.bg_preset_pill_active)
                pill.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            } else {
                pill.setBackgroundResource(R.drawable.bg_preset_pill_inactive)
                pill.setTextColor(ContextCompat.getColor(requireContext(), R.color.textSecondary))
            }
        }
    }

    private fun setupTempoControls(view: View) {
        val seekTempo = view.findViewById<SeekBar>(R.id.seekTempo)
        val tvTempoValue = view.findViewById<TextView>(R.id.tvTempoValue)

        val tempoProgress = ((currentTempo * 100f).toInt() - 50).coerceIn(0, 150)
        seekTempo.progress = tempoProgress
        tvTempoValue.text = String.format(Locale.US, "%.2fx", currentTempo)

        var lastHapticProgress = -1
        seekTempo.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val tempo = (progress + 50) / 100f
                currentTempo = tempo
                tvTempoValue.text = String.format(Locale.US, "%.2fx", tempo)

                // Light haptic snap when crossing 1.00x center detent
                if (progress == 50 && lastHapticProgress != 50) {
                    HapticManager.performSpringClick(requireContext())
                }
                lastHapticProgress = progress

                markCustomPreset()
                dispatchTempoPitchUpdate()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Quick snap buttons
        fun snapTempo(target: Float) {
            HapticManager.performSpringClick(requireContext())
            currentTempo = target
            val prog = ((target * 100f).toInt() - 50).coerceIn(0, 150)
            seekTempo.progress = prog
            tvTempoValue.text = String.format(Locale.US, "%.2fx", target)
            markCustomPreset()
            dispatchTempoPitchUpdate()
        }

        view.findViewById<View>(R.id.btnSnapTempo075).setOnClickListener { snapTempo(0.75f) }
        view.findViewById<View>(R.id.btnSnapTempo085).setOnClickListener { snapTempo(0.85f) }
        view.findViewById<View>(R.id.btnSnapTempo100).setOnClickListener { snapTempo(1.00f) }
        view.findViewById<View>(R.id.btnSnapTempo125).setOnClickListener { snapTempo(1.25f) }
        view.findViewById<View>(R.id.btnSnapTempo150).setOnClickListener { snapTempo(1.50f) }
    }

    private fun setupPitchControls(view: View) {
        val seekPitch = view.findViewById<SeekBar>(R.id.seekPitch)
        val tvPitchValue = view.findViewById<TextView>(R.id.tvPitchValue)

        val pitchProgress = ((currentPitchSemitones / 0.5f) + 12f).toInt().coerceIn(0, 24)
        seekPitch.progress = pitchProgress
        tvPitchValue.text = formatPitch(currentPitchSemitones)

        var lastHapticProgress = -1
        seekPitch.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val semitones = (progress - 12) * 0.5f
                currentPitchSemitones = semitones
                tvPitchValue.text = formatPitch(semitones)

                // Light haptic snap when crossing 0 (Natural) detent
                if (progress == 12 && lastHapticProgress != 12) {
                    HapticManager.performSpringClick(requireContext())
                }
                lastHapticProgress = progress

                markCustomPreset()
                dispatchTempoPitchUpdate()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Quick snap buttons
        fun snapPitch(target: Float) {
            HapticManager.performSpringClick(requireContext())
            currentPitchSemitones = target
            val prog = ((target / 0.5f) + 12f).toInt().coerceIn(0, 24)
            seekPitch.progress = prog
            tvPitchValue.text = formatPitch(target)
            markCustomPreset()
            dispatchTempoPitchUpdate()
        }

        view.findViewById<View>(R.id.btnSnapPitchMinus4).setOnClickListener { snapPitch(-4.0f) }
        view.findViewById<View>(R.id.btnSnapPitchMinus2).setOnClickListener { snapPitch(-2.0f) }
        view.findViewById<View>(R.id.btnSnapPitch0).setOnClickListener { snapPitch(0.0f) }
        view.findViewById<View>(R.id.btnSnapPitchPlus2).setOnClickListener { snapPitch(2.0f) }
        view.findViewById<View>(R.id.btnSnapPitchPlus3).setOnClickListener { snapPitch(3.0f) }
    }

    private fun setupReverbControls(view: View) {
        val switchReverb = view.findViewById<MaterialSwitch>(R.id.switchReverb)
        val layoutReverbControls = view.findViewById<View>(R.id.layoutReverbControls)

        switchReverb.isChecked = isReverbEnabled
        layoutReverbControls.visibility = if (isReverbEnabled) View.VISIBLE else View.GONE

        switchReverb.setOnCheckedChangeListener { _, isChecked ->
            isReverbEnabled = isChecked
            layoutReverbControls.visibility = if (isChecked) View.VISIBLE else View.GONE
            HapticManager.performSpringClick(requireContext())
            markCustomPreset()
            dispatchReverbUpdate()
        }

        val seekReverbWet = view.findViewById<SeekBar>(R.id.seekReverbWet)
        val tvReverbWet = view.findViewById<TextView>(R.id.tvReverbWetLevel)
        val wetProg = (reverbWetLevel * 100f).toInt().coerceIn(0, 100)
        seekReverbWet.progress = wetProg
        tvReverbWet.text = "${wetProg}%"

        seekReverbWet.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                reverbWetLevel = progress / 100f
                tvReverbWet.text = "${progress}%"
                markCustomPreset()
                dispatchReverbUpdate()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val seekReverbRoom = view.findViewById<SeekBar>(R.id.seekReverbRoom)
        val tvReverbRoom = view.findViewById<TextView>(R.id.tvReverbRoomSize)
        val roomProg = (reverbRoomSize * 100f).toInt().coerceIn(0, 100)
        seekReverbRoom.progress = roomProg
        tvReverbRoom.text = "${roomProg}%"

        seekReverbRoom.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                reverbRoomSize = progress / 100f
                tvReverbRoom.text = "${progress}%"
                markCustomPreset()
                dispatchReverbUpdate()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val seekReverbDamp = view.findViewById<SeekBar>(R.id.seekReverbDamping)
        val tvReverbDamp = view.findViewById<TextView>(R.id.tvReverbDamping)
        val dampProg = (reverbDamping * 100f).toInt().coerceIn(0, 100)
        seekReverbDamp.progress = dampProg
        tvReverbDamp.text = "${dampProg}%"

        seekReverbDamp.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                reverbDamping = progress / 100f
                tvReverbDamp.text = "${progress}%"
                markCustomPreset()
                dispatchReverbUpdate()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun markCustomPreset() {
        settingsManager.tempoPitchPreset = "Custom"
        updatePresetPillSelection("Custom")
    }

    private fun dispatchTempoPitchUpdate() {
        settingsManager.playbackTempo = currentTempo
        settingsManager.playbackPitchSemitones = currentPitchSemitones

        // Instant direct application to running MusicService instance
        MusicService.instance?.applyTempoAndPitch(currentTempo, currentPitchSemitones)

        // Asynchronous Intent fallback
        try {
            val intent = Intent(requireContext(), MusicService::class.java).apply {
                action = MusicService.ACTION_SET_TEMPO_PITCH
                putExtra(MusicService.EXTRA_TEMPO, currentTempo)
                putExtra(MusicService.EXTRA_PITCH_SEMITONES, currentPitchSemitones)
            }
            requireContext().startService(intent)
        } catch (_: Exception) {}
    }

    private fun dispatchReverbUpdate() {
        settingsManager.isReverbEnabled = isReverbEnabled
        settingsManager.reverbRoomSize = reverbRoomSize
        settingsManager.reverbDamping = reverbDamping
        settingsManager.reverbWetLevel = reverbWetLevel

        AudioEffectManager.setReverbParams(
            isReverbEnabled,
            reverbRoomSize,
            reverbDamping,
            reverbWetLevel,
            requireContext()
        )
    }

    private fun formatPitch(semitones: Float): String {
        return when {
            kotlin.math.abs(semitones) < 0.05f -> "Natural"
            semitones > 0 -> "+${String.format(Locale.US, "%.1f", semitones)} st"
            else -> "${String.format(Locale.US, "%.1f", semitones)} st"
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
