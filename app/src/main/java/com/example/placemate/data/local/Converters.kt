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
        return ItemStatus.valueOf(value)
    }

    @TypeConverter
    fun fromLocationType(type: LocationType): String {
        return type.name
    }

    @TypeConverter
    fun toLocationType(value: String): LocationType {
        return LocationType.valueOf(value)
    }
}
