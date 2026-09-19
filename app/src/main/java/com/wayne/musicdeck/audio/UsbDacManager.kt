/*
 * Copyright (c) 2026 Wayne Chibeu. All rights reserved.
 * MusicDeck Audio Engine - Audiophile Bit-Perfect USB-DAC Subsystem.
 */

package com.wayne.musicdeck.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.wayne.musicdeck.utils.SettingsManager
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AudioQualityTier {
    BIT_PERFECT,
    HI_RES_DIRECT,
    HI_RES_LOSSLESS,
    LOSSLESS,
    STANDARD
}

data class UsbDacState(
    val isConnected: Boolean = false,
    val deviceName: String = "",
    val deviceType: Int = 0,
    val supportedSampleRates: List<Int> = emptyList(),
    val supportedChannelCounts: List<Int> = emptyList(),
    val supportedEncodings: List<Int> = emptyList(),
    val isBitPerfectSupported: Boolean = false,
    val isBitPerfectActive: Boolean = false,
    val activeSampleRate: Int = 44100,
    val activeBitDepth: Int = 16,
    val activeAudioFormat: String = "PCM",
    val passthroughMode: String = SettingsManager.USB_DAC_MODE_PURE_DIRECT,
    val isPassthroughEnabled: Boolean = true
) {
    val isLosslessFormat: Boolean
        get() {
            val fmt = activeAudioFormat.uppercase()
            return fmt == "FLAC" || fmt == "WAV" || fmt == "ALAC" || fmt == "DSD" || fmt == "PCM"
        }

    val isHiRes: Boolean
        get() = isLosslessFormat && (activeSampleRate > 48000 || activeBitDepth > 16)

    val qualityTier: AudioQualityTier
        get() = when {
            isConnected && isPassthroughEnabled -> {
                if (passthroughMode == SettingsManager.USB_DAC_MODE_PURE_DIRECT) {
                    AudioQualityTier.BIT_PERFECT
                } else {
                    AudioQualityTier.HI_RES_DIRECT
                }
            }
            isHiRes -> AudioQualityTier.HI_RES_LOSSLESS
            isLosslessFormat -> AudioQualityTier.LOSSLESS
            else -> AudioQualityTier.STANDARD
        }

    val tierBadgeText: String
        get() = when (qualityTier) {
            AudioQualityTier.BIT_PERFECT -> "BIT-PERFECT"
            AudioQualityTier.HI_RES_DIRECT -> "HI-RES DIRECT"
            AudioQualityTier.HI_RES_LOSSLESS -> "HI-RES LOSSLESS"
            AudioQualityTier.LOSSLESS -> "LOSSLESS"
            AudioQualityTier.STANDARD -> activeAudioFormat.uppercase()
        }

    val formattedSampleRate: String
        get() {
            val rate = activeSampleRate
            return if (rate <= 0) "44.1 kHz"
            else if (rate % 1000 == 0) "${rate / 1000}.0 kHz"
            else String.format(java.util.Locale.US, "%.1f kHz", rate / 1000f)
        }

    val technicalStreamDetails: String
        get() = "${activeBitDepth}-bit / $formattedSampleRate ${activeAudioFormat.uppercase()}"

    val miniBadgeText: String
        get() = when (qualityTier) {
            AudioQualityTier.BIT_PERFECT -> "DAC"
            AudioQualityTier.HI_RES_DIRECT -> "DIR"
            AudioQualityTier.HI_RES_LOSSLESS -> "HR"
            AudioQualityTier.LOSSLESS -> "FLAC"
            AudioQualityTier.STANDARD -> activeAudioFormat.uppercase()
        }
}

object UsbDacManager {
    private const val TAG = "UsbDacManager"
    private val mmkv by lazy { MMKV.defaultMMKV() }

    private var appContext: Context? = null
    private var audioManager: AudioManager? = null
    private var connectedUsbDevice: AudioDeviceInfo? = null

