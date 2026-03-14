package com.example.placemate.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.placemate.core.notifications.ReminderScheduler
import com.example.placemate.data.local.AppDatabase
import com.example.placemate.data.local.entities.ItemEntity
import com.example.placemate.data.local.entities.ItemStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InventoryRepositoryBorrowingTest {

    private lateinit var database: AppDatabase
    private lateinit var scheduler: RecordingReminderScheduler
    private lateinit var repository: InventoryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scheduler = RecordingReminderScheduler()
        repository = InventoryRepository(
            inventoryDao = database.inventoryDao(),
            locationDao = database.locationDao(),
            trackingDao = database.trackingDao(),
            reminderScheduler = scheduler,
            database = database
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `markItemAsTaken updates status creates borrow event and schedules reminder`() = runTest {
        val item = ItemEntity(name = "Projector", category = "Electronics", description = null, photoUri = null)
        repository.saveItem(item)

        repository.markItemAsTaken(item, borrower = "Alex", dueDate = 1234L)

        val updated = repository.getItemById(item.id)
        val event = database.trackingDao().getActiveBorrowEvent(item.id)

        assertEquals(ItemStatus.TAKEN, updated?.status)
        assertNotNull(event)
        assertEquals("Alex", event?.takenBy)
        assertEquals(1234L, event?.dueAt)
        assertEquals(listOf(item.id), scheduler.scheduled)
    }

    @Test
    fun `markItemAsReturned restores status closes borrow event and cancels reminder`() = runTest {
        val item = ItemEntity(name = "Soldering Iron", category = "Tools", description = null, photoUri = null)
        repository.saveItem(item)
        repository.markItemAsTaken(item, borrower = "Me", dueDate = null)

        val takenItem = repository.getItemById(item.id)!!
        repository.markItemAsReturned(takenItem)

        val updated = repository.getItemById(item.id)
        val event = database.trackingDao().getActiveBorrowEvent(item.id)

        assertEquals(ItemStatus.PRESENT, updated?.status)
        assertEquals(null, event)
        assertEquals(listOf(item.id), scheduler.cancelled)
    }

    private class RecordingReminderScheduler : ReminderScheduler {
        val scheduled = mutableListOf<String>()
        val cancelled = mutableListOf<String>()

        override fun scheduleReminder(itemId: String) {
            scheduled += itemId
        }

        override fun cancelReminder(itemId: String) {
            cancelled += itemId
        }
    }
}
