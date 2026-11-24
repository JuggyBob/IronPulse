/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.data.backup

/**
 * One deterministic step from an older backup data schema to the next one.
 *
 * A migration receives every payload section as a mutable JSON tree. It must only add, rename or
 * reinterpret fields; it must never drop user data silently. If a field truly cannot be carried
 * forward, the migration must either map it to a documented replacement or fail loudly.
 */
interface BackupSchemaMigration {
    val fromVersion: Int
    val toVersion: Int
    fun migrate(sections: MutableMap<String, JsonNode.Obj>)
}

/**
 * Central migration registry. New versions append one migration per step and never remove old ones.
 *
 * The importer only accepts the contiguous range declared here. A backup from a newer schema is
 * rejected before any destination data is touched; a backup older than the minimum supported
 * version is rejected with a clear message rather than guessed at.
 */
object BackupSchemaMigrations {

    /**
     * v2 adds post-workout summaries and the three native-widget theme preferences. Both are
     * additive: an old backup simply has no rows and no explicit widget appearance.
     */
    object MigrateV1ToV2 : BackupSchemaMigration {
        override val fromVersion: Int = 1
        override val toVersion: Int = 2

        override fun migrate(sections: MutableMap<String, JsonNode.Obj>) {
            val ai = sections[BackupFormat.SECTION_AI]
            if (ai != null && ai["workoutSummaries"] == null) {
                ai["workoutSummaries"] = JsonNode.Arr()
            }
            val preferences = sections[BackupFormat.SECTION_PREFERENCES]
            if (preferences != null) {
                if (preferences["widgetThemes"] == null) {
                    preferences["widgetThemes"] = JsonNode.Obj(
                        linkedMapOf(
                            "streak" to JsonNode.Str("ironpulse"),
                            "weekly" to JsonNode.Str("ironpulse"),
                            "profile" to JsonNode.Str("ironpulse")
                        )
                    )
                }
                // New permissions default to off when an old archive is restored.
                if (preferences["aiWorkoutDataAccess"] == null) {
                    preferences["aiWorkoutDataAccess"] = JsonNode.Bool(false)
                }
                if (preferences["postWorkoutAiSummaries"] == null) {
                    preferences["postWorkoutAiSummaries"] = JsonNode.Bool(false)
                }
            }
        }
    }

    /**
     * v3 adds the normalized Schedule layer. Legacy multi-day routines remain in `routines`; the
     * importer converts them after restore. New-format archives carry `schedules` and their seven
     * weekday slots alongside the existing frozen routine DTOs.
     */
    object MigrateV2ToV3 : BackupSchemaMigration {
        override val fromVersion: Int = 2
        override val toVersion: Int = 3

        override fun migrate(sections: MutableMap<String, JsonNode.Obj>) {
            val routines = sections[BackupFormat.SECTION_ROUTINES] ?: return
            if (routines["schedules"] == null) routines["schedules"] = JsonNode.Arr()
            if (routines["scheduleSlots"] == null) routines["scheduleSlots"] = JsonNode.Arr()
            // Old routine rows are intentionally modelVersion 0; the DB migration converts them.
        }
    }

    /**
     * v4 adds per-set warm-up and rest override fields.
     *
     * Both are additive. Legacy sets become normal sets (`isWarmup = false`) and inherit the old
     * rest behaviour (`restSecondsOverride = null`). No existing value is touched.
     */
    object MigrateV3ToV4 : BackupSchemaMigration {
        override val fromVersion: Int = 3
        override val toVersion: Int = 4

        override fun migrate(sections: MutableMap<String, JsonNode.Obj>) {
            val workouts = sections[BackupFormat.SECTION_WORKOUTS] ?: return
            val sets = workouts["sets"] as? JsonNode.Arr ?: return
            sets.values.forEach { node ->
                val set = node as? JsonNode.Obj ?: return@forEach
                if (set["isWarmup"] == null) set["isWarmup"] = JsonNode.Bool(false)
                if (set["restSecondsOverride"] == null) {
                    set["restSecondsOverride"] = JsonNode.Null
                }
            }
        }
    }

    /**
     * v5 adds stable set UIDs and the optional drop-set parent link.
     *
     * Existing sets become top-level main sets with deterministic `legacy-<id>` UIDs. No row is
     * classified as a drop set, and no historical value is touched.
     */
    object MigrateV4ToV5 : BackupSchemaMigration {
        override val fromVersion: Int = 4
        override val toVersion: Int = 5

