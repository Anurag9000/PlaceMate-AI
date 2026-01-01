package com.example.placemate.data.di

import android.content.Context
import androidx.room.Room
import com.example.placemate.data.local.AppDatabase
import com.example.placemate.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "placemate.db"
        ).build()
    }

    @Provides
    fun provideInventoryDao(db: AppDatabase): InventoryDao = db.inventoryDao()

    @Provides
    fun provideLocationDao(db: AppDatabase): LocationDao = db.locationDao()

    @Provides
    fun provideTrackingDao(db: AppDatabase): TrackingDao = db.trackingDao()

    @Provides
    fun provideReminderDao(db: AppDatabase): ReminderDao = db.reminderDao()
}
