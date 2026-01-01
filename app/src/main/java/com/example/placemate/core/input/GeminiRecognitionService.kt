package com.example.placemate.core.input

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.placemate.core.utils.ConfigManager
import com.example.placemate.core.utils.SynonymManager
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiRecognitionService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configManager: ConfigManager,
    private val synonymManager: SynonymManager,
    private val categoryManager: com.example.placemate.core.utils.CategoryManager
) : ItemRecognitionService {

    private fun getModel(): GenerativeModel? {
        val apiKey = configManager.getGeminiApiKey() ?: return null
        return GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = apiKey
        )
    }

    override suspend fun recognizeItem(imageUri: Uri): RecognitionResult = withContext(Dispatchers.IO) {
        val model = getModel() ?: return@withContext RecognitionResult(null, null, 0f)
        
        try {
            val bitmap = loadBitmap(imageUri) ?: return@withContext RecognitionResult(null, null, 0f)
            
            val prompt = """
                Analyze this image of a single object. 
                Identify the item and provide a suggested name and its broad category.
                Also determine if this object is a container (like a shelf, box, or cabinet) that could hold other items.
                Return ONLY a JSON object in this format:
                {
                  "name": "string",
                  "category": "string",
                  "isContainer": boolean,
                  "confidence": float
                }
            """.trimIndent()

            val response = model.generateContent(content {
                image(bitmap)
                text(prompt)
            })

            val jsonStr = response.text?.replace("```json", "")?.replace("```", "")?.trim() ?: ""
            val json = JSONObject(jsonStr)
            
            val name = json.optString("name")
            val normalized = synonymManager.getRepresentativeName(name)
            
            RecognitionResult(
                suggestedName = normalized.replaceFirstChar { it.uppercase() },
                suggestedCategory = categoryManager.mapLabelToCategory(normalized),
                confidence = json.optDouble("confidence", 0.9).toFloat(),
                isContainer = categoryManager.isLabelContainer(normalized)
            )
        } catch (e: Exception) {
            RecognitionResult(null, null, 0f)
        }
    }

    override suspend fun recognizeScene(imageUri: Uri): SceneRecognitionResult = withContext(Dispatchers.IO) {
        val model = getModel() ?: return@withContext SceneRecognitionResult(emptyList())

        try {
            val bitmap = loadBitmap(imageUri) ?: return@withContext SceneRecognitionResult(emptyList())
            val width = bitmap.width
            val height = bitmap.height

            val prompt = """
                Extract ALL entities visible in this photo of a room or storage area. 
                Be EXHAUSTIVE. If there are 50 books, list them individually if possible, or provide a count.
                Identify the hierarchical relationship: what is "in" or "on" what.
                
                1. Identify the Room (e.g., "Living Room").
                2. Identify all Furniture and Storage (e.g., "Dining Table", "Sofa", "Bookshelf", "Drawer").
                3. Identify all smaller items on/in those objects (e.g., "Laptop", "Coffee Mug", "Book: The Great Gatsby").
                
                For each object, provide:
                - label: Descriptive name.
                - isContainer: True if it can hold other things.
                - quantity: Number of such items if grouped (default 1).
                - parentLabel: The name of the object it is sitting ON or INSIDE (e.g., "Dining Table", "Shelf"). Use the Room name as the ultimate parent.
                - box_2d: [ymin, xmin, ymax, xmax] in normalized coordinates (0-1000).
                
                Return ONLY a JSON object:
                {
                  "objects": [
                    {
                      "label": "string",
                      "isContainer": boolean,
                      "confidence": float,
                      "quantity": number,
                      "parentLabel": "string",
                      "box_2d": [number, number, number, number]
                    }
                  ]
                }
            """.trimIndent()

            val response = model.generateContent(content {
                image(bitmap)
                text(prompt)
            })

            val jsonStr = response.text?.replace("```json", "")?.replace("```", "")?.trim() ?: ""
            val json = JSONObject(jsonStr)
            val jsonArray = json.getJSONArray("objects")
            
            val recognized = mutableListOf<RecognizedObject>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val label = obj.getString("label")
                val normalized = synonymManager.getRepresentativeName(label)
                
                val boxArray = obj.optJSONArray("box_2d")
                val rect = if (boxArray != null && boxArray.length() == 4) {
                    val ymin = boxArray.getInt(0) * height / 1000
                    val xmin = boxArray.getInt(1) * width / 1000
                    val ymax = boxArray.getInt(2) * height / 1000
                    val xmax = boxArray.getInt(3) * width / 1000
                    android.graphics.Rect(xmin, ymin, xmax, ymax)
                } else null

                recognized.add(RecognizedObject(
                    label = normalized.replaceFirstChar { it.uppercase() },
                    isContainer = categoryManager.isLabelContainer(normalized),
                    confidence = obj.optDouble("confidence", 0.8).toFloat(),
                    boundingBox = rect,
                    quantity = obj.optInt("quantity", 1),
                    parentLabel = obj.optString("parentLabel").takeIf { it.isNotEmpty() }
                ))
            }
            SceneRecognitionResult(recognized)
        } catch (e: Exception) {
            SceneRecognitionResult(emptyList())
        }
    }

    private fun loadBitmap(uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            null
        }
    }
}
