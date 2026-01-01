package com.example.placemate.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.placemate.core.notifications.ReminderManager
import com.example.placemate.data.local.entities.ItemEntity
import com.example.placemate.data.local.entities.ItemStatus
import com.example.placemate.data.local.entities.BorrowEventEntity
import com.example.placemate.data.repository.InventoryRepository
import com.example.placemate.data.repository.TrackingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    private val trackingRepository: TrackingRepository,
    private val reminderManager: ReminderManager
) : ViewModel() {

    private val _item = MutableStateFlow<ItemEntity?>(null)
    val item: StateFlow<ItemEntity?> = _item

    private val _locationPath = MutableStateFlow<String?>(null)
    val locationPath: StateFlow<String?> = _locationPath

    fun loadItem(itemId: String) {
        viewModelScope.launch {
            val itemEntity = inventoryRepository.getItemById(itemId)
            _item.value = itemEntity
            
            itemEntity?.let {
                val location = inventoryRepository.getLocationForItem(it.id)
                _locationPath.value = location?.let { loc -> inventoryRepository.getLocationPath(loc.id) }
            }
        }
    }

    fun markAsTaken(borrower: String, dueDate: Long?) {
        val currentItem = _item.value ?: return
        viewModelScope.launch {
            val updatedItem = currentItem.copy(status = ItemStatus.TAKEN, updatedAt = System.currentTimeMillis())
            inventoryRepository.saveItem(updatedItem)
            
            val event = BorrowEventEntity(
                itemId = currentItem.id,
                takenBy = borrower,
                dueAt = dueDate
            )
            trackingRepository.saveBorrowEvent(event)
            reminderManager.scheduleReminder(currentItem.id)
            _item.value = updatedItem
        }
    }

    fun markAsReturned() {
        val currentItem = _item.value ?: return
        viewModelScope.launch {
            val updatedItem = currentItem.copy(status = ItemStatus.PRESENT, updatedAt = System.currentTimeMillis())
            inventoryRepository.saveItem(updatedItem)
            
            val activeEvent = trackingRepository.getActiveBorrowEventForItem(currentItem.id)
            activeEvent?.let {
                val updatedEvent = it.copy(returnedAt = System.currentTimeMillis())
                trackingRepository.updateBorrowEvent(updatedEvent)
            }
            reminderManager.cancelReminder(currentItem.id)
            _item.value = updatedItem
        }
    }

    fun deleteItem() {
        val currentItem = _item.value ?: return
        viewModelScope.launch {
            inventoryRepository.deleteItem(currentItem)
            _item.value = null
        }
    }
}
