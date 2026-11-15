/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.example.data.backup.BackupArchiveLimits
import com.example.data.backup.BackupException
import com.example.data.backup.BackupFormat
import com.example.data.backup.BackupOperation
import com.example.data.backup.BackupPhase
import com.example.data.backup.BackupProgress
import com.example.data.backup.DataTransferState
import com.example.data.backup.PreparedImport
import com.example.data.backup.UserDataBackupManager
import com.example.data.backup.copyWithLimit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Owns the `.ironpulse` export/import lifecycle.
 *
 * The callbacks keep the import replacement point at the ViewModel boundary: the controller owns
 * files, validation and transfer state, while the ViewModel remains responsible for resetting its
 * transient UI state and reloading an active-workout draft after a successful replacement.
 */
class DataTransferController(
    private val application: Application,
    private val backupManager: UserDataBackupManager,
    private val scope: CoroutineScope,
    private val onBeforeImportApply: suspend () -> Unit,
    private val onImportSettled: suspend () -> Unit,
    private val onImportCompleted: () -> Unit,
    private val onWidgetsChanged: () -> Unit
) {
    private val _dataTransferState =
        MutableStateFlow<DataTransferState>(DataTransferState.Idle)
    val dataTransferState: StateFlow<DataTransferState> = _dataTransferState.asStateFlow()

    private var dataTransferJob: Job? = null
    private var preparedImport: PreparedImport? = null

    fun exportUserData(uri: Uri, suggestedName: String? = null) {
        if (_dataTransferState.value is DataTransferState.Running) return
        preparedImport?.cleanup()
        preparedImport = null
        dataTransferJob = scope.launch(Dispatchers.IO) {
            var temp: File? = null
            var destinationWritten = false
            try {
                _dataTransferState.value = DataTransferState.Running(
                    BackupOperation.EXPORT,
                    BackupProgress(BackupPhase.PREPARING, 0, 8)
                )
                temp = File.createTempFile(
                    "ironpulse-export-",
                    ".${BackupFormat.EXTENSION}",
                    application.cacheDir
                )
                val summary = backupManager.exportToFile(temp) { progress ->
                    _dataTransferState.value = DataTransferState.Running(BackupOperation.EXPORT, progress)
                }
                val displayName = queryDocumentName(uri)
                    ?: suggestedName
                    ?: summary.fileName
                val output = application.contentResolver.openOutputStream(uri, "wt")
                    ?: throw BackupException(
                        "The selected destination could not be opened for writing.",
                        com.example.data.backup.BackupErrorCode.IO_ERROR
                    )
                output.use { stream ->
                    temp.inputStream().use { input ->
                        copyWithLimit(input, stream, BackupArchiveLimits().maxArchiveBytes)
                    }
                    stream.flush()
                }
                destinationWritten = true
                val reportedSize = queryDocumentSize(uri)
                if (reportedSize != null && reportedSize != summary.sizeBytes) {
                    destinationWritten = false
                    deleteDestinationDocument(uri)
                    throw BackupException(
                        "The backup file was not fully written to the selected destination.",
                        com.example.data.backup.BackupErrorCode.IO_ERROR
                    )
                }
                _dataTransferState.value = DataTransferState.Success(
                    operation = BackupOperation.EXPORT,
                    title = "Backup saved",
                    detail = "$displayName • ${formatBytes(summary.sizeBytes)}"
                )
            } catch (e: CancellationException) {
                _dataTransferState.value = DataTransferState.Cancelled(
                    "Export cancelled. No completed backup was left in IronPulse."
                )
                throw e
            } catch (e: Exception) {
                if (!destinationWritten) deleteDestinationDocument(uri)
                _dataTransferState.value = DataTransferState.Error(
                    message = friendlyBackupError(e, importOperation = false),
                    destinationPreserved = true
                )
            } finally {
                temp?.delete()
            }
        }
    }

    fun inspectUserDataImport(uri: Uri) {
        if (_dataTransferState.value is DataTransferState.Running ||
            _dataTransferState.value is DataTransferState.ReadyToImport
        ) {
            return
        }
        dataTransferJob = scope.launch(Dispatchers.IO) {
            var temp: File? = null
            try {
                _dataTransferState.value = DataTransferState.Running(
                    BackupOperation.IMPORT,
                    BackupProgress(BackupPhase.VALIDATING, 0, 5)
                )
                temp = File.createTempFile(
                    "ironpulse-import-",
                    ".${BackupFormat.EXTENSION}",
                    application.cacheDir
                )
                val input = application.contentResolver.openInputStream(uri)
                    ?: throw BackupException(
                        "The selected backup could not be opened.",
                        com.example.data.backup.BackupErrorCode.IO_ERROR
                    )
                input.use { stream ->
                    temp.outputStream().use { output ->
                        copyWithLimit(stream, output, BackupArchiveLimits().maxArchiveBytes)
                    }
                }
                val prepared = backupManager.inspectFile(
                    archive = temp,
                    displayName = queryDocumentName(uri)
                ) { progress ->
                    _dataTransferState.value = DataTransferState.Running(BackupOperation.IMPORT, progress)
                }
                preparedImport?.cleanup()
                preparedImport = prepared
                _dataTransferState.value = DataTransferState.ReadyToImport(prepared.preview)
            } catch (e: CancellationException) {
                temp?.delete()
                _dataTransferState.value = DataTransferState.Cancelled(
                    "Import cancelled before anything was changed."
                )
                throw e
            } catch (e: Exception) {
                temp?.delete()
                _dataTransferState.value = DataTransferState.Error(
                    message = friendlyBackupError(e, importOperation = true),
                    destinationPreserved = true
                )
            }
        }
    }

    fun confirmUserDataImport() {
        val prepared = preparedImport ?: return
        if (_dataTransferState.value is DataTransferState.Running) return
        preparedImport = null
        dataTransferJob = scope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main.immediate) {
                    onBeforeImportApply()
                }
                _dataTransferState.value = DataTransferState.Running(
                    BackupOperation.IMPORT,
                    BackupProgress(BackupPhase.STAGING, 0, 9)
                )
                val summary = backupManager.applyImport(prepared) { progress ->
                    _dataTransferState.value = DataTransferState.Running(BackupOperation.IMPORT, progress)
                }
                withContext(Dispatchers.Main.immediate) {
                    onImportSettled()
                }
                onWidgetsChanged()
                onImportCompleted()
                _dataTransferState.value = DataTransferState.Success(
                    operation = BackupOperation.IMPORT,
                    title = "Backup restored",
                    detail = "Restored the backup created by IronPulse ${summary.preview.appVersionName} " +
                        "on ${summary.preview.exportedAt}. API keys and your profile picture were kept on this device."
                )
            } catch (e: CancellationException) {
                withContext(Dispatchers.Main.immediate) {
                    onImportSettled()
                }
                _dataTransferState.value = DataTransferState.Cancelled(
                    "Import cancelled. Your previous data was restored."
                )
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main.immediate) {
                    onImportSettled()
                }
                _dataTransferState.value = DataTransferState.Error(
                    message = friendlyBackupError(e, importOperation = true),
                    destinationPreserved = (e as? BackupException)?.dataPreserved ?: false
                )
            } finally {
                prepared.cleanup()
            }
        }
    }

    fun cancelPendingUserDataImport() {
        preparedImport?.cleanup()
        preparedImport = null
        if (_dataTransferState.value is DataTransferState.ReadyToImport) {
            _dataTransferState.value = DataTransferState.Idle
        }
    }

    fun cancelDataTransfer() {
        val running = _dataTransferState.value as? DataTransferState.Running ?: return
        val canCancel = running.operation == BackupOperation.EXPORT ||
            running.progress.phase == BackupPhase.PREPARING ||
            running.progress.phase == BackupPhase.VALIDATING
        if (!canCancel) return
        dataTransferJob?.cancel()
    }

    fun dismissDataTransferMessage() {
        if (_dataTransferState.value !is DataTransferState.Running &&
            _dataTransferState.value !is DataTransferState.ReadyToImport
        ) {
            _dataTransferState.value = DataTransferState.Idle
        }
    }

    /** True while an imported archive is replacing local data; autosave must stay out of the way. */
    fun isImportApplying(): Boolean {
        val transfer = _dataTransferState.value
        return transfer is DataTransferState.Running &&
            transfer.operation == BackupOperation.IMPORT
    }

    private fun queryDocumentName(uri: Uri): String? = runCatching {
        application.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
        }
    }.getOrNull()

    private fun queryDocumentSize(uri: Uri): Long? = runCatching {
        application.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0).takeIf { it > 0L } else null
        }
    }.getOrNull()

    private fun deleteDestinationDocument(uri: Uri) {
        runCatching {
            if (DocumentsContract.isDocumentUri(application, uri)) {
                DocumentsContract.deleteDocument(application.contentResolver, uri)
            }
        }
    }

    private fun friendlyBackupError(error: Throwable, importOperation: Boolean): String {
        if (error is BackupException) return error.message ?: "The backup operation failed."
        return if (importOperation) {
            "The import failed unexpectedly. Your existing data was not replaced."
        } else {
            "The export failed unexpectedly. Your existing IronPulse data was not changed."
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
        return String.format(Locale.US, "%.1f MB", kb / 1024.0)
    }
}
