package com.example.placemate.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class GeminiModel(
    val name: String, // e.g. "models/gemini-1.5-flash"
    val displayName: String, // e.g. "Gemini 1.5 Flash"
    val version: String,
    val description: String
)

@Singleton
class ModelRepository @Inject constructor() {

    suspend fun fetchAvailableModels(apiKey: String): List<GeminiModel> = withContext(Dispatchers.IO) {
        val models = mutableListOf<GeminiModel>()
        var connection: HttpURLConnection? = null
        
        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()

                val jsonResponse = JSONObject(response.toString())
                val modelsArray = jsonResponse.optJSONArray("models") ?: return@withContext emptyList()

                for (i in 0 until modelsArray.length()) {
                    val modelJson = modelsArray.getJSONObject(i)
                    val name = modelJson.optString("name")
                    val displayName = modelJson.optString("displayName")
                    val description = modelJson.optString("description")
                    val version = modelJson.optString("version")
                    val supportedMethods = modelJson.optJSONArray("supportedGenerationMethods")

                    // Filter relaxed: Show all models, let user decide or fail later if method unsupported.
                    // Often "generateContent" isn't explicitly listed in the lightweight response for some keys/regions.
                    models.add(GeminiModel(name, displayName, version, description))
                }
            } else {
                android.util.Log.e("ModelRepo", "Error fetching models: $responseCode")
            }
        } catch (e: Exception) {
            android.util.Log.e("ModelRepo", "Exception fetching models", e)
        } finally {
            connection?.disconnect()
        }
        
        return@withContext models.sortedByDescending { it.version }
    }
}
