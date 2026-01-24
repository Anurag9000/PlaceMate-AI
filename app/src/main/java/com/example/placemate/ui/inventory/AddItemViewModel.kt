import com.example.placemate.data.local.entities.LocationEntity
import com.example.placemate.data.local.entities.LocationType
import androidx.lifecycle.SavedStateHandle
import javax.inject.Inject

@HiltViewModel
class AddItemViewModel @Inject constructor(
    private val repository: InventoryRepository,
    private val inputInterpreter: InputInterpreter,
    private val speechManager: SpeechManager,
    private val recognitionService: ItemRecognitionService,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Keys for SavedStateHandle
    private val KEY_NAME = "name"
    private val KEY_CATEGORY = "category"
    private val KEY_NOTES = "notes"
    private val KEY_SELECTED_LOCATION = "selected_location"
    private val KEY_IMAGE_URI = "image_uri"

    // Backing flows from SavedStateHandle
    private val _name = savedStateHandle.getStateFlow(KEY_NAME, "")
    private val _category = savedStateHandle.getStateFlow(KEY_CATEGORY, "")
    private val _notes = savedStateHandle.getStateFlow(KEY_NOTES, "")
    private val _selectedLocationId = savedStateHandle.getStateFlow<String?>(KEY_SELECTED_LOCATION, null)
    private val _imageUriString = savedStateHandle.getStateFlow<String?>(KEY_IMAGE_URI, null)
    
    // Derived UI State
    val uiState: StateFlow<AddItemUiState> = kotlinx.coroutines.flow.combine(
        _name, _category, _notes, _selectedLocationId, _imageUriString, repository.getAllLocations(), _isSaved
    ) { name, category, notes, locId, uriStr, locations, saved ->
        val path = locId?.let { id -> repository.getLocationPath(id) } ?: "Not Set"
        AddItemUiState(
            name = name,
            category = category,
            notes = notes,
            imageUri = uriStr?.let { Uri.parse(it) },
            selectedLocationId = locId,
            locationPath = path,
            isSaved = saved
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
            // Only auto-fill if empty? Or overwrite? User intent usually overwrite.
            result.suggestedName?.let { if (_name.value.isEmpty()) savedStateHandle[KEY_NAME] = it }
            result.suggestedCategory?.let { if (_category.value.isEmpty()) savedStateHandle[KEY_CATEGORY] = it }
        }
    }

    suspend fun resolveLocationPath(path: List<String>): LocationEntity {
        var parentId: String? = null
        var lastLocation: LocationEntity? = null
        
        // Cache locations once to avoid O(N*M) DB calls in the loop
        val allExisting = repository.getAllLocationsSync() ?: emptyList()

        path.forEachIndexed { index, name ->
            val type = if (index == 0) LocationType.ROOM else LocationType.STORAGE
            val entity = allExisting.find { it.name.equals(name, true) && it.parentId == parentId }
                ?: repository.addLocationSync(name, type, parentId)
            
            parentId = entity.id
            lastLocation = entity
        }
        
        // Return last or a safe default without infinite recursion
        return lastLocation ?: allExisting.firstOrNull { it.parentId == null } 
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
                        val intent = inputInterpreter.interpret(UserInput.Speech(state.text))
                        if (intent is InterpretedIntent.AddItem) {
                            intent.name?.let { savedStateHandle[KEY_NAME] = it }
                            intent.category?.let { savedStateHandle[KEY_CATEGORY] = it }
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
        val name = _name.value
        if (name.isBlank()) return

        viewModelScope.launch {
            val newItem = ItemEntity(
                name = name,
                category = _category.value,
                description = _notes.value,
                photoUri = _imageUriString.value
            )
            repository.saveItem(newItem, _selectedLocationId.value)
            // Signal navigation back? We can use a boolean in SavedState or a channel
            // For MVP simplicity, we can reset or use a dedicated event. 
            // The previous code had `isSaved`. Let's assume the fragment observes something else or just popBackStack 
            // We can emit a side-effect, but sticking to state flow:
            _isSaved.value = true
        }
    }
    
    private val _isSaved = MutableStateFlow(false)
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
