package com.example.placemate.core.di

import com.example.placemate.core.input.InputInterpreter
import com.example.placemate.core.input.StubInputInterpreter
import com.example.placemate.core.input.MLKitRecognitionService
import com.example.placemate.core.input.ItemRecognitionService
import dagger.Binds

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CoreModule {

    @Binds
    @Singleton
    abstract fun bindInputInterpreter(
        stubInputInterpreter: StubInputInterpreter
    ): InputInterpreter

    @Binds
    @Singleton
    abstract fun bindItemRecognitionService(
        service: MLKitRecognitionService
    ): ItemRecognitionService
}
