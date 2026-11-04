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

    /** No migration exists yet: schema v1 is the first format this app can read. */
    val all: List<BackupSchemaMigration> = emptyList()

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
