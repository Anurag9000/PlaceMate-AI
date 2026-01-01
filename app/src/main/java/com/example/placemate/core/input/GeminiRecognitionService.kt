package com.example.placemate.core.input

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.placemate.core.utils.ConfigManager
import com.example.placemate.core.utils.SynonymManager
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        val rawModelName = configManager.getSelectedGeminiModel()
        // SDK might want "gemini-1.5-flash" but API returns "models/gemini-1.5-flash"
        val modelName = rawModelName.removePrefix("models/")
        
        return GenerativeModel(
            modelName = modelName,
            apiKey = apiKey,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
            }
        )
    }


    private fun isOnline(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override suspend fun recognizeItem(imageUri: Uri): RecognitionResult = withContext(Dispatchers.IO) {
        if (!isOnline()) return@withContext RecognitionResult(null, null, 0f, errorMessage = "Internet connection required for Gemini AI")
        val model = getModel() ?: return@withContext RecognitionResult(null, null, 0f, errorMessage = "Gemini API Key missing or invalid")
        
        try {
            val bitmap = loadBitmap(imageUri) ?: return@withContext RecognitionResult(null, null, 0f, errorMessage = "Failed to load image")
            
            val prompt = """
                SYSTEM: You are a high-precision object detection AI. 
                Identify the primary object in this image.
                - name: Highly specific name (e.g. "Mechanical Pencil", "Sony Headphones").
                - category: Choose from [ELECTRONICS, FURNITURE, CLOTHING, KITCHENWARE, TOOLS, DECOR, MISC].
                - isContainer: true if this is something that HOLDS other things (box, shelf, drawer, cabinet, tray, bowl, bag).
                - confidence: decimal 0.0 to 1.0.

                OUTPUT FORMAT: Return ONLY a raw JSON object. Do not include markdown formatting.
                {
                  "name": "string",
                  "category": "string",
                  "isContainer": boolean,
                  "confidence": number
                }
                If absolutely nothing is found, still return the schema with "Unknown" values.
            """.trimIndent()

            val response = model.generateContent(content {
                image(bitmap)
                text(prompt)
            })

            val text = response.text ?: ""
            android.util.Log.d("GeminiService", "Raw Response: $text")
            val jsonStr = extractJson(text)
            if (jsonStr.isEmpty()) return@withContext RecognitionResult(null, null, 0f, errorMessage = "Could not parse AI response")

            val json = JSONObject(jsonStr)
            val name = json.optString("name", "Unknown Item")
            if (name.equals("Unknown", true) || name.isEmpty()) {
                 return@withContext RecognitionResult(null, null, 0f, errorMessage = "AI identified this as 'Unknown'. Try a closer photo.")
            }
            
            val normalized = synonymManager.getRepresentativeName(name)
            
            RecognitionResult(
                suggestedName = normalized.replaceFirstChar { it.uppercase() },
                suggestedCategory = categoryManager.mapLabelToCategory(normalized),
                confidence = json.optDouble("confidence", 0.0).toFloat(),
                isContainer = json.optBoolean("isContainer", categoryManager.isLabelContainer(normalized))
            )
        } catch (e: Exception) {
            android.util.Log.e("GeminiService", "Item recognition failed", e)
            val msg = e.localizedMessage ?: "Unknown error"
            val userMsg = if (msg.contains("serialization", true) || msg.contains("404", true)) {
                "Model unavailable. Please select a different model in Settings."
            } else {
                "Connection failed: $msg"
            }
            RecognitionResult(null, null, 0f, errorMessage = userMsg)
        }
    }

    override suspend fun recognizeScene(imageUri: Uri): SceneRecognitionResult = withContext(Dispatchers.IO) {
        if (!isOnline()) return@withContext SceneRecognitionResult(emptyList(), "Internet connection required for Gemini AI")
        val model = getModel() ?: return@withContext SceneRecognitionResult(emptyList(), "Gemini API Key missing or invalid")

        try {
            val bitmap = loadBitmap(imageUri) ?: return@withContext SceneRecognitionResult(emptyList(), "Failed to load image")
            val width = bitmap.width
            val height = bitmap.height

            val prompt = """
                SYSTEM: Exhaustive Scene Intelligence Mode. 
                Analyze this room/storage area and extract EVERY piece of furniture and EVERY item.
                
                STEPS:
                1. Identify the Room type (e.g. "Living Room").
                2. Identify all storage (shelves, tables, desks, boxes, bins).
                3. Identify all items sitting on or inside that storage.
                4. Group identical items with a 'quantity' count.
                
                SCHEMA:
                {
                  "objects": [
                    {
                      "label": "Name",
                      "isContainer": boolean,
                      "confidence": number,
                      "quantity": number,
                      "parentLabel": "Storage it belongs to",
                      "box_2d": [ymin, xmin, ymax, xmax]
                    }
                  ]
                }
                
                Rules:
                - Coordinates 0-1000.
                - Return ONLY the JSON. No conversational text.
                - Accuracy and completeness are non-negotiable. Aim for at least 10 entries if the room is not empty.
            """.trimIndent()

            val response = model.generateContent(content {
                image(bitmap)
                text(prompt)
            })

            val text = response.text ?: ""
            android.util.Log.d("GeminiService", "Raw Scene Response: $text")
            val jsonStr = extractJson(text)
            if (jsonStr.isEmpty()) return@withContext SceneRecognitionResult(emptyList(), "AI returned invalid JSON format")

            val json = JSONObject(jsonStr)
            val jsonArray = json.optJSONArray("objects") ?: return@withContext SceneRecognitionResult(emptyList())
            
            if (jsonArray.length() == 0) {
                 return@withContext SceneRecognitionResult(emptyList(), "AI saw the photo but found 0 objects. Try better lighting.")
            }

            val recognized = mutableListOf<RecognizedObject>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val label = obj.optString("label", "Unknown Target")
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
                    isContainer = obj.optBoolean("isContainer", categoryManager.isLabelContainer(normalized)),
                    confidence = obj.optDouble("confidence", 0.0).toFloat(),
                    boundingBox = rect,
                    quantity = obj.optInt("quantity", 1),
                    parentLabel = obj.optString("parentLabel").takeIf { it.isNotEmpty() }
                ))
            }
            SceneRecognitionResult(recognized)
        } catch (e: Exception) {
            android.util.Log.e("GeminiService", "Scene recognition failed", e)
            SceneRecognitionResult(emptyList(), "Service Error: ${e.message}")
        }
    }

    private fun extractJson(text: String): String {
        val start = text.indexOf("{")
        val end = text.lastIndexOf("}")
        return if (start != -1 && end != -1 && end > start) {
            text.substring(start, end + 1)
        } else ""
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
