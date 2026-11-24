/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import com.example.data.backup.BackupArchiveReader
import com.example.data.backup.BackupErrorCode
import com.example.data.backup.BackupException
import com.example.data.backup.BackupFormat
import com.example.data.backup.BackupMapper
import com.example.data.backup.BackupSchemaMigrations
import com.example.data.backup.BackupValidator
import com.example.data.backup.JsonNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The explicit field configuration has to survive the archive in both directions.
 *
 * Old backups have no `supportedFields` at all: they migrate to an empty value, which means "use
 * the type fallback" and never fabricates a configuration for a historical exercise.
 */
class ExerciseFieldBackupTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `supported fields survive a full archive round trip`() {
        val base = BackupTestFixtures.sampleSnapshot()
        val fields = "holdSeconds,addedWeight"
        val original = base.copy(
            exercises = base.exercises.mapIndexed { index, exercise ->
                if (index == 0) exercise.copy(supportedFields = fields) else exercise
            }
        )
        val file = temp.newFile("fields.ironpulse")
        BackupTestFixtures.writeArchive(original, file)

        val restored = BackupMapper.fromPayload(BackupArchiveReader(file).read())
        BackupValidator.validate(restored)

        assertEquals(fields, restored.exercises.first().supportedFields)
        assertEquals(original.exercises, restored.exercises)
    }

    @Test
    fun `a version 5 exercise without the field migrates to the legacy fallback`() {
        val sections = mutableMapOf(
            BackupFormat.SECTION_EXERCISES to JsonNode.parse(
                """
                {"exercises":[
                  {"id":1,"name":"Plank","category":"Core","bodyPart":"waist",
                   "target":"abs","secondaryMuscles":"[]","exerciseType":"PURE_BODYWEIGHT",
                   "instructions":"","description":"","equipment":"Bodyweight",
                   "mediaPath":"","sourceId":"0664","isCustom":false,"isAiGenerated":false}
                ]}
                """.trimIndent().toByteArray()
            ) as JsonNode.Obj
        )

        BackupSchemaMigrations.migrate(
            sections,
            fromVersion = 5,
            toVersion = 6,
            migrations = BackupSchemaMigrations.all
        )

        val exercises = sections.getValue(BackupFormat.SECTION_EXERCISES)["exercises"] as JsonNode.Arr
        val first = exercises.values.single() as JsonNode.Obj
        assertEquals("", (first["supportedFields"] as JsonNode.Str).value)
    }

    @Test
    fun `an explicit field list is not rewritten by the migration`() {
        val sections = mutableMapOf(
            BackupFormat.SECTION_EXERCISES to JsonNode.parse(
                """
                {"exercises":[
                  {"id":1,"name":"Plank","category":"Core","bodyPart":"waist",
                   "target":"abs","secondaryMuscles":"[]","exerciseType":"PURE_BODYWEIGHT",
                   "instructions":"","description":"","equipment":"Bodyweight",
                   "mediaPath":"","sourceId":"0664","isCustom":false,"isAiGenerated":false,
                   "supportedFields":"holdSeconds,addedWeight"}
                ]}
                """.trimIndent().toByteArray()
            ) as JsonNode.Obj
        )

        BackupSchemaMigrations.migrate(
            sections,
            fromVersion = 5,
            toVersion = 6,
            migrations = BackupSchemaMigrations.all
        )

        val exercises = sections.getValue(BackupFormat.SECTION_EXERCISES)["exercises"] as JsonNode.Arr
        val first = exercises.values.single() as JsonNode.Obj
        assertEquals(
            "holdSeconds,addedWeight",
            (first["supportedFields"] as JsonNode.Str).value
        )
    }

    @Test
    fun `the validator rejects invalid or conflicting field identifiers`() {
        fun snapshot(fields: String) = BackupTestFixtures.sampleSnapshot().let { base ->
            base.copy(
                exercises = base.exercises.mapIndexed { index, exercise ->
                    if (index == 0) exercise.copy(supportedFields = fields) else exercise
                }
            )
        }

        fun expectInvalid(fields: String) {
            try {
                BackupValidator.validate(snapshot(fields))
                org.junit.Assert.fail("expected INVALID_DATA for $fields")
            } catch (e: BackupException) {
                assertEquals(BackupErrorCode.INVALID_DATA, e.code)
            }
        }

        expectInvalid("reps,unknownField")
        expectInvalid("weight,addedWeight")
        expectInvalid("holdSeconds,timeSeconds")
        expectInvalid("reps,reps")
    }

    @Test
    fun `the current data schema is six`() {
        assertEquals(7, BackupFormat.CURRENT_DATA_SCHEMA_VERSION)
        assertTrue(
            BackupSchemaMigrations.all.any { it.fromVersion == 5 && it.toVersion == 6 }
        )
    }
}
