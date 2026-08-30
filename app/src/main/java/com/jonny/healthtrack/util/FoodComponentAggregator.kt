package com.jonny.healthtrack.util

import com.jonny.healthtrack.ai.latestAiAnalysis
import com.jonny.healthtrack.ai.isFoodAnalysis
import com.jonny.healthtrack.data.LogEntity
import java.util.Locale

data class ComponentSource(
    val logId: String,
    val quantity: Double
)

data class AggregatedComponent(
    val keyName: String,
    val displayName: String,
    val unit: String?,
    val quantity: Double,
    val sources: List<ComponentSource>
)

private val preferredComponentOrder: List<String> = listOf(
    "net weight",
    "energy",
    "protein",
    "carbohydrate",
    "total fat",
    "saturated fat",
    "dietary fiber",
    "soluble fiber",
    "insoluble fiber",
    "sugar",
    "sodium",
    "potassium",
    "cholesterol",
    "caffeine"
)

private val preferredComponentDisplayNames: Map<String, String> = mapOf(
    "net weight" to "Net Weight",
    "energy" to "Energy",
    "protein" to "Protein",
    "carbohydrate" to "Carbohydrate",
    "total fat" to "Total Fat",
    "saturated fat" to "Saturated Fat",
    "dietary fiber" to "Dietary Fiber",
    "soluble fiber" to "Soluble Fiber",
    "insoluble fiber" to "Insoluble Fiber",
    "sugar" to "Sugar",
    "sodium" to "Sodium",
    "potassium" to "Potassium",
    "cholesterol" to "Cholesterol",
    "caffeine" to "Caffeine"
)

private val caloricKcalPerGram: Map<String, Double> = mapOf(
    "protein" to 4.0,
    "carbohydrate" to 4.0,
    "total fat" to 9.0,
    "saturated fat" to 9.0,
    "sugar" to 4.0
)

fun caloricContributionPercent(component: AggregatedComponent, allComponents: List<AggregatedComponent>): Double? {
    val kcalPerGram = caloricKcalPerGram[component.keyName] ?: return null
    val energyComponent = allComponents.find { it.keyName == "energy" } ?: return null
    val totalKcal = energyComponent.quantity
    if (totalKcal <= 0.0) return null
    val contributionKcal = component.quantity * kcalPerGram
    return (contributionKcal / totalKcal) * 100.0
}

private fun String.toTitleCaseWords(): String {
    return split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ") { token ->
            token.split("-")
                .filter { it.isNotBlank() }
                .joinToString("-") { part ->
                    if (part.length == 1) part.uppercase(Locale.US)
                    else part.take(1).uppercase(Locale.US) + part.drop(1).lowercase(Locale.US)
                }
        }
}

fun aggregateFoodComponents(logs: List<LogEntity>): List<AggregatedComponent> {
    if (logs.isEmpty()) return emptyList()

    val componentMap = linkedMapOf<Pair<String, String?>, MutableList<ComponentSource>>()

    for (log in logs) {
        val analysis = latestAiAnalysis(log.analysisResults)
        if (!isFoodAnalysis(analysis)) continue

        for (component in analysis?.components.orEmpty()) {
            val name = component.name?.trim()?.lowercase(Locale.US)
            if (name.isNullOrBlank()) continue

            val unit = component.unit?.trim()?.lowercase(Locale.US)?.ifBlank { null }
            val quantity = component.quantity ?: continue
            val scaled = quantity * log.quantity

            val key = name to unit
            componentMap.getOrPut(key) { mutableListOf() }.add(ComponentSource(log.id, scaled))
        }
    }

    val rank = preferredComponentOrder.withIndex().associate { it.value to it.index }
    return componentMap
        .map { (key, sources) ->
            val keyName = key.first
            val totalQuantity = sources.sumOf { it.quantity }
            AggregatedComponent(
                keyName = keyName,
                displayName = preferredComponentDisplayNames[keyName] ?: keyName.toTitleCaseWords(),
                unit = key.second,
                quantity = totalQuantity,
                sources = sources
            )
        }
        .sortedWith(
            compareBy<AggregatedComponent> { rank[it.keyName] ?: Int.MAX_VALUE }
                .thenBy { it.keyName }
                .thenBy { it.unit ?: "" }
        )
}
