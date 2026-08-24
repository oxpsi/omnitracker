package com.jonny.healthtrack.data

import java.io.File

/**
 * A single image to be sent to the AI for analysis, paired with a short label
 * describing its role so the model can attribute context correctly.
 */
data class LabeledImage(val file: File, val label: String)

/**
 * Builds the note text used as input for AI analysis.
 *
 * For logs linked to a recipe, the recipe's description is appended (with a
 * "From Batch:" header) so the model understands the full context of the batch
 * the portion was taken from. Logs without a recipe are returned unchanged.
 */
suspend fun buildAnalysisNote(log: LogEntity, recipeDao: RecipeDao): String {
    val base = log.note
    val recipeId = log.recipeId ?: return base
    val recipe = recipeDao.getRecipeById(recipeId) ?: return base
    val desc = recipe.description
    if (desc.isBlank()) return base
    return if (base.isNotBlank()) {
        "$base\n\nFrom Batch:\n$desc"
    } else {
        "From Batch:\n$desc"
    }
}

/**
 * Assembles the list of images to send to the AI for a given log entry.
 *
 * Order convention (matters for prompt clarity): the batch/recipe image is
 * provided first, the log entry image second. Each is assigned a fixed label
 * that is also surfaced to the model via [buildImageContextPreamble].
 *
 * - Dedupes by canonical path so a log created directly from a recipe (which
 *   aliases the recipe's image file as the log image) is sent only once,
 *   labelled as "Batch".
 * - Null-safe: missing or non-existent images are simply skipped.
 */
suspend fun buildAnalysisImages(log: LogEntity, recipeDao: RecipeDao): List<LabeledImage> {
    val images = mutableListOf<LabeledImage>()
    val seen = mutableListOf<String>()

    fun addIfValid(path: String, label: String) {
        if (path.isBlank()) return
        val file = File(path)
        val key = try { file.canonicalPath } catch (_: Exception) { file.absolutePath }
        if (key in seen) return
        if (!file.exists() || file.length() == 0L) return
        seen.add(key)
        images.add(LabeledImage(file, label))
    }

    // 1. Batch (recipe) image first.
    val recipe = log.recipeId?.let { recipeDao.getRecipeById(it) }
    if (recipe != null) {
        addIfValid(recipe.imagePath, "Batch")
    }
    // 2. Log entry image second.
    addIfValid(log.imagePath, "Log entry")

    return images
}

/**
 * Builds a short preamble naming each provided image by its 1-based index and
 * label, so the model understands which image plays which role (e.g. a batch /
 * nutrition-label reference vs. a portion photo for scale). Returns an empty
 * string when there are no images.
 */
fun buildImageContextPreamble(labels: List<String>): String {
    if (labels.isEmpty()) return ""
    return buildString {
        labels.forEachIndexed { index, label ->
            append("Image ").append(index + 1).append(": ").append(label).append('\n')
        }
        append('\n')
    }
}
