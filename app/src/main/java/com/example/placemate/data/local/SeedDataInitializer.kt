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
            val itemCount = inventoryDao.getItemCount()
            val locationCount = locationDao.getAllLocationsSync().size
            
            // If we already have ANY data, don't seed again.
            if (itemCount > 0 || locationCount > 0) return@withContext
            
            // Locations with FIXED IDs for idempotency
            val livingRoom = LocationEntity(id = "seed_living_room", name = "Living Room", type = LocationType.ROOM, parentId = null)
            val kitchen = LocationEntity(id = "seed_kitchen", name = "Kitchen", type = LocationType.ROOM, parentId = null)
            val bedroom = LocationEntity(id = "seed_bedroom", name = "Bedroom", type = LocationType.ROOM, parentId = null)
            
            locationDao.insertLocation(livingRoom)
            locationDao.insertLocation(kitchen)
            locationDao.insertLocation(bedroom)

            // Sample Items
            val items = listOf(
                ItemEntity(id = "item_1", name = "MacBook Pro", category = "Electronics", description = "Work laptop", photoUri = null),
                ItemEntity(id = "item_2", name = "French Press", category = "Kitchen", description = "For morning coffee", photoUri = null),
                ItemEntity(id = "item_3", name = "Yoga Mat", category = "Fitness", description = "Relaxation", photoUri = null)
            )

            items.forEach { inventoryDao.insertItem(it) }

            // Placements
            inventoryDao.insertPlacement(com.example.placemate.data.local.entities.ItemPlacementEntity("item_1", "seed_living_room"))
            inventoryDao.insertPlacement(com.example.placemate.data.local.entities.ItemPlacementEntity("item_2", "seed_kitchen"))
            inventoryDao.insertPlacement(com.example.placemate.data.local.entities.ItemPlacementEntity("item_3", "seed_bedroom"))
        }
    }
}
