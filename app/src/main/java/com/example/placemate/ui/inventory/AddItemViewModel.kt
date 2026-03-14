package com.example.placemate.ui.inventory

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.placemate.core.input.InputInterpreter
import com.example.placemate.core.input.InterpretedIntent
import com.example.placemate.core.input.ItemRecognitionService
import com.example.placemate.core.input.SpeechManager
import com.example.placemate.core.input.SpeechState
import com.example.placemate.core.input.UserInput
import com.example.placemate.data.local.entities.ItemEntity
import com.example.placemate.data.local.entities.LocationEntity
import com.example.placemate.data.local.entities.LocationType
import com.example.placemate.data.repository.InventoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AddItemViewModel @Inject constructor(
    private val repository: InventoryRepository,
    private val inputInterpreter: InputInterpreter,
    private val speechManager: SpeechManager,
    private val recognitionService: ItemRecognitionService,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private companion object {
        const val KEY_NAME = "name"
        const val KEY_CATEGORY = "category"
        const val KEY_NOTES = "notes"
        const val KEY_SELECTED_LOCATION = "selected_location"
        const val KEY_IMAGE_URI = "image_uri"
    }

    private val _name = savedStateHandle.getStateFlow(KEY_NAME, "")
    private val _category = savedStateHandle.getStateFlow(KEY_CATEGORY, "")
    private val _notes = savedStateHandle.getStateFlow(KEY_NOTES, "")
    private val _selectedLocationId = savedStateHandle.getStateFlow<String?>(KEY_SELECTED_LOCATION, null)
    private val _imageUriString = savedStateHandle.getStateFlow<String?>(KEY_IMAGE_URI, null)
    private val _isSaved = MutableStateFlow(false)

    private val locationPathFlow: StateFlow<String> = _selectedLocationId
        .flatMapLatest { locationId ->
            if (locationId == null) {
                flowOf("Not Set")
            } else {
                flow { emit(repository.getLocationPath(locationId)) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, "Not Set")

    private val draftFlow = combine(
        _name,
        _category,
        _notes,
        _selectedLocationId,
        _imageUriString
    ) { name, category, notes, locationId, imageUriString ->
        AddItemDraft(
            name = name,
            category = category,
            notes = notes,
            selectedLocationId = locationId,
            imageUriString = imageUriString
        )
    }

    val uiState: StateFlow<AddItemUiState> = combine(
        draftFlow,
        locationPathFlow,
        _isSaved
    ) { draft, locationPath, isSaved ->
        AddItemUiState(
            name = draft.name,
            category = draft.category,
            notes = draft.notes,
            imageUri = draft.imageUriString?.let(Uri::parse),
            selectedLocationId = draft.selectedLocationId,
            locationPath = locationPath,
            isSaved = isSaved
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, AddItemUiState())

    val availableLocations: StateFlow<List<LocationEntity>> =
        repository.getAllLocations().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun onNameChanged(name: String) {
        savedStateHandle[KEY_NAME] = name
    }

    fun onCategoryChanged(category: String) {
        savedStateHandle[KEY_CATEGORY] = category
    }

    fun onNotesChanged(notes: String) {
        savedStateHandle[KEY_NOTES] = notes
    }

    fun onLocationSelected(locationId: String?) {
        savedStateHandle[KEY_SELECTED_LOCATION] = locationId
    }

    fun onImagePicked(uri: Uri) {
        savedStateHandle[KEY_IMAGE_URI] = uri.toString()
        viewModelScope.launch {
            val result = recognitionService.recognizeItem(uri)
            result.suggestedName?.let {
                if (_name.value.isBlank()) {
                    savedStateHandle[KEY_NAME] = it
                }
            }
            result.suggestedCategory?.let {
                if (_category.value.isBlank()) {
                    savedStateHandle[KEY_CATEGORY] = it
                }
            }
        }
    }

    suspend fun resolveLocationPath(path: List<String>): LocationEntity {
        var parentId: String? = null
        var lastLocation: LocationEntity? = null
        val allExisting = repository.getAllLocationsSync() ?: emptyList()

        path.forEachIndexed { index, name ->
            val type = if (index == 0) LocationType.ROOM else LocationType.STORAGE
            val entity = allExisting.find { it.name.equals(name, true) && it.parentId == parentId }
                ?: repository.addLocationSync(name, type, parentId)
            parentId = entity.id
            lastLocation = entity
        }

        return lastLocation
            ?: allExisting.firstOrNull { it.parentId == null }
            ?: repository.addLocationSync("Default Room", LocationType.ROOM, null)
    }

    fun setLocationPath(path: List<String>) {
        viewModelScope.launch {
            val entity = resolveLocationPath(path)
            savedStateHandle[KEY_SELECTED_LOCATION] = entity.id
        }
    }

    fun startSpeechInput() {
        viewModelScope.launch {
            speechManager.startListening().collect { state ->
                when (state) {
                    is SpeechState.Result -> {
                        when (val intent = inputInterpreter.interpret(UserInput.Speech(state.text))) {
                            is InterpretedIntent.AddItem -> {
                                intent.name?.let { savedStateHandle[KEY_NAME] = it }
                                intent.category?.let { savedStateHandle[KEY_CATEGORY] = it }
                                intent.locationPath?.let(::setLocationPath)
                            }
                            is InterpretedIntent.AssignLocation -> setLocationPath(intent.locationPath)
                            else -> Unit
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    fun saveItem() {
        val name = _name.value.trim()
        if (name.isBlank()) {
            return
        }

        viewModelScope.launch {
            val newItem = ItemEntity(
                name = name,
                category = _category.value.trim(),
                description = _notes.value.takeIf { it.isNotBlank() },
                photoUri = _imageUriString.value
            )
            repository.saveItem(newItem, _selectedLocationId.value)
            _isSaved.value = true
        }
    }

    val isSaved: StateFlow<Boolean> = _isSaved
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

private data class AddItemDraft(
    val name: String,
    val category: String,
    val notes: String,
    val selectedLocationId: String?,
    val imageUriString: String?
)
