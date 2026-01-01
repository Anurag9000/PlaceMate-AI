package com.example.placemate.core.utils

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val standardPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    fun saveGeminiApiKey(apiKey: String) {
        securePrefs.edit().putString(KEY_GEMINI_API_KEY, apiKey).apply()
    }

    fun getGeminiApiKey(): String? {
        return securePrefs.getString(KEY_GEMINI_API_KEY, null)
    }

    fun hasGeminiApiKey(): Boolean {
        return !getGeminiApiKey().isNullOrBlank()
    }

    fun setUseGemini(enabled: Boolean) {
        standardPrefs.edit().putBoolean(KEY_USE_GEMINI, enabled).apply()
    }

    fun isGeminiEnabled(): Boolean {
        return standardPrefs.getBoolean(KEY_USE_GEMINI, false)
    }

    fun isOnboardingCompleted(): Boolean {
        return standardPrefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        standardPrefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }

    fun getSelectedGeminiModel(): String {
        return standardPrefs.getString(KEY_SELECTED_GEMINI_MODEL, "gemini-1.5-flash") ?: "gemini-1.5-flash"
    }

    fun setSelectedGeminiModel(model: String) {
        standardPrefs.edit().putString(KEY_SELECTED_GEMINI_MODEL, model).apply()
    }

    companion object {
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_USE_GEMINI = "use_gemini"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_SELECTED_GEMINI_MODEL = "selected_gemini_model"
    }
}
