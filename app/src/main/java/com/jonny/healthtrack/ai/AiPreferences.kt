package com.jonny.healthtrack.ai

import android.content.Context

enum class OpenAiApiType {
    COMPLETIONS,
    RESPONSES
}

enum class AiModelPreset {
    LOW,
    HIGH
}

object AiPreferences {
    private const val PREFS_NAME = "ai_settings"
    private const val KEY_AI_ENABLED = "ai_enabled"
    private const val KEY_OPENAI_MODEL = "openai_model"
    private const val KEY_OPENAI_MODEL_LOW = "openai_model_low"
    private const val KEY_OPENAI_MODEL_HIGH = "openai_model_high"
    private const val KEY_AI_REASONING_LEVEL = "ai_reasoning_level"
    private const val KEY_AI_REASONING_LEVEL_LOW = "ai_reasoning_level_low"
    private const val KEY_AI_REASONING_LEVEL_HIGH = "ai_reasoning_level_high"
    private const val KEY_OPENAI_API_TYPE = "openai_api_type"
    private const val KEY_ACTIVE_PRESET = "active_preset"
    private const val KEY_OPENAI_WEB_SEARCH_ENABLED = "openai_web_search_enabled"

    // GPT-5.6 family: Luna (cost-sensitive), Terra (balanced), Sol (flagship).
    private val openAiModelOptionsCompletions: List<String> = listOf(
        "gpt-5.6-luna",
        "gpt-5.6-terra",
        "gpt-5.6-sol"
    )

    private val openAiModelOptionsResponses: List<String> = listOf(
        "gpt-5.6-luna",
        "gpt-5.6-terra",
        "gpt-5.6-sol"
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

    fun getActivePreset(context: Context): AiModelPreset {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_ACTIVE_PRESET, null)
        val parsed = stored?.let { runCatching { AiModelPreset.valueOf(it) }.getOrNull() }
        return parsed ?: AiModelPreset.HIGH
    }

    fun setActivePreset(context: Context, preset: AiModelPreset) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ACTIVE_PRESET, preset.name).apply()
    }

    fun isEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AI_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AI_ENABLED, enabled).apply()
    }

    fun isWebSearchEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_OPENAI_WEB_SEARCH_ENABLED, false)
    }

    fun setWebSearchEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_OPENAI_WEB_SEARCH_ENABLED, enabled).apply()
    }

    fun getReasoningLevel(context: Context, preset: AiModelPreset = getActivePreset(context)): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = reasoningKeyForPreset(preset)
        val stored = prefs.getString(key, null)
        if (stored in reasoningOptions) return stored ?: defaultReasoningForPreset(preset)

        val legacy = prefs.getString(KEY_AI_REASONING_LEVEL, null)
        val fallback = if (preset == AiModelPreset.HIGH && legacy in reasoningOptions) legacy else defaultReasoningForPreset(preset)
        val normalized = fallback ?: defaultReasoningForPreset(preset)
        prefs.edit().putString(key, normalized).apply()
        return normalized
    }

    fun setReasoningLevel(context: Context, level: String, preset: AiModelPreset = getActivePreset(context)) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val normalized = if (level in reasoningOptions) level else defaultReasoningForPreset(preset)
        prefs.edit()
            .putString(reasoningKeyForPreset(preset), normalized)
            .putString(KEY_AI_REASONING_LEVEL, normalized)
            .apply()
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
        val lowModel = prefs.getString(KEY_OPENAI_MODEL_LOW, null)
        val highModel = prefs.getString(KEY_OPENAI_MODEL_HIGH, null)
        if (lowModel == null || lowModel !in options) {
            prefs.edit().putString(KEY_OPENAI_MODEL_LOW, defaultModelForPreset(AiModelPreset.LOW, options)).apply()
        }
        if (highModel == null || highModel !in options) {
            prefs.edit().putString(KEY_OPENAI_MODEL_HIGH, defaultModelForPreset(AiModelPreset.HIGH, options)).apply()
        }
    }

    fun getOpenAiModel(context: Context, preset: AiModelPreset = getActivePreset(context)): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(modelKeyForPreset(preset), null)
        val options = getOpenAiModelOptions(getOpenAiApiType(context))
        if (stored != null && stored in options) return stored

        val legacy = prefs.getString(KEY_OPENAI_MODEL, null)
        val fallback = if (preset == AiModelPreset.HIGH && legacy != null && legacy in options) {
            legacy
        } else {
            defaultModelForPreset(preset, options)
        }
        val normalized = if (fallback in options) fallback else options.first()
        prefs.edit().putString(modelKeyForPreset(preset), normalized).apply()
        return normalized
    }

    fun setOpenAiModel(context: Context, model: String, preset: AiModelPreset = getActivePreset(context)) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val options = getOpenAiModelOptions(getOpenAiApiType(context))
        val normalized = if (model in options) model else options.first()
        prefs.edit()
            .putString(modelKeyForPreset(preset), normalized)
            .putString(KEY_OPENAI_MODEL, normalized)
            .apply()
    }

    fun getEstimatedCost(context: Context, preset: AiModelPreset = getActivePreset(context)): String {
        val model = getOpenAiModel(context, preset)
        val reasoning = getReasoningLevel(context, preset)

        // Published GPT-5.6 pricing per 1M tokens (input / output, USD)
        val (inputPricePerM, outputPricePerM) = when (model) {
            "gpt-5.6-luna" -> 0.20 to 1.20
            "gpt-5.6-terra" -> 2.0 to 12.0
            "gpt-5.6-sol" -> 5.0 to 30.0
            else -> 2.0 to 12.0
        }

        // Rough per-log token estimates for a photo + note analysis.
        // Reasoning tokens are billed as output tokens.
        val inputTokens = 1500
        val outputTokens = 600
        val reasoningTokens = when (reasoning) {
            "low" -> 800
            "medium" -> 3000
            "high" -> 12000
            else -> 3000
        }

        val cost = inputTokens / 1_000_000.0 * inputPricePerM +
            (outputTokens + reasoningTokens) / 1_000_000.0 * outputPricePerM

        return String.format("$%.4f / log (est.)", cost)
    }

    private val reasoningOptions = listOf("low", "medium", "high")

    private fun defaultReasoningForPreset(preset: AiModelPreset): String {
        return if (preset == AiModelPreset.LOW) "low" else "medium"
    }

    private fun modelKeyForPreset(preset: AiModelPreset): String {
        return if (preset == AiModelPreset.LOW) KEY_OPENAI_MODEL_LOW else KEY_OPENAI_MODEL_HIGH
    }

    private fun reasoningKeyForPreset(preset: AiModelPreset): String {
        return if (preset == AiModelPreset.LOW) KEY_AI_REASONING_LEVEL_LOW else KEY_AI_REASONING_LEVEL_HIGH
    }

    private fun defaultModelForPreset(preset: AiModelPreset, options: List<String>): String {
        return if (preset == AiModelPreset.LOW) {
            when {
                "gpt-5.6-luna" in options -> "gpt-5.6-luna"
                "gpt-5.6-terra" in options -> "gpt-5.6-terra"
                else -> options.first()
            }
        } else {
            when {
                "gpt-5.6-sol" in options -> "gpt-5.6-sol"
                "gpt-5.6-terra" in options -> "gpt-5.6-terra"
                else -> options.last()
            }
        }
    }
}
