/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import com.example.data.backup.cloud.CloudBackupConfig
import com.example.data.backup.cloud.CloudBackupFile
import com.example.data.backup.cloud.CloudBackupNaming
import com.example.data.backup.cloud.CloudBackupProviders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

class CloudBackupNamingTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val baseMillis = Instant.parse("2026-11-12T21:49:07Z").toEpochMilli()

    @Test
    fun `file name is readable and timestamped`() {
        assertEquals(
            "IronPulse_Cloud_Backup_2026-11-12_21-49-07.ironpulse",
            CloudBackupNaming.fileName(baseMillis, utc)
        )
    }

    @Test
    fun `same second gets a suffix instead of overwriting`() {
        val base = CloudBackupNaming.fileName(baseMillis, utc)
        val second = CloudBackupNaming.uniqueFileName(baseMillis, setOf(base), utc)
        assertEquals("IronPulse_Cloud_Backup_2026-11-12_21-49-07_2.ironpulse", second)
        val third = CloudBackupNaming.uniqueFileName(baseMillis, setOf(base, second), utc)
        assertEquals("IronPulse_Cloud_Backup_2026-11-12_21-49-07_3.ironpulse", third)
    }

    @Test
    fun `retention keeps the newest cloud backups and ignores other files`() {
        val names = mutableListOf(
            "IronPulse_Backup_2026-11-01.ironpulse",
            "notes.txt",
            "IronPulse_Cloud_Backup_2026-11-12_21-49-07.ironpulse.partial"
        )
        for (day in 1..7) {
            names += "IronPulse_Cloud_Backup_2026-11-0${day}_10-00-00.ironpulse"
        }

        val victims = CloudBackupNaming.retentionVictims(names)

        assertEquals(
            listOf(
                "IronPulse_Cloud_Backup_2026-11-02_10-00-00.ironpulse",
                "IronPulse_Cloud_Backup_2026-11-01_10-00-00.ironpulse"
            ),
            victims
        )
        assertFalse(victims.any { it.startsWith("IronPulse_Backup_") })
        assertFalse(victims.any { it.endsWith(".partial") })
    }

    @Test
    fun `retention with a custom keep count only considers our files`() {
        val names = (1..4).map { "IronPulse_Cloud_Backup_2026-11-0${it}_10-00-00.ironpulse" } +
            "manual.ironpulse"
        assertEquals(
            listOf("IronPulse_Cloud_Backup_2026-11-02_10-00-00.ironpulse", "IronPulse_Cloud_Backup_2026-11-01_10-00-00.ironpulse"),
            CloudBackupNaming.retentionVictims(names, keep = 2)
        )
    }

    @Test
    fun `the newest restorable archive can be a manual export or a cloud backup`() {
        val files = listOf(
            CloudBackupFile(
                name = "IronPulse_Backup_2026-11-01.ironpulse",
                uri = "content://manual",
                sizeBytes = 10,
                modifiedAt = 1_000L
            ),
            CloudBackupFile(
                name = "IronPulse_Cloud_Backup_2026-11-12_21-49-07.ironpulse",
                uri = "content://cloud",
                sizeBytes = 20,
                modifiedAt = 2_000L
            ),
            CloudBackupFile(
                name = "IronPulse_Cloud_Backup_2026-11-13_21-49-07.ironpulse.partial",
                uri = "content://partial",
                sizeBytes = 30,
                modifiedAt = 3_000L
            ),
            CloudBackupFile(
                name = "notes.txt",
                uri = "content://notes",
                sizeBytes = 1,
                modifiedAt = 4_000L
            )
        )

        assertEquals("content://cloud", CloudBackupNaming.newestBackup(files)?.uri)
        assertTrue(CloudBackupNaming.isRestorableBackup("IronPulse_Backup_2026-11-01.ironpulse"))
        assertFalse(CloudBackupNaming.isRestorableBackup("IronPulse_Backup_2026-11-01.ironpulse.partial"))
        assertFalse(CloudBackupNaming.isRestorableBackup("notes.txt"))
    }

    @Test
    fun `no restorable archive returns null`() {
        assertNull(
            CloudBackupNaming.newestBackup(
                listOf(
                    CloudBackupFile("notes.txt", "content://notes", 1, 1_000L),
                    CloudBackupFile("backup.ironpulse.partial", "content://partial", 1, 2_000L)
                )
            )
        )
    }

    @Test
    fun `provider labels and network needs are user friendly`() {
        assertEquals("Google Drive", CloudBackupProviders.label("com.google.android.apps.docs.storage"))
        assertEquals("OneDrive", CloudBackupProviders.label("com.microsoft.skydrive"))
        assertEquals("This device", CloudBackupProviders.label("com.android.externalstorage.documents"))
        assertEquals("Downloads", CloudBackupProviders.label("com.android.providers.downloads.documents"))
        assertEquals("Document provider", CloudBackupProviders.label(null))
        assertTrue(CloudBackupProviders.requiresNetwork("com.google.android.apps.docs.storage"))
        assertTrue(CloudBackupProviders.requiresNetwork("com.microsoft.skydrive"))
        assertFalse(CloudBackupProviders.requiresNetwork("com.android.externalstorage.documents"))
        assertFalse(CloudBackupProviders.requiresNetwork("com.android.providers.downloads.documents"))
    }

    @Test
    fun `an old failure is hidden by a newer success`() {
        val failed = CloudBackupConfig(
            lastSuccessEpochMillis = 1_000L,
            lastFailureEpochMillis = 2_000L,
            lastFailureMessage = "failed"
        )
        assertEquals("failed", failed.currentFailure)
        val recovered = failed.copy(lastSuccessEpochMillis = 3_000L)
        assertEquals(null, recovered.currentFailure)
    }
}