        override fun migrate(sections: MutableMap<String, JsonNode.Obj>) {
            val workouts = sections[BackupFormat.SECTION_WORKOUTS] ?: return
            val sets = workouts["sets"] as? JsonNode.Arr ?: return
            sets.values.forEachIndexed { index, node ->
                val set = node as? JsonNode.Obj ?: return@forEachIndexed
                val id = (set["id"] as? JsonNode.Num)?.toLong() ?: 0L
                if (set["setUid"] == null || (set["setUid"] as? JsonNode.Str)?.value.isNullOrBlank()) {
                    set["setUid"] = JsonNode.Str(if (id > 0L) "legacy-$id" else "legacy-set-$index")
                }
                if (set["parentSetUid"] == null) set["parentSetUid"] = JsonNode.Null
                if (set["dropIndex"] == null) set["dropIndex"] = JsonNode.Num("0")
            }
        }
    }

    /**
     * v6 adds the explicit per-exercise field list. Old exercises are left empty: the type fallback
     * remains authoritative until the catalogue reseed or the athlete configures fields.
     */
    object MigrateV5ToV6 : BackupSchemaMigration {
        override val fromVersion: Int = 5
        override val toVersion: Int = 6

        override fun migrate(sections: MutableMap<String, JsonNode.Obj>) {
            val exercises = sections[BackupFormat.SECTION_EXERCISES] ?: return
            val rows = exercises["exercises"] as? JsonNode.Arr ?: return
            rows.values.forEach { node ->
                val exercise = node as? JsonNode.Obj ?: return@forEach
                if (exercise["supportedFields"] == null) {
                    exercise["supportedFields"] = JsonNode.Str("")
                }
            }
        }
    }

    /**
     * v7 adds superset group IDs and canonical effort. Both are additive and null for every
     * historical row: no relationship or effort is inferred from old data.
     */
    object MigrateV6ToV7 : BackupSchemaMigration {
        override val fromVersion: Int = 6
        override val toVersion: Int = 7

        override fun migrate(sections: MutableMap<String, JsonNode.Obj>) {
            val workouts = sections[BackupFormat.SECTION_WORKOUTS] ?: return
            val sets = workouts["sets"] as? JsonNode.Arr ?: return
            sets.values.forEach { node ->
                val set = node as? JsonNode.Obj ?: return@forEach
                if (set["supersetGroupId"] == null) set["supersetGroupId"] = JsonNode.Null
                if (set["effortRpe"] == null) set["effortRpe"] = JsonNode.Null
            }
            val preferences = sections[BackupFormat.SECTION_PREFERENCES]
            if (preferences != null && preferences["effortDisplay"] == null) {
                preferences["effortDisplay"] = JsonNode.Str("RIR")
            }
        }
    }

    val all: List<BackupSchemaMigration> = listOf(
        MigrateV1ToV2,
        MigrateV2ToV3,
        MigrateV3ToV4,
        MigrateV4ToV5,
        MigrateV5ToV6,
        MigrateV6ToV7
    )

    fun migrate(
        sections: MutableMap<String, JsonNode.Obj>,
        fromVersion: Int,
        toVersion: Int = BackupFormat.CURRENT_DATA_SCHEMA_VERSION,
        migrations: List<BackupSchemaMigration> = all
    ): MutableMap<String, JsonNode.Obj> {
        if (fromVersion == toVersion) return sections
        if (fromVersion > toVersion) {
            throw BackupException(
                "This backup uses newer IronPulse data (schema v$fromVersion) than this app can read.",
                BackupErrorCode.UNSUPPORTED_SCHEMA
            )
        }
        if (fromVersion < BackupFormat.MIN_SUPPORTED_DATA_SCHEMA_VERSION) {
            throw BackupException(
                "This backup uses an older IronPulse data schema that this app no longer supports.",
                BackupErrorCode.UNSUPPORTED_SCHEMA
            )
        }

        var current = fromVersion
        while (current < toVersion) {
            val step = migrations.firstOrNull {
                it.fromVersion == current && it.toVersion == current + 1
            } ?: throw BackupException(
                "No migration path exists from backup schema v$current to v${current + 1}.",
                BackupErrorCode.UNSUPPORTED_SCHEMA
            )
            step.migrate(sections)
            current = step.toVersion
        }
        return sections
    }
}
