package com.example.placemate.core.notifications

import android.content.Context
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Singleton
class ReminderManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: com.example.placemate.data.repository.SettingsRepository
) {
    fun scheduleReminder(itemId: String) {
        kotlinx.coroutines.MainScope().launch {
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
