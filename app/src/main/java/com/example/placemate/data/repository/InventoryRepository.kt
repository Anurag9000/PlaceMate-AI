package com.example.placemate.data.repository

import com.example.placemate.data.local.dao.InventoryDao
import com.example.placemate.data.local.dao.LocationDao
import com.example.placemate.data.local.entities.ItemEntity
import com.example.placemate.data.local.entities.LocationEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InventoryRepository @Inject constructor(
    private val inventoryDao: InventoryDao,
    private val locationDao: LocationDao
) {
    fun getAllItems(): Flow<List<ItemEntity>> = inventoryDao.getAllItems()

    fun searchItems(query: String): Flow<List<ItemEntity>> = inventoryDao.searchItems(query)

    suspend fun getItemById(id: String): ItemEntity? = inventoryDao.getItemById(id)

    suspend fun saveItem(item: ItemEntity, locationId: String? = null) {
        inventoryDao.insertItem(item)
        locationId?.let {
            inventoryDao.insertPlacement(com.example.placemate.data.local.entities.ItemPlacementEntity(item.id, it))
        }
    }

    suspend fun deleteItem(item: ItemEntity) = inventoryDao.deleteItem(item)

    fun getAllLocations(): Flow<List<LocationEntity>> = locationDao.getAllLocations()

    suspend fun saveLocation(location: LocationEntity) = locationDao.insertLocation(location)

    suspend fun getLocationForItem(itemId: String): LocationEntity? = inventoryDao.getLocationForItem(itemId)

    suspend fun getLocationPath(locationId: String): String {
        val path = mutableListOf<String>()
        var current: LocationEntity? = locationDao.getLocationById(locationId)
        while (current != null) {
            path.add(0, current.name)
            current = current.parentId?.let { locationDao.getLocationById(it) }
        }
        return path.joinToString(" > ")
    }
}
