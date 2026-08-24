package com.jonny.healthtrack.util

import android.content.Context

enum class AppThemeColor(val key: String) {
    Green("green"),
    Blue("blue"),
    Red("red"),
    Purple("purple"),
    Orange("orange"),
    Teal("teal");

    companion object {
        fun fromKey(key: String): AppThemeColor = values().find { it.key == key } ?: Green
    }
}

object ThemePreferences {
    private const val PREF_NAME = "theme_prefs"
    private const val KEY_THEME_COLOR = "theme_color"

    fun getThemeColor(context: Context): AppThemeColor {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val key = prefs.getString(KEY_THEME_COLOR, AppThemeColor.Green.key)
        return AppThemeColor.fromKey(key ?: AppThemeColor.Green.key)
    }

    fun setThemeColor(context: Context, themeColor: AppThemeColor) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_COLOR, themeColor.key).apply()
    }
}

/**
 * Returns the theme's primary color as an ARGB int for use in non-Composable
 * code (e.g. rendering a thumbnail outline). Mirrors the values defined in
 * HealthTrackTheme so the outline matches the active color scheme.
 */
fun primaryColorArgb(themeColor: AppThemeColor, dark: Boolean): Int {
    val rgb = when (themeColor) {
        AppThemeColor.Green -> if (dark) 0xFF8BC34A else 0xFF4CAF50
        AppThemeColor.Blue -> if (dark) 0xFF64B5F6 else 0xFF2196F3
        AppThemeColor.Red -> if (dark) 0xFFE57373 else 0xFFF44336
        AppThemeColor.Purple -> if (dark) 0xFFBA68C8 else 0xFF9C27B0
        AppThemeColor.Orange -> if (dark) 0xFFFFB74D else 0xFFFF9800
        AppThemeColor.Teal -> if (dark) 0xFF4DB6AC else 0xFF009688
    }
    return (0xFF000000.toInt() or (rgb.toInt() and 0xFFFFFF))
}
