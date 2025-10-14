package com.example.offlinehqasr.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("user_settings")

data class UserSettings(
    val useWhisper: Boolean = false,
    val whisperModelPath: String? = null,
    val preferredLanguage: String = "auto"
)

class SettingsRepository(private val context: Context) {

    val settings: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            useWhisper = prefs[USE_WHISPER] ?: false,
            whisperModelPath = prefs[WHISPER_MODEL_PATH],
            preferredLanguage = prefs[PREFERRED_LANGUAGE] ?: "auto"
        )
    }

    suspend fun current(): UserSettings = settings.first()

    suspend fun setUseWhisper(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[USE_WHISPER] = enabled
        }
    }

    suspend fun setWhisperModelPath(path: String?) {
        context.dataStore.edit { prefs ->
            if (path.isNullOrBlank()) {
                prefs.remove(WHISPER_MODEL_PATH)
            } else {
                prefs[WHISPER_MODEL_PATH] = path
            }
        }
    }

    suspend fun setPreferredLanguage(language: String) {
        context.dataStore.edit { prefs ->
            prefs[PREFERRED_LANGUAGE] = language
        }
    }

    companion object {
        private val USE_WHISPER = booleanPreferencesKey("use_whisper")
        private val WHISPER_MODEL_PATH = stringPreferencesKey("whisper_model_path")
        private val PREFERRED_LANGUAGE = stringPreferencesKey("preferred_language")
    }
}
