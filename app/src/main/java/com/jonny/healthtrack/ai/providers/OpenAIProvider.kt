package com.jonny.healthtrack.ai.providers

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.jonny.healthtrack.BuildConfig
import com.jonny.healthtrack.ai.AiPrompts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class OpenAIProvider(
    private val apiKey: String = BuildConfig.OPENAI_API_KEY,
    private val model: String = BuildConfig.OPENAI_MODEL
) : AiProvider {

    override suspend fun analyzeLog(imageFile: File, note: String, userId: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(IllegalStateException("Missing OpenAI API key"))
        if (!imageFile.exists()) return@withContext Result.failure(IllegalArgumentException("Image file not found"))

        val imageBytes = imageFile.readBytes()
        val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        val mimeType = guessMimeType(imageFile)

        val requestBody = buildRequestBody(base64Image, mimeType, note, userId)
        val url = URL("https://api.openai.com/v1/chat/completions")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
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
                return@withContext Result.failure(IllegalStateException("OpenAI error $responseCode: $responseText"))
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

    private fun buildRequestBody(base64Image: String, mimeType: String, note: String, userId: String): String {
        val prompt = AiPrompts.getAnalysisPrompt(note)
        
        // Use shared schema
        val schemaStructure = AiPrompts.getAnalysisSchemaStructure()

        val jsonSchema = mapOf(
            "name" to "health_log_analysis",
            "schema" to schemaStructure,
            "strict" to true
        )

        val request = mapOf(
            "model" to model,
            "user" to userId,
            "messages" to listOf(
                mapOf(
                    "role" to "system",
                    "content" to "You are a health log analysis assistant."
                ),
                mapOf(
                    "role" to "user",
                    "content" to listOf(
                        mapOf("type" to "text", "text" to prompt),
                        mapOf(
                            "type" to "image_url",
                            "image_url" to mapOf(
                                "url" to "data:$mimeType;base64,$base64Image"
                            )
                        )
                    )
                )
            ),
            "response_format" to mapOf(
                "type" to "json_schema",
                "json_schema" to jsonSchema
            )
        )
        return Gson().toJson(request)
    }

    private fun extractJsonText(responseText: String): String {
        val root = JsonParser.parseString(responseText).asJsonObject
        val choices = root.getAsJsonArray("choices")
        if (choices == null || choices.size() == 0) throw IllegalStateException("No choices returned")
        
        val message = choices[0].asJsonObject.getAsJsonObject("message")
        val content = message.get("content")?.asString
        
        if (content.isNullOrBlank()) throw IllegalStateException("Empty response content")
        return content
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
