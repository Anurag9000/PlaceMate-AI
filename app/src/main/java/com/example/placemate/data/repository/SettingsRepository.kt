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
    @ApplicationContext private val context: Context
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
}
