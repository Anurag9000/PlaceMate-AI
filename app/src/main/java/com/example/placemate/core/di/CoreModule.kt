package com.example.placemate.core.di

import com.example.placemate.core.input.InputInterpreter
import com.example.placemate.core.input.StubInputInterpreter
import com.example.placemate.core.input.MLKitRecognitionService
import com.example.placemate.core.input.ItemRecognitionService
import com.example.placemate.core.input.GeminiRecognitionService
import com.example.placemate.core.utils.ConfigManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides
    @Singleton
    fun provideInputInterpreter(
        stubInputInterpreter: StubInputInterpreter
    ): InputInterpreter = stubInputInterpreter

    @Provides
    @Singleton
    fun provideItemRecognitionService(
        configManager: ConfigManager,
        geminiService: GeminiRecognitionService,
        mlKitService: MLKitRecognitionService
    ): ItemRecognitionService {
        return object : ItemRecognitionService {
            private fun getActiveService(): ItemRecognitionService {
                return if (configManager.isGeminiEnabled() && configManager.hasGeminiApiKey()) {
                    geminiService
                } else {
                    mlKitService
                }
            }

            override suspend fun recognizeItem(imageUri: android.net.Uri): com.example.placemate.core.input.RecognitionResult {
                return getActiveService().recognizeItem(imageUri)
            }

            override suspend fun recognizeScene(imageUri: android.net.Uri): com.example.placemate.core.input.SceneRecognitionResult {
                return getActiveService().recognizeScene(imageUri)
            }
        }
    }
}
