package com.jonny.healthtrack.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val ingredients: String,
    val imagePath: String = "",
    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis()
)
