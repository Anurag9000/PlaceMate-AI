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


    private fun isOnline(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override suspend fun recognizeItem(imageUri: Uri): RecognitionResult = withContext(Dispatchers.IO) {
        if (!isOnline()) return@withContext RecognitionResult(null, null, 0f)
        val model = getModel() ?: return@withContext RecognitionResult(null, null, 0f)
        
        try {
            val bitmap = loadBitmap(imageUri) ?: return@withContext RecognitionResult(null, null, 0f)
            
            val prompt = """
                Analyze this image of a single object. 
                Identify the item and provide a suggested name and its broad category.
                Also determine if this object is a container (like a shelf, box, or cabinet).
                Return ONLY a JSON object:
                {"name": "string", "category": "string", "isContainer": boolean, "confidence": float}
                If nothing is found, return {"name": "Unknown", "category": "Misc", "isContainer": false, "confidence": 0.0}
            """.trimIndent()

            val response = model.generateContent(content {
                image(bitmap)
                text(prompt)
            })

            val text = response.text ?: ""
            val jsonStr = extractJson(text)
            if (jsonStr.isEmpty()) return@withContext RecognitionResult("Unknown", "Misc", 0f)

            val json = JSONObject(jsonStr)
            val name = json.optString("name", "Unknown")
            val normalized = synonymManager.getRepresentativeName(name)
            
            RecognitionResult(
                suggestedName = normalized.replaceFirstChar { it.uppercase() },
                suggestedCategory = categoryManager.mapLabelToCategory(normalized),
                confidence = json.optDouble("confidence", 0.0).toFloat(),
                isContainer = categoryManager.isLabelContainer(normalized)
            )
        } catch (e: Exception) {
            android.util.Log.e("GeminiService", "Item recognition failed", e)
            RecognitionResult(null, null, 0f)
        }
    }

    override suspend fun recognizeScene(imageUri: Uri): SceneRecognitionResult = withContext(Dispatchers.IO) {
        if (!isOnline()) return@withContext SceneRecognitionResult(emptyList())
        val model = getModel() ?: return@withContext SceneRecognitionResult(emptyList())

        try {
            val bitmap = loadBitmap(imageUri) ?: return@withContext SceneRecognitionResult(emptyList())
            val width = bitmap.width
            val height = bitmap.height

            val prompt = """
                Extract ALL entities in this room/storage area. Hierarchize them.
                1. Identify the Room.
                2. Identify Furniture/Storage.
                3. Identify smaller items on/in them.
                
                Format as JSON:
                {
                  "objects": [
                    {
                      "label": "string",
                      "isContainer": boolean,
                      "confidence": float,
                      "quantity": number,
                      "parentLabel": "string",
                      "box_2d": [ymin, xmin, ymax, xmax] 
                    }
                  ]
                }
                Use coordinates 0-1000. If no items, return {"objects": []}.
            """.trimIndent()

            val response = model.generateContent(content {
                image(bitmap)
                text(prompt)
            })

            val text = response.text ?: ""
            val jsonStr = extractJson(text)
            if (jsonStr.isEmpty()) return@withContext SceneRecognitionResult(emptyList())

            val json = JSONObject(jsonStr)
            val jsonArray = json.optJSONArray("objects") ?: return@withContext SceneRecognitionResult(emptyList())
            
            val recognized = mutableListOf<RecognizedObject>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val label = obj.optString("label", "Unknown")
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
                    confidence = obj.optDouble("confidence", 0.0).toFloat(),
                    boundingBox = rect,
                    quantity = obj.optInt("quantity", 1),
                    parentLabel = obj.optString("parentLabel").takeIf { it.isNotEmpty() }
                ))
            }
            SceneRecognitionResult(recognized)
        } catch (e: Exception) {
            android.util.Log.e("GeminiService", "Scene recognition failed", e)
            SceneRecognitionResult(emptyList())
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
