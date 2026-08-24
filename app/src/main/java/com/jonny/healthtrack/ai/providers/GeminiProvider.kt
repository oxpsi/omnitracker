package com.jonny.healthtrack.ai.providers

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.jonny.healthtrack.BuildConfig
import com.jonny.healthtrack.ai.AiPrompts
import com.jonny.healthtrack.util.encodeImageForAiUpload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class GeminiProvider(
    private val apiKey: String = BuildConfig.GEMINI_API_KEY,
    private val model: String = BuildConfig.GEMINI_MODEL
) : AiProvider {

    override suspend fun analyzeLog(imageFiles: List<File>, note: String, userId: String, reasoningLevel: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(IllegalStateException("Missing Gemini API key"))

        // Encode each provided image (downscaled/capped to a sane size) as JPEG.
        val encodedImages: List<Pair<String, String>> = imageFiles.mapNotNull { file ->
            if (!file.exists()) return@mapNotNull null
            val bytes = encodeImageForAiUpload(file) ?: return@mapNotNull null
            Base64.encodeToString(bytes, Base64.NO_WRAP) to "image/jpeg"
        }

        // Inject reasoning level instruction into the note/prompt as Gemini doesn't have a parameter for it in this API version
        val enhancedNote = "$note\n\n(Please apply $reasoningLevel reasoning effort for this analysis)"
        
        val requestBody = buildRequestBody(encodedImages, enhancedNote)
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

    private fun buildRequestBody(images: List<Pair<String, String>>, note: String): String {
        val prompt = AiPrompts.getAnalysisPrompt(note)

        val parts = buildList {
            add(mapOf("text" to prompt))
            for ((base64, mimeType) in images) {
                add(
                    mapOf(
                        "inlineData" to mapOf(
                            "mimeType" to mimeType,
                            "data" to base64
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
}
