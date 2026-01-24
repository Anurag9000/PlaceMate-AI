package com.example.placemate.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.placemate.data.repository.SettingsRepository
import com.example.placemate.data.repository.ModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val apiKey: String = "",
    val useGemini: Boolean = false,
    val selectedModel: String = "",
    val availableModels: List<String> = emptyList(),
    val reminderCadence: Int = 24,
    val customPrompt: String = "",
    val isLoadingModels: Boolean = false,
    val saveSuccess: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val modelRepository: ModelRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val config = settingsRepository
            _uiState.update { it.copy(
                apiKey = config.getGeminiApiKey() ?: "",
                useGemini = config.isGeminiEnabled(),
                selectedModel = config.getSelectedGeminiModel(),
                customPrompt = config.getCustomGeminiPrompt()
            ) }
            
            settingsRepository.reminderCadenceHours.collect { hours ->
                _uiState.update { it.copy(reminderCadence = hours) }
            }
        }
    }

    fun onApiKeyChanged(apiKey: String) {
        _uiState.update { it.copy(apiKey = apiKey) }
    }

    fun onUseGeminiChanged(enabled: Boolean) {
        _uiState.update { it.copy(useGemini = enabled) }
    }

    fun onModelSelected(model: String) {
        _uiState.update { it.copy(selectedModel = model) }
    }

    fun onPromptChanged(prompt: String) {
        _uiState.update { it.copy(customPrompt = prompt) }
    }

    fun onCadenceChanged(hours: Int) {
        _uiState.update { it.copy(reminderCadence = hours) }
    }

    fun fetchModels() {
        val apiKey = _uiState.value.apiKey
        if (apiKey.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingModels = true) }
            val models = modelRepository.fetchAvailableModels(apiKey)
            _uiState.update { it.copy(
                isLoadingModels = false,
                availableModels = models.map { "${it.name} (${it.displayName})" }
            ) }
        }
    }

    fun resetPrompt() {
        settingsRepository.resetGeminiPrompt()
        _uiState.update { it.copy(customPrompt = settingsRepository.getCustomGeminiPrompt()) }
    }

    fun saveSettings() {
        viewModelScope.launch {
            val state = _uiState.value
            settingsRepository.updateGeminiApiKey(state.apiKey)
            settingsRepository.setUseGemini(state.useGemini)
            settingsRepository.setSelectedGeminiModel(state.selectedModel.split(" ")[0])
            settingsRepository.updateReminderCadence(state.reminderCadence)
            settingsRepository.updateCustomGeminiPrompt(state.customPrompt)
            _uiState.update { it.copy(saveSuccess = true) }
        }
    }
    
    fun consumeSaveSuccess() {
        _uiState.update { it.copy(saveSuccess = false) }
    }
}
