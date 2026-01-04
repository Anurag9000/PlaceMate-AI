package com.example.placemate.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configManager: com.example.placemate.core.utils.ConfigManager
) {
    private val CADENCE_HOURS = intPreferencesKey("reminder_cadence_hours")

    val reminderCadenceHours: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[CADENCE_HOURS] ?: 24
    }

    suspend fun updateReminderCadence(hours: Int) {
        context.dataStore.edit { preferences ->
            preferences[CADENCE_HOURS] = hours
        }
    }

    fun getGeminiApiKey(): String? = configManager.getGeminiApiKey()

    fun updateGeminiApiKey(key: String) {
        configManager.saveGeminiApiKey(key)
    }

    fun isGeminiEnabled(): Boolean = configManager.isGeminiEnabled()

    fun setUseGemini(enabled: Boolean) {
        configManager.setUseGemini(enabled)
    }

    fun getSelectedGeminiModel(): String = configManager.getSelectedGeminiModel()

    fun setSelectedGeminiModel(model: String) {
        configManager.setSelectedGeminiModel(model)
    }

    fun getCustomGeminiPrompt(): String = configManager.getCustomGeminiPrompt()

    fun updateCustomGeminiPrompt(prompt: String) {
        configManager.setCustomGeminiPrompt(prompt)
    }

    fun resetGeminiPrompt() {
        configManager.resetGeminiPrompt()
    }
}
