package com.example.placemate.core.input

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.label.ImageLabel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MLKitRecognitionService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val synonymManager: com.example.placemate.core.utils.SynonymManager
) : ItemRecognitionService {

    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

    override suspend fun recognizeItem(imageUri: Uri): RecognitionResult {
        return try {
            val image = InputImage.fromFilePath(context, imageUri)
            val labels: List<ImageLabel> = labeler.process(image).await()
            
            if (labels.isNotEmpty()) {
                val bestLabel = labels[0]
                val normalizedLabel = synonymManager.getRepresentativeName(bestLabel.text)
                RecognitionResult(
                    suggestedName = normalizedLabel.replaceFirstChar { it.uppercase() },
                    suggestedCategory = mapLabelToCategory(normalizedLabel),
                    confidence = bestLabel.confidence
                )

            } else {
                RecognitionResult(null, null, 0f)
            }
        } catch (e: Exception) {
            RecognitionResult(null, null, 0f)
        }
    }

    private fun mapLabelToCategory(label: String): String {
        return when (label.lowercase()) {
            "tool", "screwdriver", "hammer" -> "Tools"
            "book", "paper" -> "Media"
            "furniture", "chair", "table" -> "Furniture"
            "electronics", "gadget", "phone" -> "Electronics"
            else -> "Household"
        }
    }
}