    private val _dacState = MutableStateFlow(UsbDacState())
    val dacState: StateFlow<UsbDacState> = _dacState.asStateFlow()

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
            scanConnectedDevices()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
            scanConnectedDevices()
        }
    }

    fun init(context: Context) {
        if (appContext != null) return
        val appCtx = context.applicationContext
        appContext = appCtx
        val am = appCtx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager = am

        am?.registerAudioDeviceCallback(deviceCallback, null)
        scanConnectedDevices()
    }

    fun getConnectedDevice(): AudioDeviceInfo? = connectedUsbDevice

    fun isPureDirectActive(): Boolean {
        val state = _dacState.value
        return state.isConnected && 
               state.isPassthroughEnabled && 
               state.passthroughMode == SettingsManager.USB_DAC_MODE_PURE_DIRECT
    }

    fun setPassthroughEnabled(enabled: Boolean) {
        mmkv.encode("usb_dac_passthrough_enabled", enabled)
        updateState()
        applyBitPerfectConfiguration()
    }

    fun setPassthroughMode(mode: String) {
        mmkv.encode("usb_dac_mode", mode)
        updateState()
        applyBitPerfectConfiguration()
    }

    fun updateTrackTelemetry(sampleRate: Int, bitDepth: Int, formatString: String) {
        val current = _dacState.value
        if (current.activeSampleRate == sampleRate && 
            current.activeBitDepth == bitDepth && 
            current.activeAudioFormat == formatString) {
            return
        }

        _dacState.value = current.copy(
            activeSampleRate = if (sampleRate > 0) sampleRate else 44100,
            activeBitDepth = if (bitDepth > 0) bitDepth else 16,
            activeAudioFormat = formatString
        )

        applyBitPerfectConfiguration()
    }

    private fun scanConnectedDevices() {
        val am = audioManager ?: return
        val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val usbDevice = devices.firstOrNull { isUsbAudioDevice(it) }

        connectedUsbDevice = usbDevice

        val isEnabled = mmkv.decodeBool("usb_dac_passthrough_enabled", true)
        val mode = mmkv.decodeString("usb_dac_mode", SettingsManager.USB_DAC_MODE_PURE_DIRECT) ?: SettingsManager.USB_DAC_MODE_PURE_DIRECT

        if (usbDevice == null) {
            _dacState.value = UsbDacState(
                isConnected = false,
                isPassthroughEnabled = isEnabled,
                passthroughMode = mode
            )
            Log.d(TAG, "USB Audio DAC disconnected")
            return
        }

        val name = resolveDeviceName(usbDevice)
        val sampleRates = usbDevice.sampleRates.toList().sorted()
        val channelCounts = usbDevice.channelCounts.toList().sorted()
        val encodings = usbDevice.encodings.toList()
        val bitPerfectSupported = checkBitPerfectSupport(usbDevice)

        _dacState.value = _dacState.value.copy(
            isConnected = true,
            deviceName = name,
            deviceType = usbDevice.type,
            supportedSampleRates = sampleRates,
            supportedChannelCounts = channelCounts,
            supportedEncodings = encodings,
            isBitPerfectSupported = bitPerfectSupported,
            isPassthroughEnabled = isEnabled,
            passthroughMode = mode
        )

        Log.i(TAG, "USB Audio DAC detected: $name (Sample Rates: $sampleRates, Bit-Perfect Supported: $bitPerfectSupported)")
        applyBitPerfectConfiguration()
    }

    private fun updateState() {
        val isEnabled = mmkv.decodeBool("usb_dac_passthrough_enabled", true)
        val mode = mmkv.decodeString("usb_dac_mode", SettingsManager.USB_DAC_MODE_PURE_DIRECT) ?: SettingsManager.USB_DAC_MODE_PURE_DIRECT
        _dacState.value = _dacState.value.copy(
            isPassthroughEnabled = isEnabled,
            passthroughMode = mode
        )
    }

    private fun applyBitPerfectConfiguration() {
        val device = connectedUsbDevice ?: return
        val isEnabled = mmkv.decodeBool("usb_dac_passthrough_enabled", true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (isEnabled) {
                configureAndroid14BitPerfect(device)
            } else {
                clearAndroid14BitPerfect(device)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun configureAndroid14BitPerfect(device: AudioDeviceInfo) {
        val am = audioManager ?: return
        try {
            val supportedMixers = am.getSupportedMixerAttributes(device)
            val bitPerfectMixer = supportedMixers.firstOrNull {
                it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT
            }

            if (bitPerfectMixer != null) {
                val mediaAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()

                am.setPreferredMixerAttributes(mediaAttributes, device, bitPerfectMixer)
                _dacState.value = _dacState.value.copy(isBitPerfectActive = true)
                Log.i(TAG, "Configured Android 14 MIXER_BEHAVIOR_BIT_PERFECT on ${device.productName}")
            } else {
                _dacState.value = _dacState.value.copy(isBitPerfectActive = false)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not set preferred mixer attributes: ${e.message}")
            _dacState.value = _dacState.value.copy(isBitPerfectActive = false)
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun clearAndroid14BitPerfect(device: AudioDeviceInfo) {
        val am = audioManager ?: return
        try {
            val mediaAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()
            am.clearPreferredMixerAttributes(mediaAttributes, device)
            _dacState.value = _dacState.value.copy(isBitPerfectActive = false)
            Log.i(TAG, "Cleared preferred mixer attributes on ${device.productName}")
        } catch (e: Exception) {
            Log.w(TAG, "Could not clear preferred mixer attributes: ${e.message}")
        }
    }

    private fun checkBitPerfectSupport(device: AudioDeviceInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val am = audioManager ?: return false
            return try {
                val mixers = am.getSupportedMixerAttributes(device)
                mixers.any { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT }
            } catch (e: Exception) {
                false
            }
        }
        return true
    }

    private fun isUsbAudioDevice(device: AudioDeviceInfo): Boolean {
        if (!device.isSink) return false
        return device.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
               device.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
               device.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
    }

    private fun resolveDeviceName(device: AudioDeviceInfo): String {
        val productName = device.productName?.toString()?.trim()
        if (!productName.isNullOrEmpty() && productName != "USB-Audio") {
            return productName
        }
        return when (device.type) {
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB-C Audio Headset"
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB Audio Accessory"
            else -> "USB Audio DAC"
        }
    }
}
