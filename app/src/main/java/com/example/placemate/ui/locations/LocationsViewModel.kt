package com.example.placemate.ui.locations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.placemate.data.local.entities.LocationEntity
import com.example.placemate.data.local.entities.LocationType
import com.example.placemate.data.repository.InventoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class LocationsViewModel @Inject constructor(
    private val repository: InventoryRepository
) : ViewModel() {

    val locations: StateFlow<List<LocationEntity>> = repository.getAllLocations()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun addLocation(name: String, type: LocationType, parentId: String?) {
        viewModelScope.launch {
            val newLocation = LocationEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                type = type,
                parentId = parentId
            )
            repository.saveLocation(newLocation)
        }
    }
}
