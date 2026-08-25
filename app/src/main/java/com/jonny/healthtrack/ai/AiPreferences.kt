package com.jonny.healthtrack.ai

import android.content.Context

object AiPreferences {
    private const val PREFS_NAME = "ai_settings"
    private const val KEY_AI_ENABLED = "ai_enabled"
    private const val KEY_BASE_URL = "chat_base_url"
    private const val KEY_API_KEY = "chat_api_key"
    private const val KEY_MODEL = "chat_model"
    private const val KEY_DISCOVERED_MODELS = "chat_discovered_models"
    private const val KEY_REASONING_LEVEL = "chat_reasoning_level"

    val DEFAULT_BASE_URL = "https://api.openai.com/v1"
    private val reasoningOptions = listOf("low", "medium", "high")
    private val defaultReasoning = "medium"

    fun isEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AI_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AI_ENABLED, enabled).apply()
    }

    fun getBaseUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_BASE_URL, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL
    }

    fun setBaseUrl(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_BASE_URL, url.trim()).apply()
    }

    fun getApiKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_API_KEY, null) ?: ""
    }

    fun setApiKey(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_API_KEY, key.trim()).apply()
    }

    fun getModel(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_MODEL, null)?.takeIf { it.isNotBlank() } ?: ""
    }

    fun setModel(context: Context, model: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_MODEL, model.trim()).apply()
    }

    fun getDiscoveredModels(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_DISCOVERED_MODELS, null) ?: return emptyList()
        return raw.split(',').map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }

    fun setDiscoveredModels(context: Context, models: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DISCOVERED_MODELS, models.distinct().joinToString(",")).apply()
    }

    fun getReasoningLevel(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_REASONING_LEVEL, null)
        return if (stored in reasoningOptions) stored!! else defaultReasoning
    }

    fun setReasoningLevel(context: Context, level: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val normalized = if (level in reasoningOptions) level else defaultReasoning
        prefs.edit().putString(KEY_REASONING_LEVEL, normalized).apply()
    }

    val reasoningOptionList: List<String> get() = reasoningOptions
}
