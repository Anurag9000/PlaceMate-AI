package com.example.placemate.core.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.placemate.data.repository.InventoryRepository
import com.example.placemate.data.repository.TrackingRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import androidx.hilt.work.HiltWorker

@HiltWorker
class BorrowReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val inventoryRepository: InventoryRepository,
    private val trackingRepository: TrackingRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val itemId = inputData.getString("itemId") ?: return Result.failure()
        
        val item = inventoryRepository.getItemById(itemId)
        if (item == null || item.status == com.example.placemate.data.local.entities.ItemStatus.PRESENT) {
            return Result.success()
        }

        val activeEvent = trackingRepository.getActiveBorrowEventForItem(itemId)
        if (activeEvent == null) return Result.success()

        showNotification(applicationContext, item.name, activeEvent.takenBy)
        
        return Result.success()
    }

    private fun showNotification(context: Context, itemName: String, borrower: String) {
        // Simple notification logic (MVP)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "borrow_reminders"
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {

            val channel = android.app.NotificationChannel(channelId, "Borrow Reminders", android.app.NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("PlaceMate Reminder")
            .setContentText("$borrower still has the $itemName. Is it back yet?")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(itemName.hashCode(), notification)
    }
}
