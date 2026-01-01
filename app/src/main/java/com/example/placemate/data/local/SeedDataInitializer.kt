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
            val count = inventoryDao.getItemCount()
            if (count > 0) return@withContext
            
            // Locations
            val livingRoom = LocationEntity(name = "Living Room", type = LocationType.ROOM, parentId = null)
            val kitchen = LocationEntity(name = "Kitchen", type = LocationType.ROOM, parentId = null)
            val bedroom = LocationEntity(name = "Bedroom", type = LocationType.ROOM, parentId = null)
            
            locationDao.insertLocation(livingRoom)
            locationDao.insertLocation(kitchen)
            locationDao.insertLocation(bedroom)
        }
    }
}
