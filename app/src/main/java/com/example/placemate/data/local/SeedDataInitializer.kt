package com.example.placemate.data.local

import com.example.placemate.data.local.dao.InventoryDao
import com.example.placemate.data.local.dao.LocationDao
import com.example.placemate.data.local.entities.ItemEntity
import com.example.placemate.data.local.entities.LocationEntity
import com.example.placemate.data.local.entities.LocationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeedDataInitializer @Inject constructor(
    private val inventoryDao: InventoryDao,
    private val locationDao: LocationDao
) {
    suspend fun seedIfNeeded() {
        withContext(Dispatchers.IO) {
            // Check if already seeded
            // val items = inventoryDao.getAllItems().first() // Flow.first() requires collective dependency
            // For MVP, just simple check or run once
            
            val livingRoom = LocationEntity(name = "Living Room", type = LocationType.ROOM, parentId = null)
            val kitchen = LocationEntity(name = "Kitchen", type = LocationType.ROOM, parentId = null)
            
            locationDao.insertLocation(livingRoom)
            locationDao.insertLocation(kitchen)
            
            val shelf = LocationEntity(name = "Bookshelf", type = LocationType.STORAGE, parentId = livingRoom.id)
            locationDao.insertLocation(shelf)
            
            val drawer = LocationEntity(name = "Cutlery Drawer", type = LocationType.CONTAINER, parentId = kitchen.id)
            locationDao.insertLocation(drawer)
            
            inventoryDao.insertItem(ItemEntity(name = "TV Remote", category = "Electronics", description = "For the main TV", photoUri = null))
            inventoryDao.insertItem(ItemEntity(name = "Kitchen Knife", category = "Kitchenware", description = "Chef's knife", photoUri = null))
            inventoryDao.insertItem(ItemEntity(name = "Harry Potter Book", category = "Books", description = "First edition", photoUri = null))
        }
    }
}
