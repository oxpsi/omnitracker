package com.oxpsi.omnitracker.ai

import android.content.Context
import com.oxpsi.omnitracker.ai.providers.AiProvider
import com.oxpsi.omnitracker.ai.providers.ChatCompletionsProvider
import com.oxpsi.omnitracker.util.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class AiAnalysisService {

    private fun getProvider(context: Context): ChatCompletionsProvider {
        return ChatCompletionsProvider(
            apiKey = AiPreferences.getApiKey(context),
            model = AiPreferences.getModel(context),
            baseUrl = AiPreferences.getBaseUrl(context),
            promptTemplate = AiPreferences.getCustomPrompt(context).takeIf { it.isNotBlank() }
        )
    }

    suspend fun analyzeLog(context: Context, imageFiles: List<File>, note: String, correlationId: String? = null): Result<String> = withContext(Dispatchers.IO) {
        if (!AiPreferences.isEnabled(context)) {
            return@withContext Result.failure(IllegalStateException("AI analysis is disabled"))
        }
        val provider = getProvider(context)
        correlationId?.let { activeProviders[it] = provider }
        try {
            val userId = UserPreferences.getOrCreateUserId(context)
            val reasoningLevel = AiPreferences.getReasoningLevel(context)
            provider.analyzeLog(imageFiles, note, userId, reasoningLevel)
        } finally {
            correlationId?.let { activeProviders.remove(it) }
        }
    }

    fun getActiveModelName(context: Context): String {
        val baseUrl = AiPreferences.getBaseUrl(context)
        val host = runCatching { java.net.URI(baseUrl).host }.getOrNull()?.removePrefix("www.") ?: baseUrl
        return "Chat Completions: ${AiPreferences.getModel(context)} @ $host"
    }

    companion object {
        private val activeProviders = ConcurrentHashMap<String, ChatCompletionsProvider>()

        /** Cancels any in-flight analysis associated with the given correlation id (usually the log id). */
        fun cancel(correlationId: String) {
            activeProviders[correlationId]?.cancel()
        }
    }
}
