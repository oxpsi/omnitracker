package com.jonny.healthtrack.ai

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

object AiAnalysisStatus {
    const val PENDING = "pending"
    const val COMPLETE = "complete"
    const val ERROR = "error"
}

data class AiAnalysisComponent(
    val name: String? = null,
    val unit: String? = null,
    val quantity: Double? = null
)

data class AiAnalysisResult(
    val title: String? = null,
    val type: String? = null,
    val components: List<AiAnalysisComponent> = emptyList()
)

fun parseAiAnalysis(json: String?): AiAnalysisResult? {
    if (json.isNullOrBlank()) return null
    return try {
        Gson().fromJson(json, AiAnalysisResult::class.java)
    } catch (_: JsonSyntaxException) {
        null
    }
}
