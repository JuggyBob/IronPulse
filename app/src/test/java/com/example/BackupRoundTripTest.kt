/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import com.example.data.backup.BackupArchiveReader
import com.example.data.backup.BackupMapper
import com.example.data.backup.BackupValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The archive has to preserve every field the exporter put in it, including identifiers, timestamps
 * and double precision, and the restored snapshot has to pass the same validation as a live one.
 */
class BackupRoundTripTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `archive round trip preserves every exported field`() {
        val original = BackupTestFixtures.sampleSnapshot()
        val file = temp.newFile("round-trip.ironpulse")
        BackupTestFixtures.writeArchive(original, file)

        val payload = BackupArchiveReader(file).read()
        val restored = BackupMapper.fromPayload(payload)
        BackupValidator.validate(restored)

        assertEquals(original.dataStoreProfile, restored.dataStoreProfile)
        assertEquals(original.roomProfile, restored.roomProfile)
        assertEquals(original.preferences, restored.preferences)
        assertEquals(original.dashboard, restored.dashboard)
        assertEquals(original.editedLibraryExerciseIds, restored.editedLibraryExerciseIds)
        assertEquals(original.exercises, restored.exercises)
        assertEquals(original.sessions, restored.sessions)
        assertEquals(original.sets, restored.sets)
        assertEquals(original.routines, restored.routines)
        assertEquals(original.scheduleDays, restored.scheduleDays)
        assertEquals(original.bodyWeightLogs, restored.bodyWeightLogs)
        assertEquals(original.chatSessions, restored.chatSessions)
        assertEquals(original.chatMessages, restored.chatMessages)
        assertEquals(original.geminiSummaries, restored.geminiSummaries)
        assertTrue(original.widgetPhoto!!.bytes.contentEquals(restored.widgetPhoto!!.bytes))
        assertEquals("image/jpeg", restored.widgetPhoto!!.mediaType)

        // The archive is a portable snapshot, not a copy of source-device paths.
        val entryNames = java.util.zip.ZipFile(file).use { zip ->
            zip.entries().asSequence().map { it.name }.toSet()
        }
        assertFalse(entryNames.any { it.contains("profile_media") })
        assertFalse(entryNames.any { it.contains("exercise_media") })
        assertTrue(entryNames.contains("manifest.json"))
        assertTrue(entryNames.contains("checksums.json"))
        assertTrue(entryNames.contains("payload/workouts.json"))
    }

    @Test
    fun `a snapshot without a Home photo stays without one`() {
        val original = BackupTestFixtures.sampleSnapshot(withPhoto = false)
        val file = temp.newFile("no-photo.ironpulse")
        BackupTestFixtures.writeArchive(original, file)
        val restored = BackupMapper.fromPayload(BackupArchiveReader(file).read())
        BackupValidator.validate(restored)
        assertEquals(null, restored.widgetPhoto)
        assertFalse(restored.dashboard.photoPresent)
    }
}
