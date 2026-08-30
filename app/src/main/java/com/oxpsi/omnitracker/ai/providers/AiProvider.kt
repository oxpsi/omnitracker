package com.oxpsi.omnitracker.ai.providers

import java.io.File

interface AiProvider {
    suspend fun analyzeLog(imageFiles: List<File>, note: String, userId: String, reasoningLevel: String = "medium"): Result<String>

    val isCancelled: Boolean get() = false

    fun cancel() {}
}
