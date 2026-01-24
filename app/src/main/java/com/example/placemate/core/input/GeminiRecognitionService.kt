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
import com.example.placemate.core.utils.CategoryManager
import com.example.placemate.core.utils.ImageUtils
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiRecognitionService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configManager: ConfigManager,
    private val synonymManager: SynonymManager,
    private val categoryManager: CategoryManager
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

    override suspend fun recognizeItem(imageUri: Uri, contextHint: String?): RecognitionResult = withContext(Dispatchers.IO) {
        if (!isOnline()) return@withContext RecognitionResult(null, null, 0f, errorMessage = "Internet connection required for Gemini AI")
        val model = getModel() ?: return@withContext RecognitionResult(null, null, 0f, errorMessage = "Gemini API Key missing or invalid")
        
        try {
            val bitmap = loadBitmap(imageUri) ?: return@withContext RecognitionResult(null, null, 0f, errorMessage = "Failed to load image")
            
            val basePrompt = """
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

            val prompt = if (!contextHint.isNullOrEmpty()) {
                "CONTEXT: The user has previously cataloged these items/locations: $contextHint.\n" +
                "If the object in the image looks like one of these, use that EXACT name.\n\n" +
                basePrompt
            } else {
                basePrompt
            }

            val response = model.generateContent(content {
                image(bitmap)
                text(prompt)
            })

            val text = response.text ?: ""
            val jsonStr = extractJson(text)
            if (jsonStr.isEmpty()) return@withContext RecognitionResult(null, null, 0f, errorMessage = "Could not parse AI response")

            val json = JSONObject(jsonStr)
            val name = json.optString("name", "Unknown Item")
            if (name.equals("Unknown", true) || name.isEmpty()) {
                 return@withContext RecognitionResult(null, null, 0f, errorMessage = "AI identified this as 'Unknown'. Try a closer photo.")
            }
            
            val isContextMatch = contextHint != null && contextHint.contains(name, ignoreCase = true)
            
            val normalized = if (isContextMatch) {
                name
            } else {
                synonymManager.getRepresentativeName(name)
            }
            
            RecognitionResult(
                suggestedName = normalized.replaceFirstChar { it.uppercase() },
                suggestedCategory = categoryManager.mapLabelToCategory(normalized),
                confidence = json.optDouble("confidence", 0.0).toFloat(),
                isContainer = json.optBoolean("isContainer", categoryManager.isLabelContainer(normalized))
            )
        } catch (e: Exception) {
            android.util.Log.e("GeminiService", "Item recognition failed", e)
            val msg = e.localizedMessage ?: "Unknown error"
            val userMsg = when {
                msg.contains("429", true) || msg.contains("quota", true) -> 
                    "AI Quota exceeded. Please wait a minute or switch to 'Gemini 1.5 Flash' in Settings for higher limits."
                msg.contains("serialization", true) || msg.contains("404", true) ->
                    "Model unavailable. Please select a different model in Settings."
                else -> "Connection failed: $msg"
            }
            RecognitionResult(null, null, 0f, errorMessage = userMsg)
        }
    }

    override suspend fun recognizeScene(imageUri: Uri, contextHint: String?): SceneRecognitionResult = withContext(Dispatchers.IO) {
        if (!isOnline()) return@withContext SceneRecognitionResult(emptyList(), "Internet connection required for Gemini AI")
        val model = getModel() ?: return@withContext SceneRecognitionResult(emptyList(), "Gemini API Key missing or invalid")

        try {
            val bitmap = loadBitmap(imageUri) ?: return@withContext SceneRecognitionResult(emptyList(), "Failed to load image")
            val width = bitmap.width
            val height = bitmap.height

            val basePrompt = configManager.getCustomGeminiPrompt()
            val prompt = if (!contextHint.isNullOrEmpty()) {
                "CONTEXT: The user has an existing inventory with these exact shelf/room names: [$contextHint].\n" +
                "TASK: Analyze the image. If the room or any container in the image appears to match one of the stored names, YOU MUST USE THAT EXACT NAME in your response.\n" +
                "Do NOT generate generic names (like 'Wooden Shelf') if a specific name (like 'Pantry Shelf A') from the list applies.\n\n" +
                basePrompt
            } else {
                basePrompt
            }

            val response = model.generateContent(content {
                image(bitmap)
                text(prompt)
            })

            val text = response.text ?: ""
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
                
                // If the raw label matches something in our context hint (user's existing items),
                // we should PREFER the raw label and NOT normalize it (which might turn "Pantry" -> "Kitchen").
                val isContextMatch = contextHint != null && contextHint.contains(label, ignoreCase = true)
                
                val finalLabel = if (isContextMatch) {
                    label // Keep exact name found by AI which matches user DB
                } else {
                    synonymManager.getRepresentativeName(label) // Normalize generic terms
                }
                
                val boxArray = obj.optJSONArray("box_2d")
                val rect = if (boxArray != null && boxArray.length() == 4) {
                    val ymin = boxArray.getInt(0) * height / 1000
                    val xmin = boxArray.getInt(1) * width / 1000
                    val ymax = boxArray.getInt(2) * height / 1000
                    val xmax = boxArray.getInt(3) * width / 1000
                    android.graphics.Rect(xmin, ymin, xmax, ymax)
                } else null

                recognized.add(RecognizedObject(
                    label = finalLabel.replaceFirstChar { it.uppercase() },
                    isContainer = obj.optBoolean("isContainer", categoryManager.isLabelContainer(finalLabel)),
                    confidence = obj.optDouble("confidence", 0.0).toFloat(),
                    boundingBox = rect,
                    quantity = obj.optInt("quantity", 1),
                    parentLabel = obj.optString("parentLabel").takeIf { it.isNotEmpty() }
                ))
            }
            SceneRecognitionResult(recognized)
        } catch (e: Exception) {
            android.util.Log.e("GeminiService", "Scene recognition failed", e)
            val msg = e.localizedMessage ?: "Unknown error"
            val userMsg = when {
                msg.contains("429", true) || msg.contains("quota", true) -> 
                    "AI Quota exceeded. Please wait a minute or switch to 'Gemini 1.5 Flash' in Settings for higher limits."
                else -> "Service Error: $msg"
            }
            SceneRecognitionResult(emptyList(), userMsg)
        }
    }

    override suspend fun findVisualMatch(targetUri: Uri, candidates: List<VisualCandidate>): String? = withContext(Dispatchers.IO) {
        if (!isOnline()) return@withContext null
        val model = getModel() ?: return@withContext null
        if (candidates.isEmpty()) return@withContext null

        try {
            val targetBitmap = loadBitmap(targetUri) ?: return@withContext null
            
            // Limit to top 5 candidates to prevent payload issues
            val topCandidates = candidates.take(5)
            
            val candidateBitmaps = topCandidates.mapNotNull { 
                loadBitmap(it.photoUri)?.let { bmp -> it to bmp } 
            }
            
            if (candidateBitmaps.isEmpty()) return@withContext null

            val prompt = """
                Comparing images to identify a specific physical object/location.
                TARGET IMAGE: The first image provided.
                CANDIDATE IMAGES: The subsequent images, labeled A, B, C, D, E...
                
                TASK: Look at the TARGET image. Does it appear to be the SAME physical object (e.g. same shelf, same box) as any of the CANDIDATE images?
                Ignore minor lighting/angle differences.
                
                OUTPUT: Return ONLY the JSON: {"match": "A" or "B" or "None", "confidence": 0.0-1.0}
            """.trimIndent()

            val contentBlock = content {
                text("TARGET IMAGE:")
                image(targetBitmap)
                
                text("\nCANDIDATE IMAGES:")
                candidateBitmaps.forEachIndexed { index, pair ->
                    val label = ('A' + index).toString()
                    text("\nImage $label (ID: ${pair.first.id}, Name: ${pair.first.name}):")
                    image(pair.second)
                }
                
                text("\n$prompt")
            }

            val response = model.generateContent(contentBlock)
            val jsonStr = extractJson(response.text ?: "")
            if (jsonStr.isEmpty()) return@withContext null
            
            val json = JSONObject(jsonStr)
            val matchLabel = json.optString("match", "None")
            val confidence = json.optDouble("confidence", 0.0)

            if (matchLabel != "None" && confidence > 0.7) {
                val index = matchLabel.firstOrNull()?.minus('A') ?: -1
                if (index in candidateBitmaps.indices) {
                    return@withContext candidateBitmaps[index].first.id
                }
            }
            return@withContext null

        } catch (e: Exception) {
            android.util.Log.e("GeminiService", "Visual match failed", e)
            return@withContext null
        }
    }

    private fun extractJson(text: String): String {
        val pattern = Regex("""\{.*\}""", RegexOption.DOT_MATCHES_ALL)
        return pattern.find(text)?.value ?: ""
    }

    private fun loadBitmap(uri: Uri): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { 
                BitmapFactory.decodeStream(it, null, options) 
            }
            
            // Downsample if larger than 1024px to save memory and payload
            options.inSampleSize = calculateInSampleSize(options, 1024, 1024)
            options.inJustDecodeBounds = false
            
            context.contentResolver.openInputStream(uri)?.use { 
                BitmapFactory.decodeStream(it, null, options) 
            }
        } catch (e: Exception) {
            android.util.Log.e("GeminiService", "Load bitmap failed", e)
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
