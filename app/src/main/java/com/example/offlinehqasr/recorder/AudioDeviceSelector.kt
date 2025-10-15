package com.example.offlinehqasr.recorder

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.MediaRecorder

/**
 * Picks the most appropriate audio input device available on the handset.
 * Preference order: wired headsets/USB interfaces, then Bluetooth, then the built-in microphone.
 * Returns the target audio source, channel configuration and a user facing label.
 */
object AudioDeviceSelector {

    private val preferredTypes = listOf(
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BUILTIN_MIC
    )

    fun select(context: Context): Selection {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val inputs = audioManager?.getDevices(AudioManager.GET_DEVICES_INPUTS)?.toList().orEmpty()
        val device = preferredTypes
            .asSequence()
            .mapNotNull { desired -> inputs.firstOrNull { it.type == desired } }
            .firstOrNull() ?: inputs.firstOrNull()

        val channelMask = when {
            device?.channelCounts?.any { it >= 2 } == true -> AudioFormat.CHANNEL_IN_STEREO
            else -> AudioFormat.CHANNEL_IN_MONO
        }

        val audioSource = when (device?.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
            else -> MediaRecorder.AudioSource.VOICE_RECOGNITION
        }

        val label = buildString {
            if (device != null) {
                append(device.productName ?: "")
                if (device.type == AudioDeviceInfo.TYPE_BUILTIN_MIC) {
                    append(" (int.)")
                }
            } else {
                append("Micro interne")
            }
        }.ifBlank { "Micro interne" }

        return Selection(
            audioSource = audioSource,
            channelMask = channelMask,
            preferredDevice = device,
            label = label
        )
    }

    data class Selection(
        val audioSource: Int,
        val channelMask: Int,
        val preferredDevice: AudioDeviceInfo?,
        val label: String
    )
}
