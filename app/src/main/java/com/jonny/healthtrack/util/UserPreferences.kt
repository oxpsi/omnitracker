package com.jonny.healthtrack.util

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

object UserPreferences {
    private const val PREF_NAME = "user_prefs"
    private const val KEY_USER_ID = "user_id"

    fun getOrCreateUserId(context: Context): String {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val existingId = prefs.getString(KEY_USER_ID, null)
        
        return if (existingId != null) {
            existingId
        } else {
            val newId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_USER_ID, newId).apply()
            newId
        }
    }
}
