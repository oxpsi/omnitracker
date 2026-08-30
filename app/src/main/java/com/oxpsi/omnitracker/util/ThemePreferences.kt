package com.oxpsi.omnitracker.util

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
    private const val KEY_DARK_THEME = "dark_theme"

    fun getThemeColor(context: Context): AppThemeColor {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val key = prefs.getString(KEY_THEME_COLOR, AppThemeColor.Green.key)
        return AppThemeColor.fromKey(key ?: AppThemeColor.Green.key)
    }

    fun setThemeColor(context: Context, themeColor: AppThemeColor) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_COLOR, themeColor.key).apply()
    }

    fun isDarkTheme(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DARK_THEME, false)
    }

    fun setDarkTheme(context: Context, dark: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DARK_THEME, dark).apply()
    }
}
