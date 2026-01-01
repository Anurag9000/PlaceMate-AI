package com.example.placemate

import android.app.Application
import androidx.work.Configuration
import com.example.placemate.data.local.SeedDataInitializer
import androidx.hilt.work.HiltWorkerFactory
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltAndroidApp
class PlaceMateApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var seedDataInitializer: SeedDataInitializer

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Run seeding in global scope for simplicity in MVP
        MainScope().launch {
            seedDataInitializer.seedIfNeeded()
        }
    }
}
