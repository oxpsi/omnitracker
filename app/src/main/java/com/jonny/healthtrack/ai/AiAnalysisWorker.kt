package com.jonny.healthtrack.ai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jonny.healthtrack.data.AppDatabase
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class AiAnalysisWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val logId = inputData.getString(AiAnalysisWork.KEY_LOG_ID).orEmpty()
        val force = inputData.getBoolean(AiAnalysisWork.KEY_FORCE, false)
        if (logId.isBlank()) return Result.failure()

        val db = AppDatabase.getDatabase(applicationContext)
        val logDao = db.logDao()
        val recipeDao = db.recipeDao()
        val aiService = AiAnalysisService()

        val latest = logDao.getLogById(logId) ?: return Result.failure()
        if (!force && latest.analysisStatus == AiAnalysisStatus.COMPLETE) {
            return Result.success()
        }
        // Do not short-circuit on PENDING here. We often mark logs as PENDING when queued, and the worker
        // must still perform the analysis and transition to COMPLETE/ERROR.

        val pendingUpdate = latest.copy(
            analysisStatus = AiAnalysisStatus.PENDING,
            analysisUpdatedAt = System.currentTimeMillis(),
            analysisError = null,
            analysisModel = aiService.getActiveModelName(applicationContext)
        )
        logDao.insertLog(pendingUpdate)

        val analysisImages = com.jonny.healthtrack.data.buildAnalysisImages(pendingUpdate, recipeDao)
        val imageLabels = analysisImages.map { it.label }
        val contextPreamble = com.jonny.healthtrack.data.buildImageContextPreamble(imageLabels)
        val analysisText = contextPreamble + com.jonny.healthtrack.data.buildAnalysisNote(pendingUpdate, recipeDao)
        val result = aiService.analyzeLog(applicationContext, analysisImages.map { it.file }, analysisText)
        val now = System.currentTimeMillis()

        return if (result.isSuccess) {
            val refreshed = logDao.getLogById(logId) ?: pendingUpdate
            val parsed = parseAiAnalysis(result.getOrNull())
            val nextResults = if (parsed != null) {
                refreshed.analysisResults.orEmpty() + parsed
            } else {
                refreshed.analysisResults
            }
            val aiPrivate = parsed?.isPrivate == true
            val updated = refreshed.copy(
                analysisResults = nextResults,
                analysisStatus = AiAnalysisStatus.COMPLETE,
                analysisUpdatedAt = now,
                analysisError = null,
                analysisModel = aiService.getActiveModelName(applicationContext),
                isPrivate = if (aiPrivate) true else refreshed.isPrivate
            )
            logDao.insertLog(updated)
            Result.success()
        } else {
            val error = result.exceptionOrNull()
            if (error != null && isRetryable(error)) {
                val refreshed = logDao.getLogById(logId) ?: pendingUpdate
                // Keep status as pending so UI doesn't show a "final" error while WorkManager retries.
                val updated = refreshed.copy(
                    analysisStatus = AiAnalysisStatus.PENDING,
                    analysisUpdatedAt = now,
                    analysisError = error.message
                )
                logDao.insertLog(updated)
                Result.retry()
            } else {
                val refreshed = logDao.getLogById(logId) ?: pendingUpdate
                val updated = refreshed.copy(
                    analysisStatus = AiAnalysisStatus.ERROR,
                    analysisUpdatedAt = now,
                    analysisError = error?.message
                )
                logDao.insertLog(updated)
                Result.failure()
            }
        }
    }

    private fun isRetryable(error: Throwable): Boolean {
        return when (error) {
            is UnknownHostException,
            is SocketTimeoutException,
            is ConnectException,
            is IOException -> true
            else -> {
                val message = error.message.orEmpty()
                val code = parseHttpStatusCode(message)
                code == 408 || code == 429 || code == 500 || code == 502 || code == 503 || code == 504
            }
        }
    }

    private fun parseHttpStatusCode(message: String): Int? {
        // Providers use messages like "OpenAI error 429: ..." or "Gemini error 500: ..."
        val regex = Regex("""\berror\s+(\d{3})\b""", RegexOption.IGNORE_CASE)
        return regex.find(message)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }
}
