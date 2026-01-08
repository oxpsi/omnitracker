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
    private val openAiApiKey: String = BuildConfig.OPENAI_API_KEY,
    private val openAiModel: String = BuildConfig.OPENAI_MODEL
) {

    private fun getProvider(): AiProvider {
        // Simple strategy: Prefer OpenAI if key is present, otherwise fallback to Gemini
        return if (openAiApiKey.isNotBlank()) {
            OpenAIProvider(openAiApiKey, openAiModel)
        } else {
            GeminiProvider(geminiApiKey, geminiModel)
        }
    }

    suspend fun analyzeLog(context: Context, imageFile: File, note: String): Result<String> = withContext(Dispatchers.IO) {
        val provider = getProvider()
        val userId = UserPreferences.getOrCreateUserId(context)
        
        provider.analyzeLog(imageFile, note, userId)
    }
    
    // Helper to expose the active model name to the repository
    fun getActiveModelName(): String {
        return if (openAiApiKey.isNotBlank()) {
            "OpenAI: $openAiModel"
        }
        else {
            "Gemini: $geminiModel"
        }
    }
}
