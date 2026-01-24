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

    @Query("SELECT COUNT(*) FROM items")
    fun getItemCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM items WHERE status = 'TAKEN'")
    fun getTakenItemCountFlow(): Flow<Int>

    @Query("SELECT * FROM items WHERE status = 'TAKEN' ORDER BY updatedAt DESC")
    fun getTakenItemsFlow(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items ORDER BY updatedAt DESC LIMIT 5")
    fun getRecentItemsFlow(): Flow<List<ItemEntity>>

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

    @Query("""
        SELECT l.*, (SELECT COUNT(DISTINCT itemId) FROM item_placements WHERE locationId = l.id) as itemCount
        FROM locations l
        WHERE l.parentId IS :parentId OR (l.parentId IS NULL AND :parentId IS NULL)
    """)
    suspend fun getLocationsWithItemCountsSync(parentId: String?): List<LocationWithCount>

    @Query("""
        SELECT l.*, (SELECT COUNT(DISTINCT itemId) FROM item_placements WHERE locationId = l.id) as itemCount
        FROM locations l
    """)
    fun getAllLocationsWithCountsFlow(): Flow<List<LocationWithCount>>

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

    @Query("DELETE FROM item_placements WHERE itemId = :itemId")
    suspend fun deletePlacementsForItem(itemId: String)
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
