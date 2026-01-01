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

            // 1. Identify valid room/root
            val roomLabel = objects.find { 
                val l = it.label.lowercase()
                l.contains("room") || l.contains("kitchen") || l.contains("office") || l.contains("bedroom")
            }?.label ?: "Scanned Room"
            
            val currentLocations = repository.getAllLocationsSync()
            val parentLocation = currentLocations?.find { it.name.equals(roomLabel, true) }
                ?: repository.addLocationSync(roomLabel, LocationType.ROOM, null)

            // 2. Identify containers
            val containerObjects = objects.filter { it.isContainer && it.label != roomLabel }
            val containerMap = containerObjects.map { cont ->
                val entity = currentLocations?.find { it.name.equals(cont.label, true) && it.parentId == parentLocation.id }
                    ?: repository.addLocationSync(cont.label, LocationType.STORAGE, parentLocation.id)
                cont to entity
            }

            // 3. Identify items and place them spatially
            val items = objects.filter { !it.isContainer && !it.label.contains("Room", true) }
            items.forEach { item ->
                val targetLocation = item.boundingBox?.let { itemRect ->
                    val centerX = itemRect.centerX()
                    val centerY = itemRect.centerY()
                    
                    containerMap.filter { (contObj, _) ->
                        contObj.boundingBox?.contains(centerX, centerY) == true
                    }.minByOrNull { (contObj, _) -> 
                        val r = contObj.boundingBox!!
                        r.width() * r.height()
                    }?.second
                } ?: parentLocation

                val croppedUri = item.boundingBox?.let { 
                    com.example.placemate.core.utils.ImageUtils.cropAndSave(context, imageUri, it)
                }
                
                val itemEntity = ItemEntity(
                    name = item.label,
                    category = categoryManager.mapLabelToCategory(item.label),
                    description = "Detected in ${parentLocation.name}",
                    photoUri = croppedUri?.toString()
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
}
