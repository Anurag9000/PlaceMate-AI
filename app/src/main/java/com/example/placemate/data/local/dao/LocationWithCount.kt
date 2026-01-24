package com.example.placemate.data.local.dao

import androidx.room.Embedded
import com.example.placemate.data.local.entities.LocationEntity

data class LocationWithCount(
    @Embedded val location: LocationEntity,
    val itemCount: Int
)
