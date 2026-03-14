package com.example.placemate.data.repository

import androidx.room.withTransaction
import com.example.placemate.data.local.dao.InventoryDao
import com.example.placemate.data.local.dao.LocationDao
import com.example.placemate.data.local.dao.LocationWithCount
import com.example.placemate.data.local.entities.ItemEntity
import com.example.placemate.data.local.entities.LocationEntity
import com.example.placemate.data.local.entities.LocationType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

import com.example.placemate.data.local.entities.ItemPlacementEntity
import com.example.placemate.data.local.entities.ItemStatus
import com.example.placemate.data.local.entities.BorrowEventEntity
import com.example.placemate.ui.inventory.ExplorerItem

@Singleton
class InventoryRepository @Inject constructor(
    private val inventoryDao: InventoryDao,
    private val locationDao: LocationDao,
    private val trackingDao: com.example.placemate.data.local.dao.TrackingDao,
    private val reminderManager: com.example.placemate.core.notifications.ReminderManager,
    private val database: com.example.placemate.data.local.AppDatabase
) {
    fun getAllItems(): Flow<List<ItemEntity>> = inventoryDao.getAllItems()

    fun getItemCount(): Flow<Int> = inventoryDao.getItemCountFlow()
    
    fun getTakenItemCount(): Flow<Int> = inventoryDao.getTakenItemCountFlow()

    fun getTakenItems(): Flow<List<ItemEntity>> = inventoryDao.getTakenItemsFlow()

    fun getRecentItems(): Flow<List<ItemEntity>> = inventoryDao.getRecentItemsFlow()

    fun searchItems(query: String): Flow<List<ItemEntity>> = inventoryDao.searchItems(query)

    suspend fun getItemById(id: String): ItemEntity? = inventoryDao.getItemById(id)

    fun observeItemById(id: String): Flow<ItemEntity?> = kotlinx.coroutines.flow.flow {
        inventoryDao.getAllItems().collect { list ->
            emit(list.find { it.id == id })
        }
    }

    suspend fun saveItem(item: ItemEntity, locationId: String? = null) {
        database.withTransaction {
            inventoryDao.insertItem(item)
            locationId?.let {
                // Enforce single-location rule: Clear previous placements
                inventoryDao.deletePlacementsForItem(item.id)
                inventoryDao.insertPlacement(ItemPlacementEntity(item.id, it))
            }
        }
    }

    suspend fun deleteItem(item: ItemEntity) = inventoryDao.deleteItem(item)

    fun getAllLocations(): Flow<List<LocationEntity>> = locationDao.getAllLocations()

    fun getAllLocationsWithCounts(): Flow<List<LocationWithCount>> = inventoryDao.getAllLocationsWithCountsFlow()

    suspend fun saveLocation(location: LocationEntity) = locationDao.insertLocation(location)

    suspend fun updateLocation(location: LocationEntity) = locationDao.updateLocation(location)

    suspend fun getLocationPathForItem(itemId: String): String {
        val location = inventoryDao.getLocationForItem(itemId) ?: return "Root"
        return getLocationPath(location.id)
    }

    suspend fun getLocationForItem(itemId: String): LocationEntity? = inventoryDao.getLocationForItem(itemId)

    suspend fun getLocationPath(locationId: String): String {
        val path = mutableListOf<String>()
        var current: LocationEntity? = locationDao.getLocationById(locationId)
        var depth = 0
        val visited = mutableSetOf<String>()
        
        while (current != null && depth < 50) {
            if (visited.contains(current.id)) break // Cycle detected
            visited.add(current.id)
            
            path.add(0, current.name)
            current = current.parentId?.let { locationDao.getLocationById(it) }
            depth++
        }
        return path.joinToString(" > ")
    }
    suspend fun getItemsForLocation(locationId: String): List<ItemEntity> {
        return inventoryDao.getItemsForLocation(locationId)
    }

    suspend fun nukeData() {
        database.withTransaction {
            inventoryDao.deleteAllPlacements()
            inventoryDao.deleteAllItems()
            locationDao.deleteAllLocations()
        }
    }

    suspend fun getAllLocationsSync(): List<LocationEntity>? {
        return locationDao.getAllLocationsSync()
    }

    suspend fun getAllItemsSync(): List<ItemEntity> {
        return inventoryDao.getAllItemsSync()
    }

    suspend fun getExplorerContent(parentId: String?): List<ExplorerItem> {
        val folders = inventoryDao.getLocationsWithItemCountsSync(parentId).map { locWithCount ->
            ExplorerItem.Folder(locWithCount.location, locWithCount.itemCount)
        }

        val files = if (parentId != null) {
            inventoryDao.getItemsForLocation(parentId).map { item ->
                ExplorerItem.File(item)
            }
        } else {
            emptyList()
        }

        return folders + files
    }

    suspend fun addLocationSync(name: String, type: LocationType, parentId: String?, photoUri: String? = null): LocationEntity {
        val location = LocationEntity(name = name, type = type, parentId = parentId, photoUri = photoUri)
        locationDao.insertLocation(location)
        return location
    }

    suspend fun markItemAsTaken(item: ItemEntity, borrower: String, dueDate: Long?) {
        database.withTransaction {
            val updatedItem = item.copy(
                status = ItemStatus.TAKEN,
                updatedAt = System.currentTimeMillis()
            )
            inventoryDao.insertItem(updatedItem)
            
            val event = BorrowEventEntity(
                itemId = item.id,
                takenBy = borrower,
                dueAt = dueDate
            )
            trackingDao.insertBorrowEvent(event)
            reminderManager.scheduleReminder(item.id)
        }
    }

    suspend fun markItemAsReturned(item: ItemEntity) {
        database.withTransaction {
            val updatedItem = item.copy(
                status = com.example.placemate.data.local.entities.ItemStatus.PRESENT,
                updatedAt = System.currentTimeMillis()
            )
            inventoryDao.insertItem(updatedItem)
            
            val activeEvent = trackingDao.getActiveBorrowEvent(item.id)
            activeEvent?.let {
                val updatedEvent = it.copy(returnedAt = System.currentTimeMillis())
                trackingDao.updateBorrowEvent(updatedEvent)
            }
            reminderManager.cancelReminder(item.id)
        }
    }
}
