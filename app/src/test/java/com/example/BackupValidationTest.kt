/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import com.example.data.backup.BackupErrorCode
import com.example.data.backup.BackupException
import com.example.data.backup.BackupValidator
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupValidationTest {

    private fun expect(code: BackupErrorCode, block: () -> Unit) {
        try {
            block()
            org.junit.Assert.fail("expected ${code.name}")
        } catch (e: BackupException) {
            assertEquals(code, e.code)
        }
    }

    @Test
    fun `the representative snapshot validates`() {
        BackupValidator.validate(BackupTestFixtures.sampleSnapshot())
    }

    @Test
    fun `a logged set without its workout is a relationship error`() {
        val snapshot = BackupTestFixtures.sampleSnapshot()
        val orphan = snapshot.copy(
            sets = snapshot.sets.map { it.copy(sessionId = 999L) }
        )
        expect(BackupErrorCode.RELATIONSHIP_ERROR) { BackupValidator.validate(orphan) }
    }

    @Test
    fun `an AI message without its conversation is a relationship error`() {
        val snapshot = BackupTestFixtures.sampleSnapshot()
        val orphan = snapshot.copy(
            chatMessages = snapshot.chatMessages.map { it.copy(sessionId = "missing") }
        )
        expect(BackupErrorCode.RELATIONSHIP_ERROR) { BackupValidator.validate(orphan) }
    }

    @Test
    fun `duplicate workout identifiers are rejected`() {
        val snapshot = BackupTestFixtures.sampleSnapshot()
        val duplicate = snapshot.copy(
            sessions = snapshot.sessions + snapshot.sessions.first()
        )
        expect(BackupErrorCode.INVALID_DATA) { BackupValidator.validate(duplicate) }
    }

    @Test
    fun `duplicate exercise names are rejected case insensitively`() {
        val snapshot = BackupTestFixtures.sampleSnapshot()
        val duplicate = snapshot.copy(
            exercises = snapshot.exercises + snapshot.exercises.first().copy(
                id = 777L,
                name = snapshot.exercises.first().name.uppercase()
            )
        )
        expect(BackupErrorCode.INVALID_DATA) { BackupValidator.validate(duplicate) }
    }

    @Test
    fun `an edited library id must have its row in the section`() {
        val snapshot = BackupTestFixtures.sampleSnapshot()
        val broken = snapshot.copy(editedLibraryExerciseIds = setOf(9001L, 12345L))
        expect(BackupErrorCode.INVALID_DATA) { BackupValidator.validate(broken) }
    }

    @Test
    fun `an unedited library row is not valid backup content`() {
        val snapshot = BackupTestFixtures.sampleSnapshot()
        val broken = snapshot.copy(editedLibraryExerciseIds = emptySet())
        expect(BackupErrorCode.INVALID_DATA) { BackupValidator.validate(broken) }
    }

    @Test
    fun `a missing Home photo is rejected`() {
        val snapshot = BackupTestFixtures.sampleSnapshot().copy(widgetPhoto = null)
        expect(BackupErrorCode.INVALID_DATA) { BackupValidator.validate(snapshot) }
    }

    @Test
    fun `an unknown theme is rejected`() {
        val snapshot = BackupTestFixtures.sampleSnapshot()
        val broken = snapshot.copy(
            preferences = snapshot.preferences.copy(themeMode = "neon")
        )
        expect(BackupErrorCode.INVALID_DATA) { BackupValidator.validate(broken) }
    }

    @Test
    fun `an AI endpoint with embedded credentials is rejected`() {
        val snapshot = BackupTestFixtures.sampleSnapshot()
        val ai = snapshot.preferences.ai!!.copy(
            customBaseUrl = "https://user:secret@example.com/v1?api_key=secret"
        )
        val broken = snapshot.copy(
            preferences = snapshot.preferences.copy(ai = ai)
        )
        expect(BackupErrorCode.INVALID_DATA) { BackupValidator.validate(broken) }
    }
}
