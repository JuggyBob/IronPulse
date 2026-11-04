/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import com.example.data.backup.BackupArchiveReader
import com.example.data.backup.BackupFormat
import com.example.data.backup.BackupMapper
import com.example.data.backup.BackupValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The committed v1 fixture is the compatibility contract for every future importer.
 *
 * When this test starts failing, do not edit the fixture to match the new code. Add a migration and
 * a new fixture instead. The full procedure is in docs/BACKUP_MIGRATION_GUIDE.md.
 */
class BackupV1FixtureTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `the initial format fixture still imports through the current schema`() {
        val bytes = javaClass.classLoader!!
            .getResourceAsStream("backups/v1-minimal.ironpulse")
            ?.use { it.readBytes() }
            ?: error("missing v1 fixture")
        val file = temp.newFile("v1-minimal.ironpulse")
        file.writeBytes(bytes)

        val payload = BackupArchiveReader(file).read()
        assertEquals(BackupFormat.CURRENT_FORMAT_VERSION, payload.manifest.formatVersion)
        assertEquals(1, payload.manifest.dataSchemaVersion)
        assertEquals("3.0.0", payload.manifest.appVersionName)

        val snapshot = BackupMapper.fromPayload(payload)
        BackupValidator.validate(snapshot)
        assertEquals("Test Athlete", snapshot.dataStoreProfile?.name)
        assertEquals(2, snapshot.sessions.size)
        assertEquals(4, snapshot.sets.size)
        assertEquals(2, snapshot.chatSessions.size)
        assertEquals(3, snapshot.chatMessages.size)
        assertEquals(1, snapshot.geminiSummaries.size)
        assertNotNull(snapshot.widgetPhoto)
        assertTrue(snapshot.widgetPhoto!!.bytes.isNotEmpty())
    }
}
