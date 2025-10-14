package com.example.offlinehqasr.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
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
            if (!isRecording) startRecording() else stopRecording()
        }

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_import_vosk -> { openFilePicker(arrayOf("application/zip")); true }
                R.id.action_import_whisper -> { openFilePicker(arrayOf("*/*")); true }
                R.id.action_export_markdown -> { exportAllMarkdown(); true }
                else -> false
            }
        }

        updateStatus()
        refreshList()
    }

    private fun updateStatus() {
        val filesDir = filesDir
        val voskOk = File(filesDir, "models/vosk").exists()
        val whisperOk = File(filesDir, "models/whisper").exists()
        binding.statusText.text = "Vosk: " + (if (voskOk) "OK" else "—") + " | Whisper: " + (if (whisperOk) "OK" else "—")
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
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 10)
            return
        }
        RecordService.start(this)
        isRecording = true
        binding.recordFab.setImageResource(android.R.drawable.ic_media_pause)
        Toast.makeText(this, "Enregistrement démarré", Toast.LENGTH_SHORT).show()
    }

    private fun stopRecording() {
        RecordService.stop(this)
        isRecording = false
        binding.recordFab.setImageResource(android.R.drawable.ic_btn_speak_now)
        Toast.makeText(this, "Enregistrement arrêté", Toast.LENGTH_SHORT).show()
        refreshList()
    }

    private fun openFilePicker(mimes: Array<String>) {
        pickFile.launch(mimes)
    }

    private fun handleImport(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val name = ExportUtils.copyAndMaybeUnzip(this@MainActivity, uri)
                launch(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Importé: $name", Toast.LENGTH_LONG).show()
                    updateStatus()
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Échec import: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun exportAllMarkdown() {
        lifecycleScope.launch(Dispatchers.IO) {
            val path = ExportUtils.exportAllToMarkdown(this@MainActivity)
            launch(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Exporté vers: $path", Toast.LENGTH_LONG).show()
            }
        }
    }
}
