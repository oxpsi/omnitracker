package com.jonny.healthtrack.ai.providers

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.jonny.healthtrack.BuildConfig
import com.jonny.healthtrack.ai.AiPrompts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class GeminiProvider(
    private val apiKey: String = BuildConfig.GEMINI_API_KEY,
    private val model: String = BuildConfig.GEMINI_MODEL
) : AiProvider {

    override suspend fun analyzeLog(imageFile: File?, note: String, userId: String, reasoningLevel: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(IllegalStateException("Missing Gemini API key"))

        val base64Image = if (imageFile != null) {
            if (!imageFile.exists()) return@withContext Result.failure(IllegalArgumentException("Image file not found"))
            val imageBytes = imageFile.readBytes()
            Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        } else {
            null
        }
        val mimeType = if (imageFile != null) guessMimeType(imageFile) else null

        // Inject reasoning level instruction into the note/prompt as Gemini doesn't have a parameter for it in this API version
        val enhancedNote = "$note\n\n(Please apply $reasoningLevel reasoning effort for this analysis)"
        
        val requestBody = buildRequestBody(base64Image, mimeType, enhancedNote)
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 300_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", apiKey)
        }

        try {
            connection.outputStream.use { outputStream ->
                outputStream.write(requestBody.toByteArray())
            }

            val responseCode = connection.responseCode
            val responseText = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.readText()
                .orEmpty()

            if (responseCode !in 200..299) {
                return@withContext Result.failure(IllegalStateException("Gemini error $responseCode: $responseText"))
            }

            val jsonText = extractJsonText(responseText)
            // Validate JSON
            JsonParser.parseString(jsonText)
            Result.success(jsonText.trim())
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection.disconnect()
        }
    }

    private fun buildRequestBody(base64Image: String?, mimeType: String?, note: String): String {
        val prompt = AiPrompts.getAnalysisPrompt(note)

        val parts = buildList {
            add(mapOf("text" to prompt))
            if (!base64Image.isNullOrBlank() && !mimeType.isNullOrBlank()) {
                add(
                    mapOf(
                        "inlineData" to mapOf(
                            "mimeType" to mimeType,
                            "data" to base64Image
                        )
                    )
                )
            }
        }

        val request = mapOf(
            "contents" to listOf(
                mapOf(
                    "parts" to parts
                )
            ),
            "generationConfig" to mapOf(
                "responseMimeType" to "application/json",
                "responseJsonSchema" to AiPrompts.getAnalysisSchemaStructure()
            )
        )
        return Gson().toJson(request)
    }

    private fun extractJsonText(responseText: String): String {
        val root = JsonParser.parseString(responseText).asJsonObject
        val candidates = root.getAsJsonArray("candidates")
        if (candidates == null || candidates.size() == 0) throw IllegalStateException("No candidates returned")
        val content = candidates[0].asJsonObject.getAsJsonObject("content")
        val parts = content.getAsJsonArray("parts")
        if (parts == null || parts.size() == 0) throw IllegalStateException("No content parts returned")
        val text = parts[0].asJsonObject.get("text")?.asString
        if (text.isNullOrBlank()) throw IllegalStateException("Empty response text")
        return text
    }

    private fun guessMimeType(file: File): String {
        return when (file.extension.lowercase(Locale.US)) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
    }
}
