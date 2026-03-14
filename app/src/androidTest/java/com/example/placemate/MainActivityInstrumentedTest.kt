package com.example.placemate

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.core.content.ContextCompat
import androidx.room.Room
import androidx.test.core.app.ActivityScenario.launch
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.navigation.findNavController
import com.example.placemate.core.utils.ConfigManager
import com.example.placemate.data.local.AppDatabase
import com.example.placemate.data.local.entities.ItemEntity
import com.example.placemate.data.local.entities.ItemPlacementEntity
import com.example.placemate.data.local.entities.ItemStatus
import com.example.placemate.data.local.entities.LocationEntity
import com.example.placemate.data.local.entities.LocationType
import com.example.placemate.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class)
class MainActivityInstrumentedTest {

    @Test
    fun appContext_usesExpectedPackageName() {
        val appContext = targetContext()
        assertEquals(APP_PACKAGE, appContext.packageName)
    }

    @Test
    fun mainActivity_launches() {
        launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity)
            }
        }
    }

    @Test
    fun addItemFlow_savesItemToDatabase() {
        val uniqueName = "UITest-${System.currentTimeMillis()}"

        launch(MainActivity::class.java).use { scenario ->
            dismissOnboardingIfPresent()
            navigateTo(scenario, R.id.nav_home)

            onView(withId(R.id.btn_home_add)).perform(click())
            onView(withId(R.id.name_edit_text)).perform(replaceText(uniqueName))
            closeSoftKeyboard()
            onView(withId(R.id.btn_save)).perform(click())

            onView(withText("Dashboard")).check(matches(isDisplayed()))
        }

        val db = openDb()
        try {
            val savedItem = runBlocking {
                db.inventoryDao().getAllItemsSync().find { it.name == uniqueName }
            }
            assertNotNull("Expected saved item '$uniqueName' to exist in Room DB", savedItem)
        } finally {
            db.close()
        }
    }

    @Test
    fun addItemLocationFlow_createsHierarchyAndPlacement() {
        val uniqueName = "HierItem-${System.currentTimeMillis()}"

        launch(MainActivity::class.java).use { scenario ->
            dismissOnboardingIfPresent()
            navigateTo(scenario, R.id.nav_home)

            onView(withId(R.id.btn_home_add)).perform(click())
            onView(withId(R.id.name_edit_text)).perform(replaceText(uniqueName))
            onView(withId(R.id.btn_edit_location)).perform(click())
            onView(withText("Set Hierarchical Location")).check(matches(isDisplayed()))
            onView(isAssignableFrom(android.widget.EditText::class.java)).perform(
                replaceText("Living Room > Drawer > Box")
            )
            onView(withText("Set")).perform(click())
            onView(withId(R.id.tv_selected_location)).check(
                matches(withText("Living Room > Drawer > Box"))
            )
            closeSoftKeyboard()
            onView(withId(R.id.btn_save)).perform(scrollTo(), click())
            assertTrue(waitUntil {
                val db = openDb()
                try {
                    runBlocking {
                        db.inventoryDao().getAllItemsSync().any { it.name == uniqueName }
                    }
                } finally {
                    db.close()
                }
            })
        }

        val db = openDb()
        try {
            val locations = runBlocking { db.locationDao().getAllLocationsSync() }
            val item = runBlocking { db.inventoryDao().getAllItemsSync().find { it.name == uniqueName } }
            val placement = item?.let { runBlocking { db.inventoryDao().getLocationForItem(it.id) } }

            assertNotNull(item)
            assertTrue(locations.any { it.name == "Living Room" && it.parentId == null })
            val room = locations.first { it.name == "Living Room" && it.parentId == null }
            assertTrue(locations.any { it.name == "Drawer" && it.parentId == room.id })
            val drawer = locations.first { it.name == "Drawer" && it.parentId == room.id }
            assertTrue(locations.any { it.name == "Box" && it.parentId == drawer.id })
            assertEquals("Box", placement?.name)
        } finally {
            db.close()
        }
    }

    @Test
    fun itemDetail_markTakenAndReturned_updatesStatus() {
        val db = openDb()
        val item = ItemEntity(
            id = "ui-item-${System.currentTimeMillis()}",
            name = "Loaner Drill",
            category = "Tools",
            description = "instrumented",
            photoUri = null
        )

        try {
            runBlocking { db.inventoryDao().insertItem(item) }

            launch(MainActivity::class.java).use { scenario ->
                dismissOnboardingIfPresent()
                navigateTo(scenario, R.id.nav_item_detail, Bundle().apply { putString("itemId", item.id) })

                onView(withId(R.id.btn_action)).perform(scrollTo(), click())
                onView(withId(R.id.borrower_edit_text)).perform(replaceText("QAUser"))
                closeSoftKeyboard()
                onView(withText(R.string.btn_save)).perform(click())
                onView(withId(R.id.text_item_status)).check(matches(withText("TAKEN")))

                onView(withId(R.id.btn_action)).perform(scrollTo(), click())
                onView(withId(R.id.text_item_status)).check(matches(withText("PRESENT")))
            }

            val updated = runBlocking { db.inventoryDao().getItemById(item.id) }
            assertNotNull(updated)
            assertEquals(ItemStatus.PRESENT, updated?.status)
        } finally {
            db.close()
        }
    }

    @Test
    fun itemDetail_deleteItem_removesFromDatabase() {
        val db = openDb()
        val item = ItemEntity(
            id = "delete-item-${System.currentTimeMillis()}",
            name = "Delete Me",
            category = "Tests",
            description = "instrumented delete",
            photoUri = null
        )

        try {
            runBlocking { db.inventoryDao().insertItem(item) }

            launch(MainActivity::class.java).use { scenario ->
                dismissOnboardingIfPresent()
                navigateTo(scenario, R.id.nav_home)
                navigateTo(scenario, R.id.nav_item_detail, Bundle().apply { putString("itemId", item.id) })

                onView(withId(R.id.btn_delete)).perform(scrollTo(), click())
                onView(withText("Delete Item")).check(matches(isDisplayed()))
                onView(withText("Delete")).perform(click())
                onView(withText("Dashboard")).check(matches(isDisplayed()))
            }

            val deleted = runBlocking { db.inventoryDao().getItemById(item.id) }
            assertNull("Expected item to be deleted from Room DB", deleted)
        } finally {
            db.close()
        }
    }

    @Test
    fun settingsFlow_savesGeminiConfiguration() {
        val appContext = targetContext()
        val configManager = ConfigManager(appContext)
        val settingsRepository = SettingsRepository(appContext, configManager)
        val apiKey = "api-${System.currentTimeMillis()}"
        val prompt = "Prompt ${System.currentTimeMillis()}"

        launch(MainActivity::class.java).use { scenario ->
            dismissOnboardingIfPresent()
            navigateTo(scenario, R.id.nav_settings)

            onView(withId(R.id.switch_use_gemini)).perform(click())
            onView(withId(R.id.edit_api_key)).perform(replaceText(apiKey))
            onView(withId(R.id.edit_custom_prompt)).perform(scrollTo(), replaceText(prompt))
            closeSoftKeyboard()
            onView(withId(R.id.btn_save_settings)).perform(scrollTo(), click())
        }

        assertTrue(configManager.isGeminiEnabled())
        assertEquals(apiKey, configManager.getGeminiApiKey())
        assertEquals(prompt, configManager.getCustomGeminiPrompt())
        val cadence = runBlocking { settingsRepository.reminderCadenceHours.first() }
        assertTrue(cadence in 1..168)
    }

    @Test
    fun inventoryClearData_confirmationClearsDatabase() {
        val db = openDb()
        val room = LocationEntity(
            id = "clear-room-${System.currentTimeMillis()}",
            name = "Clear Room",
            type = LocationType.ROOM,
            parentId = null
        )
        val item = ItemEntity(
            id = "clear-item-${System.currentTimeMillis()}",
            name = "Clear Target",
            category = "Tests",
            description = null,
            photoUri = null
        )

        try {
            runBlocking {
                db.locationDao().insertLocation(room)
                db.inventoryDao().insertItem(item)
                db.inventoryDao().insertPlacement(ItemPlacementEntity(item.id, room.id))
            }

            launch(MainActivity::class.java).use { scenario ->
                dismissOnboardingIfPresent()
                navigateTo(scenario, R.id.nav_inventory)

                onView(withId(R.id.btn_clear_data)).perform(click())
                onView(withText("Clear All Data")).check(matches(isDisplayed()))
                onView(withText("Clear Everything")).perform(click())
                onView(withId(R.id.tv_empty_state)).check(matches(isDisplayed()))
                onView(withText("No items yet. Scan something!")).check(matches(isDisplayed()))
            }

            val remainingItems = runBlocking { db.inventoryDao().getAllItemsSync() }
            val remainingLocations = runBlocking { db.locationDao().getAllLocationsSync() }
            assertTrue(remainingItems.isEmpty())
            assertTrue(remainingLocations.isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun addItemCameraPermissionPrompt_canBeGrantedOnDevice() {
        revokePermission(Manifest.permission.CAMERA)
        assertEquals(PackageManager.PERMISSION_DENIED, permissionState(Manifest.permission.CAMERA))

        launch(MainActivity::class.java).use { scenario ->
            dismissOnboardingIfPresent()
            navigateTo(scenario, R.id.nav_add_item)
            onView(withId(R.id.btn_take_photo)).perform(click())
            assertTrue(allowPermissionDialogIfShown())
        }

        assertEquals(PackageManager.PERMISSION_GRANTED, permissionState(Manifest.permission.CAMERA))
    }

    @Test
    fun omniSearchMicrophonePermissionPrompt_canBeGrantedOnDevice() {
        revokePermission(Manifest.permission.RECORD_AUDIO)
        assertEquals(PackageManager.PERMISSION_DENIED, permissionState(Manifest.permission.RECORD_AUDIO))

        launch(MainActivity::class.java).use { scenario ->
            dismissOnboardingIfPresent()
            navigateTo(scenario, R.id.nav_omni_search)
            onView(withId(R.id.btn_voice_search)).perform(click())
            assertTrue(allowPermissionDialogIfShown())
        }

        assertEquals(PackageManager.PERMISSION_GRANTED, permissionState(Manifest.permission.RECORD_AUDIO))
    }

    private fun targetContext(): Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    private fun openDb(): AppDatabase {
        return Room.databaseBuilder(targetContext(), AppDatabase::class.java, "placemate.db")
            .allowMainThreadQueries()
            .build()
    }

    private fun dismissOnboardingIfPresent() {
        maybeClickText("Get Started")
        maybeClickText("Skip")
    }

    private fun navigateTo(
        scenario: androidx.test.core.app.ActivityScenario<MainActivity>,
        destinationId: Int,
        args: Bundle? = null
    ) {
        scenario.onActivity { activity ->
            activity.findNavController(R.id.nav_host_fragment_content_main).navigate(destinationId, args)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun permissionState(permission: String): Int {
        return ContextCompat.checkSelfPermission(targetContext(), permission)
    }

    private fun revokePermission(permission: String) {
        executeShellCommand("pm revoke $APP_PACKAGE $permission")
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun allowPermissionDialogIfShown(): Boolean {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val patterns = listOf(
            "(?i)while using the app",
            "(?i)only this time",
            "(?i)allow",
            "(?i)permit",
            "(?i)ok"
        )

        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            patterns.forEach { pattern ->
                val button = device.wait(Until.findObject(By.text(Pattern.compile(pattern))), 1_000)
                if (button != null) {
                    button.click()
                    device.waitForIdle()
                    return true
                }
            }
        }
        return false
    }

    private fun executeShellCommand(command: String) {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
            while (input.read() != -1) {
                // Drain the shell output so the command fully completes.
            }
        }
    }

    private fun maybeClickText(text: String) {
        try {
            onView(withText(text)).perform(click())
        } catch (_: Exception) {
        }
    }

    private fun waitUntil(timeoutMs: Long = 5_000, intervalMs: Long = 100, predicate: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) {
                return true
            }
            Thread.sleep(intervalMs)
        }
        return predicate()
    }

    private companion object {
        const val APP_PACKAGE = "com.example.placemate"
    }
}
