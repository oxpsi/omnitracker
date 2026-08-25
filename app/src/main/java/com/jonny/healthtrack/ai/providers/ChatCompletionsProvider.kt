package com.jonny.healthtrack.ai.providers

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.jonny.healthtrack.ai.AiPrompts
import com.jonny.healthtrack.util.encodeImageForAiUpload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ChatCompletionsProvider(
    private val apiKey: String = "",
    private val model: String = "",
    private val baseUrl: String = "https://api.openai.com/v1"
) : AiProvider {

    override suspend fun analyzeLog(imageFiles: List<File>, note: String, userId: String, reasoningLevel: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(IllegalStateException("Missing API key"))
        if (model.isBlank()) return@withContext Result.failure(IllegalStateException("No model selected"))

        val encodedImages: List<Pair<String, String>> = imageFiles.mapNotNull { file ->
            if (!file.exists()) return@mapNotNull null
            val bytes = encodeImageForAiUpload(file) ?: return@mapNotNull null
            Base64.encodeToString(bytes, Base64.NO_WRAP) to "image/jpeg"
        }

        val url = URL("${baseUrl.trimEnd('/')}/chat/completions")
        val requestBody = buildChatCompletionsRequestBody(encodedImages, note, userId, reasoningLevel)

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
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
                return@withContext Result.failure(IllegalStateException("Chat completions error $responseCode: $responseText"))
            }

            val jsonText = extractJsonFromChatCompletions(responseText)
            JsonParser.parseString(jsonText)
            Result.success(jsonText.trim())
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection.disconnect()
        }
    }

    private fun buildChatCompletionsRequestBody(images: List<Pair<String, String>>, note: String, userId: String, reasoningLevel: String): String {
        val prompt = AiPrompts.getAnalysisPrompt(note)
        val schemaStructure = AiPrompts.getAnalysisSchemaStructure()

        val jsonSchema = mapOf(
            "name" to "health_log_analysis",
            "schema" to schemaStructure,
            "strict" to true
        )

        val contentParts = buildList {
            add(mapOf("type" to "text", "text" to prompt))
            for ((base64, mimeType) in images) {
                add(
                    mapOf(
                        "type" to "image_url",
                        "image_url" to mapOf(
                            "url" to "data:$mimeType;base64,$base64"
                        )
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

    private fun extractJsonFromChatCompletions(responseText: String): String {
        val root = JsonParser.parseString(responseText).asJsonObject
        val choices = root.getAsJsonArray("choices")
        if (choices == null || choices.size() == 0) throw IllegalStateException("No choices returned")

        val message = choices[0].asJsonObject.getAsJsonObject("message")
        val content = message.get("content")?.asString

        if (content.isNullOrBlank()) throw IllegalStateException("Empty response content")
        return content
    }

    private fun supportsReasoningEffort(model: String): Boolean {
        return model.startsWith("gpt-5") || model.startsWith("o")
    }

    companion object {
        fun discoverModels(baseUrl: String, apiKey: String): Result<List<String>> {
            if (apiKey.isBlank()) return Result.failure(IllegalStateException("Missing API key"))
            val url = URL("${baseUrl.trimEnd('/')}/models")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 30_000
                readTimeout = 60_000
                setRequestProperty("Authorization", "Bearer $apiKey")
            }

            try {
                val responseCode = connection.responseCode
                val responseText = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()
                    ?.readText()
                    .orEmpty()

                if (responseCode !in 200..299) {
                    return Result.failure(IllegalStateException("Model discovery error $responseCode: $responseText"))
                }

                val root = JsonParser.parseString(responseText).asJsonObject
                val data = root.getAsJsonArray("data") ?: return Result.success(emptyList())
                val models = (0 until data.size()).mapNotNull { i ->
                    runCatching { data[i].asJsonObject.get("id")?.asString }.getOrNull()
                }.filter { it.isNotBlank() }.distinct().sorted()
                return Result.success(models)
            } catch (e: Exception) {
                return Result.failure(e)
            } finally {
                connection.disconnect()
            }
        }
    }
}
