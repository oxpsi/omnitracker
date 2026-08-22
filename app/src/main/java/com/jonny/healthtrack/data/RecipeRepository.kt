package com.jonny.healthtrack.data

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecipeRepository(
    private val context: Context,
    private val recipeDao: RecipeDao,
    private val logDao: LogDao
) {

    val allRecipes: Flow<List<RecipeEntity>> = recipeDao.getAllRecipes()

    suspend fun getRecipeById(id: String): RecipeEntity? = recipeDao.getRecipeById(id)

    suspend fun addRecipe(recipe: RecipeEntity) {
        recipeDao.insertRecipe(recipe)
    }

    suspend fun updateRecipe(recipe: RecipeEntity) {
        recipeDao.updateRecipe(recipe)
    }

    suspend fun deleteRecipe(recipe: RecipeEntity) {
        recipeDao.deleteRecipe(recipe)
        if (recipe.imagePath.isNotEmpty()) {
            val recipeRefs = recipeDao.countByImagePath(recipe.imagePath)
            val logRefs = logDao.countByImagePath(recipe.imagePath)
            if (recipeRefs == 0 && logRefs == 0) {
                val file = File(recipe.imagePath)
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }

    fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("RECIPE_${timeStamp}_", ".jpg", storageDir)
    }
}
