# Backup schema migration guide

## Two independent versions

- `formatVersion` describes the ZIP/manifest structure.
- `dataSchemaVersion` describes the JSON payload semantics.

The Room schema version and the app `versionName` are deliberately **not** used to parse an archive.
Changing Room tables does not invalidate old backups.

The current values are `formatVersion = 1` and **`dataSchemaVersion = 7`**, defined in
`BackupFormat`.

## Data schema history

| Version | Migration | What it adds | Safe defaults |
|---|---|---|---|
| 1 | — | Initial frozen DTOs | — |
| 2 | `MigrateV1ToV2` | Post-workout summaries, three widget themes, AI workout-data and post-workout-summary switches | Empty summary list, `ironpulse` widget themes, both switches `false` |
| 3 | `MigrateV2ToV3` | Schedules and seven weekday slots, routine model versions | Empty schedule/slot arrays; legacy routine rows stay `modelVersion = 0` and are converted after restore |
| 4 | `MigrateV3ToV4` | `isWarmup`, `restSecondsOverride` | `false`, `null` (normal, inherited set) |
| 5 | `MigrateV4ToV5` | `setUid`, `parentSetUid`, `dropIndex` | Deterministic `legacy-<id>` UID, `null` parent, `0` index; no row is guessed into a drop set |
| 6 | `MigrateV5ToV6` | `exercises.supportedFields` | Empty string, which keeps the legacy type fallback |
| 7 | `MigrateV6ToV7` | Nullable per-set `effortRpe` and `supersetGroupId`, and the `effortDisplay` preference | `null` for both set fields (historical sets stay effort-free and ungrouped); `effortDisplay` defaults to `RIR` |

The registry only accepts contiguous steps: v1→v2→v3→v4→v5→v6→v7, never a jump. A missing step is a
programming error and produces `UNSUPPORTED_SCHEMA` before destination data is touched.

## What a 4.4.0 archive looks like

A 4.4.0 export is data schema 2. It has:

- `profile` and `preferences`, including `activeRoutineId`;
- `dashboard`;
- user-owned `exercises`;
- `workouts` (sessions and sets without warm-up/drop fields);
- `routines` with multi-day JSON and materialized schedule days;
- `progress` and `ai`.

On import, the archive is migrated to schema 7 in memory. The legacy multi-day routines are then
converted by the same Room migration used for an in-place upgrade: one Schedule plus one standalone
Routine per distinct workout day. If the archive carried `activeRoutineId`, the migrated Schedule is
promoted to active once it exists.

The 4.4.0 → 5.0.0/5.0.1 release validation restored a real schema-2 archive into a fresh install;
the two logged sets survived and the active Schedule appeared automatically.

Schema 6 → 7 is additive. `workouts.sets` gains nullable `effortRpe` and `supersetGroupId`,
`routines.exercisesJson` may carry `targetEffortRpe` and `supersetGroupId`, and `preferences`
gains `effortDisplay` (default `RIR`; RIR is a display of the canonical RPE 6–10 value). A schema-6
archive imports with those fields absent; the app treats them as null and keeps every historical set
ungrouped and effort-free. Import validation rejects a present but invalid RPE, a malformed group id,
or a group that does not contain exactly two exercises in one session.

## Why the DTOs are frozen

`BackupSchema.kt` is the on-disk contract. Do not rename, repurpose or delete a field in a shipped
DTO, even if the Kotlin property would read better. A rename changes the JSON key and breaks old
archives. Create a new DTO version and migrate old JSON into it.

`BackupJson` uses Moshi codegen adapters. Unknown fields are ignored by the current adapter, but that
is not a substitute for a migration: a field that carries user data must be mapped deliberately.

## Adding a data schema version

1. **Freeze the old DTOs.** Leave every shipped class and its field names untouched.
2. **Add new DTOs.** Add a new version only for sections whose shape actually changes. Unchanged
   sections can continue to use their existing class.
3. **Add a migration.** Implement `BackupSchemaMigration`:

   ```kotlin
   object MigrateV7ToV8 : BackupSchemaMigration {
       override val fromVersion = 7
       override val toVersion = 8
       override fun migrate(sections: MutableMap<String, JsonNode.Obj>) {
           val profile = sections.getValue(BackupFormat.SECTION_PROFILE)
           // Rename, add with a documented default, or transform. Never silently drop user data.
           profile["newField"] = JsonNode.Null
       }
   }
   ```

4. **Register it.** Append it to `BackupSchemaMigrations.all`.
5. **Bump the version.** Set `BackupFormat.CURRENT_DATA_SCHEMA_VERSION = 8` and keep
   `MIN_SUPPORTED_DATA_SCHEMA_VERSION` at the oldest schema you still support.
6. **Change the current parser.** `BackupMapper.fromPayload` should parse the new DTOs after
   migration. `inspectFile` already parses older payloads as `JsonNode`, migrates to the current
   schema, renders the bytes, and then runs the current mapper.
7. **Update the manifest.** New exports automatically use the new current data schema. Section paths
   can stay the same. If the container structure changes, bump `formatVersion` separately.
8. **Add fixtures.**
   - Keep `app/src/test/resources/backups/v1-minimal.ironpulse` unchanged forever as the v1 fixture.
   - Keep representative v2/v4/v6 fixtures for the drop-set and field migrations.
   - Add a test that imports each fixture and checks representative values and relationships.
9. **Test the migration path.** Add tests for the exact step sequence, id/timestamp/numeric
   preservation, documented defaults, renamed fields, enum changes, a missing step failing rather
   than guessing, and a newer-than-current schema failing before destination changes.
10. **Update docs.** Add the version to `BACKUP_FORMAT.md` and this guide.

## Rules for migrations

1. Deterministic: the same input always produces the same output.
2. No device, clock, locale, network or app-version dependency.
3. No silent data loss. Removed fields need a deliberate mapping or a hard failure.
4. Preserve ids and relationships unless a complete old→new mapping is written and tested.
5. New fields get safe, documented defaults. A missing value is not zero unless zero means
   "not recorded".
6. Enum changes are mapped explicitly; unknown values fail rather than fall through to a random
   default.
7. Old migration code is permanent. Do not delete it when a new version ships.
8. Migrations run in memory after full archive validation and before the destination is touched.

## Do not edit a fixture to make a test pass

`BackupV1FixtureTest` protects the initial format. If it fails after a schema change, the bug is in
the migration or the parser, not in the fixture. Editing the fixture erases the compatibility
guarantee.

## Unsupported versions

- A backup with `dataSchemaVersion > CURRENT` is rejected with "newer user data" before any data is
  changed.
- A backup with `formatVersion > CURRENT` is rejected with "newer IronPulse format".
- A backup below the minimum supported version is rejected without guessing.
- Missing migration steps produce `UNSUPPORTED_SCHEMA`; the UI reports it and the destination is
  untouched.

## Where the code lives

- `BackupFormat` — constants.
- `BackupSchema.kt` — DTOs and snapshot.
- `JsonNode.kt` — mutable JSON tree with exact numeric literals.
- `BackupSchemaMigrations.kt` — registry and range gate.
- `UserDataBackupManager.inspectFile` — migration call site.
- `BackupV1FixtureTest`, `BackupSchemaMigrationTest`, `DropSetMigrationTest`,
  `ExerciseFieldBackupTest` — compatibility tests.
