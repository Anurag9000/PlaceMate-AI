package com.example.placemate.core.notifications

import android.content.Context
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.example.placemate.data.repository.SettingsRepository

@Singleton
class ReminderManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun scheduleReminder(itemId: String) {
        scope.launch {
            val interval = settingsRepository.reminderCadenceHours.first()
            val data = workDataOf("itemId" to itemId)
            val request = PeriodicWorkRequestBuilder<BorrowReminderWorker>(interval.toLong(), TimeUnit.HOURS)
                .setInputData(data)
                .addTag("reminder_$itemId")
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "reminder_$itemId",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }

    fun cancelReminder(itemId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("reminder_$itemId")
    }
}
