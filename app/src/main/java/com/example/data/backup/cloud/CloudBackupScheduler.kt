/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.data.backup.cloud

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.concurrent.TimeUnit

/**
 * WorkManager policy for cloud backups.
 *
 * The app does not try to watch the folder. Changes request a debounced one-time job, finishing a
 * workout or restoring a backup requests a shorter one, and a periodic job is the recovery net.
 * WorkManager handles reboots and retries; the runner keeps the last, the current and the next
 * attempt from overlapping.
 */
class CloudBackupScheduler(
    context: Context,
    private val preferences: CloudBackupPreferences
) {
    private val appContext = context.applicationContext
    private val workManager by lazy { WorkManager.getInstance(appContext) }

    fun scheduleDebounced() = enqueueOneTime(
        name = DEBOUNCE_WORK,
        delaySeconds = 45L,
        policy = ExistingWorkPolicy.KEEP,
        reason = CloudBackupReason.AUTO
    )

    fun scheduleAfterWorkout() = enqueueOneTime(
        name = EVENT_WORK,
        delaySeconds = 10L,
        policy = ExistingWorkPolicy.KEEP,
        reason = CloudBackupReason.WORKOUT
    )

    fun scheduleAfterImport() = enqueueOneTime(
        name = EVENT_WORK,
        delaySeconds = 10L,
        policy = ExistingWorkPolicy.REPLACE,
        reason = CloudBackupReason.IMPORT
    )

    fun setAutoEnabled(enabled: Boolean, scheduleInitial: Boolean = true) {
        if (!enabled) {
            workManager.cancelUniqueWork(DEBOUNCE_WORK)
            workManager.cancelUniqueWork(EVENT_WORK)
            workManager.cancelUniqueWork(PERIODIC_WORK)
            return
        }
        enqueuePeriodic()
        if (scheduleInitial) {
            // Give the athlete one verified backup shortly after turning the feature on. During
            // onboarding the profile is not written yet, so that caller waits for the real first
            // backup instead of creating an empty one.
            enqueueOneTime(
                name = EVENT_WORK,
                delaySeconds = 10L,
                policy = ExistingWorkPolicy.REPLACE,
                reason = CloudBackupReason.AUTO
            )
        }
    }

    fun cancelAutomatic() {
        workManager.cancelUniqueWork(DEBOUNCE_WORK)
        workManager.cancelUniqueWork(EVENT_WORK)
        workManager.cancelUniqueWork(PERIODIC_WORK)
    }

    fun observeAutomaticWorkState(): Flow<CloudBackupWorkState> = combine(
        workManager.getWorkInfosForUniqueWorkFlow(DEBOUNCE_WORK),
        workManager.getWorkInfosForUniqueWorkFlow(EVENT_WORK),
        workManager.getWorkInfosForUniqueWorkFlow(PERIODIC_WORK)
    ) { debounce, event, periodic ->
        val all = debounce + event + periodic
        when {
            all.any { it.state == WorkInfo.State.RUNNING } -> CloudBackupWorkState.RUNNING
            all.any { it.state == WorkInfo.State.FAILED } -> CloudBackupWorkState.FAILED
            all.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED } ->
                CloudBackupWorkState.SCHEDULED
            else -> CloudBackupWorkState.IDLE
        }
    }

    private fun enqueueOneTime(
        name: String,
        delaySeconds: Long,
        policy: ExistingWorkPolicy,
        reason: CloudBackupReason
    ) {
        val config = preferences.config.value
        if (!config.isConfigured || !config.autoBackupEnabled) return
        val request = OneTimeWorkRequestBuilder<CloudBackupWorker>()
            .setConstraints(networkConstraints(config))
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
            .setInputData(workDataOf(CloudBackupWorker.KEY_REASON to reason.name))
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(name, policy, request)
    }

    private fun enqueuePeriodic() {
        val config = preferences.config.value
        if (!config.isConfigured || !config.autoBackupEnabled) return
        val request = PeriodicWorkRequestBuilder<CloudBackupWorker>(24L, TimeUnit.HOURS)
            .setConstraints(networkConstraints(config))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
            .setInputData(workDataOf(CloudBackupWorker.KEY_REASON to CloudBackupReason.PERIODIC.name))
            .addTag(TAG)
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request
        )
    }

    private fun networkConstraints(config: CloudBackupConfig): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(
                if (config.requiresNetwork) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED
            )
            .build()

    companion object {
        private const val TAG = "ironpulse-cloud-backup"
        const val DEBOUNCE_WORK = "ironpulse-cloud-backup-debounce"
        const val EVENT_WORK = "ironpulse-cloud-backup-event"
        const val PERIODIC_WORK = "ironpulse-cloud-backup-periodic"
    }
}
