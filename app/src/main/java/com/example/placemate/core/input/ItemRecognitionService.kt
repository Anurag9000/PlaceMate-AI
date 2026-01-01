package com.example.placemate.core.input

import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton

interface ItemRecognitionService {
    suspend fun recognizeItem(imageUri: Uri): RecognitionResult
}

data class RecognitionResult(
    val suggestedName: String?,
    val suggestedCategory: String?,
    val confidence: Float
)

@Singleton
class StubRecognitionService @Inject constructor() : ItemRecognitionService {
    override suspend fun recognizeItem(imageUri: Uri): RecognitionResult {
        // In a real app, this would use ML Kit or a Cloud API.
        // For the stub, we return a generic "Object" suggestion.
        return RecognitionResult(
            suggestedName = "Scanned Item",
            suggestedCategory = "Uncategorized",
            confidence = 0.5f
        )
    }
}
