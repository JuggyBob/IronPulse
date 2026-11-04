# IronPulse backup format

## Container

- Extension: `.ironpulse`
- MIME type used by the create-document picker: `application/octet-stream` (neutral binary type, so
  providers do not append `.zip` to the `.ironpulse` filename). Import accepts ZIP and binary types.
- Container: standard ZIP, written with `java.util.zip`
- Suggested name: `IronPulse_Backup_YYYY-MM-DD.ironpulse`
- The archive is **not encrypted**. The Settings screen says so and tells the athlete to store it
  securely.

## Internal structure

```text
IronPulse_Backup_2026-11-04.ironpulse
├── manifest.json
├── checksums.json
├── payload/
│   ├── profile.json
│   ├── preferences.json
│   ├── dashboard.json
│   ├── exercises.json
│   ├── workouts.json
│   ├── routines.json
│   ├── progress.json
│   └── ai.json
└── files/
    └── widget_photo.jpg        # only when a Home photo is present
```

Every section is written even when its list is empty. An empty section is meaningful and clears the
destination category on import.

## Manifest

`manifest.json` is a Moshi `BackupManifest` document:

| Field | Meaning |
|---|---|
| `format` | Always `ironpulse-backup`. |
| `formatVersion` | Container/manifest structure version. Currently `1`. |
| `dataSchemaVersion` | Serialized user-data schema. Currently `2`. It is independent of both the app version and the Room schema. |
| `appVersionName`, `appVersionCode` | Source IronPulse version. Informational; never used to decide how to parse. |
| `exportedAt` | ISO-8601 UTC timestamp with milliseconds. |
| `exportedAtEpochMillis` | Same instant in epoch milliseconds, for display without timezone parsing. |
| `serialization` | `json`. |
| `archive` | `zip`. |
| `checksumsFile` | `checksums.json`. |
| `sections` | Map of logical section name to `{ path, required, recordCount }`. |
| `files` | List of `{ path, role, mediaType, size, sha256 }`. At most one file is supported in v1 (`home_widget_photo`). |
| `migrationNote` | Human-readable note; migrations are driven by version numbers, not this string. |

The manifest contains no credentials, no profile picture path and no absolute source-device path.

## Checksums

`checksums.json` contains an algorithm name (`SHA-256`) and a map from every payload/file entry path
to its lowercase SHA-256 hex digest. The two metadata entries (`manifest.json`, `checksums.json`) are
not self-checked.

During import the reader verifies:

- The file exists, is non-empty, is within the archive-size limit and begins with the ZIP signature.
- Every ZIP entry name is safe: no absolute path, no drive colon, no backslash, no `..`, no empty
  segment, no duplicate name, no directory entry.
- The manifest parses, uses the expected format/serialization/archive values, has a supported format
  and data-schema version, and has bounded metadata strings.
- Every required section is listed with its exact expected path and is present.
- There are no unknown sections or unknown file roles.
- The checksum key set is exactly the manifest's section/file path set.
- The ZIP entry set is exactly the checksum set plus `manifest.json` and `checksums.json`.
- Every entry's actual SHA-256 matches its checksum.
- Every file's size matches its manifest entry.
- Every data section's `recordCount` matches the restored DTO's count. The `preferences` count is
  advisory so an older archive keeps importing after additive preference fields are introduced.

A failure at any point produces a user-facing error and leaves destination data untouched.

## Sections

| Section | DTO | Contents |
|---|---|---|
| `profile` | `ProfileSectionV1` | DataStore profile fields and Room profile fields; `customApiKey` is not present. |
| `preferences` | `PreferencesSectionV1` | Explicit allowlist of non-secret preferences, benchmark records, rest timers, AI provider/model/tool-mode selections, the AI Coach workout-data and post-workout-summary switches, and the three independent Android widget themes. |
| `dashboard` | `DashboardSectionV1` | Widget order, hidden ids, weekly goal and Home photo metadata. |
| `exercises` | `ExerciseSectionV1` | User-created/AI exercises, athlete-edited library rows, and the edited-library id set. Local media fields are absent. |
| `workouts` | `WorkoutsSectionV1` | All `WorkoutSessionV1` rows and all `LoggedSetV1` rows, ids preserved. |
| `routines` | `RoutinesSectionV1` | All saved routines and all materialized schedule days, ids/date keys preserved. |
| `progress` | `ProgressSectionV1` | All body-weight logs. |
| `ai` | `AiSectionV1` | All chat sessions, all messages (including routine-proposal kind/payload/status), all saved Gemini summaries, and every immutable post-workout AI summary keyed by workout id. |
| `files/widget_photo.*` | binary | The referenced Home photo, stored without recompression. |

## Compression and limits

- JSON sections are DEFLATED.
- The Home photo is STORED, because JPEG/PNG/WebP data is already compressed.
- Entry timestamps are fixed for deterministic archives; the real export time is in the manifest.
- Default hard limits: archive 256 MB, total uncompressed 512 MB, one entry 128 MB, 64 entries,
  compression ratio 500:1 for entries over 1 MB. A valid IronPulse backup is far below these.
- Export writes to a sibling `.part` file, verifies the completed ZIP, deletes an existing target and
  renames the verified file into place. The ViewModel then copies it to the SAF document and checks
  the provider-reported size when the provider reports one.

## Date and number handling

- Timestamps are stored as epoch milliseconds exactly as Room stores them. No timezone conversion is
  applied to historical values.
- Date-only schedule keys (`yyyyMMdd`) are preserved as integers.
- Weight, distance, body weight and summary values are JSON numbers. They are not rounded,
  recalculated or converted.
- Null/absent preferences are represented by absent nullable DTO fields. A missing value is not
  turned into zero unless the app's own schema defines zero as "not recorded".

## Excluded data

Never present in the archive:

- `gemini_api_key`, every `ai_api_key_<provider>`, and any other credential.
- `profile_avatar_path` and the profile picture file.
- Exercise `customMediaUri`, `customMediaIsVideo`, and custom exercise media bytes.
- Built-in library rows the athlete never edited.
- Downloaded update APKs, caches, crash/debug logs, dataset-version cache and cached GitHub release
  metadata.

## Deliberate limitations

- **Snapshot consistency across stores.** Room tables are read in one transaction. DataStore and the
  Home photo are read immediately afterwards, so a preference write that lands in the few
  milliseconds between them is not part of the same atomic snapshot. The limitation is documented
  because Android has no cross-store transaction; the export never interleaves a partial Room
  transaction with a partial preference read.
- **No encryption.** The format is portable and inspectable. Password protection was not invented.
- **Exercise media.** A restored exercise keeps its textual metadata but has no local image/GIF. The
  UI's existing missing-media fallback is used.
- **In-progress workout draft.** The draft is restored verbatim, including its exercise ids. Custom
  exercise ids are preserved. Shipped library ids are stable for the same bundled dataset; every
  logged set also stores its exercise name, so history remains readable even if a future dataset
  version changes row ordering.
