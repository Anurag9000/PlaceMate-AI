package com.example.placemate.data.repository

import com.example.placemate.data.local.dao.TrackingDao
import com.example.placemate.data.local.entities.BorrowEventEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackingRepository @Inject constructor(
    private val trackingDao: TrackingDao
) {
    fun getActiveBorrowEvents(): Flow<List<BorrowEventEntity>> = trackingDao.getAllActiveBorrowEvents()

    suspend fun getActiveBorrowEventForItem(itemId: String): BorrowEventEntity? = 
        trackingDao.getActiveBorrowEvent(itemId)

    suspend fun saveBorrowEvent(event: BorrowEventEntity) = trackingDao.insertBorrowEvent(event)

    suspend fun updateBorrowEvent(event: BorrowEventEntity) = trackingDao.updateBorrowEvent(event)
}
