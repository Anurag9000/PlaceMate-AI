package com.example.placemate.ui.inventory

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.placemate.core.input.ItemRecognitionService
import com.example.placemate.core.input.SceneRecognitionResult
import com.example.placemate.core.input.VisualCandidate
import com.example.placemate.core.utils.CategoryManager
import com.example.placemate.core.utils.ConfigManager
import com.example.placemate.core.utils.ImageUtils
import com.example.placemate.core.utils.SynonymManager
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    private val searchQueryFlow = MutableStateFlow("")
    private val currentLocationIdFlow = MutableStateFlow<String?>(null)
    private val refreshTrigger = MutableStateFlow(0)

    val currentPath: StateFlow<String> = currentLocationIdFlow
        .flatMapLatest { locationId ->
            if (locationId == null) {
                flowOf("Home")
            } else {
                flow {
                    emit(repository.getLocationPath(locationId).ifBlank { "Unknown Location" })
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, "Home")

    val explorerItems: StateFlow<List<ExplorerItem>> = combine(
        currentLocationIdFlow,
        searchQueryFlow,
        refreshTrigger
    ) { locationId, query, _ ->
        if (query.isNotBlank()) {
            repository.searchItems(query).first().map { item ->
                val path = repository.getLocationPathForItem(item.id)
                ExplorerItem.File(item, path)
            }
        } else {
            repository.getExplorerContent(locationId)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val searchQueryState: StateFlow<String> = searchQueryFlow
    val currentLocationIdState: StateFlow<String?> = currentLocationIdFlow
    val currentLocationId: StateFlow<String?> = currentLocationIdState

    fun syncScene(context: Context, result: SceneRecognitionResult, imageUri: Uri) {
        viewModelScope.launch {
            val objects = result.objects
            if (objects.isEmpty()) {
                return@launch
            }

            val currentLocations = repository.getAllLocationsSync() ?: emptyList()
            val locationCache = mutableMapOf<String, LocationEntity>()

            val roomObject = objects.find {
                val label = it.label.lowercase()
                label.contains("room") ||
                    label.contains("kitchen") ||
                    label.contains("office") ||
                    label.contains("bedroom") ||
                    label.contains("garage") ||
                    label.contains("basement")
            } ?: objects.firstOrNull { it.isContainer && it.parentLabel == null }

            val roomLabel = roomObject?.label ?: "Scanned Room"
            val roomPhotoUri = roomObject?.boundingBox?.let { ImageUtils.cropAndSave(context, imageUri, it) }

            var roomEntity = currentLocations.find { location ->
                val locationName = location.name.lowercase()
                val normalizedRoomLabel = roomLabel.lowercase()
                locationName == normalizedRoomLabel ||
                    (
                        locationName.length > 3 &&
                            normalizedRoomLabel.length > 3 &&
                            (locationName.contains(normalizedRoomLabel) ||
                                normalizedRoomLabel.contains(locationName))
                        )
            }

            if (roomEntity == null) {
                val candidates = currentLocations
                    .filter { it.type == LocationType.ROOM || it.type == LocationType.STORAGE }
                    .mapNotNull { location ->
                        location.photoUri?.let { photoUri ->
                            runCatching {
                                VisualCandidate(location.id, location.name, Uri.parse(photoUri))
                            }.getOrNull()
                        }
                    }
                if (candidates.isNotEmpty()) {
                    val matchedId = recognitionService.findVisualMatch(imageUri, candidates)
                    roomEntity = currentLocations.find { it.id == matchedId }
                }
            }

            val finalRoomEntity = if (roomEntity != null) {
                roomPhotoUri?.let {
                    repository.updateLocation(roomEntity.copy(photoUri = it.toString()))
                }
                roomEntity
            } else {
                repository.addLocationSync(roomLabel, LocationType.ROOM, null, roomPhotoUri?.toString())
            }

            locationCache[roomLabel] = finalRoomEntity

            val containerObjects = objects.filter { it.isContainer && !it.label.equals(roomLabel, true) }
            val containersToProcess = containerObjects.toMutableList()
            var passes = 0

            while (containersToProcess.isNotEmpty() && passes < 5) {
                val iterator = containersToProcess.iterator()
                while (iterator.hasNext()) {
                    val container = iterator.next()
                    val parentEntity = container.parentLabel?.let { parentLabel ->
                        locationCache.entries.find { it.key.equals(parentLabel, true) }?.value
                    } ?: finalRoomEntity

                    val containerPhotoUri =
                        container.boundingBox?.let { ImageUtils.cropAndSave(context, imageUri, it) }
                    val existingEntity = currentLocations.find {
                        it.name.equals(container.label, true) && it.parentId == parentEntity.id
                    }
                    val entity = if (existingEntity != null) {
                        containerPhotoUri?.let {
                            repository.updateLocation(existingEntity.copy(photoUri = it.toString()))
                        }
                        existingEntity
                    } else {
                        repository.addLocationSync(
                            container.label,
                            LocationType.STORAGE,
                            parentEntity.id,
                            containerPhotoUri?.toString()
                        )
                    }
                    locationCache[container.label] = entity
                    iterator.remove()
                }
                passes++
            }

            containersToProcess.forEach { container ->
                val entity = repository.addLocationSync(container.label, LocationType.STORAGE, finalRoomEntity.id)
                locationCache[container.label] = entity
            }

            val existingItems = repository.getAllItemsSync().map { it.name }.toMutableSet()
            val items = objects.filter { !it.isContainer && !it.label.equals(roomLabel, true) }

            items.forEach { item ->
                val targetLocation = item.parentLabel?.let { parentLabel ->
                    locationCache.entries.find { it.key.equals(parentLabel, true) }?.value
                } ?: item.boundingBox?.let { itemRect ->
                    val centerX = itemRect.centerX()
                    val centerY = itemRect.centerY()
                    containerObjects
                        .filter { container -> container.boundingBox?.contains(centerX, centerY) == true }
                        .minByOrNull { container ->
                            val bounds = container.boundingBox ?: android.graphics.Rect()
                            bounds.width() * bounds.height()
                        }
                        ?.let { locationCache[it.label] }
                } ?: finalRoomEntity

                val croppedUri = item.boundingBox?.let { ImageUtils.cropAndSave(context, imageUri, it) }
                val count = item.quantity.coerceAtLeast(1)

                repeat(count) { index ->
                    val baseName = if (count > 1) "${item.label} #${index + 1}" else item.label
                    var finalName = baseName
                    var suffix = 1
                    while (existingItems.contains(finalName)) {
                        suffix++
                        finalName = "$baseName ($suffix)"
                    }
                    existingItems.add(finalName)

                    val itemEntity = ItemEntity(
                        name = finalName,
                        category = categoryManager.mapLabelToCategory(item.label),
                        description = "Detected in ${targetLocation.name}",
                        photoUri = croppedUri?.toString()
                    )
                    repository.saveItem(itemEntity, targetLocation.id)
                }
            }

            refreshExplorer()
        }
    }

    fun updateSearchQuery(query: String) {
        searchQueryFlow.value = query
    }

    fun navigateTo(locationId: String?) {
        currentLocationIdFlow.value = locationId
    }

    fun navigateUp() {
        viewModelScope.launch {
            val current = currentLocationIdFlow.value ?: return@launch
            val parentId = repository.getAllLocationsSync()
                ?.find { it.id == current }
                ?.parentId
            currentLocationIdFlow.value = parentId
        }
    }

    fun refreshExplorer() {
        refreshTrigger.value += 1
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
            currentLocationIdFlow.value = null
            refreshExplorer()
        }
    }

    fun updateLocation(id: String, name: String, type: LocationType, parentId: String?) {
        viewModelScope.launch {
            repository.updateLocationDetails(id, name, type, parentId)
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
        return locations.distinctBy { it.name }.joinToString(", ") { it.name }
    }
}
