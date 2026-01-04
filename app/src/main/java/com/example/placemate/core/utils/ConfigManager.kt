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

    fun getCustomGeminiPrompt(): String {
        return standardPrefs.getString(KEY_CUSTOM_GEMINI_PROMPT, DEFAULT_PROMPT) ?: DEFAULT_PROMPT
    }

    fun setCustomGeminiPrompt(prompt: String) {
        standardPrefs.edit().putString(KEY_CUSTOM_GEMINI_PROMPT, prompt).apply()
    }

    fun resetGeminiPrompt() {
        standardPrefs.edit().remove(KEY_CUSTOM_GEMINI_PROMPT).apply()
    }

    companion object {
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_USE_GEMINI = "use_gemini"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_SELECTED_GEMINI_MODEL = "selected_gemini_model"
        private const val KEY_CUSTOM_GEMINI_PROMPT = "custom_gemini_prompt"

        private const val DEFAULT_PROMPT = """
SYSTEM: EXHAUSTIVE Deep-Hierarchy Scene Intelligence for Personal Organization.
Your goal is to digitize the nested structure of USABLE items and storage containers in this scene.

CRITICAL INSTRUCTIONS:
1. FOCUS ON USABLE OBJECTS: Identify items that can be organized, moved, or misplaced (tools, electronics, stationary, kitchenware, toys, documents). 
   - DO NOT list infrastructure (walls, floors, ceilings, windows, tubelights, switchboards).
   - DO NOT list humans, pets, or outdoor scenery.
2. DETECT EVERYTHING USABLE: List every single distinct object, no matter how small (pins, clips, coins, pens). If there are multiple identical items, group them with an exact quantity.
3. DEEP NESTING: Capture the hierarchical relationship (e.g., Screws inside a Box inside a Drawer inside a Workbench).
   - Identify "Containers" (surfaces or receptacles that hold items: tables, shelves, drawers, boxes, bags, trays). Set `isContainer: true`.
   - For every object, identify its `parentLabel` (the IMMEDIATE container or surface it is on/in).
4. ACCURACY: Use precise bounding boxes [ymin, xmin, ymax, xmax] (0-1000).

SCHEMA:
{
  "objects": [
    {
      "label": "Specific Name",
      "isContainer": boolean,
      "confidence": number,
      "quantity": number,
      "parentLabel": "Name of the IMMEDIATE container/surface",
      "box_2d": [ymin, xmin, ymax, xmax]
    }
  ]
}
Return ONLY valid JSON.
"""
    }
}
