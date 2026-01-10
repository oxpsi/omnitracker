package com.jonny.healthtrack.ai

import android.content.Context
import com.jonny.healthtrack.BuildConfig
import com.jonny.healthtrack.ai.providers.AiProvider
import com.jonny.healthtrack.ai.providers.GeminiProvider
import com.jonny.healthtrack.ai.providers.OpenAIProvider
import com.jonny.healthtrack.util.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AiAnalysisService(
    private val geminiApiKey: String = BuildConfig.GEMINI_API_KEY,
    private val geminiModel: String = BuildConfig.GEMINI_MODEL,
    private val openAiApiKey: String = BuildConfig.OPENAI_API_KEY
) {

    private fun getProvider(context: Context): AiProvider {
        // Simple strategy: Prefer OpenAI if key is present, otherwise fallback to Gemini
        return if (openAiApiKey.isNotBlank()) {
            OpenAIProvider(openAiApiKey, AiPreferences.getOpenAiModel(context))
        } else {
            GeminiProvider(geminiApiKey, geminiModel)
        }
    }

    suspend fun analyzeLog(context: Context, imageFile: File?, note: String): Result<String> = withContext(Dispatchers.IO) {
        val provider = getProvider(context)
        val userId = UserPreferences.getOrCreateUserId(context)
        val reasoningLevel = AiPreferences.getReasoningLevel(context)
        
        provider.analyzeLog(imageFile, note, userId, reasoningLevel)
    }
    
    // Helper to expose the active model name to the repository
    fun getActiveModelName(context: Context): String {
        return if (openAiApiKey.isNotBlank()) {
            "OpenAI: ${AiPreferences.getOpenAiModel(context)}"
        }
        else {
            "Gemini: $geminiModel"
        }
    }
}
