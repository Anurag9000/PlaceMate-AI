package com.example.placemate.ui.inventory

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.placemate.core.notifications.ReminderScheduler
import com.example.placemate.core.input.InputInterpreter
import com.example.placemate.core.input.InterpretedIntent
import com.example.placemate.core.input.ItemRecognitionService
import com.example.placemate.core.input.RecognitionResult
import com.example.placemate.core.input.SceneRecognitionResult
import com.example.placemate.core.input.SpeechManager
import com.example.placemate.core.input.UserInput
import com.example.placemate.core.input.VisualCandidate
import com.example.placemate.data.local.AppDatabase
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
class AddItemViewModelTest {

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
    fun `resolveLocationPath reuses matching hierarchy instead of duplicating locations`() = runTest {
        val room = repository.addLocationSync("Garage", LocationType.ROOM, null)
        val shelf = repository.addLocationSync("Shelf", LocationType.STORAGE, room.id)
        val viewModel = AddItemViewModel(
            repository = repository,
            inputInterpreter = NoOpInputInterpreter(),
            speechManager = SpeechManager(ApplicationProvider.getApplicationContext()),
            recognitionService = FakeRecognitionService(),
            savedStateHandle = SavedStateHandle()
        )

        val resolved = viewModel.resolveLocationPath(listOf("garage", "shelf"))

        val locations = repository.getAllLocationsSync().orEmpty()

        assertEquals(2, locations.size)
        assertEquals(shelf.id, resolved.id)
        assertEquals("Garage > Shelf", repository.getLocationPath(resolved.id))
    }

    private class NoOpInputInterpreter : InputInterpreter {
        override suspend fun interpret(input: UserInput): InterpretedIntent = InterpretedIntent.Unknown
    }

    private class FakeRecognitionService(
        private val result: RecognitionResult = RecognitionResult(null, null, 0f)
    ) : ItemRecognitionService {
        override suspend fun recognizeItem(imageUri: Uri, contextHint: String?): RecognitionResult = result

        override suspend fun recognizeScene(imageUri: Uri, contextHint: String?): SceneRecognitionResult =
            SceneRecognitionResult(emptyList())

        override suspend fun findVisualMatch(targetUri: Uri, candidates: List<VisualCandidate>): String? = null
    }

    private class NoOpReminderScheduler : ReminderScheduler {
        override fun scheduleReminder(itemId: String) = Unit

        override fun cancelReminder(itemId: String) = Unit
    }
}
