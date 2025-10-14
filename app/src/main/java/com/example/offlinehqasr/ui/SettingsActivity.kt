package com.example.offlinehqasr.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.example.offlinehqasr.R
import com.example.offlinehqasr.databinding.ActivitySettingsBinding
import com.example.offlinehqasr.export.ExportUtils
import com.example.offlinehqasr.settings.SettingsRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val repository by lazy { SettingsRepository(this) }

    private val pickWhisperModel =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let { handleModelSelection(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.settings_container, SettingsFragment())
            }
        }
    }

    fun launchModelPicker() {
        pickWhisperModel.launch(arrayOf("*/*"))
    }

    private fun handleModelSelection(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // best effort only
        }
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val name = ExportUtils.importWhisperModel(this@SettingsActivity, uri)
                    val path = File(filesDir, "models/whisper/$name").absolutePath
                    repository.setWhisperModelPath(path)
                    name
                }
            }.onSuccess { name ->
                Toast.makeText(
                    this@SettingsActivity,
                    getString(R.string.settings_whisper_model_success, name),
                    Toast.LENGTH_LONG
                ).show()
            }.onFailure { throwable ->
                Toast.makeText(
                    this@SettingsActivity,
                    getString(R.string.settings_whisper_model_error, throwable.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    companion object {
        fun createIntent(context: Context): Intent = Intent(context, SettingsActivity::class.java)
    }
}

class SettingsFragment : PreferenceFragmentCompat() {

    private val repository by lazy { SettingsRepository(requireContext()) }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_preferences, rootKey)

        val useWhisperPref = findPreference<SwitchPreferenceCompat>(KEY_USE_WHISPER)!!
        val modelPref = findPreference<Preference>(KEY_WHISPER_MODEL)!!
        val languagePref = findPreference<ListPreference>(KEY_PREFERRED_LANGUAGE)!!
        val purgePref = findPreference<Preference>(KEY_PURGE_ALL)!!

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.settings.collect { settings ->
                    useWhisperPref.isChecked = settings.useWhisper
                    languagePref.value = settings.preferredLanguage
                    languagePref.summary =
                        languagePref.entry ?: getString(R.string.settings_language_summary_default)
                    val name = settings.whisperModelPath?.let { File(it).name }
                    modelPref.summary = name ?: getString(R.string.settings_whisper_model_summary)
                }
            }
        }

        useWhisperPref.setOnPreferenceChangeListener { _, newValue ->
            viewLifecycleOwner.lifecycleScope.launch {
                repository.setUseWhisper(newValue as Boolean)
            }
            true
        }

        modelPref.setOnPreferenceClickListener {
            (activity as? SettingsActivity)?.launchModelPicker()
            true
        }

        languagePref.setOnPreferenceChangeListener { _, newValue ->
            viewLifecycleOwner.lifecycleScope.launch {
                repository.setPreferredLanguage(newValue as String)
            }
            true
        }

        purgePref.setOnPreferenceClickListener {
            showPurgeDialog()
            true
        }
    }

    private fun showPurgeDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_purge_dialog_title)
            .setMessage(R.string.settings_purge_dialog_message)
            .setPositiveButton(R.string.settings_purge_dialog_confirm) { _, _ ->
                executePurge()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun executePurge() {
        viewLifecycleOwner.lifecycleScope.launch {
            val start = SystemClock.elapsedRealtime()
            runCatching {
                withContext(Dispatchers.IO) {
                    val settings = repository.current()
                    val summary = purgeFiles(requireContext(), settings.whisperModelPath)
                    if (summary.whisperModelRemoved) {
                        repository.setWhisperModelPath(null)
                    }
                    summary
                }
            }.onSuccess { summary ->
                val durationSec = (SystemClock.elapsedRealtime() - start) / 1000f
                Toast.makeText(
                    requireContext(),
                    getString(R.string.settings_purge_result, durationSec, summary.deletedItems),
                    Toast.LENGTH_LONG
                ).show()
            }.onFailure { throwable ->
                Toast.makeText(
                    requireContext(),
                    getString(R.string.settings_purge_result_error, throwable.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun purgeFiles(context: Context, whisperPath: String?): PurgeSummary {
        var deleted = 0
        deleted += clearDirectory(File(context.filesDir, "audio"))
        deleted += clearDirectory(File(context.filesDir, "exports"))
        deleted += clearDirectory(File(context.filesDir, "tmp"))

        var modelRemoved = false
        val whisperDir = File(context.filesDir, "models/whisper")
        whisperDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("user_")) {
                deleted += file.deleteRecursivelyCounting()
                if (whisperPath != null && file.absolutePath == whisperPath) {
                    modelRemoved = true
                }
            }
        }
        if (whisperPath != null && !File(whisperPath).exists()) {
            modelRemoved = true
        }
        return PurgeSummary(deleted, modelRemoved)
    }

    private fun clearDirectory(dir: File): Int {
        if (!dir.exists()) return 0
        var deleted = 0
        dir.listFiles()?.forEach { file ->
            deleted += file.deleteRecursivelyCounting()
        }
        return deleted
    }

    private fun File.deleteRecursivelyCounting(): Int {
        if (!exists()) return 0
        var deleted = 0
        if (isDirectory) {
            listFiles()?.forEach { child ->
                deleted += child.deleteRecursivelyCounting()
            }
        }
        if (delete()) {
            deleted++
        }
        return deleted
    }

    companion object {
        private const val KEY_USE_WHISPER = "use_whisper"
        private const val KEY_WHISPER_MODEL = "whisper_model_path"
        private const val KEY_PREFERRED_LANGUAGE = "preferred_language"
        private const val KEY_PURGE_ALL = "purge_all"
    }

    private data class PurgeSummary(val deletedItems: Int, val whisperModelRemoved: Boolean)
}
