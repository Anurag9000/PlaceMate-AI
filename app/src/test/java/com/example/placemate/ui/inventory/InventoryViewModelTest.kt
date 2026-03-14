package com.example.placemate.ui.inventory

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.placemate.core.notifications.ReminderScheduler
import com.example.placemate.data.local.AppDatabase
import com.example.placemate.data.local.entities.LocationType
import com.example.placemate.data.repository.InventoryRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InventoryViewModelTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: InventoryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        repository = InventoryRepository(
            inventoryDao = database.inventoryDao(),
            locationDao = database.locationDao(),
            trackingDao = database.trackingDao(),
            reminderScheduler = NoOpReminderScheduler(),
            database = database
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `updateLocationDetails preserves photoUri and createdAt`() = runTest {
        val original = repository.addLocationSync(
            name = "Garage",
            type = LocationType.ROOM,
            parentId = null,
            photoUri = "content://placemate/garage.jpg"
        )

        repository.updateLocationDetails(original.id, "Workshop", LocationType.ROOM, null)

        val updated = repository.getLocationById(original.id)

        assertNotNull(updated)
        assertEquals("Workshop", updated?.name)
        assertEquals(original.photoUri, updated?.photoUri)
        assertEquals(original.createdAt, updated?.createdAt)
    }

    private class NoOpReminderScheduler : ReminderScheduler {
        override fun scheduleReminder(itemId: String) = Unit

        override fun cancelReminder(itemId: String) = Unit
    }
}
