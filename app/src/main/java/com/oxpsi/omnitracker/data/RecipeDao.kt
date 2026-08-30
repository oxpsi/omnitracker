package com.oxpsi.omnitracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes ORDER BY lastActivity DESC, createdAt DESC")
    fun getAllRecipes(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes WHERE id IN (:ids)")
    suspend fun getRecipesByIds(ids: List<String>): List<RecipeEntity>

    @Query("SELECT * FROM recipes ORDER BY lastActivity DESC, createdAt DESC")
    suspend fun getAllRecipesSnapshot(): List<RecipeEntity>

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getRecipeById(id: String): RecipeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipe(recipe: RecipeEntity)

    @Update
    suspend fun updateRecipe(recipe: RecipeEntity)

    @Query("UPDATE recipes SET lastActivity = :timestamp WHERE id = :id")
    suspend fun touchLastActivity(id: String, timestamp: Long)

    @Delete
    suspend fun deleteRecipe(recipe: RecipeEntity)

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun deleteRecipeById(id: String)

    @Query("SELECT COUNT(*) FROM recipes WHERE imagePath = :path")
    suspend fun countByImagePath(path: String): Int
}
