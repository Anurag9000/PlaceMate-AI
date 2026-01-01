package com.example.placemate.ui.taken

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.placemate.data.local.entities.ItemEntity
import com.example.placemate.data.local.entities.ItemStatus
import com.example.placemate.data.repository.InventoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class TakenItemsViewModel @Inject constructor(
    private val repository: InventoryRepository
) : ViewModel() {

    val takenItems: StateFlow<List<ItemEntity>> = repository.getAllItems()
        .map { items -> items.filter { it.status == ItemStatus.TAKEN } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
