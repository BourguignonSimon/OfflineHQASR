package com.example.offlinehqasr.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.widget.Toast
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.offlinehqasr.R
import com.example.offlinehqasr.data.AppDb
import com.example.offlinehqasr.data.entities.Recording
import com.example.offlinehqasr.databinding.ActivityMainBinding
import com.example.offlinehqasr.export.ExportUtils
import com.example.offlinehqasr.recorder.RecordService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isRecording = false
    private var errorBannerVisible = false
    private var errorReceiverRegistered = false

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

        binding.errorBannerClose.setOnClickListener { hideRecordingError() }

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
    }

    override fun onStart() {
        super.onStart()
        if (!errorReceiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                recordErrorReceiver,
                IntentFilter(RecordService.ACTION_RECORDING_ERROR),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            errorReceiverRegistered = true
        }
    }

    override fun onStop() {
        super.onStop()
        if (errorReceiverRegistered) {
            unregisterReceiver(recordErrorReceiver)
            errorReceiverRegistered = false
        }
    }

    override fun onResume() {
        super.onResume()
        syncRecordingState()
        refreshList()
        updateStatus()
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
        if (!active && errorBannerVisible) {
            // ensure stale error is visible when we return to idle
            binding.errorBanner.visibility = View.VISIBLE
        }
    }

    private fun showRecordingError(code: String?) {
        errorBannerVisible = true
        val message = when (code) {
            RecordService.ERROR_PERMISSION -> getString(R.string.microphone_error_permission)
            RecordService.ERROR_INVALID_OPERATION -> getString(R.string.microphone_error_invalid_operation)
            RecordService.ERROR_BAD_VALUE -> getString(R.string.microphone_error_bad_value)
            RecordService.ERROR_DISCONNECTED -> getString(R.string.microphone_error_disconnected)
            RecordService.ERROR_UNKNOWN -> getString(R.string.microphone_error_unknown)
            else -> getString(R.string.microphone_error_placeholder)
        }
        binding.errorBannerText.text = message
        binding.errorBanner.visibility = View.VISIBLE
    }

    private fun hideRecordingError() {
        errorBannerVisible = false
        binding.errorBanner.visibility = View.GONE
    }

    private val recordErrorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val reason = intent.getStringExtra(RecordService.EXTRA_ERROR_CODE)
            showRecordingError(reason)
        }
    }

    private fun modelStatusLabel(dir: File): String {
        if (!dir.exists()) return getString(R.string.model_status_missing)
        val children = dir.listFiles()
        return if (children.isNullOrEmpty()) getString(R.string.model_status_incomplete) else getString(R.string.model_status_ready)
    }
}
