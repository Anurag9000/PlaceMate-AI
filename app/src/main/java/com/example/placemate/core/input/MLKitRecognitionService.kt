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

import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.objects.DetectedObject

@Singleton
class MLKitRecognitionService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val synonymManager: com.example.placemate.core.utils.SynonymManager
) : ItemRecognitionService {

    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    private val objectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
    )

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
                    confidence = bestLabel.confidence,
                    isContainer = isLabelContainer(normalizedLabel)
                )

            } else {
                RecognitionResult(null, null, 0f)
            }
        } catch (e: Exception) {
            RecognitionResult(null, null, 0f)
        }
    }

    override suspend fun recognizeScene(imageUri: Uri): SceneRecognitionResult {
        return try {
            val image = InputImage.fromFilePath(context, imageUri)
            
            // 1. Get Scene Context (e.g. "Living Room") via Labeler
            val labels = labeler.process(image).await()
            val sceneContext = labels.find { label ->
                val text = label.text.lowercase()
                text.contains("room") || text.contains("kitchen") || text.contains("office") || text.contains("garage")
            }?.text?.replaceFirstChar { it.uppercase() }

            // 2. Get Specific Objects via Detector
            val detectedObjects: List<DetectedObject> = objectDetector.process(image).await()
            
            val recognized = mutableListOf<RecognizedObject>()
            
            // Add the scene context if found as a container
            sceneContext?.let {
                recognized.add(RecognizedObject(it, true, 0.9f)) // Treat room as root container
            }

            for (obj in detectedObjects) {
                val label = obj.labels.firstOrNull()?.text ?: "Object"
                val normalized = synonymManager.getRepresentativeName(label)
                recognized.add(RecognizedObject(
                    label = normalized.replaceFirstChar { it.uppercase() },
                    isContainer = isLabelContainer(normalized),
                    confidence = obj.labels.firstOrNull()?.confidence ?: 0.5f,
                    boundingBox = obj.boundingBox
                ))
            }
            SceneRecognitionResult(recognized)
        } catch (e: Exception) {
            SceneRecognitionResult(emptyList())
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

    private fun isLabelContainer(label: String): Boolean {
        val lower = label.lowercase()
        return lower.contains("shelf") || 
               lower.contains("bookcase") ||
               lower.contains("cupboard") || 
               lower.contains("wardrobe") || 
               lower.contains("fridge") || 
               lower.contains("refrigerator") || 
               lower.contains("table") || 
               lower.contains("desk") ||
               lower.contains("box") ||
               lower.contains("cabinet")
    }
}
