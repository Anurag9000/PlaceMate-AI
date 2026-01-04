package com.example.placemate.ui.sentinel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.placemate.core.input.ItemRecognitionService
import com.example.placemate.data.local.entities.ItemEntity
import com.example.placemate.data.local.entities.LocationEntity
import com.example.placemate.data.repository.InventoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuditItem(
    val id: String,
    val name: String,
    val status: AuditStatus,
    val photoUri: String? = null
)

enum class AuditStatus {
    MATCHED, MISSING, NEW
}

@HiltViewModel
class SentinelViewModel @Inject constructor(
    private val repository: InventoryRepository,
    private val recognitionService: ItemRecognitionService
) : ViewModel() {

    private val _auditResults = MutableStateFlow<List<AuditItem>>(emptyList())
    val auditResults: StateFlow<List<AuditItem>> = _auditResults

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _locations = MutableStateFlow<List<LocationEntity>>(emptyList())
    val locations: StateFlow<List<LocationEntity>> = _locations

    init {
        loadLocations()
    }

    private fun loadLocations() {
        viewModelScope.launch {
            _locations.value = repository.getAllLocationsSync() ?: emptyList()
        }
    }

    fun performAudit(imageUri: Uri, targetLocationId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 1. Get current items in that location
                val dbItems = repository.getItemsForLocation(targetLocationId)
                
                // 2. Perform AI Recognition
                // We pass the location name as hint to improve context
                val targetLoc = _locations.value.find { it.id == targetLocationId }
                val sceneResult = recognitionService.recognizeScene(imageUri, targetLoc?.name)
                
                if (sceneResult.errorMessage != null) {
                    // Handle error (maybe via a state? for now just empty)
                    _auditResults.value = emptyList()
                } else {
                    // 3. Compare AI results with DB
                    val aiLabels = sceneResult.objects.map { it.label.lowercase() }
                    
                    val results = mutableListOf<AuditItem>()
                    
                    // Check for MATCHED and MISSING
                    dbItems.forEach { item ->
                        val nameLower = item.name.lowercase()
                        val matched = aiLabels.any { it.contains(nameLower) || nameLower.contains(it) }
                        
                        if (matched) {
                            results.add(AuditItem(item.id, item.name, AuditStatus.MATCHED, item.photoUri))
                        } else {
                            results.add(AuditItem(item.id, item.name, AuditStatus.MISSING, item.photoUri))
                        }
                    }
                    
                    // Check for NEW (found by AI but NOT in DB for this room)
                    val dbItemNames = dbItems.map { it.name.lowercase() }
                    sceneResult.objects.forEach { obj ->
                        if (!obj.isContainer) {
                            val labelLower = obj.label.lowercase()
                            val existsInDb = dbItemNames.any { it.contains(labelLower) || labelLower.contains(it) }
                            if (!existsInDb) {
                                results.add(AuditItem("new_${obj.label}", obj.label, AuditStatus.NEW, null))
                            }
                        }
                    }
                    
                    _auditResults.value = results.sortedBy { it.status }
                }
            } catch (e: Exception) {
                _auditResults.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
