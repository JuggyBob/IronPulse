/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.backup.cloud.CloudBackupPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CloudBackupPreferencesTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val folder = Uri.parse(
        "content://com.google.android.apps.docs.storage/tree/ironpulse-test"
    )

    @Before
    fun reset() {
        CloudBackupPreferences(context).clearFolder()
    }

    @Test
    fun `folder and automatic setting survive a new instance`() {
        val preferences = CloudBackupPreferences(context)
        assertTrue(
            preferences.saveFolder(
                folder,
                "IronPulse backups",
                "com.google.android.apps.docs.storage"
            )
        )
        assertTrue(preferences.setAutoEnabled(true))

        val reloaded = CloudBackupPreferences(context)
        val config = reloaded.config.value
        assertEquals(folder.toString(), config.folderUri)
        assertEquals("IronPulse backups", config.folderName)
        assertEquals("Google Drive", config.providerLabel)
        assertTrue(config.isConfigured)
        assertTrue(config.autoBackupEnabled)
    }

    @Test
    fun `success clears an older failure and is timestamped`() {
        val preferences = CloudBackupPreferences(context)
        preferences.saveFolder(folder, "Backups", "com.google.android.apps.docs.storage")
        preferences.recordFailure(1_000L, "could not write")
        assertEquals("could not write", preferences.config.value.currentFailure)

        preferences.recordSuccess(2_000L, "IronPulse_Cloud_Backup.ironpulse")
        val config = preferences.config.value
        assertEquals(2_000L, config.lastSuccessEpochMillis)
        assertEquals("IronPulse_Cloud_Backup.ironpulse", config.lastSuccessFileName)
        assertNull(config.lastFailureMessage)
        assertNull(config.currentFailure)
    }

    @Test
    fun `clearing the folder removes the destination and the automatic setting`() {
        val preferences = CloudBackupPreferences(context)
        preferences.saveFolder(folder, "Backups", "com.google.android.apps.docs.storage")
        preferences.setAutoEnabled(true)
        preferences.recordSuccess(5_000L, "backup.ironpulse")

        assertTrue(preferences.clearFolder())

        val config = preferences.config.value
        assertFalse(config.isConfigured)
        assertFalse(config.autoBackupEnabled)
        assertNull(config.folderUri)
        assertEquals(0L, config.lastSuccessEpochMillis)
    }

    @Test
    fun `changing destination does not show the old folder backup as the new one`() {
        val preferences = CloudBackupPreferences(context)
        preferences.saveFolder(folder, "Backups", "com.google.android.apps.docs.storage")
        preferences.recordSuccess(5_000L, "backup.ironpulse")

        val other = Uri.parse(
            "content://com.android.externalstorage.documents/tree/primary%3ADownload%2FBackups"
        )
        preferences.saveFolder(other, "Backups", "com.android.externalstorage.documents")

        val config = preferences.config.value
        assertEquals(0L, config.lastSuccessEpochMillis)
        assertNull(config.lastSuccessFileName)
        assertEquals("This device", config.providerLabel)
    }

    @Test
    fun `a dismissed failure stays dismissed`() {
        val preferences = CloudBackupPreferences(context)
        preferences.saveFolder(folder, "Backups", "com.google.android.apps.docs.storage")
        preferences.recordFailure(1_000L, "failed")
        assertTrue(preferences.clearFailure())
        assertNull(preferences.config.value.currentFailure)
    }
}
