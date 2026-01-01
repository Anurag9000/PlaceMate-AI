package com.example.placemate.core.input

import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton

interface ItemRecognitionService {
    suspend fun recognizeItem(imageUri: Uri): RecognitionResult
    suspend fun recognizeScene(imageUri: Uri): SceneRecognitionResult
}

data class RecognitionResult(
    val suggestedName: String?,
    val suggestedCategory: String?,
    val confidence: Float,
    val isContainer: Boolean = false,
    val errorMessage: String? = null
)

data class RecognizedObject(
    val label: String,
    val isContainer: Boolean,
    val confidence: Float,
    val boundingBox: android.graphics.Rect? = null,
    val quantity: Int = 1,
    val parentLabel: String? = null
)

data class SceneRecognitionResult(
    val objects: List<RecognizedObject>,
    val errorMessage: String? = null
)

@Singleton
class StubRecognitionService @Inject constructor() : ItemRecognitionService {
    override suspend fun recognizeItem(imageUri: Uri): RecognitionResult {
        return RecognitionResult(
            suggestedName = "Scanned Item",
            suggestedCategory = "Uncategorized",
            confidence = 0.5f,
            isContainer = false
        )
    }

    override suspend fun recognizeScene(imageUri: Uri): SceneRecognitionResult {
        return SceneRecognitionResult(
            objects = listOf(
                RecognizedObject("Item 1", false, 0.9f),
                RecognizedObject("Shelf", true, 0.8f)
            )
        )
    }
}
