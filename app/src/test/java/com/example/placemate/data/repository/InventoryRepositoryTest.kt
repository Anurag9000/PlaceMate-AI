package com.example.placemate.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.placemate.core.notifications.ReminderScheduler
import com.example.placemate.data.local.AppDatabase
import com.example.placemate.data.local.entities.ItemEntity
import com.example.placemate.data.local.entities.LocationEntity
import com.example.placemate.data.local.entities.LocationType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InventoryRepositoryTest {

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
    fun `saveItem keeps a single active placement when item is moved`() = runTest {
        val room = repository.addLocationSync("Office", LocationType.ROOM, null)
        val shelf = repository.addLocationSync("Shelf", LocationType.STORAGE, room.id)
        val drawer = repository.addLocationSync("Drawer", LocationType.STORAGE, room.id)
        val item = ItemEntity(name = "Notebook", category = "Office", description = null, photoUri = null)

        repository.saveItem(item, shelf.id)
        repository.saveItem(item.copy(category = "Stationery"), drawer.id)

        val shelfItems = repository.getItemsForLocation(shelf.id)
        val drawerItems = repository.getItemsForLocation(drawer.id)

        assertTrue(shelfItems.isEmpty())
        assertEquals(listOf(item.id), drawerItems.map { it.id })
        assertEquals("Office > Drawer", repository.getLocationPathForItem(item.id))
    }

    @Test
    fun `getLocationPath stops cleanly when locations contain a cycle`() = runTest {
        val room = LocationEntity(name = "Room", type = LocationType.ROOM, parentId = null)
        val box = LocationEntity(name = "Box", type = LocationType.STORAGE, parentId = room.id)
        database.locationDao().insertLocation(room)
        database.locationDao().insertLocation(box)
        database.locationDao().updateLocation(room.copy(parentId = box.id))

        val path = repository.getLocationPath(room.id)

        assertEquals("Box > Room", path)
    }

    private class NoOpReminderScheduler : ReminderScheduler {
        override fun scheduleReminder(itemId: String) = Unit

        override fun cancelReminder(itemId: String) = Unit
    }
}
