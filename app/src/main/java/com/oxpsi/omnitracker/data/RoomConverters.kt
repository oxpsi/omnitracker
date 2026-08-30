package com.oxpsi.omnitracker.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.oxpsi.omnitracker.ai.AiAnalysisResult

object RoomConverters {
    private val gson = Gson()
    private val analysisListType = object : TypeToken<List<AiAnalysisResult>>() {}.type

    @TypeConverter
    fun analysisResultsToJson(value: List<AiAnalysisResult>?): String? {
        if (value.isNullOrEmpty()) return null
        return gson.toJson(value, analysisListType)
    }

    @TypeConverter
    fun jsonToAnalysisResults(value: String?): List<AiAnalysisResult>? {
        if (value.isNullOrBlank()) return null
        return try {
            val element = JsonParser.parseString(value)
            when {
                element.isJsonArray -> gson.fromJson(value, analysisListType)
                element.isJsonObject -> listOf(gson.fromJson(value, AiAnalysisResult::class.java))
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}
