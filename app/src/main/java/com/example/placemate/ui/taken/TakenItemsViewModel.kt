package com.example.placemate.ui.taken

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.placemate.data.repository.InventoryRepository
import com.example.placemate.ui.inventory.ExplorerItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class TakenItemsViewModel @Inject constructor(
    private val repository: InventoryRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    val takenItems: StateFlow<List<ExplorerItem>> = combine(
        repository.getTakenItems(),
        searchQuery
    ) { items, query ->
        val filtered = if (query.isBlank()) {
            items
        } else {
            items.filter { it.name.contains(query, ignoreCase = true) }
        }

        filtered.map { item ->
            val path = repository.getLocationPathForItem(item.id)
            ExplorerItem.File(item, path)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
