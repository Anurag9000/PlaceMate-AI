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

    private val genericLabels = setOf("home good", "furniture", "building", "rectangle", "shape", "material", "object", "product")

    private fun getSpecificLabel(labels: List<com.google.mlkit.vision.objects.DetectedObject.Label>): String {
        // Find first label that isn't in genericLabels
        val best = labels.find { !genericLabels.contains(it.text.lowercase()) }?.text
            ?: labels.firstOrNull()?.text ?: "Object"
        return best
    }

    override suspend fun recognizeItem(imageUri: Uri): RecognitionResult {
        return try {
            val image = InputImage.fromFilePath(context, imageUri)
            val labels: List<ImageLabel> = labeler.process(image).await()
            
            val specificLabels = labels.filter { !genericLabels.contains(it.text.lowercase()) }
            val bestLabelText = specificLabels.firstOrNull()?.text ?: labels.firstOrNull()?.text ?: "Item"
            
            val normalizedLabel = synonymManager.getRepresentativeName(bestLabelText)
            RecognitionResult(
                suggestedName = normalizedLabel.replaceFirstChar { it.uppercase() },
                suggestedCategory = mapLabelToCategory(normalizedLabel),
                confidence = labels.firstOrNull()?.confidence ?: 0f,
                isContainer = isLabelContainer(normalizedLabel)
            )
        } catch (e: Exception) {
            RecognitionResult(null, null, 0f)
        }
    }

    override suspend fun recognizeScene(imageUri: Uri): SceneRecognitionResult {
        return try {
            val image = InputImage.fromFilePath(context, imageUri)
            
            // 1. Get Scene Context
            val labels = labeler.process(image).await()
            val sceneContext = labels.find { label ->
                val text = label.text.lowercase()
                text.contains("room") || text.contains("kitchen") || text.contains("office") || text.contains("garage")
            }?.text?.replaceFirstChar { it.uppercase() }

            // 2. Get Specific Objects
            val detectedObjects: List<DetectedObject> = objectDetector.process(image).await()
            
            val recognized = mutableListOf<RecognizedObject>()
            
            sceneContext?.let {
                recognized.add(RecognizedObject(it, true, 0.9f))
            }

            for (obj in detectedObjects) {
                val labelText = getSpecificLabel(obj.labels)
                val normalized = synonymManager.getRepresentativeName(labelText)
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
        val lower = label.lowercase()
        return when {
            lower.contains("tool") || lower.contains("hammer") || lower.contains("screw") -> "Tools"
            lower.contains("book") || lower.contains("paper") || lower.contains("magazine") -> "Media"
            lower.contains("electronics") || lower.contains("phone") || lower.contains("laptop") || lower.contains("computer") -> "Electronics"
            lower.contains("furniture") || lower.contains("chair") || lower.contains("table") || lower.contains("desk") || lower.contains("sofa") -> "Furniture"
            lower.contains("kitchen") || lower.contains("cook") || lower.contains("food") || lower.contains("appliance") -> "Kitchen"
            lower.contains("toy") || lower.contains("game") -> "Leisure"
            lower.contains("clothing") || lower.contains("wear") || lower.contains("shoe") -> "Apparel"
            else -> "Decor & Misc"
        }
    }

    private fun isLabelContainer(label: String): Boolean {
        val lower = label.lowercase()
        return lower.contains("shelf") || 
               lower.contains("bookcase") ||
               lower.contains("cupboard") || 
               lower.contains("wardrobe") || 
               lower.contains("almirah") ||
               lower.contains("rack") ||
               lower.contains("drawer") ||
               lower.contains("fridge") || 
               lower.contains("refrigerator") || 
               lower.contains("table") || 
               lower.contains("desk") ||
               lower.contains("box") ||
               lower.contains("cabinet") ||
               lower.contains("storage")
    }
}
