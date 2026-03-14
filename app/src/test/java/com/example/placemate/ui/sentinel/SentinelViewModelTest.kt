package com.example.placemate.ui.sentinel

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.placemate.core.input.ItemRecognitionService
import com.example.placemate.core.input.RecognitionResult
import com.example.placemate.core.input.RecognizedObject
import com.example.placemate.core.input.SceneRecognitionResult
import com.example.placemate.core.input.VisualCandidate
import com.example.placemate.core.notifications.ReminderScheduler
import com.example.placemate.data.local.AppDatabase
import com.example.placemate.data.local.entities.ItemEntity
import com.example.placemate.data.local.entities.LocationType
import com.example.placemate.data.repository.InventoryRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SentinelViewModelTest {

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
    fun `buildAuditResults classifies matched missing and new items`() = runTest {
        val room = repository.addLocationSync("Kitchen", LocationType.ROOM, null)
        val mug = ItemEntity(name = "Coffee Mug", category = "Kitchen", description = null, photoUri = null)
        val spoon = ItemEntity(name = "Spoon", category = "Kitchen", description = null, photoUri = null)
        repository.saveItem(mug, room.id)
        repository.saveItem(spoon, room.id)

        val viewModel = SentinelViewModel(
            repository = repository,
            recognitionService = FakeRecognitionService(
                SceneRecognitionResult(
                    objects = listOf(
                        RecognizedObject("Coffee Mug", false, 0.98f),
                        RecognizedObject("Plate", false, 0.88f),
                        RecognizedObject("Shelf", true, 0.77f)
                    )
                )
            )
        )

        val results = viewModel.buildAuditResults(
            dbItems = repository.getItemsForLocation(room.id),
            sceneResult = SceneRecognitionResult(
                objects = listOf(
                    RecognizedObject("Coffee Mug", false, 0.98f),
                    RecognizedObject("Plate", false, 0.88f),
                    RecognizedObject("Shelf", true, 0.77f)
                )
            )
        ).associateBy { it.name }

        assertEquals(AuditStatus.MATCHED, results["Coffee Mug"]?.status)
        assertEquals(AuditStatus.MISSING, results["Spoon"]?.status)
        assertEquals(AuditStatus.NEW, results["Plate"]?.status)
    }

    private class FakeRecognitionService(
        private val sceneResult: SceneRecognitionResult
    ) : ItemRecognitionService {
        override suspend fun recognizeItem(
            imageUri: android.net.Uri,
            contextHint: String?
        ): RecognitionResult = RecognitionResult(null, null, 0f)

        override suspend fun recognizeScene(
            imageUri: android.net.Uri,
            contextHint: String?
        ): SceneRecognitionResult = sceneResult

        override suspend fun findVisualMatch(
            targetUri: android.net.Uri,
            candidates: List<VisualCandidate>
        ): String? = null
    }

    private class NoOpReminderScheduler : ReminderScheduler {
        override fun scheduleReminder(itemId: String) = Unit

        override fun cancelReminder(itemId: String) = Unit
    }
}
