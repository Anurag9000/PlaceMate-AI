package com.example.placemate.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.placemate.core.utils.ConfigManager
import com.example.placemate.data.repository.InventoryRepository
import com.example.placemate.ui.inventory.ItemUiModel
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

    val aiEngineStatus: String = if (configManager.isGeminiEnabled() && configManager.hasGeminiApiKey()) "Gemini 1.5 Flash" else "Basic (ML Kit)"

    val totalItemsCount: StateFlow<Int> = repository.getAllItems()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val takenItemsCount: StateFlow<Int> = repository.getAllItems()
        .map { it.count { item -> item.status == com.example.placemate.data.local.entities.ItemStatus.TAKEN } }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val recentItems: StateFlow<List<ItemUiModel>> = repository.getAllItems()
        .map { entities -> 
            entities.take(5).map { ItemUiModel(it, 1) }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
