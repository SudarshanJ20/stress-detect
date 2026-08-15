package com.stressdetect.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters

/**
 * Runs the retrospective backfill off the main thread via WorkManager (android/CLAUDE.md:
 * all background work goes through WorkManager or a typed foreground service).
 *
 * One-shot at install: the ~10-day event retention means the useful history is already on
 * the device, so there is nothing to poll for.
 */
class RetrospectiveBackfillWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (RetrospectiveExtractor(applicationContext).run()) {
        // Not retryable: the user must grant usage access in Settings first. Retrying on a
        // backoff would just burn battery re-reading the same denied permission.
        is ExtractionOutcome.MissingUsageAccess -> Result.failure()

        // Both of these ran to completion; low coverage is a recorded finding, not a
        // failure to retry (re-querying cannot invent history the OS already pruned).
        is ExtractionOutcome.InsufficientCoverage,
        is ExtractionOutcome.Success -> Result.success()
    }

    companion object {
        private const val WORK_NAME = "retrospective-backfill"

        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<RetrospectiveBackfillWorker>().build(),
            )
        }
    }
}
