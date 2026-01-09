package com.jonny.healthtrack.ai.providers

import java.io.File

interface AiProvider {
    suspend fun analyzeLog(imageFile: File?, note: String, userId: String): Result<String>
}
