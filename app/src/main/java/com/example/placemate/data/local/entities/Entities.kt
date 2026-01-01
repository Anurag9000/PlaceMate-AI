package com.example.placemate.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: String,
    val description: String?,
    val photoUri: String?,
    val status: ItemStatus = ItemStatus.PRESENT,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class ItemStatus {
    PRESENT, TAKEN, UNKNOWN
}

@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: LocationType,
    val parentId: String?,
    val createdAt: Long = System.currentTimeMillis()
)

enum class LocationType {
    ROOM, STORAGE, CONTAINER
}

@Entity(
    tableName = "item_placements",
    primaryKeys = ["itemId", "locationId"],
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("locationId")]
)
data class ItemPlacementEntity(
    val itemId: String,
    val locationId: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "borrow_events",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("itemId")]
)
data class BorrowEventEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val itemId: String,
    val takenBy: String = "Me",
    val takenAt: Long = System.currentTimeMillis(),
    val dueAt: Long?,
    val returnedAt: Long? = null,
    val note: String? = null
)

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("itemId")]
)
data class ReminderEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val itemId: String,
    val nextTriggerAt: Long,
    val cadenceHours: Int,
    val isEnabled: Boolean = true,
    val lastNotifiedAt: Long? = null
)
