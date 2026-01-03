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

    val aiEngineStatus: String = if (configManager.isGeminiEnabled() && !configManager.getGeminiApiKey().isNullOrEmpty()) "Gemini 1.5 Flash" else "Basic (ML Kit)"

    val totalItemsCount: StateFlow<Int> = repository.getAllItems()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val takenItemsCount: StateFlow<Int> = repository.getAllItems()
        .map { it.count { item -> item.status == com.example.placemate.data.local.entities.ItemStatus.TAKEN } }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val recentItems: StateFlow<List<ExplorerItem>> = repository.getAllItems()
        .map { entities -> 
            entities.take(5).map { ExplorerItem.File(it) }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
