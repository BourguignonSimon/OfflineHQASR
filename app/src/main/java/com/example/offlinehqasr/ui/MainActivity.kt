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
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.offlinehqasr.R
import com.example.offlinehqasr.data.AppDb
import com.example.offlinehqasr.databinding.ActivityMainBinding
import com.example.offlinehqasr.export.ExportUtils
import com.example.offlinehqasr.recorder.RecordService
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isRecording = false
    private var errorBannerVisible = false
    private var errorReceiverRegistered = false
    private lateinit var adapter: RecordingAdapter
    private val selectedTags = mutableSetOf<String>()
    private val selectedParticipants = mutableSetOf<String>()
    private var searchJob: Job? = null

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
        adapter = RecordingAdapter { rec ->
            val i = Intent(this@MainActivity, DetailActivity::class.java)
            i.putExtra("recordingId", rec.id)
            startActivity(i)
        }
        binding.recordingsList.adapter = adapter

        binding.searchInput.doOnTextChanged { _, _, _, _ -> scheduleSearch() }

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
        refreshFilters { performSearch() }
    }

    private fun refreshFilters(onComplete: (() -> Unit)? = null) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDb.get(this@MainActivity)
            val searchDao = db.transcriptSearchDao()
            val tags = parseFilterValues(searchDao.getAllTagsRaw())
            val participants = parseFilterValues(searchDao.getAllParticipantsRaw())
            launch(Dispatchers.Main) {
                updateChipGroup(binding.tagChipGroup, binding.tagFiltersLabel, tags, selectedTags)
                updateChipGroup(binding.participantChipGroup, binding.participantFiltersLabel, participants, selectedParticipants)
                onComplete?.invoke()
            }
        }
    }

    private fun performSearch() {
        val queryText = binding.searchInput.text?.toString().orEmpty()
        val tags = selectedTags.toSet()
        val participants = selectedParticipants.toSet()
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                val db = AppDb.get(this@MainActivity)
                val match = buildMatchQuery(queryText, tags, participants)
                if (match == null) {
                    db.recordingDao().getAll().map { RecordingListItem(it, null) }
                } else {
                    db.transcriptSearchDao().searchRecordings(match).map { row ->
                        val snippet = row.snippet
                            ?.replace('\n', ' ')
                            ?.replace(Regex("\\s+"), " ")
                            ?.trim()
                        RecordingListItem(row.recording, snippet)
                    }
                }
            }
            adapter.submit(items)
        }
    }

    private fun scheduleSearch() {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            delay(200)
            performSearch()
        }
    }

    private fun parseFilterValues(raw: List<String>): List<String> {
        return raw.asSequence()
            .flatMap { it.splitToSequence('\n') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
            .toList()
    }

    private fun updateChipGroup(group: ChipGroup, label: View, values: List<String>, selected: MutableSet<String>) {
        val allowed = values.toSet()
        val removed = selected.filterNot { it in allowed }
        if (removed.isNotEmpty()) {
            selected.removeAll(removed)
        }
        group.removeAllViews()
        val hasValues = values.isNotEmpty()
        label.isVisible = hasValues
        group.isVisible = hasValues
        if (!hasValues) return
        values.forEach { value ->
            val chip = Chip(this).apply {
                text = value
                isCheckable = true
                isChecked = selected.contains(value)
            }
            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selected.add(value)
                } else {
                    selected.remove(value)
                }
                performSearch()
            }
            group.addView(chip)
        }
    }

    private fun buildMatchQuery(text: String, tags: Set<String>, participants: Set<String>): String? {
        val clauses = mutableListOf<String>()
        val tokens = text.trim().split(Regex("\\s+")).mapNotNull {
            val cleaned = it.trim()
            if (cleaned.isEmpty()) null else "\"${ftsEscape(cleaned)}\""
        }
        if (tokens.isNotEmpty()) {
            clauses.add(tokens.joinToString(separator = " "))
        }
        tags.forEach { value -> clauses.add("tags:\"${ftsEscape(value)}\"") }
        participants.forEach { value -> clauses.add("participants:\"${ftsEscape(value)}\"") }
        return if (clauses.isEmpty()) null else clauses.joinToString(separator = " AND ")
    }

    private fun ftsEscape(raw: String): String = raw.replace("\"", "\"\"")

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
