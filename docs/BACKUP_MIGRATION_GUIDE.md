# Backup schema migration guide

## Two independent versions

- `formatVersion` describes the ZIP/manifest structure.
- `dataSchemaVersion` describes the JSON payload semantics.

The Room schema version and the app `versionName` are deliberately **not** used to parse an archive.
Changing Room tables does not invalidate old backups.

The current values are both `1`, defined in `BackupFormat`.

## Why the DTOs are frozen

`BackupSchema.kt` is the on-disk contract. Do not rename, repurpose or delete a field in a shipped DTO,
even if the Kotlin property would read better. A rename changes the JSON key and breaks old archives.
Create a new DTO version and migrate old JSON into it.

`BackupJson` uses Moshi codegen adapters. Unknown fields are ignored by the current adapter, but that
is not a substitute for a migration: a field that carries user data must be mapped deliberately.

## Adding data schema v2

1. **Freeze the old DTOs.** Leave every `*V1` class and its field names untouched.
2. **Add new DTOs.** Add `*V2` classes only for sections whose shape actually changes. Unchanged
   sections can continue to use their v1 class.
3. **Add a migration.** Implement `BackupSchemaMigration`:

   ```kotlin
   object MigrateV1ToV2 : BackupSchemaMigration {
       override val fromVersion = 1
       override val toVersion = 2
       override fun migrate(sections: MutableMap<String, JsonNode.Obj>) {
           val profile = sections.getValue(BackupFormat.SECTION_PROFILE)
           // Rename, add with a documented default, or transform. Never silently drop user data.
           profile["newField"] = JsonNode.Null
       }
   }
   ```

4. **Register it.** Append it to `BackupSchemaMigrations.all`. The registry only accepts contiguous
   steps: v1→v2→v3, never a jump.
5. **Bump the version.** Set `BackupFormat.CURRENT_DATA_SCHEMA_VERSION = 2` and keep
   `MIN_SUPPORTED_DATA_SCHEMA_VERSION` at the oldest schema you still support. If you deliberately
   stop supporting v1, raise the minimum and document it; do not silently guess at v0 data.
6. **Change the current parser.** `BackupMapper.fromPayload` should parse v2 DTOs after migration.
   `inspectFile` already parses older payloads as `JsonNode`, migrates to the current schema, renders
   the bytes, and then runs the current mapper.
7. **Update the manifest.** New exports automatically use the new current data schema. Section paths
   can stay the same. If the container structure changes, bump `formatVersion` separately and update
   `BackupArchiveReader`.
8. **Add fixtures.**
   - Keep `app/src/test/resources/backups/v1-minimal.ironpulse` unchanged forever as the v1 fixture.
   - Generate a new `v2-*.ironpulse` fixture after the implementation is stable.
   - Add a test that imports each fixture and checks representative values and relationships.
9. **Test the migration path.** Add tests to `BackupSchemaMigrationTest` for:
   - the exact step sequence;
   - preservation of ids, timestamps and numeric literals;
   - documented defaults for new fields;
   - renamed fields;
   - enum changes;
   - a missing step failing rather than guessing;
   - a newer-than-current schema failing before destination changes.
10. **Update docs.** Add the version to `BACKUP_FORMAT.md` and this guide. Record what changed and why
    a default is safe.

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
- Missing migration steps are a programming error and produce `UNSUPPORTED_SCHEMA`; the UI reports it
  and the destination is untouched.

## Where the code lives

- `BackupFormat` — constants.
- `BackupSchema.kt` — DTOs and snapshot.
- `JsonNode.kt` — mutable JSON tree with exact numeric literals.
- `BackupSchemaMigrations.kt` — registry and range gate.
- `UserDataBackupManager.inspectFile` — migration call site.
- `BackupV1FixtureTest`, `BackupSchemaMigrationTest` — compatibility tests.
