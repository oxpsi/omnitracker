package com.jonny.healthtrack.ai

import android.content.Context

object AiPreferences {
    private const val PREFS_NAME = "ai_settings"
    private const val KEY_AI_ENABLED = "ai_enabled"
    private const val KEY_OPENAI_MODEL = "openai_model"
    private const val KEY_AI_REASONING_LEVEL = "ai_reasoning_level"

    val openAiModelOptions: List<String> = listOf(
        "gpt-4.1",
        "gpt-5-nano",
        "gpt-5-mini",
        "gpt-5.2"
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

    fun getOpenAiModel(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_OPENAI_MODEL, null)
        if (stored != null && stored in openAiModelOptions) return stored
        return openAiModelOptions.first()
    }

    fun setOpenAiModel(context: Context, model: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val normalized = if (model in openAiModelOptions) model else openAiModelOptions.first()
        prefs.edit().putString(KEY_OPENAI_MODEL, normalized).apply()
    }

    fun getEstimatedCost(context: Context): String {
        val model = getOpenAiModel(context)
        val reasoning = getReasoningLevel(context)
        
        // Fixed cost table per user specification
        val cost = when (model) {
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
