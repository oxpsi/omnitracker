package com.oxpsi.omnitracker.ai

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object AiAnalysisWork {
    const val KEY_LOG_ID = "log_id"
    const val KEY_FORCE = "force"

    private fun uniqueWorkName(logId: String): String = "ai_analysis_$logId"

    fun enqueue(context: Context, logId: String, force: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<AiAnalysisWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    KEY_LOG_ID to logId,
                    KEY_FORCE to force
                )
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30,
                TimeUnit.SECONDS
            )
            .build()

        val policy = if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(uniqueWorkName(logId), policy, request)
    }

    fun cancel(context: Context, logId: String) {
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(uniqueWorkName(logId))
    }
}

