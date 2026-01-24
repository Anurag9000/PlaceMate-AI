package com.example.placemate.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.placemate.core.utils.ConfigManager
import com.example.placemate.data.repository.InventoryRepository
import com.example.placemate.ui.inventory.ExplorerItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: InventoryRepository,
    private val configManager: ConfigManager
) : ViewModel() {

    val aiEngineStatus: StateFlow<String> = configManager.geminiEnabledFlow
        .map { enabled -> if (enabled) "Gemini 1.5 Flash" else "Basic (ML Kit)" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Basic (ML Kit)")

    val totalItemsCount: StateFlow<Int> = repository.getItemCount()
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val takenItemsCount: StateFlow<Int> = repository.getTakenItemCount()
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val recentItems: StateFlow<List<ExplorerItem>> = repository.getRecentItems()
        .map { entities -> 
            entities.map { ExplorerItem.File(it) }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
