package com.example.placemate.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.placemate.data.local.entities.ItemPlacementEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SeedDataInitializerTest {

    private lateinit var database: AppDatabase
    private lateinit var subject: SeedDataInitializer

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        subject = SeedDataInitializer(
            inventoryDao = database.inventoryDao(),
            locationDao = database.locationDao(),
            database = database
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `seedIfNeeded inserts default rooms items and placements once`() = runTest {
        subject.seedIfNeeded()
        subject.seedIfNeeded()

        val locations = database.locationDao().getAllLocationsSync()
        val items = database.inventoryDao().getAllItemsSync()

        assertEquals(3, locations.size)
        assertEquals(3, items.size)
        assertNotNull(database.inventoryDao().getLocationForItem("item_1"))
        assertEquals("Living Room", database.inventoryDao().getLocationForItem("item_1")?.name)
    }
}
