/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.data.backup.cloud

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Small, device-local preferences for the cloud backup destination.
 *
 * Kept in SharedPreferences rather than the user DataStore because a URI permission belongs to this
 * installation. It must never be exported in a backup that may be restored on another phone.
 */
class CloudBackupPreferences(context: Context) {

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _config = MutableStateFlow(load())
    val config: StateFlow<CloudBackupConfig> = _config.asStateFlow()

    // The WorkManager worker runs in this process but owns a separate instance. Listening to the
    // preference file keeps the ViewModel's state in sync when a background backup records its
    // result. The listener is held strongly here because SharedPreferences keeps only weak refs.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _config.value = load()
    }

    init {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    @Synchronized
    fun saveFolder(uri: Uri, folderName: String, providerAuthority: String?): Boolean {
        val previousUri = preferences.getString(KEY_FOLDER_URI, null)
        val changedDestination = previousUri != null && previousUri != uri.toString()
        val ok = preferences.edit()
            .putString(KEY_FOLDER_URI, uri.toString())
            .putString(KEY_FOLDER_NAME, folderName)
            .putString(KEY_PROVIDER_AUTHORITY, providerAuthority)
            .apply {
                // A new destination deserves a clean status. A success from the previous folder
                // must not look like it belongs to the new one.
                remove(KEY_LAST_FAILURE_AT)
                remove(KEY_LAST_FAILURE_MESSAGE)
                if (changedDestination) {
                    remove(KEY_LAST_SUCCESS_AT)
                    remove(KEY_LAST_SUCCESS_NAME)
                }
            }
            .commit()
        if (ok) _config.value = load()
        return ok
    }

    @Synchronized
    fun setAutoEnabled(enabled: Boolean): Boolean {
        val ok = preferences.edit().putBoolean(KEY_AUTO_ENABLED, enabled).commit()
        if (ok) _config.value = load()
        return ok
    }

    @Synchronized
    fun recordSuccess(epochMillis: Long, fileName: String): Boolean {
        val ok = preferences.edit()
            .putLong(KEY_LAST_SUCCESS_AT, epochMillis)
            .putString(KEY_LAST_SUCCESS_NAME, fileName)
            .remove(KEY_LAST_FAILURE_AT)
            .remove(KEY_LAST_FAILURE_MESSAGE)
            .commit()
        if (ok) _config.value = load()
        return ok
    }

    @Synchronized
    fun recordFailure(epochMillis: Long, message: String): Boolean {
        val ok = preferences.edit()
            .putLong(KEY_LAST_FAILURE_AT, epochMillis)
            .putString(KEY_LAST_FAILURE_MESSAGE, message)
            .commit()
        if (ok) _config.value = load()
        return ok
    }

    @Synchronized
    fun clearFailure(): Boolean {
        val ok = preferences.edit()
            .remove(KEY_LAST_FAILURE_AT)
            .remove(KEY_LAST_FAILURE_MESSAGE)
            .commit()
        if (ok) _config.value = load()
        return ok
    }

    @Synchronized
    fun clearFolder(): Boolean {
        val ok = preferences.edit()
            .remove(KEY_FOLDER_URI)
            .remove(KEY_FOLDER_NAME)
            .remove(KEY_PROVIDER_AUTHORITY)
            .putBoolean(KEY_AUTO_ENABLED, false)
            .remove(KEY_LAST_SUCCESS_AT)
            .remove(KEY_LAST_SUCCESS_NAME)
            .remove(KEY_LAST_FAILURE_AT)
            .remove(KEY_LAST_FAILURE_MESSAGE)
            .commit()
        if (ok) _config.value = load()
        return ok
    }

    fun folderUri(): Uri? = _config.value.folderUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)

    private fun load(): CloudBackupConfig = CloudBackupConfig(
        folderUri = preferences.getString(KEY_FOLDER_URI, null)?.takeIf { it.isNotBlank() },
        folderName = preferences.getString(KEY_FOLDER_NAME, null)?.takeIf { it.isNotBlank() },
        providerAuthority = preferences.getString(KEY_PROVIDER_AUTHORITY, null)?.takeIf { it.isNotBlank() },
        autoBackupEnabled = preferences.getBoolean(KEY_AUTO_ENABLED, false),
        lastSuccessEpochMillis = preferences.getLong(KEY_LAST_SUCCESS_AT, 0L),
        lastSuccessFileName = preferences.getString(KEY_LAST_SUCCESS_NAME, null)?.takeIf { it.isNotBlank() },
        lastFailureEpochMillis = preferences.getLong(KEY_LAST_FAILURE_AT, 0L),
        lastFailureMessage = preferences.getString(KEY_LAST_FAILURE_MESSAGE, null)?.takeIf { it.isNotBlank() }
    )

    private companion object {
        const val PREFS_NAME = "ironpulse_cloud_backup"
        const val KEY_FOLDER_URI = "folder_uri"
        const val KEY_FOLDER_NAME = "folder_name"
        const val KEY_PROVIDER_AUTHORITY = "provider_authority"
        const val KEY_AUTO_ENABLED = "auto_enabled"
        const val KEY_LAST_SUCCESS_AT = "last_success_at"
        const val KEY_LAST_SUCCESS_NAME = "last_success_name"
        const val KEY_LAST_FAILURE_AT = "last_failure_at"
        const val KEY_LAST_FAILURE_MESSAGE = "last_failure_message"
    }
}
