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
            
            // Locations
            val livingRoom = LocationEntity(name = "Living Room", type = LocationType.ROOM, parentId = null)
            val kitchen = LocationEntity(name = "Kitchen", type = LocationType.ROOM, parentId = null)
            val bedroom = LocationEntity(name = "Bedroom", type = LocationType.ROOM, parentId = null)
            val study = LocationEntity(name = "Study Room", type = LocationType.ROOM, parentId = null)
            
            locationDao.insertLocation(livingRoom)
            locationDao.insertLocation(kitchen)
            locationDao.insertLocation(bedroom)
            locationDao.insertLocation(study)
            
            val shelf = LocationEntity(name = "Bookshelf", type = LocationType.STORAGE, parentId = livingRoom.id)
            val almirah = LocationEntity(name = "Almirah", type = LocationType.STORAGE, parentId = bedroom.id)
            val studyTable = LocationEntity(name = "Study Table", type = LocationType.FURNITURE, parentId = study.id)
            val drawer = LocationEntity(name = "Cutlery Drawer", type = LocationType.CONTAINER, parentId = kitchen.id)

            locationDao.insertLocation(shelf)
            locationDao.insertLocation(almirah)
            locationDao.insertLocation(studyTable)
            locationDao.insertLocation(drawer)
            
            // Items
            val tvRemote = ItemEntity(name = "TV Remote", category = "Electronics", description = "For the main TV", photoUri = null)
            val harryPotter = ItemEntity(name = "Harry Potter Book", category = "Books", description = "First edition", photoUri = null)
            val laptop = ItemEntity(name = "Laptop", category = "Electronics", description = "Work Laptop", photoUri = null)
            val chair = ItemEntity(name = "Office Chair", category = "Furniture", description = "Ergonomic chair", photoUri = null)
            val jacket = ItemEntity(name = "Winter Jacket", category = "Clothing", description = "Blue Jacket", photoUri = null)
            
            inventoryDao.insertItem(tvRemote)
            inventoryDao.insertItem(harryPotter)
            inventoryDao.insertItem(laptop)
            inventoryDao.insertItem(chair)
            inventoryDao.insertItem(jacket)
            
            // Placements
            inventoryDao.insertPlacement(com.example.placemate.data.local.entities.ItemPlacementEntity(tvRemote.id, livingRoom.id))
            inventoryDao.insertPlacement(com.example.placemate.data.local.entities.ItemPlacementEntity(harryPotter.id, shelf.id))
            inventoryDao.insertPlacement(com.example.placemate.data.local.entities.ItemPlacementEntity(laptop.id, studyTable.id))
            inventoryDao.insertPlacement(com.example.placemate.data.local.entities.ItemPlacementEntity(chair.id, study.id))
            inventoryDao.insertPlacement(com.example.placemate.data.local.entities.ItemPlacementEntity(jacket.id, almirah.id))

        }
    }
}
