package com.jonny.healthtrack.ai

import android.content.Context

object AiPreferences {
    private const val PREFS_NAME = "ai_settings"
    private const val KEY_AI_ENABLED = "ai_enabled"
    private const val KEY_OPENAI_MODEL = "openai_model"

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
}
