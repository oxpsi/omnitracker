package com.jonny.healthtrack.util

import com.jonny.healthtrack.ai.latestAiAnalysis
import com.jonny.healthtrack.ai.isFoodAnalysis
import com.jonny.healthtrack.data.LogEntity
import java.util.Locale

data class AggregatedComponent(
    val name: String,
    val unit: String?,
    val quantity: Double
)

fun aggregateFoodComponents(logs: List<LogEntity>): List<AggregatedComponent> {
    if (logs.isEmpty()) return emptyList()

    val totals = linkedMapOf<Pair<String, String?>, Double>()

    for (log in logs) {
        val analysis = latestAiAnalysis(log.analysisResults)
        if (!isFoodAnalysis(analysis)) continue

        for (component in analysis?.components.orEmpty()) {
            val name = component.name?.trim()?.lowercase(Locale.US)
            if (name.isNullOrBlank()) continue

            val unit = component.unit?.trim()?.lowercase(Locale.US)?.ifBlank { null }
            val quantity = component.quantity ?: continue

            val key = name to unit
            totals[key] = (totals[key] ?: 0.0) + quantity
        }
    }

    return totals
        .map { (key, quantity) -> AggregatedComponent(name = key.first, unit = key.second, quantity = quantity) }
        .sortedWith(compareBy<AggregatedComponent> { it.name }.thenBy { it.unit ?: "" })
}
