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
    private val repository: InventoryRepository
) : ViewModel() {
// ... existing items flow ...

    fun syncScene(result: SceneRecognitionResult) {
        viewModelScope.launch {
            val objects = result.objects
            if (objects.isEmpty()) return@launch

            // 1. Identify valid room/root if any. Default to "New Room" if none found.
            val roomLabel = objects.find { 
                val l = it.label.lowercase()
                l.contains("room") || l.contains("kitchen") || l.contains("office") || l.contains("bedroom")
            }?.label ?: "Scanned Room"
            
            val currentLocations = repository.getAllLocationsSync()
            val parentLocation = currentLocations?.find { it.name.equals(roomLabel, true) }
                ?: repository.addLocationSync(roomLabel, LocationType.ROOM, null)

            // 2. Identify containers (Shelves, Tables, Almirahs)
            val containers = objects.filter { it.isContainer && it.label != roomLabel }
            val containerEntities = containers.map { cont ->
                currentLocations?.find { it.name.equals(cont.label, true) && it.parentId == parentLocation.id }
                    ?: repository.addLocationSync(cont.label, LocationType.STORAGE, parentLocation.id)
            }

            // 3. Identify items and place them in the nearest container or the room
            val items = objects.filter { !it.isContainer && !it.label.contains("Room", true) }
            items.forEach { item ->
                // Logic: if we found a container, put it there. Otherwise, put in the room.
                val targetLocation = containerEntities.firstOrNull() ?: parentLocation
                
                val itemEntity = ItemEntity(
                    name = item.label,
                    category = "Detected",
                    description = "Detected in ${parentLocation.name}",
                    photoUri = null
                )
                repository.saveItem(itemEntity, targetLocation.id)
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

    fun repairData() {
        viewModelScope.launch {
            repository.repairBadData()
        }
    }
}
