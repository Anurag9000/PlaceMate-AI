package com.example.placemate.ui.inventory

import com.example.placemate.data.local.entities.ItemEntity
import com.example.placemate.data.local.entities.LocationEntity

sealed class ExplorerItem {
    data class Folder(val location: LocationEntity, val itemCount: Int) : ExplorerItem()
    data class File(val item: ItemEntity, val locationPath: String? = null) : ExplorerItem()
}
