package com.jonny.healthtrack.ai.providers

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.jonny.healthtrack.BuildConfig
import com.jonny.healthtrack.ai.AiPrompts
import com.jonny.healthtrack.ai.OpenAiApiType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class OpenAIProvider(
    private val apiKey: String = BuildConfig.OPENAI_API_KEY,
    private val model: String = "gpt-4.1",
    private val apiType: OpenAiApiType = OpenAiApiType.RESPONSES
) : AiProvider {

    override suspend fun analyzeLog(imageFile: File?, note: String, userId: String, reasoningLevel: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(IllegalStateException("Missing OpenAI API key"))

        val base64Image = if (imageFile != null) {
            if (!imageFile.exists()) return@withContext Result.failure(IllegalArgumentException("Image file not found"))
            val imageBytes = imageFile.readBytes()
            Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        } else {
            null
        }
        val mimeType = if (imageFile != null) guessMimeType(imageFile) else null

        val (url, requestBody) = when (apiType) {
            OpenAiApiType.RESPONSES -> {
                URL("https://api.openai.com/v1/responses") to buildResponsesRequestBody(base64Image, mimeType, note, userId, reasoningLevel)
            }
            OpenAiApiType.COMPLETIONS -> {
                URL("https://api.openai.com/v1/chat/completions") to buildChatCompletionsRequestBody(base64Image, mimeType, note, userId, reasoningLevel)
            }
        }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            // Some models (e.g. larger reasoning models) can take a while to respond; avoid premature timeouts.
            readTimeout = 300_000
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

            val jsonText = when (apiType) {
                OpenAiApiType.RESPONSES -> extractJsonFromResponses(responseText)
                OpenAiApiType.COMPLETIONS -> extractJsonFromChatCompletions(responseText)
            }
            // Validate JSON
            JsonParser.parseString(jsonText)
            Result.success(jsonText.trim())
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection.disconnect()
        }
    }

    private fun buildChatCompletionsRequestBody(base64Image: String?, mimeType: String?, note: String, userId: String, reasoningLevel: String): String {
        val prompt = AiPrompts.getAnalysisPrompt(note)
        
        // Use shared schema
        val schemaStructure = AiPrompts.getAnalysisSchemaStructure()

        val jsonSchema = mapOf(
            "name" to "health_log_analysis",
            "schema" to schemaStructure,
            "strict" to true
        )

        val contentParts = buildList {
            add(mapOf("type" to "text", "text" to prompt))
            if (!base64Image.isNullOrBlank() && !mimeType.isNullOrBlank()) {
                add(
                    mapOf(
                        "type" to "image_url",
                        "image_url" to mapOf(
                            "url" to "data:$mimeType;base64,$base64Image"
                        )
                    )
                )
            }
        }

        val supportsReasoningParam = supportsReasoningEffort(model)
        
        // If not supported natively, inject into system prompt
        val systemInstruction = if (supportsReasoningParam) {
            "You are a health log analysis assistant."
        } else {
            "You are a health log analysis assistant. Please use $reasoningLevel reasoning effort for this analysis."
        }

        val request = mutableMapOf<String, Any>(
            "model" to model,
            "user" to userId,
            "messages" to listOf(
                mapOf(
                    "role" to "system",
                    "content" to systemInstruction
                ),
                mapOf(
                    "role" to "user",
                    "content" to contentParts
                )
            ),
            "response_format" to mapOf(
                "type" to "json_schema",
                "json_schema" to jsonSchema
            )
        )
        
        if (supportsReasoningParam) {
            request["reasoning_effort"] = reasoningLevel
        }

        return Gson().toJson(request)
    }

    private fun buildResponsesRequestBody(base64Image: String?, mimeType: String?, note: String, userId: String, reasoningLevel: String): String {
        val prompt = AiPrompts.getAnalysisPrompt(note)
        val schemaStructure = AiPrompts.getAnalysisSchemaStructure()

        val contentParts = buildList {
            add(mapOf("type" to "input_text", "text" to prompt))
            if (!base64Image.isNullOrBlank() && !mimeType.isNullOrBlank()) {
                add(
                    mapOf(
                        "type" to "input_image",
                        "image_url" to "data:$mimeType;base64,$base64Image"
                    )
                )
            }
        }

        val supportsReasoningParam = supportsReasoningEffort(model)
        val systemInstruction = if (supportsReasoningParam) {
            "You are a health log analysis assistant."
        } else {
            "You are a health log analysis assistant. Please use $reasoningLevel reasoning effort for this analysis."
        }

        val request = mutableMapOf<String, Any>(
            "model" to model,
            "instructions" to systemInstruction,
            "input" to listOf(
                mapOf(
                    "role" to "user",
                    "content" to contentParts
                )
            ),
            "text" to mapOf(
                "format" to mapOf(
                    "type" to "json_schema",
                    "name" to "health_log_analysis",
                    "schema" to schemaStructure,
                    "strict" to true
                )
            ),
            "safety_identifier" to userId
        )

        if (supportsReasoningParam) {
            request["reasoning"] = mapOf("effort" to reasoningLevel)
        }

        return Gson().toJson(request)
    }

    private fun extractJsonFromChatCompletions(responseText: String): String {
        val root = JsonParser.parseString(responseText).asJsonObject
        val choices = root.getAsJsonArray("choices")
        if (choices == null || choices.size() == 0) throw IllegalStateException("No choices returned")
        
        val message = choices[0].asJsonObject.getAsJsonObject("message")
        val content = message.get("content")?.asString
        
        if (content.isNullOrBlank()) throw IllegalStateException("Empty response content")
        return content
    }

    private fun extractJsonFromResponses(responseText: String): String {
        val root = JsonParser.parseString(responseText).asJsonObject
        val output = root.getAsJsonArray("output")
        if (output == null || output.size() == 0) throw IllegalStateException("No output returned")

        val builder = StringBuilder()
        output.forEach { itemEl ->
            val item = itemEl.asJsonObject
            if (item.get("type")?.asString != "message") return@forEach

            val content = item.getAsJsonArray("content") ?: return@forEach
            content.forEach { contentEl ->
                val contentObj = contentEl.asJsonObject
                if (contentObj.get("type")?.asString == "output_text") {
                    builder.append(contentObj.get("text")?.asString.orEmpty())
                }
            }
        }

        if (builder.isEmpty()) throw IllegalStateException("No output_text returned")
        return builder.toString()
    }

    private fun supportsReasoningEffort(model: String): Boolean {
        return model.startsWith("gpt-5") || model.startsWith("o")
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
