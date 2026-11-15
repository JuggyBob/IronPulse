/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.data.backup.cloud

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.data.backup.BackupArchiveLimits
import com.example.data.backup.BackupErrorCode
import com.example.data.backup.BackupException
import com.example.data.backup.BackupFormat
import com.example.data.backup.UserDataBackupManager
import com.example.data.backup.copyWithLimit
import com.example.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale

/**
 * Writes one verified `.ironpulse` archive into the folder the athlete chose.
 *
 * The archive is built by the existing [UserDataBackupManager], so cloud backups and manual
 * exports are byte-for-byte the same format. A backup is only recorded as successful after the
 * document's bytes have been read back and hashed. A failed attempt never overwrites an older
 * backup because every backup has its own timestamped name.
 */
class CloudBackupRunner(
    private val context: Context,
    private val backupManager: UserDataBackupManager,
    private val preferences: CloudBackupPreferences
) {

    /**
     * Finds the newest complete `.ironpulse` archive in a folder the athlete just selected.
     *
     * Both manual exports and IronPulse cloud backups are eligible. The caller asks before
     * replacing local data; this method only lists and sorts.
     */
    suspend fun findLatestBackup(treeUri: Uri): CloudBackupFile? = withContext(Dispatchers.IO) {
        val parent = runCatching { DocumentFile.fromTreeUri(context, treeUri) }.getOrNull()
            ?: return@withContext null
        val files = runCatching { parent.listFiles().toList() }.getOrDefault(emptyList())
            .filter { it.isFile }
            .mapNotNull { file ->
                val name = file.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                CloudBackupFile(
                    name = name,
                    uri = file.uri.toString(),
                    sizeBytes = runCatching { file.length() }.getOrDefault(0L),
                    modifiedAt = runCatching { file.lastModified() }.getOrDefault(0L)
                )
            }
        CloudBackupNaming.newestBackup(files)
    }

    suspend fun run(
        reason: CloudBackupReason,
        onProgress: (CloudBackupProgress) -> Unit = {}
    ): CloudBackupOutcome = withContext(Dispatchers.IO) {
        backupMutex.withLock {
            val config = preferences.config.value
            val now = System.currentTimeMillis()
            if (!config.isConfigured) {
                return@withLock CloudBackupOutcome.Failed(
                    "Choose a backup folder first.",
                    CloudBackupErrorCode.NOT_CONFIGURED,
                    retryable = false
                )
            }
            if (!hasFinishedSetup()) {
                return@withLock CloudBackupOutcome.Skipped(
                    "Finish setting up your profile before IronPulse creates a cloud backup."
                )
            }
            if (reason.isAutomatic && !config.autoBackupEnabled) {
                return@withLock CloudBackupOutcome.Skipped("Automatic backups are off.")
            }
            if (reason.isAutomatic &&
                config.lastSuccessEpochMillis > 0L &&
                now - config.lastSuccessEpochMillis < CloudBackupNaming.MIN_AUTO_INTERVAL_MS
            ) {
                return@withLock CloudBackupOutcome.Skipped("A backup was already saved recently.")
            }

            val treeUri = runCatching { Uri.parse(config.folderUri) }.getOrNull()
                ?: return@withLock fail(
                    now,
                    "The saved backup folder is not valid. Choose it again.",
                    CloudBackupErrorCode.FOLDER_UNAVAILABLE,
                    retryable = false
                )
            if (!CloudBackupPermissions.hasPersistedWrite(context, treeUri)) {
                return@withLock fail(
                    now,
                    "IronPulse no longer has permission to write to this folder. Choose it again or pick another folder.",
                    CloudBackupErrorCode.PERMISSION_LOST,
                    retryable = false
                )
            }

            onProgress(CloudBackupProgress("Opening folder", 0.02f))
            val parent = runCatching { DocumentFile.fromTreeUri(context, treeUri) }.getOrNull()
                ?: return@withLock fail(
                    now,
                    "The selected folder is no longer available. Choose another folder.",
                    CloudBackupErrorCode.FOLDER_UNAVAILABLE,
                    retryable = false
                )
            val usable = runCatching {
                parent.exists() && parent.isDirectory && parent.canWrite()
            }.getOrDefault(false)
            if (!usable) {
                return@withLock fail(
                    now,
                    "The selected folder is no longer available. Choose another folder.",
                    CloudBackupErrorCode.FOLDER_UNAVAILABLE,
                    retryable = false
                )
            }

            val temp = try {
                File.createTempFile("ironpulse-cloud-", ".ironpulse", context.cacheDir)
            } catch (e: Exception) {
                return@withLock fail(
                    now,
                    "IronPulse could not prepare the backup. Your existing data was not changed.",
                    CloudBackupErrorCode.EXPORT_FAILED,
                    retryable = true
                )
            }

            var partialUri: Uri? = null
            var finalUri: Uri? = null
            var finalCompleted = false
            try {
                val summary = try {
                    backupManager.exportToFile(temp) { progress ->
                        onProgress(
                            CloudBackupProgress(
                                label = progress.phase.label,
                                fraction = progress.fraction * 0.8f
                            )
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: BackupException) {
                    return@withLock fail(
                        now,
                        e.message ?: "IronPulse could not prepare the backup. Your existing data was not changed.",
                        CloudBackupErrorCode.EXPORT_FAILED,
                        retryable = e.code == BackupErrorCode.IO_ERROR
                    )
                } catch (e: Exception) {
                    return@withLock fail(
                        now,
                        "IronPulse could not prepare the backup. Your existing data was not changed.",
                        CloudBackupErrorCode.EXPORT_FAILED,
                        retryable = true
                    )
                }

                if (summary.sizeBytes <= 0L || !temp.isFile || temp.length() != summary.sizeBytes) {
                    return@withLock fail(
                        now,
                        "The backup was not created correctly. Your existing data was not changed.",
                        CloudBackupErrorCode.EXPORT_FAILED,
                        retryable = true
                    )
                }

                onProgress(CloudBackupProgress("Checking the archive", 0.82f))
                val sourceHash = try {
                    sha256(temp)
                } catch (e: Exception) {
                    return@withLock fail(
                        now,
                        "IronPulse could not verify the backup before writing it.",
                        CloudBackupErrorCode.EXPORT_FAILED,
                        retryable = true
                    )
                }

                val existingNames = runCatching {
                    parent.listFiles().mapNotNull { it.name }.toSet()
                }.getOrDefault(emptySet())
                val finalName = CloudBackupNaming.uniqueFileName(now, existingNames)
                val partialName = "$finalName${CloudBackupNaming.PARTIAL_SUFFIX}"

                onProgress(CloudBackupProgress("Writing to folder", 0.88f))
                val partial = runCatching {
                    parent.createFile(BackupFormat.MIME_TYPE, partialName)
                }.getOrNull()
                if (partial == null) {
                    return@withLock fail(
                        now,
                        "The backup could not be created in the selected folder. Your previous backup was kept.",
                        CloudBackupErrorCode.WRITE_FAILED,
                        retryable = true
                    )
                }
                partialUri = partial.uri

                try {
                    writeDocument(partial.uri, temp)
                    onProgress(CloudBackupProgress("Verifying the backup", 0.94f))
                    verifyDocument(partial.uri, summary.sizeBytes, sourceHash)

                    onProgress(CloudBackupProgress("Finalizing backup", 0.98f))
                    val renamed = renameDocument(partial.uri, finalName)
                        ?: throw CloudBackupFailure(
                            "The backup was written but could not be finalized. Your previous backup was kept.",
                            CloudBackupErrorCode.VERIFY_FAILED,
                            retryable = false
                        )
                    finalUri = renamed
                    partialUri = null
                    val renamedName = runCatching {
                        DocumentFile.fromSingleUri(context, renamed)?.name
                    }.getOrNull()
                    if (renamedName != null && renamedName != finalName) {
                        throw CloudBackupFailure(
                            "The backup could not be given its final name. Your previous backup was kept.",
                            CloudBackupErrorCode.VERIFY_FAILED,
                            retryable = false
                        )
                    }
                    verifyDocument(renamed, summary.sizeBytes, sourceHash)
                    // The file is now valid and final. Mark it before recording the status so a
                    // preference failure cannot make the cleanup delete a good backup.
                    finalCompleted = true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: CloudBackupFailure) {
                    return@withLock fail(
                        now,
                        e.message ?: "The backup could not be finalized. Your previous backup was kept.",
                        e.code,
                        e.retryable
                    )
                } catch (e: SecurityException) {
                    return@withLock fail(
                        now,
                        "IronPulse lost permission to write to this folder. Your previous backup was kept.",
                        CloudBackupErrorCode.PERMISSION_LOST,
                        retryable = false
                    )
                } catch (e: IOException) {
                    return@withLock fail(
                        now,
                        "The backup could not be written to the selected folder. Your previous backup was kept.",
                        CloudBackupErrorCode.WRITE_FAILED,
                        retryable = true
                    )
                } catch (e: Exception) {
                    return@withLock fail(
                        now,
                        "The backup could not be saved to the selected folder. Your previous backup was kept.",
                        CloudBackupErrorCode.WRITE_FAILED,
                        retryable = true
                    )
                }

                val completedAt = System.currentTimeMillis()
                if (!preferences.recordSuccess(completedAt, finalName)) {
                    val message = "The backup was saved, but IronPulse could not update its status."
                    preferences.recordFailure(completedAt, message)
                    return@withLock CloudBackupOutcome.Failed(
                        message,
                        CloudBackupErrorCode.UNKNOWN,
                        retryable = false
                    )
                }
                cleanup(parent)
                onProgress(CloudBackupProgress("Completed", 1f))
                Log.i(TAG, "Cloud backup completed: ${summary.sizeBytes} bytes")
                CloudBackupOutcome.Success(
                    fileName = finalName,
                    sizeBytes = summary.sizeBytes,
                    completedAtEpochMillis = completedAt
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(
                    now,
                    "The backup failed unexpectedly. Your previous backup was kept.",
                    CloudBackupErrorCode.UNKNOWN,
                    retryable = false
                )
            } finally {
                if (!finalCompleted) {
                    partialUri?.let { uri -> deleteDocumentQuietly(uri) }
                    finalUri?.let { uri -> deleteDocumentQuietly(uri) }
                }
                temp.delete()
            }
        }
    }

    /**
     * Onboarding can connect a folder before the profile exists. A backup written at that point
     * would contain no athlete, and it would run before an existing archive had a chance to be
     * restored. Both automatic and manual cloud backups wait for a saved profile.
     */
    private suspend fun hasFinishedSetup(): Boolean {
        val preferences = UserPreferencesRepository(context)
        return preferences.onboardingCompletedFlow.first() ||
            preferences.storedProfileWeightKg() != null
    }

    private fun deleteDocumentQuietly(uri: Uri) {
        runCatching {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            }
        }
    }

    private fun fail(
        now: Long,
        message: String,
        code: CloudBackupErrorCode,
        retryable: Boolean
    ): CloudBackupOutcome.Failed {
        preferences.recordFailure(now, message)
        Log.w(TAG, "Cloud backup failed ($code)")
        return CloudBackupOutcome.Failed(message, code, retryable)
    }

    private fun writeDocument(uri: Uri, source: File) {
        val output = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("The selected folder did not open for writing.")
        output.use { stream ->
            source.inputStream().use { input ->
                copyWithLimit(input, stream, BackupArchiveLimits().maxArchiveBytes)
            }
            stream.flush()
        }
    }

    private fun verifyDocument(uri: Uri, expectedSize: Long, expectedHash: String) {
        val reportedSize = queryDocumentSize(uri)
        if (reportedSize != null && reportedSize != expectedSize) {
            throw CloudBackupFailure(
                "The backup file was not fully written to the selected folder.",
                CloudBackupErrorCode.WRITE_FAILED,
                retryable = true
            )
        }
        val actualHash = context.contentResolver.openInputStream(uri)?.use { input ->
            sha256(input)
        } ?: throw CloudBackupFailure(
            "The backup file could not be read back for verification.",
            CloudBackupErrorCode.VERIFY_FAILED,
            retryable = true
        )
        if (!actualHash.equals(expectedHash, ignoreCase = true)) {
            throw CloudBackupFailure(
                "The backup file did not match what IronPulse wrote, so it was removed.",
                CloudBackupErrorCode.VERIFY_FAILED,
                retryable = true
            )
        }
    }

    private fun renameDocument(uri: Uri, newName: String): Uri? {
        val renamed = runCatching {
            DocumentsContract.renameDocument(context.contentResolver, uri, newName)
        }.getOrNull()
        if (renamed != null) return renamed
        val single = DocumentFile.fromSingleUri(context, uri) ?: return null
        return if (runCatching { single.renameTo(newName) }.getOrDefault(false)) {
            single.uri
        } else {
            null
        }
    }

    private fun cleanup(parent: DocumentFile) {
        val children = runCatching { parent.listFiles() }.getOrDefault(emptyArray())
        val existingNames = children.mapNotNull { it.name }
        val victims = CloudBackupNaming.retentionVictims(existingNames).toSet()
        children.filter { child ->
            child.isFile && child.name?.let { it in victims } == true
        }.forEach { child ->
            runCatching { child.delete() }
        }

        // A process killed mid-write leaves a partial document behind. It is never treated as a
        // backup, and only old partials are removed so a backup currently being written is safe.
        val staleBefore = System.currentTimeMillis() - PARTIAL_RETENTION_MS
        children.filter { child ->
            val name = child.name ?: return@filter false
            child.isFile &&
                CloudBackupNaming.isPartial(name) &&
                child.lastModified() in 1..staleBefore
        }.forEach { child ->
            runCatching { child.delete() }
        }
    }

    private fun queryDocumentSize(uri: Uri): Long? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0).takeIf { it > 0L } else null
        }
    }.getOrNull()

    private fun sha256(file: File): String = file.inputStream().use(::sha256)

    private fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(Locale.US, it) }
    }

    private class CloudBackupFailure(
        message: String,
        val code: CloudBackupErrorCode,
        val retryable: Boolean
    ) : Exception(message)

    companion object {
        private const val TAG = "CloudBackupRunner"
        private const val PARTIAL_RETENTION_MS = 24L * 60L * 60L * 1000L

        /** Shared by the ViewModel's manual attempt and the WorkManager worker. */
        private val backupMutex = Mutex()
    }
}
