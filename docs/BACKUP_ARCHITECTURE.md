# Export/import architecture

## Components

| Component | Responsibility |
|---|---|
| `BackupSchema.kt` | Frozen v1 DTOs, manifest/checksum models, runtime snapshot, progress and UI state, error codes. |
| `BackupArchiveCodec.kt` | ZIP writing, manifest/checksum generation, safe reading, size/path limits, SHA-256 verification. |
| `BackupJson.kt` | Central Moshi instance and strict byte-to-DTO parsing that rejects trailing data. |
| `JsonNode.kt` | Minimal JSON tree with exact numeric literals for migration transforms. |
| `BackupSchemaMigrations.kt` | Central contiguous migration registry and version gate. |
| `BackupMapper.kt` | Domain ↔ frozen DTO mapping. Drops excluded exercise media fields. |
| `BackupValidator.kt` | Semantic, numeric, relationship and settings validation before any write. |
| `BackupPreferencesStore.kt` | Explicit DataStore allowlist read/write, SharedPreferences library-edit record, rest-alert mirror, widget-photo file handling. |
| `UserDataBackupManager.kt` | Export orchestration, import preflight, Room replacement, seeding, verification, rollback. |
| `MainViewModel` | SAF URI handling, temp files, progress state, confirmation state, post-import UI refresh. |
| `SettingsScreen` | Data Management card, file pickers, progress/error states and destructive confirmation. |

The Room DAOs gained read-all/insert-all methods and guarded user-exercise deletion. No unrelated
repository behavior changed.

## Export flow

1. User taps **Export** in Settings → Data Management.
2. `ActivityResultContracts.CreateDocument("application/octet-stream")` asks for a destination. The
   suggested name is `IronPulse_Backup_YYYY-MM-DD.ironpulse`. The neutral binary type is deliberate:
   document providers that receive `application/zip` commonly append their own `.zip` and turn the
   filename into `.ironpulse.zip`.
3. `MainViewModel` creates a cache temp file.
4. `UserDataBackupManager.readSnapshot()`:
   - reads all Room tables in one `db.withTransaction`;
   - reads the DataStore allowlist once;
   - reads the referenced Home photo bytes and validates that the file is inside the app's widget
     photo directory.
5. `BackupValidator` validates the live snapshot.
6. `BackupMapper` builds all eight sections; the manifest's record counts come from the same snapshot.
7. `BackupArchiveWriter` writes a `.part` archive, computes SHA-256 checksums, writes the manifest last
   and finishes the ZIP.
8. `BackupArchiveReader.verify()` reopens the completed archive and verifies structure and checksums.
9. The verified archive is copied to the SAF output stream. If the provider reports a size, it is
   compared with the archive length. On a failed write, the provider document is deleted when the
   provider supports `DocumentsContract.deleteDocument`.
10. The temp file is always deleted in `finally`.

A category failure (missing widget photo, unreadable rest-timer JSON, unknown stored provider) fails
the whole export rather than creating a partial archive.

## Import flow

### Phase A — copy and validate

1. User taps **Import** and picks a file through `ActivityResultContracts.OpenDocument()`.
2. The URI is copied into a cache temp file with a hard byte limit. No destination data is touched.
3. `BackupArchiveReader.read()` performs the structural and checksum validation described in
   `BACKUP_FORMAT.md`.
4. If the data schema is older than the current one, `BackupSchemaMigrations.migrate()` transforms the
   JSON trees in memory. A future/unsupported version is rejected before this point.
5. `BackupMapper` materializes the current snapshot.
6. `BackupValidator` validates ids, numeric values, dates, exercise ownership, every set-to-session
   and message-to-conversation relationship, edits, dashboard photo state and preference values.
7. Manifest record counts must equal the restored counts.
8. Only after all of the above does the UI show the destructive confirmation with the backup's file
   name, creation time, source version, size and category counts.

### Phase B — replace after confirmation

1. The ViewModel resets transient in-memory state (active session, rest timer, pending draft, chat
   selection) so the autosave loop cannot overwrite the restoring preferences.
2. The manager reads a **rollback snapshot** of the current install, including its preferences and
   Home photo path/bytes.
3. Identifier collisions with shipped library rows are checked. A different custom exercise with the
   same auto-generated id is not a conflict (it will be deleted).
4. The imported `editedLibraryExerciseIds` set is written to `ironpulse_library_edits` first, so the
   subsequent dataset reseed respects the restored edits.
