package com.jonny.healthtrack.ai

import android.content.Context
import com.jonny.healthtrack.ai.providers.AiProvider
import com.jonny.healthtrack.ai.providers.ChatCompletionsProvider
import com.jonny.healthtrack.util.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AiAnalysisService {

    private fun getProvider(context: Context): AiProvider {
        return ChatCompletionsProvider(
            apiKey = AiPreferences.getApiKey(context),
            model = AiPreferences.getModel(context),
            baseUrl = AiPreferences.getBaseUrl(context)
        )
    }

    suspend fun analyzeLog(context: Context, imageFiles: List<File>, note: String): Result<String> = withContext(Dispatchers.IO) {
        if (!AiPreferences.isEnabled(context)) {
            return@withContext Result.failure(IllegalStateException("AI analysis is disabled"))
        }
        val provider = getProvider(context)
        val userId = UserPreferences.getOrCreateUserId(context)
        val reasoningLevel = AiPreferences.getReasoningLevel(context)

        provider.analyzeLog(imageFiles, note, userId, reasoningLevel)
    }

    fun getActiveModelName(context: Context): String {
        val baseUrl = AiPreferences.getBaseUrl(context)
        val host = runCatching { java.net.URI(baseUrl).host }.getOrNull()?.removePrefix("www.") ?: baseUrl
        return "Chat Completions: ${AiPreferences.getModel(context)} @ $host"
    }
}
