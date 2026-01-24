package com.example.placemate.data.local

import androidx.room.TypeConverter
import com.example.placemate.data.local.entities.ItemStatus
import com.example.placemate.data.local.entities.LocationType

class Converters {
    @TypeConverter
    fun fromItemStatus(status: ItemStatus): String {
        return status.name
    }

    @TypeConverter
    fun toItemStatus(value: String): ItemStatus {
        return try {
            ItemStatus.valueOf(value)
        } catch (e: Exception) {
            ItemStatus.UNKNOWN
        }
    }

    @TypeConverter
    fun fromLocationType(type: LocationType): String {
        return type.name
    }

    @TypeConverter
    fun toLocationType(value: String): LocationType {
        return try {
            LocationType.valueOf(value)
        } catch (e: Exception) {
            LocationType.STORAGE // Fallback default
        }
    }
}