5. One Room transaction:
   - deletes all logged sets, sessions, routines, schedule days, weigh-ins, chat messages, chat
     sessions, Gemini summaries, immutable post-workout summaries and the profile row;
   - deletes all destination custom/AI exercises and destination library rows whose names collide
     with restored custom exercises;
   - inserts the imported profile (with the destination's `customApiKey`), routines, exercises,
     sessions, sets, schedule days, weigh-ins, conversations, messages (including proposal
     kind/payload/status), Gemini summaries and post-workout summaries with their original ids.
6. `DatasetSeeder.seedFresh()` restores the shipped catalog and reverts destination library edits
   that are absent from the backup. Imported edited rows are skipped because their ids are in the
   tracker.
7. The imported Home photo is written through a staging name and renamed. The destination's old file
   is not deleted yet.
8. One DataStore edit removes every allowlisted exportable key and writes the imported profile,
   preferences, dashboard configuration, AI-access/summary switches, the three Android widget
   themes and the new widget-photo path. API keys, the avatar path, the cached release and
   update-prompt suppression are not in the removal list and are not written.
9. The rest-alert SharedPreferences mirror is rebuilt from the imported switches.
10. Verification reads Room, DataStore and the widget file again and compares them with the imported
    snapshot. Any mismatch throws.
11. Only after verification: unreferenced widget photos are deleted, and media files attached to the
    replaced destination custom/AI exercises are removed.
12. Installed Android launcher widgets are refreshed so restored themes and profile/workout data
    appear immediately.
13. The UI success message appears. Room/DataStore flows have already emitted; transient state was
    reset and the imported draft is loaded if one exists.

## Full replacement semantics

The import deletes every user-owned row in every exported table. It never inserts into the old
dataset. Lists that are empty in the archive are still written as empty, so:

- zero routines in the backup → zero routines after import;
- zero conversations or messages → zero after import;
- zero weigh-ins → zero after import;
- zero custom/AI exercises → none after import;
- an absent preference key → removed, not kept from the destination.

The only user-owned content intentionally kept is the excluded local state: API keys, the profile
picture and its path, plus device-specific installation state.

## Credential and profile-picture preservation

- `gemini_api_key` and all provider `ai_api_key_*` keys are never in `EXPORTABLE_KEYS`, never removed,
  never written by the importer.
- `UserProfile.customApiKey` is excluded from the Room DTO. When an imported Room profile is written,
  it receives the destination's current value.
- `profile_avatar_path` and `files/profile_media/` are outside every read/write path.
- Tests set source and destination credentials and assert the destination values after import.

## Rollback

Import is not a single cross-store transaction. The manager tracks whether it has changed the
library-edit SharedPreferences, Room, the staged widget file and DataStore. On any exception,
including cancellation, it attempts each necessary inverse independently:

1. Reapply the rollback preferences/dashboard/photo path.
2. Delete a newly staged widget photo if it is not the old path.
3. Restore the old edited-library id set.
4. Replace Room with the rollback snapshot and reseed the catalog under the old edit set.

If every step succeeds, the user is told the import failed and the previous data was restored. If any
step fails, the import is reported as **not** safely restored with a `ROLLBACK_FAILED` error; the UI
does not claim preservation.

The failure paths tested include a simulated preference-write failure after the Room replacement and a
corrupt archive that never reaches the replacement phase.

## UI state and refresh

`DataTransferState` has `Idle`, `Running`, `ReadyToImport`, `Success`, `Error` and `Cancelled` states.
Progress phases are real stages of the work, not a timer. Buttons are disabled while running or while
a validated archive awaits confirmation. A pending import can be dismissed; an export or pre-apply
validation can be cancelled.

After a successful import the ViewModel stops the rest timer, clears the live session, sets, routine
workout, pending draft and chat selection, and reloads the imported draft. Room and DataStore flows
repopulate Home, Progress, workouts, routines, streaks, AI conversations, saved summaries, profile and
Settings. No manual force-stop or activity recreation is required; the existing theme, Liquid Glass
and fullscreen flow collectors apply the restored preferences.

## Security notes

- The archive is treated as hostile input: path traversal, duplicate entries, directory entries,
  unexpected roles, oversized/truncated data, compression bombs, malformed JSON, unsupported versions
  and bad checksums are rejected.
- No archive content is executed, logged or sent to an AI provider.
- Error messages describe the failure category without dumping record contents or secrets.
- Temporary archives are deleted in `finally`.
- The archive is not encrypted and the UI says so.
