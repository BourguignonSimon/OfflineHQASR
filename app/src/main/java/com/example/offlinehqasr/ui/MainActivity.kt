package com.example.offlinehqasr.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.offlinehqasr.R
import com.example.offlinehqasr.data.AppDb
import com.example.offlinehqasr.data.entities.Recording
import com.example.offlinehqasr.databinding.ActivityMainBinding
import com.example.offlinehqasr.export.ExportUtils
import com.example.offlinehqasr.recorder.RecordService
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isRecording = false
    private var microphoneReceiverRegistered = false
    private val hideBannerRunnable = Runnable { binding.microphoneBanner.isVisible = false }

    private val microphoneStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != RecordService.ACTION_MICROPHONE_STATUS) return
            val status = intent.getStringExtra(RecordService.EXTRA_STATUS) ?: return
            val message = intent.getStringExtra(RecordService.EXTRA_MESSAGE)
            handleMicrophoneStatus(status, message)
        }
    }

    private val audioPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            startRecordingInternal()
        } else {
            Toast.makeText(this, R.string.permission_microphone_required, Toast.LENGTH_LONG).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { handleImport(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        binding.recordingsList.layoutManager = LinearLayoutManager(this)

        binding.recordFab.setOnClickListener {
            if (!isRecording) startRecording() else stopRecordingInternal()
        }

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_import_vosk -> { openFilePicker(arrayOf("application/zip")); true }
                R.id.action_import_whisper -> { openFilePicker(arrayOf("*/*")); true }
                R.id.action_export_markdown -> { exportAllMarkdown(); true }
                else -> false
            }
        }

        ensureNotificationPermission()
        updateStatus()
        refreshList()
        syncRecordingState()
        registerMicrophoneStatusReceiver()
    }

    override fun onResume() {
        super.onResume()
        syncRecordingState()
        refreshList()
        updateStatus()
    }

    override fun onDestroy() {
        if (microphoneReceiverRegistered) {
            try {
                unregisterReceiver(microphoneStatusReceiver)
            } catch (_: IllegalArgumentException) {
            }
            microphoneReceiverRegistered = false
        }
        binding.microphoneBanner.removeCallbacks(hideBannerRunnable)
        super.onDestroy()
    }

    private fun updateStatus() {
        val voskStatus = modelStatusLabel(File(filesDir, "models/vosk"))
        val whisperStatus = modelStatusLabel(File(filesDir, "models/whisper"))
        binding.statusText.text = getString(R.string.model_status_template, voskStatus, whisperStatus)
    }

    private fun refreshList() {
        val dao = AppDb.get(this).recordingDao()
        lifecycleScope.launch(Dispatchers.IO) {
            val items = dao.getAll()
            launch(Dispatchers.Main) {
                binding.recordingsList.adapter = RecordingAdapter(items) { rec ->
                    val i = Intent(this@MainActivity, DetailActivity::class.java)
                    i.putExtra("recordingId", rec.id)
                    startActivity(i)
                }
            }
        }
    }

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        startRecordingInternal()
    }

    private fun startRecordingInternal() {
        ensureNotificationPermission()
        RecordService.start(this)
        setRecordingState(true)
        Toast.makeText(this, R.string.recording_started, Toast.LENGTH_SHORT).show()
    }

    private fun stopRecordingInternal() {
        RecordService.stop(this)
        setRecordingState(false)
        Toast.makeText(this, R.string.recording_stopped, Toast.LENGTH_SHORT).show()
        refreshList()
    }

    private fun openFilePicker(mimes: Array<String>) {
        pickFile.launch(mimes)
    }

    private fun handleImport(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // best effort; continue without persisted access
        }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val name = ExportUtils.copyAndMaybeUnzip(this@MainActivity, uri)
                launch(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.import_success, name), Toast.LENGTH_LONG).show()
                    updateStatus()
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.import_failure, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun exportAllMarkdown() {
        lifecycleScope.launch(Dispatchers.IO) {
            val path = ExportUtils.exportAllToMarkdown(this@MainActivity)
            launch(Dispatchers.Main) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.export_markdown_success, path),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun syncRecordingState() {
        setRecordingState(RecordService.isRunning())
    }

    private fun setRecordingState(active: Boolean) {
        isRecording = active
        binding.recordFab.setImageResource(
            if (active) android.R.drawable.ic_media_pause else android.R.drawable.ic_btn_speak_now
        )
    }

    private fun modelStatusLabel(dir: File): String {
        if (!dir.exists()) return getString(R.string.model_status_missing)
        val children = dir.listFiles()
        return if (children.isNullOrEmpty()) getString(R.string.model_status_incomplete) else getString(R.string.model_status_ready)
    }

    private fun registerMicrophoneStatusReceiver() {
        if (microphoneReceiverRegistered) return
        val filter = IntentFilter(RecordService.ACTION_MICROPHONE_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(microphoneStatusReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(microphoneStatusReceiver, filter)
        }
        microphoneReceiverRegistered = true
    }

    private fun handleMicrophoneStatus(status: String, message: String?) {
        binding.microphoneBanner.removeCallbacks(hideBannerRunnable)
        when (status) {
            RecordService.STATUS_OK -> {
                if (!message.isNullOrBlank()) {
                    showMicrophoneBanner(
                        getString(R.string.microphone_status_source, message),
                        R.attr.colorSecondaryContainer,
                        R.attr.colorOnSecondaryContainer
                    )
                    binding.microphoneBanner.postDelayed(hideBannerRunnable, 2_000)
                } else {
                    binding.microphoneBanner.isVisible = false
                }
            }
            RecordService.STATUS_WARNING -> {
                showMicrophoneBanner(
                    getString(R.string.microphone_status_warning),
                    R.attr.colorTertiaryContainer,
                    R.attr.colorOnTertiaryContainer
                )
            }
            RecordService.STATUS_ERROR -> {
                val text = getString(R.string.microphone_status_error, message ?: "?")
                showMicrophoneBanner(text, R.attr.colorErrorContainer, R.attr.colorOnErrorContainer)
            }
            RecordService.STATUS_IDLE -> {
                binding.microphoneBanner.isVisible = false
            }
        }
    }

    private fun showMicrophoneBanner(text: String, backgroundAttr: Int, foregroundAttr: Int) {
        val background = MaterialColors.getColor(binding.root, backgroundAttr)
        val foreground = MaterialColors.getColor(binding.root, foregroundAttr)
        binding.microphoneBanner.setCardBackgroundColor(background)
        binding.microphoneBannerText.setTextColor(foreground)
        binding.microphoneBannerText.text = text
        binding.microphoneBanner.isVisible = true
    }
}
