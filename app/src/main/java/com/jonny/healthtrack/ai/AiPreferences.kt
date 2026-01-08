package com.jonny.healthtrack.ai

import android.content.Context

object AiPreferences {
    private const val PREFS_NAME = "ai_settings"
    private const val KEY_AI_ENABLED = "ai_enabled"

    fun isEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AI_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AI_ENABLED, enabled).apply()
    }
}
