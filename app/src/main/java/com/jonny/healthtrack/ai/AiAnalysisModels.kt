package com.jonny.healthtrack.ai

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import java.util.Locale

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
    val components: List<AiAnalysisComponent> = emptyList(),
    @SerializedName("private")
    val isPrivate: Boolean = false
)

fun parseAiAnalysis(json: String?): AiAnalysisResult? {
    if (json.isNullOrBlank()) return null
    return try {
        Gson().fromJson(json, AiAnalysisResult::class.java)
    } catch (_: JsonSyntaxException) {
        null
    }
}

fun latestAiAnalysis(results: List<AiAnalysisResult>?): AiAnalysisResult? {
    return results?.lastOrNull()
}

fun isFoodAnalysis(result: AiAnalysisResult?): Boolean {
    val type = result?.type?.trim()?.lowercase(Locale.US)
    return type == "food"
}
