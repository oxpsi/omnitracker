package com.jonny.healthtrack.ai

import android.content.Context

enum class OpenAiApiType {
    COMPLETIONS,
    RESPONSES
}

object AiPreferences {
    private const val PREFS_NAME = "ai_settings"
    private const val KEY_AI_ENABLED = "ai_enabled"
    private const val KEY_OPENAI_MODEL = "openai_model"
    private const val KEY_AI_REASONING_LEVEL = "ai_reasoning_level"
    private const val KEY_OPENAI_API_TYPE = "openai_api_type"

    private val openAiModelOptionsCompletions: List<String> = listOf(
        "gpt-4.1",
        "gpt-5-nano",
        "gpt-5-mini",
        "gpt-5.2"
    )

    private val openAiModelOptionsResponses: List<String> = listOf(
        "gpt-4.1",
        "gpt-5-nano",
        "gpt-5-mini",
        "gpt-5.2",
        "gpt-5.2-pro"
    )

    fun getOpenAiModelOptions(apiType: OpenAiApiType): List<String> {
        return when (apiType) {
            OpenAiApiType.COMPLETIONS -> openAiModelOptionsCompletions
            OpenAiApiType.RESPONSES -> openAiModelOptionsResponses
        }
    }

    val openAiApiTypeOptions: List<OpenAiApiType> = listOf(
        OpenAiApiType.RESPONSES,
        OpenAiApiType.COMPLETIONS
    )

    fun isEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AI_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AI_ENABLED, enabled).apply()
    }

    fun getReasoningLevel(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_AI_REASONING_LEVEL, "medium") ?: "medium"
    }

    fun setReasoningLevel(context: Context, level: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_AI_REASONING_LEVEL, level).apply()
    }

    fun getOpenAiApiType(context: Context): OpenAiApiType {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_OPENAI_API_TYPE, null)
        val parsed = stored?.let { runCatching { OpenAiApiType.valueOf(it) }.getOrNull() }
        if (parsed != null && parsed in openAiApiTypeOptions) return parsed
        return OpenAiApiType.RESPONSES
    }

    fun setOpenAiApiType(context: Context, apiType: OpenAiApiType) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val normalized = if (apiType in openAiApiTypeOptions) apiType else OpenAiApiType.RESPONSES
        prefs.edit().putString(KEY_OPENAI_API_TYPE, normalized.name).apply()

        // Ensure selected model is valid for the chosen API type.
        val options = getOpenAiModelOptions(normalized)
        val currentModel = prefs.getString(KEY_OPENAI_MODEL, null)
        if (currentModel == null || currentModel !in options) {
            prefs.edit().putString(KEY_OPENAI_MODEL, options.first()).apply()
        }
    }

    fun getOpenAiModel(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_OPENAI_MODEL, null)
        val options = getOpenAiModelOptions(getOpenAiApiType(context))
        if (stored != null && stored in options) return stored
        return options.first()
    }

    fun setOpenAiModel(context: Context, model: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val options = getOpenAiModelOptions(getOpenAiApiType(context))
        val normalized = if (model in options) model else options.first()
        prefs.edit().putString(KEY_OPENAI_MODEL, normalized).apply()
    }

    fun getEstimatedCost(context: Context): String {
        val model = getOpenAiModel(context)
        val reasoning = getReasoningLevel(context)
        val costModel = model.removeSuffix("-pro")
        
        // Fixed cost table per user specification
        val cost = when (costModel) {
            "gpt-4.1" -> 0.0037
            "gpt-5-nano" -> when (reasoning) {
                "low" -> 0.0003
                "medium" -> 0.0009
                "high" -> 0.0033
                else -> 0.0009
            }
            "gpt-5-mini" -> when (reasoning) {
                "low" -> 0.0017
                "medium" -> 0.0047
                "high" -> 0.0167
                else -> 0.0047
            }
            "gpt-5.2" -> when (reasoning) {
                "low" -> 0.0120
                "medium" -> 0.0330
                "high" -> 0.1170
                else -> 0.0330
            }
            else -> 0.0037 // Default fallback
        }
        
        return String.format("$%.4f / log", cost)
    }
}
