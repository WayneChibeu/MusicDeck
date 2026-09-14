/*
 * Copyright (c) 2026 Wayne Chibeu. All rights reserved.
 * MusicDeck Audio Engine - Audiophile Bit-Perfect USB-DAC Studio Diagnostics.
 */

package com.wayne.musicdeck

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.wayne.musicdeck.audio.UsbDacManager
import com.wayne.musicdeck.audio.UsbDacState
import com.wayne.musicdeck.utils.SettingsManager
import org.koin.android.ext.android.inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class UsbDacBottomSheet : BottomSheetDialogFragment() {

    private val settingsManager: SettingsManager by inject()

    private lateinit var switchPassthrough: MaterialSwitch
    private lateinit var tvDacName: TextView
    private lateinit var tvDacStatusSubtitle: TextView
    private lateinit var tvDacBadge: TextView
    private lateinit var ivDacIcon: ImageView
    private lateinit var tvSignalSource: TextView
    private lateinit var tvSignalEngine: TextView
    private lateinit var tvSignalOutput: TextView
    private lateinit var tvSignalResampler: TextView
    private lateinit var cgDacMode: ChipGroup
    private lateinit var chipPureDirect: Chip
    private lateinit var chipDeckAcoustixDsp: Chip
    private lateinit var cgSampleRates: ChipGroup

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_usb_dac, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        switchPassthrough = view.findViewById(R.id.switchUsbDacPassthrough)
        tvDacName = view.findViewById(R.id.tvDacName)
        tvDacStatusSubtitle = view.findViewById(R.id.tvDacStatusSubtitle)
        tvDacBadge = view.findViewById(R.id.tvDacBadge)
        ivDacIcon = view.findViewById(R.id.ivDacIcon)
        tvSignalSource = view.findViewById(R.id.tvSignalSource)
        tvSignalEngine = view.findViewById(R.id.tvSignalEngine)
        tvSignalOutput = view.findViewById(R.id.tvSignalOutput)
        tvSignalResampler = view.findViewById(R.id.tvSignalResampler)
        cgDacMode = view.findViewById(R.id.cgDacMode)
        chipPureDirect = view.findViewById(R.id.chipPureDirect)
        chipDeckAcoustixDsp = view.findViewById(R.id.chipDeckAcoustixDsp)
        cgSampleRates = view.findViewById(R.id.cgSampleRates)

        setupControls()
        observeDacState()
    }

    private fun setupControls() {
        switchPassthrough.isChecked = settingsManager.isUsbDacPassthroughEnabled
        switchPassthrough.setOnCheckedChangeListener { _, isChecked ->
            UsbDacManager.setPassthroughEnabled(isChecked)
        }

        if (settingsManager.usbDacMode == SettingsManager.USB_DAC_MODE_PURE_DIRECT) {
            chipPureDirect.isChecked = true
        } else {
            chipDeckAcoustixDsp.isChecked = true
        }

        cgDacMode.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.contains(R.id.chipPureDirect)) {
                UsbDacManager.setPassthroughMode(SettingsManager.USB_DAC_MODE_PURE_DIRECT)
            } else if (checkedIds.contains(R.id.chipDeckAcoustixDsp)) {
                UsbDacManager.setPassthroughMode(SettingsManager.USB_DAC_MODE_DECKACOUSTIX_DSP)
            }
        }
    }

    private fun observeDacState() {
        viewLifecycleOwner.lifecycleScope.launch {
            UsbDacManager.dacState.collectLatest { state ->
                renderState(state)
            }
        }
    }

    private fun renderState(state: UsbDacState) {
        if (state.isConnected) {
            tvDacName.text = state.deviceName
            tvDacStatusSubtitle.text = if (state.isPassthroughEnabled) {
                if (state.isBitPerfectActive) "Android 14+ Bit-Perfect Direct HAL" else "Direct USB Audio Output"
            } else {
                "Standard Android Audio Routing (Passthrough Disabled)"
            }
            tvDacBadge.text = if (state.isPassthroughEnabled) "BIT-PERFECT" else "STANDBY"
            tvDacBadge.visibility = View.VISIBLE
            ivDacIcon.alpha = 1.0f
        } else {
            tvDacName.text = "No USB DAC Connected"
            tvDacStatusSubtitle.text = "Connect a USB-C DAC, dongle, or studio audio interface"
            tvDacBadge.text = "DISCONNECTED"
            tvDacBadge.visibility = View.GONE
            ivDacIcon.alpha = 0.5f
        }

        // Signal Path
        val rateKhz = String.format(Locale.US, "%.1f", state.activeSampleRate / 1000.0)
        tvSignalSource.text = "${state.activeAudioFormat} $rateKhz kHz • ${state.activeBitDepth}-bit"

        if (state.isPassthroughEnabled && state.isConnected) {
            if (state.passthroughMode == SettingsManager.USB_DAC_MODE_PURE_DIRECT) {
                tvSignalEngine.text = "Pure Direct (Bit-Perfect Bypass • 0% DSP)"
            } else {
                tvSignalEngine.text = "DeckAcoustix DSP ($rateKhz kHz Studio Clock)"
            }
            tvSignalOutput.text = "Direct USB HAL -> ${state.deviceName}"
            tvSignalResampler.text = "Android 48kHz Resampler: Bypassed (0.0% Distortion)"
        } else {
            tvSignalEngine.text = "DeckAcoustix DSP Engine"
            tvSignalOutput.text = "Android AudioTrack System Output"
            tvSignalResampler.text = "Android System Audio Resampler Active"
        }

        renderSampleRates(state)
    }

    private fun renderSampleRates(state: UsbDacState) {
        cgSampleRates.removeAllViews()
        val rates = if (state.supportedSampleRates.isNotEmpty()) {
            state.supportedSampleRates
        } else {
            listOf(44100, 48000, 88200, 96000, 176400, 192000)
        }

        for (rate in rates) {
            val chip = Chip(requireContext()).apply {
                val formatted = String.format(Locale.US, "%.1f kHz", rate / 1000.0)
                text = formatted
                isCheckable = true
                isClickable = false
                isChecked = (state.isConnected && rate == state.activeSampleRate)
            }
            cgSampleRates.addView(chip)
        }
    }
}
