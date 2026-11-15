/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.data.backup.cloud

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Device-local cloud backup settings.
 *
 * This deliberately lives outside the `.ironpulse` archive: a folder URI is a permission granted
 * to this installation, not data that should travel to another phone. The archive itself stays
 * exactly the same format as a manual export.
 */
data class CloudBackupConfig(
    val folderUri: String? = null,
    val folderName: String? = null,
    val providerAuthority: String? = null,
    val autoBackupEnabled: Boolean = false,
    val lastSuccessEpochMillis: Long = 0L,
    val lastSuccessFileName: String? = null,
    val lastFailureEpochMillis: Long = 0L,
    val lastFailureMessage: String? = null
) {
    val isConfigured: Boolean get() = !folderUri.isNullOrBlank()
    val providerLabel: String get() = CloudBackupProviders.label(providerAuthority)
    val requiresNetwork: Boolean get() = CloudBackupProviders.requiresNetwork(providerAuthority)

    /** A failure that is newer than the last success, so the UI does not show a stale warning. */
    val currentFailure: String?
        get() = lastFailureMessage?.takeIf { lastFailureEpochMillis > lastSuccessEpochMillis }
}

enum class CloudBackupErrorCode {
    NOT_CONFIGURED,
    PERMISSION_LOST,
    FOLDER_UNAVAILABLE,
    EXPORT_FAILED,
    WRITE_FAILED,
    VERIFY_FAILED,
    PROVIDER_ERROR,
    UNKNOWN
}

enum class CloudBackupReason(val isAutomatic: Boolean) {
    MANUAL(false),
    AUTO(true),
    PERIODIC(true),
    WORKOUT(true),
    IMPORT(true)
}

data class CloudBackupProgress(
    val label: String,
    val fraction: Float?
)

/**
 * One `.ironpulse` file found in the selected folder.
 *
 * The cloud folder can contain both IronPulse cloud backups and manual exports. A restore scans
 * every real archive, not only the files this feature named itself.
 */
data class CloudBackupFile(
    val name: String,
    val uri: String,
    val sizeBytes: Long,
    val modifiedAt: Long
)

sealed interface CloudBackupOutcome {
    data class Success(
        val fileName: String,
        val sizeBytes: Long,
        val completedAtEpochMillis: Long
    ) : CloudBackupOutcome

    data class Skipped(val reason: String) : CloudBackupOutcome

    data class Failed(
        val message: String,
        val code: CloudBackupErrorCode,
        val retryable: Boolean
    ) : CloudBackupOutcome
}

/** UI state for the manual attempt currently running in the app process. */
data class CloudBackupRuntimeState(
    val isRunning: Boolean = false,
    val progressLabel: String? = null,
    val progressFraction: Float? = null,
    val message: String? = null,
    val messageIsError: Boolean = false
)

/** State of WorkManager's automatic backup chain, not of a manual backup. */
enum class CloudBackupWorkState {
    IDLE,
    SCHEDULED,
    RUNNING,
    FAILED
}

data class CloudBackupUiState(
    val config: CloudBackupConfig = CloudBackupConfig(),
    val permissionValid: Boolean = false,
    val runtime: CloudBackupRuntimeState = CloudBackupRuntimeState(),
    val autoWorkState: CloudBackupWorkState = CloudBackupWorkState.IDLE
) {
    val isBusy: Boolean
        get() = runtime.isRunning || autoWorkState == CloudBackupWorkState.RUNNING
}

object CloudBackupProviders {
    const val GOOGLE_DRIVE = "com.google.android.apps.docs.storage"
    const val ONEDRIVE = "com.microsoft.skydrive"
    const val LOCAL_STORAGE = "com.android.externalstorage.documents"
    const val DOWNLOADS = "com.android.providers.downloads.documents"
    const val MEDIA_STORE = "com.android.providers.media.documents"

    fun label(authority: String?): String = when {
        authority.isNullOrBlank() -> "Document provider"
        authority == GOOGLE_DRIVE || authority.startsWith("$GOOGLE_DRIVE.") -> "Google Drive"
        authority == ONEDRIVE || authority.startsWith("$ONEDRIVE.") -> "OneDrive"
        authority == LOCAL_STORAGE -> "This device"
        authority == DOWNLOADS -> "Downloads"
        authority == MEDIA_STORE -> "This device"
        else -> "Document provider"
    }

    /**
     * Local providers keep working offline. Unknown providers are treated as remote so the worker
     * waits for a connection instead of failing on a provider that needs one.
     */
    fun requiresNetwork(authority: String?): Boolean = when {
        authority == LOCAL_STORAGE -> false
        authority == DOWNLOADS -> false
        authority == MEDIA_STORE -> false
        else -> true
    }
}

object CloudBackupNaming {
    const val PREFIX = "IronPulse_Cloud_Backup_"
    const val EXTENSION = "ironpulse"
    const val PARTIAL_SUFFIX = ".partial"
    const val MAX_BACKUPS = 5

    /** Do not write a second automatic backup within this window. */
    const val MIN_AUTO_INTERVAL_MS = 60_000L

    fun fileName(epochMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).apply {
            this.timeZone = timeZone
        }
        return "$PREFIX${formatter.format(Date(epochMillis))}.$EXTENSION"
    }

    /** Avoids a same-second collision without overwriting an existing backup. */
    fun uniqueFileName(
        epochMillis: Long,
        existingNames: Set<String>,
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        val base = fileName(epochMillis, timeZone)
        if (base !in existingNames) return base
        val stem = base.removeSuffix(".$EXTENSION")
        var index = 2
        while ("${stem}_$index.$EXTENSION" in existingNames) index++
        return "${stem}_$index.$EXTENSION"
    }

    fun isCloudBackup(name: String): Boolean =
        name.startsWith(PREFIX) &&
            name.endsWith(".$EXTENSION") &&
            !name.endsWith(PARTIAL_SUFFIX)

    fun isPartial(name: String): Boolean =
        name.startsWith(PREFIX) && name.endsWith(".$EXTENSION$PARTIAL_SUFFIX")

    /**
     * Anything that is a complete `.ironpulse` archive can seed a restore.
     *
     * Manual exports use `IronPulse_Backup_...` and cloud backups use
     * `IronPulse_Cloud_Backup_...`; both are valid. A `.partial` write is never a candidate because
     * its name ends with `.partial` rather than `.ironpulse`.
     */
    fun isRestorableBackup(name: String): Boolean =
        name.endsWith(".$EXTENSION", ignoreCase = true) &&
            !name.endsWith(PARTIAL_SUFFIX, ignoreCase = true)

    /** Newest archive by provider timestamp, with the name as a deterministic tie-breaker. */
    fun newestBackup(files: List<CloudBackupFile>): CloudBackupFile? =
        files.filter { isRestorableBackup(it.name) }
            .maxWithOrNull(compareBy({ it.modifiedAt }, { it.name }))

    /**
     * Newest first by the timestamp embedded in the name. Only files this feature created are
     * eligible, so manual exports and anything else the athlete keeps in the folder are untouched.
     */
    fun retentionVictims(existingNames: Collection<String>, keep: Int = MAX_BACKUPS): List<String> {
        if (keep <= 0) return emptyList()
        val backups = existingNames.filter(::isCloudBackup).sortedDescending()
        return backups.drop(keep)
    }
}
