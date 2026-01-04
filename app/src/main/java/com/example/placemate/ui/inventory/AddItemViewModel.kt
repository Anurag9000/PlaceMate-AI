package com.example.placemate.ui.inventory

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.placemate.core.input.*
import com.example.placemate.data.local.entities.ItemEntity
import com.example.placemate.data.repository.InventoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import javax.inject.Inject

@HiltViewModel
class AddItemViewModel @Inject constructor(
    private val repository: InventoryRepository,
    private val inputInterpreter: InputInterpreter,
    private val speechManager: SpeechManager,
    private val recognitionService: ItemRecognitionService
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddItemUiState())
    val uiState: StateFlow<AddItemUiState> = _uiState

    val availableLocations: StateFlow<List<com.example.placemate.data.local.entities.LocationEntity>> =
        repository.getAllLocations().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun onNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun onCategoryChanged(category: String) {
        _uiState.value = _uiState.value.copy(category = category)
    }

    fun onNotesChanged(notes: String) {
        _uiState.value = _uiState.value.copy(notes = notes)
    }

    fun onLocationSelected(locationId: String?) {
        _uiState.value = _uiState.value.copy(selectedLocationId = locationId)
    }

    fun onImagePicked(uri: Uri) {
        _uiState.value = _uiState.value.copy(imageUri = uri)
        viewModelScope.launch {
            val result = recognitionService.recognizeItem(uri)
            _uiState.value = _uiState.value.copy(
                name = result.suggestedName ?: _uiState.value.name,
                category = result.suggestedCategory ?: _uiState.value.category
            )
            // If AI suggested a location, we could resolve it here if we extend RecognitionResult
        }
    }

    suspend fun resolveLocationPath(path: List<String>): com.example.placemate.data.local.entities.LocationEntity {
        var parentId: String? = null
        var lastLocation: com.example.placemate.data.local.entities.LocationEntity? = null
        
        path.forEachIndexed { index, name ->
            val type = if (index == 0) com.example.placemate.data.local.entities.LocationType.ROOM else com.example.placemate.data.local.entities.LocationType.STORAGE
            val existing = repository.getAllLocationsSync()?.find { it.name.equals(name, true) && it.parentId == parentId }
            val entity = existing ?: repository.addLocationSync(name, type, parentId)
            parentId = entity.id
            lastLocation = entity
        }
        
        return lastLocation ?: resolveLocationPath(listOf("Default Room"))
    }

    fun setLocationPath(path: List<String>) {
        viewModelScope.launch {
            val entity = resolveLocationPath(path)
            val pathString = repository.getLocationPath(entity.id)
            _uiState.value = _uiState.value.copy(
                selectedLocationId = entity.id,
                locationPath = pathString
            )
        }
    }

    fun startSpeechInput() {
        viewModelScope.launch {
            speechManager.startListening().collect { state ->
                when (state) {
                    is SpeechState.Result -> {
                        val intent = inputInterpreter.interpret(UserInput.Speech(state.text))
                        if (intent is InterpretedIntent.AddItem) {
                            _uiState.value = _uiState.value.copy(
                                name = intent.name ?: _uiState.value.name,
                                category = intent.category ?: _uiState.value.category
                            )
                            intent.locationPath?.let { setLocationPath(it) }
                        } else if (intent is InterpretedIntent.AssignLocation) {
                             setLocationPath(intent.locationPath)
                        }
                    }
                    else -> { /* Handle other states if needed */ }
                }
            }
        }
    }

    fun saveItem() {
        val state = _uiState.value
        if (state.name.isBlank()) return

        viewModelScope.launch {
            val newItem = ItemEntity(
                name = state.name,
                category = state.category,
                description = state.notes,
                photoUri = state.imageUri?.toString()
            )
            repository.saveItem(newItem, state.selectedLocationId)
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }
}

data class AddItemUiState(
    val name: String = "",
    val category: String = "",
    val notes: String = "",
    val imageUri: Uri? = null,
    val selectedLocationId: String? = null,
    val locationPath: String = "Not Set",
    val isSaved: Boolean = false
)
