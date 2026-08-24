package com.jonny.healthtrack.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.jonny.healthtrack.ai.AiAnalysisResult
import java.util.UUID

@Entity(tableName = "logs")
data class LogEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val timestamp: Long,
    val imagePath: String,
    val note: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @ColumnInfo(name = "analysisData")
    val analysisResults: List<AiAnalysisResult>? = null,
    val analysisModel: String? = null,
    val analysisUpdatedAt: Long? = null,
    val analysisStatus: String? = null,
    val analysisError: String? = null,
    val isOriginalImage: Boolean = true,
    val isPrivate: Boolean = false,
    val recipeId: String? = null,
    @ColumnInfo(name = "quantity", defaultValue = "1.0")
    val quantity: Double = 1.0
)
