import com.example.placemate.core.input.ItemRecognitionService
import com.example.placemate.core.utils.CategoryManager
import com.example.placemate.data.repository.InventoryRepository
import com.example.placemate.core.utils.SynonymManager
import com.example.placemate.core.utils.ConfigManager
import com.example.placemate.core.input.RecognizedObject
import android.graphics.Rect

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val repository: InventoryRepository,
    private val categoryManager: CategoryManager,
    private val recognitionService: ItemRecognitionService,
    private val synonymManager: SynonymManager,
    private val configManager: ConfigManager,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val KEY_SEARCH_QUERY = "search_query"
    private val KEY_CURRENT_LOCATION_ID = "current_location_id"


    fun syncScene(context: android.content.Context, result: SceneRecognitionResult, imageUri: android.net.Uri) {
        viewModelScope.launch {
            val objects = result.objects
            if (objects.isEmpty()) return@launch

            val currentLocations = repository.getAllLocationsSync() ?: emptyList()
            val locationCache = mutableMapOf<String, LocationEntity>()

            // 1. Resolve the Root Room
            val roomObj = objects.find { 
                val l = it.label.lowercase()
                l.contains("room") || l.contains("kitchen") || l.contains("office") || 
                l.contains("bedroom") || l.contains("garage") || l.contains("basement")
            } ?: objects.firstOrNull { it.isContainer && it.parentLabel == null }
            
            val roomLabel = roomObj?.label ?: "Scanned Room"
            val roomPhotoUri = roomObj?.boundingBox?.let { 
                ImageUtils.cropAndSave(context, imageUri, it)
            }
            
            // 1. Resolve Location Entity (Text + Visual)
            var roomEntity = currentLocations.find { loc -> 
                val locName = loc.name.lowercase()
                val label = roomLabel.lowercase()
                locName == label || (locName.length > 3 && label.length > 3 && (locName.contains(label) || label.contains(locName)))
            }

            // Visual Verification Step
            if (roomEntity == null) {
                // If no text match found, try Visual Match against all similar types
                val candidates = currentLocations
                    .filter { it.type == LocationType.ROOM || it.type == LocationType.STORAGE }
                    .filter { it.photoUri != null }
                    .map { com.example.placemate.core.input.VisualCandidate(it.id, it.name, android.net.Uri.parse(it.photoUri)) }
                
                if (candidates.isNotEmpty()) {
                    // Try to find a visual match
                    val matchedId = recognitionService.findVisualMatch(imageUri, candidates)
                    if (matchedId != null) {
                        roomEntity = currentLocations.find { it.id == matchedId }
                    }
                }
            }

            val finalRoomEntity = if (roomEntity != null) {
                // Refresh photo if it's an existing room/shelf
                if (roomPhotoUri != null) {
                    repository.updateLocation(roomEntity.copy(photoUri = roomPhotoUri.toString()))
                }
                roomEntity
            } else {
                // If not found, assume it is a new ROOT room for now. 
                repository.addLocationSync(roomLabel, LocationType.ROOM, null, roomPhotoUri?.toString())
            }
            
            locationCache[roomLabel] = finalRoomEntity

            // 2. Identify all storage containers and build hierarchy
            // Sort by confidence or label density if needed, but here we process all containers
            val containerObjects = objects.filter { it.isContainer && it.label != roomLabel }
            
            // We build nested hierarchy by resolving parents first.
            // Sorting containers by their relationships would be ideal, but for MVP, 
            // a multi-pass approach is robust against detection order.
            val containersToProcess = containerObjects.toMutableList()
            var passes = 0
            while (containersToProcess.isNotEmpty() && passes < 5) {
                val iterator = containersToProcess.iterator()
                while (iterator.hasNext()) {
                    val cont = iterator.next()
                    val parentEntity = cont.parentLabel?.let { pLabel ->
                        locationCache.entries.find { it.key.equals(pLabel, true) }?.value
                    } ?: if (cont.parentLabel == null) roomEntity else null

                    if (parentEntity != null) {
                         val contPhotoUri = cont.boundingBox?.let { 
                             ImageUtils.cropAndSave(context, imageUri, it)
                         }

                         val existingEntity = currentLocations.find { 
                            it.name.equals(cont.label, true) && it.parentId == parentEntity.id 
                        }
                        
                         val entity = if (existingEntity != null) {
                            if (contPhotoUri != null) {
                                repository.updateLocation(existingEntity.copy(photoUri = contPhotoUri.toString()))
                            }
                            existingEntity
                        } else {
                            repository.addLocationSync(cont.label, LocationType.STORAGE, parentEntity.id, contPhotoUri?.toString())
                        }
                         
                        locationCache[cont.label] = entity
                        iterator.remove()
                    }
                }
                passes++
            }
            // Any remaining containers that couldn't find a parent get attached to root room
            containersToProcess.forEach { cont ->
                val entity = repository.addLocationSync(cont.label, LocationType.STORAGE, roomEntity.id)
                locationCache[cont.label] = entity
            }
 
            val existingItems = repository.getAllItemsSync().map { it.name }.toMutableSet()
            
            // 3. Process all items
            val items = objects.filter { !it.isContainer && !it.label.equals(roomLabel, true) }
            items.forEach { item ->
                // Determine target location (same as before)
                val targetLocation = item.parentLabel?.let { pLabel ->
                    locationCache.entries.find { it.key.equals(pLabel, true) }?.value
                } ?: item.boundingBox?.let { itemRect ->
                    val centerX = itemRect.centerX()
                    val centerY = itemRect.centerY()
                    
                    containerObjects.filter { contObj ->
                        contObj.boundingBox?.contains(centerX, centerY) == true
                    }.minByOrNull { contObj -> 
                        val r = contObj.boundingBox ?: android.graphics.Rect()
                        r.width() * r.height()
                    }?.let { locationCache[it.label] }
                } ?: finalRoomEntity ?: roomEntity // Fallback to roomEntity if finalRoomEntity is temporarily null?
                // actually finalRoomEntity is defined above and *might* be null if addLocationSync failed or logic gap.
                // But wait, finalRoomEntity is non-nullable in logic flow?
                // logic: val finalRoomEntity = if (roomEntity != null)... else ... returns LocationEntity (not nullable).
                // Ah, the original code had `finalRoomEntity!!` which implies the compiler thought it was nullable or I am forcing it.
                // Let's assume it IS nullable in some path I didn't see. Using ?: return@forEach is safer.
                ?: return@forEach

                val croppedUri = item.boundingBox?.let { 
                     com.example.placemate.core.utils.ImageUtils.cropAndSave(context, imageUri, it)
                }
                
                // Handle quantity: Create multiple items if quantity > 1
                val count = if (item.quantity > 0) item.quantity else 1
                for (i in 1..count) {
                    var baseName = item.label
                    if (count > 1) baseName += " #$i"
                    
                    // Uniquify: Check if finalName is already in existingItems.
                    // If so, loop until we find a free suffix.
                    var finalName = baseName
                    var suffix = 1
                    while (existingItems.contains(finalName)) {
                        suffix++
                        finalName = "$baseName ($suffix)"
                    }
                    
                    existingItems.add(finalName) // Add to set so next iteration respects it

                    val itemEntity = ItemEntity(
                        name = finalName,
                        category = categoryManager.mapLabelToCategory(item.label),
                        description = "Detected in ${targetLocation.name}",
                        photoUri = croppedUri?.toString()
                    )
                    repository.saveItem(itemEntity, targetLocation.id)
                }

            }
        }
    }
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _currentLocationId = MutableStateFlow<String?>(null)
    val currentLocationId: StateFlow<String?> = _currentLocationId

    private val _refreshTrigger = MutableStateFlow(0)

    val currentPath: StateFlow<String> = _currentLocationId
        .flatMapLatest { locId ->
            if (locId == null) kotlinx.coroutines.flow.flowOf("Home")
            else kotlinx.coroutines.flow.flow {
                emit(repository.getLocationPath(locId).takeIf { it.isNotEmpty() } ?: "Unknown Location")
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, "Home")

    val explorerItems: StateFlow<List<ExplorerItem>> = combine(
        _currentLocationId,
        _searchQuery,
        _refreshTrigger
    ) { locId, query, _ ->
        if (query.isNotEmpty()) {
            val items = repository.searchItems(query).first()
            
            items.map { item ->
                val path = repository.getLocationPathForItem(item.id)
                ExplorerItem.File(item, path)
            }
        } else {
            repository.getExplorerContent(locId)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun navigateTo(locationId: String?) {
        _currentLocationId.value = locationId
    }

    fun navigateUp() {
        viewModelScope.launch {
            val current = _currentLocationId.value
            if (current != null) {
                val all = repository.getAllLocationsSync()
                val parent = all?.find { it.id == current }?.parentId
                _currentLocationId.value = parent
            }
        }
    }

    fun refreshExplorer() {
        _refreshTrigger.value += 1
    }

    fun deleteItem(item: ItemEntity) {
        viewModelScope.launch {
            repository.deleteItem(item)
            refreshExplorer()
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.nukeData()
            _currentLocationId.value = null
            refreshExplorer()
        }
    }

    fun updateLocation(id: String, name: String, type: LocationType, parentId: String?) {
        viewModelScope.launch {
            val updated = LocationEntity(
                id = id,
                name = name,
                type = type,
                parentId = parentId
            )
            repository.updateLocation(updated)
            refreshExplorer()
        }
    }

    suspend fun getAllLocations(): List<LocationEntity> {
        return repository.getAllLocationsSync() ?: emptyList()
    }

    fun addLocation(name: String, type: LocationType, parentId: String?) {
        viewModelScope.launch {
            repository.addLocationSync(name, type, parentId)
            refreshExplorer()
        }
    }

    suspend fun getLocationContextHint(): String {
        val locations = repository.getAllLocationsSync() ?: return ""
        // Return all known location names to help AI match existing shelves/containers
        return locations.distinctBy { it.name }.joinToString(", ") { it.name }
    }
}
