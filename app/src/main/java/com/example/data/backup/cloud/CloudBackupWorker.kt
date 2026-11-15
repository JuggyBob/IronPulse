/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.data.backup.cloud

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.backup.UserDataBackupManager
import com.example.data.db.AppDatabase

/**
 * Automatic cloud backup entry point. The work is silent: no notification is posted because the
 * archive contains personal data and the athlete does not need a system alert for every save.
 */
class CloudBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val preferences = CloudBackupPreferences(applicationContext)
        val config = preferences.config.value
        if (!config.isConfigured || !config.autoBackupEnabled) return Result.success()

        val backupManager = UserDataBackupManager(
            context = applicationContext,
            db = AppDatabase.getInstance(applicationContext)
        )
        val runner = CloudBackupRunner(
            context = applicationContext,
            backupManager = backupManager,
            preferences = preferences
        )
        val reason = inputData.getString(KEY_REASON)
            ?.let { raw -> runCatching { CloudBackupReason.valueOf(raw) }.getOrNull() }
            ?: CloudBackupReason.AUTO

        return when (val outcome = runner.run(reason)) {
            is CloudBackupOutcome.Success,
            is CloudBackupOutcome.Skipped -> Result.success()

            is CloudBackupOutcome.Failed -> {
                if (outcome.retryable && runAttemptCount < MAX_ATTEMPTS) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        }
    }

    companion object {
        const val KEY_REASON = "reason"
        private const val MAX_ATTEMPTS = 3
    }
}
