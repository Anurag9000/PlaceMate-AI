package com.example.placemate.ui.sentinel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.placemate.core.input.ItemRecognitionService
import com.example.placemate.data.local.entities.LocationEntity
import com.example.placemate.data.repository.InventoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
    val auditResults: StateFlow<List<AuditItem>> = _auditResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val locations: StateFlow<List<LocationEntity>> = repository.getAllLocations()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun performAudit(imageUri: Uri, targetLocationId: String) {
        viewModelScope.launch {
            _error.update { null }
            _isLoading.update { true }
            try {
                // 1. Get current items in that location
                val dbItems = repository.getItemsForLocation(targetLocationId)
                
                // 2. Perform AI Recognition
                val targetLoc = locations.value.find { it.id == targetLocationId }
                val sceneResult = recognitionService.recognizeScene(imageUri, targetLoc?.name)
                
                if (sceneResult.errorMessage != null) {
                    _error.update { sceneResult.errorMessage }
                    _auditResults.update { emptyList() }
                } else {
                    // 3. Compare AI results with DB using normalized matching
                    val aiLabels = sceneResult.objects.map { it.label.lowercase().trim() }
                    
                    val results = mutableListOf<AuditItem>()
                    
                    // Check for MATCHED and MISSING
                    dbItems.forEach { item ->
                        val nameLower = item.name.lowercase().trim()
                        val matched = aiLabels.any { aiLabel ->
                            aiLabel == nameLower || aiLabel.contains(nameLower) || nameLower.contains(aiLabel)
                        }
                        
                        if (matched) {
                            results.add(AuditItem(item.id, item.name, AuditStatus.MATCHED, item.photoUri))
                        } else {
                            results.add(AuditItem(item.id, item.name, AuditStatus.MISSING, item.photoUri))
                        }
                    }
                    
                    // Check for NEW (found by AI but NOT in DB for this room)
                    val dbItemNames = dbItems.map { it.name.lowercase().trim() }
                    sceneResult.objects.forEach { obj ->
                        if (!obj.isContainer) {
                            val labelLower = obj.label.lowercase().trim()
                            val existsInDb = dbItemNames.any { dbName ->
                                dbName == labelLower || dbName.contains(labelLower) || labelLower.contains(dbName)
                            }
                            if (!existsInDb) {
                                results.add(AuditItem("new_${obj.label}", obj.label, AuditStatus.NEW, null))
                            }
                        }
                    }
                    
                    _auditResults.update { results.sortedBy { it.status } }
                }
            } catch (e: Exception) {
                _error.update { e.localizedMessage ?: "Unknown audit error" }
                _auditResults.update { emptyList() }
            } finally {
                _isLoading.update { false }
            }
        }
    }
    
    fun clearError() {
        _error.update { null }
    }
}
