package com.example

import com.example.data.backup.ArchivePayload
import com.example.data.backup.BackupFormat
import com.example.data.backup.BackupManifest
import com.example.data.backup.BackupMapper
import com.example.data.backup.BackupSchemaMigrations
import com.example.data.backup.LoggedSetV2
import com.example.data.backup.WorkoutsSectionV2
import com.example.data.backup.JsonNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Backup compatibility for the two new set fields.
 *
 * A v3 archive has no warm-up flag and no per-set rest. It must become a normal, inherited set. A
 * v4 archive must preserve both a true and a false value and a null and non-null rest exactly.
 */
class WarmupBackupTest {

    private fun snapshotWith(set: LoggedSetV2) =
        BackupTestFixtures.sampleSnapshot(withPhoto = false).copy(
            sets = listOf(set)
        )

    private fun payload(snapshot: com.example.data.backup.UserDataSnapshot, schema: Int) =
        ArchivePayload(
            manifest = BackupManifest(
                format = BackupFormat.MAGIC,
                formatVersion = 1,
                dataSchemaVersion = schema,
                appVersionName = "4.6.0",
                appVersionCode = 15,
                exportedAt = "2026-11-12T00:00:00Z",
                exportedAtEpochMillis = BackupTestFixtures.EXPORT_EPOCH,
                sections = emptyMap(),
                files = emptyList()
            ),
            sections = BackupMapper.toSections(snapshot),
            files = emptyMap()
        )

    private fun v4Set(isWarmup: Boolean, rest: Int?) = LoggedSetV2(
        id = 1L,
        sessionId = 10L,
        exerciseId = 42L,
        exerciseName = "Barbell Front Squat",
        exerciseType = "STANDARD_WEIGHT",
        setNumber = 1,
        weight = 80.0,
        addedWeight = 0.0,
        reps = 5,
        holdDurationSeconds = 0,
        distanceKm = 0.0,
        isCompleted = true,
        timestamp = 1_760_000_000_000L,
        bodyWeightKg = 75.0,
        isWarmup = isWarmup,
        restSecondsOverride = rest
    )

    @Test
    fun `v4 round trip preserves warmup and set rest`() {
        val original = snapshotWith(v4Set(isWarmup = true, rest = 120))
        val restored = BackupMapper.fromPayload(payload(original, schema = 4))

        assertEquals(1, restored.sets.size)
        assertTrue(restored.sets.first().isWarmup)
        assertEquals(120, restored.sets.first().restSecondsOverride)
        assertEquals(80.0, restored.sets.first().weight, 0.001)
        assertEquals(5, restored.sets.first().reps)
    }

    @Test
    fun `v3 archive without the fields becomes normal with inherited rest`() {
        val legacy = snapshotWith(v4Set(isWarmup = true, rest = 120))
        val sections = BackupMapper.toSections(legacy).toMutableMap()

        // Recreate the v3 document shape: the two fields did not exist yet.
        val trees = LinkedHashMap<String, JsonNode.Obj>()
        for ((name, bytes) in sections) {
            val node = JsonNode.parse(bytes) as JsonNode.Obj
            trees[name] = node
        }
        val workouts = trees.getValue(BackupFormat.SECTION_WORKOUTS)
        val sets = workouts["sets"] as JsonNode.Arr
        (sets[0] as JsonNode.Obj).remove("isWarmup")
        (sets[0] as JsonNode.Obj).remove("restSecondsOverride")

        val migratedTrees = BackupSchemaMigrations.migrate(trees, fromVersion = 3, toVersion = 4)
        val migrated = migratedTrees.mapValues { (_, node) -> node.render().toByteArray(Charsets.UTF_8) }
        val payload = ArchivePayload(
            manifest = payload(legacy, schema = 3).manifest,
            sections = migrated,
            files = emptyMap()
        )
        val restored = BackupMapper.fromPayload(payload)

        assertEquals(1, restored.sets.size)
        assertFalse(restored.sets.first().isWarmup)
        assertNull(restored.sets.first().restSecondsOverride)
        assertEquals(80.0, restored.sets.first().weight, 0.001)
    }

    @Test
    fun `v4 mapper writes the new fields into the workouts section`() {
        val sections = BackupMapper.toSections(
            snapshotWith(v4Set(isWarmup = false, rest = null))
        )
        val workouts = JsonNode.parse(sections.getValue(BackupFormat.SECTION_WORKOUTS)) as JsonNode.Obj
        val sets = workouts["sets"] as JsonNode.Arr
        val set = sets[0] as JsonNode.Obj
        assertFalse((set["isWarmup"] as JsonNode.Bool).value)
        // Moshi omits a null property rather than writing an explicit null; both mean inherited.
        val rest = set["restSecondsOverride"]
        assertTrue(rest == null || rest is JsonNode.Null)
    }
}
