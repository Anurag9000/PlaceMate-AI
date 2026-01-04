package com.example.placemate.data.local.dao

import androidx.room.*
import com.example.placemate.data.local.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {
    @Query("SELECT * FROM items ORDER BY updatedAt DESC")
    fun getAllItems(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE id = :itemId")
    suspend fun getItemById(itemId: String): ItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ItemEntity)

    @Update
    suspend fun updateItem(item: ItemEntity)

    @Delete
    suspend fun deleteItem(item: ItemEntity)

    @Query("""
        WITH RECURSIVE
          matching_locations AS (
            -- Base case: locations that match the query directly
            SELECT id FROM locations WHERE name LIKE '%' || :query || '%'
            UNION ALL
            -- Recursive step: all children of matching locations
            SELECT l.id FROM locations l
            JOIN matching_locations ml ON l.parentId = ml.id
          )
        SELECT DISTINCT i.* FROM items i
        LEFT JOIN item_placements p ON i.id = p.itemId
        LEFT JOIN locations l ON p.locationId = l.id
        WHERE i.name LIKE '%' || :query || '%'
        OR i.category LIKE '%' || :query || '%'
        OR p.locationId IN matching_locations
    """)
    fun searchItems(query: String): Flow<List<ItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlacement(placement: ItemPlacementEntity)

    @Query("SELECT l.* FROM locations l JOIN item_placements p ON l.id = p.locationId WHERE p.itemId = :itemId LIMIT 1")
    suspend fun getLocationForItem(itemId: String): LocationEntity?

    @Query("SELECT i.* FROM items i JOIN item_placements p ON i.id = p.itemId WHERE p.locationId = :locationId")
    suspend fun getItemsForLocation(locationId: String): List<ItemEntity>

    @Query("SELECT COUNT(*) FROM items")
    suspend fun getItemCount(): Int

    @Query("DELETE FROM items")
    suspend fun deleteAllItems()

    @Query("DELETE FROM item_placements")
    suspend fun deleteAllPlacements()

    @Query("SELECT * FROM items")
    suspend fun getAllItemsSync(): List<ItemEntity>
}

@Dao
interface LocationDao {
    @Query("SELECT * FROM locations")
    fun getAllLocations(): Flow<List<LocationEntity>>


    @Query("SELECT * FROM locations")
    suspend fun getAllLocationsSync(): List<LocationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: LocationEntity)

    @Update
    suspend fun updateLocation(location: LocationEntity)

    @Delete
    suspend fun deleteLocation(location: LocationEntity)

    @Query("DELETE FROM locations")
    suspend fun deleteAllLocations()

    @Query("SELECT * FROM locations WHERE parentId = :parentId")
    fun getChildren(parentId: String): Flow<List<LocationEntity>>

    @Query("SELECT * FROM locations WHERE id = :id")
    suspend fun getLocationById(id: String): LocationEntity?
}

@Dao
interface TrackingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBorrowEvent(event: BorrowEventEntity)

    @Update
    suspend fun updateBorrowEvent(event: BorrowEventEntity)

    @Query("SELECT * FROM borrow_events WHERE itemId = :itemId AND returnedAt IS NULL")
    suspend fun getActiveBorrowEvent(itemId: String): BorrowEventEntity?

    @Query("SELECT * FROM borrow_events WHERE returnedAt IS NULL")
    fun getAllActiveBorrowEvents(): Flow<List<BorrowEventEntity>>
}

@Dao
interface ReminderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity)

    @Query("SELECT * FROM reminders WHERE itemId = :itemId")
    suspend fun getReminderForItem(itemId: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE isEnabled = 1 AND nextTriggerAt <= :currentTime")
    suspend fun getPendingReminders(currentTime: Long): List<ReminderEntity>
}
