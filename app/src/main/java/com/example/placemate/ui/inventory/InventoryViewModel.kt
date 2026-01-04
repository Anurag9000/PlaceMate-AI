package com.example.placemate.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.placemate.data.local.entities.ItemEntity
import com.example.placemate.data.repository.InventoryRepository
import kotlinx.coroutines.flow.combine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import com.example.placemate.core.input.SceneRecognitionResult
import com.example.placemate.data.local.entities.LocationType
import com.example.placemate.data.local.entities.LocationEntity
import com.example.placemate.data.local.entities.ItemPlacementEntity

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val repository: com.example.placemate.data.repository.InventoryRepository,
    private val categoryManager: com.example.placemate.core.utils.CategoryManager
) : ViewModel() {


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
                com.example.placemate.core.utils.ImageUtils.cropAndSave(context, imageUri, it)
            }
            
            val roomEntity = currentLocations.find { it.name.equals(roomLabel, true) && it.parentId == null }
                ?: repository.addLocationSync(roomLabel, LocationType.ROOM, null, roomPhotoUri?.toString())
            
            locationCache[roomLabel] = roomEntity

            // 2. Identify all storage containers and build hierarchy
            // Sort by confidence or label density if needed, but here we process all containers
            val containerObjects = objects.filter { it.isContainer && it.label != roomLabel }
            
            // We might need multiple passes if there's deep nesting (e.g. Box in a Shelf)
            // For simplicity, we'll do up to 3 passes to resolve parents
            // We handle deep nesting (e.g. Pin -> Box -> Drawer -> Desk -> Room)
            // We run multiple passes to ensure parents are created before their children link to them.
            // 10 passes should cover any realistic physical storage depth.
            repeat(10) {
                containerObjects.forEach { cont ->
                    // ... same logic ...
                    if (!locationCache.containsKey(cont.label)) {
                        val parentEntity = cont.parentLabel?.let { pLabel ->
                            locationCache.entries.find { it.key.equals(pLabel, true) }?.value
                        } ?: roomEntity // Default to room if parent not yet found (will be updated in next pass? No, insert is final here)
                        
                        // Wait, if we fallback to roomEnity immediately, we break the chain if the parent IS in the list but not yet processed.
                        // We should only create if parent IS found or if parentLabel is null.
                        
                        val resolvedParent = cont.parentLabel?.let { pLabel ->
                             locationCache.entries.find { it.key.equals(pLabel, true) }?.value
                        }
                        
                        // If detected parent is strictly missing from cache but exists in our objects list, wait.
                        val parentIsKnownContainer = containerObjects.any { it.label.equals(cont.parentLabel, true) }
                        
                        if (resolvedParent != null || !parentIsKnownContainer || cont.parentLabel == null) {
                             val finalParent = resolvedParent ?: roomEntity
                             
                             val contPhotoUri = cont.boundingBox?.let { 
                                 com.example.placemate.core.utils.ImageUtils.cropAndSave(context, imageUri, it)
                             }

                             val entity = currentLocations.find { 
                                it.name.equals(cont.label, true) && it.parentId == finalParent.id 
                            } ?: repository.addLocationSync(cont.label, LocationType.STORAGE, finalParent.id, contPhotoUri?.toString())
                            
                            locationCache[cont.label] = entity
                        }
                    }
                }
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
                        val r = contObj.boundingBox!!
                        r.width() * r.height()
                    }?.let { locationCache[it.label] }
                } ?: roomEntity

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

    val explorerItems: StateFlow<List<ExplorerItem>> = kotlinx.coroutines.flow.combine(
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
}
