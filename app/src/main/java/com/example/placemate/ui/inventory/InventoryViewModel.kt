package com.example.placemate.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.placemate.data.local.entities.ItemEntity
import com.example.placemate.data.repository.InventoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.map
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
// ... existing items flow ...

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
            val roomEntity = currentLocations.find { it.name.equals(roomLabel, true) && it.parentId == null }
                ?: repository.addLocationSync(roomLabel, LocationType.ROOM, null)
            
            locationCache[roomLabel] = roomEntity

            // 2. Identify all storage containers and build hierarchy
            // Sort by confidence or label density if needed, but here we process all containers
            val containerObjects = objects.filter { it.isContainer && it.label != roomLabel }
            
            // We might need multiple passes if there's deep nesting (e.g. Box in a Shelf)
            // For simplicity, we'll do up to 3 passes to resolve parents
            repeat(3) {
                containerObjects.forEach { cont ->
                    if (!locationCache.containsKey(cont.label)) {
                        val parentEntity = cont.parentLabel?.let { pLabel ->
                            locationCache.entries.find { it.key.equals(pLabel, true) }?.value
                        } ?: roomEntity // Fallback to room for containers

                        val entity = currentLocations.find { 
                            it.name.equals(cont.label, true) && it.parentId == parentEntity.id 
                        } ?: repository.addLocationSync(cont.label, LocationType.STORAGE, parentEntity.id)
                        
                        locationCache[cont.label] = entity
                    }
                }
            }

            // 3. Process all items
            val items = objects.filter { !it.isContainer && !it.label.equals(roomLabel, true) }
            items.forEach { item ->
                // Determine target location
                val targetLocation = item.parentLabel?.let { pLabel ->
                    locationCache.entries.find { it.key.equals(pLabel, true) }?.value
                } ?: item.boundingBox?.let { itemRect ->
                    // Spatial fallback
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
                    val nameSuffix = if (count > 1) " #$i" else ""
                    val itemEntity = ItemEntity(
                        name = "${item.label}$nameSuffix",
                        category = categoryManager.mapLabelToCategory(item.label),
                        description = "Exhaustively detected on/in ${targetLocation.name}",
                        photoUri = croppedUri?.toString()
                    )
                    repository.saveItem(itemEntity, targetLocation.id)
                }
            }
        }
    }
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val items: StateFlow<List<ItemUiModel>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isEmpty()) {
                repository.getAllItems()
            } else {
                repository.searchItems(query)
            }
        }
        .map { entityList ->
            entityList.groupBy { 
                // Group by attributes that define "sameness" for the user
                Triple(it.name, it.category, it.status) 
            }.map { (_, group) ->
                ItemUiModel(group.first(), group.size)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteItem(item: ItemEntity) {
        viewModelScope.launch {
            repository.deleteItem(item)
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.nukeData()
        }
    }
}
