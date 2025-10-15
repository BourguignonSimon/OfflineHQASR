package com.example.offlinehqasr.recorder.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder

/**
 * Chooses the most relevant audio input depending on connected peripherals.
 */
object AudioDeviceSelector {

    data class Selection(
        val audioSource: Int,
        val preferredDevice: AudioDeviceInfo?,
        val label: String
    )

    fun select(context: Context): Selection {
        val audioManager = context.getSystemService(AudioManager::class.java)
            ?: return Selection(MediaRecorder.AudioSource.VOICE_RECOGNITION, null, "builtin")
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val wired = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET }
        if (wired != null) {
            return Selection(MediaRecorder.AudioSource.MIC, wired, "wired-headset")
        }
        val bluetooth = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        if (bluetooth != null) {
            return Selection(MediaRecorder.AudioSource.VOICE_COMMUNICATION, bluetooth, "bt-sco")
        }
        val builtin = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
        return if (builtin != null) {
            Selection(MediaRecorder.AudioSource.VOICE_RECOGNITION, builtin, "builtin")
        } else {
            Selection(MediaRecorder.AudioSource.MIC, null, "fallback")
        }
    }
}
