package com.example.placemate.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.placemate.data.local.dao.*
import com.example.placemate.data.local.entities.*

@Database(
    entities = [
        ItemEntity::class,
        LocationEntity::class,
        ItemPlacementEntity::class,
        BorrowEventEntity::class,
        ReminderEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun inventoryDao(): InventoryDao
    abstract fun locationDao(): LocationDao
    abstract fun trackingDao(): TrackingDao
    abstract fun reminderDao(): ReminderDao
}
