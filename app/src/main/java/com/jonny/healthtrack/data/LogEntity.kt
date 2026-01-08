package com.jonny.healthtrack.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "logs")
data class LogEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val timestamp: Long,
    val imagePath: String,
    val note: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val analysisData: String? = null,
    val isOriginalImage: Boolean = true,
    val isPrivate: Boolean = false
)