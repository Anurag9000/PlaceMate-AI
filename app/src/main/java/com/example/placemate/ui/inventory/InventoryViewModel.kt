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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val repository: InventoryRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val items: StateFlow<List<ItemEntity>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isEmpty()) {
                repository.getAllItems()
            } else {
                repository.searchItems(query)
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
}
