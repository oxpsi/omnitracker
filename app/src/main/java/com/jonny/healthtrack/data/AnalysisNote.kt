package com.jonny.healthtrack.data

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
