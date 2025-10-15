package com.example.offlinehqasr.ui

import com.example.offlinehqasr.data.entities.Recording

data class RecordingListItem(
    val recording: Recording,
    val snippet: String?
)
