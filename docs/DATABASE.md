# Database and migrations

IronPulse stores everything on the device in a Room (SQLite) database named
`ironpulse_database`. The current schema is **version 14**. The database is created and upgraded by
[`AppDatabase.kt`](../app/src/main/java/com/example/data/db/AppDatabase.kt); the entities live in
[`Entities.kt`](../app/src/main/java/com/example/data/model/Entities.kt).

There is no account and no server-side copy. An uninstall or **Delete App Data** removes the
database, so the app never depends on a remote restore.

---

## Tables

| Table | Holds | Indexes |
|---|---|---|
| `exercises` | The built-in catalog, user-created movements and AI-created movements. | Unique `name`. |
| `workout_sessions` | One logged workout: start/end time, duration and notes. | `startTime`. |
| `logged_sets` | Every set: session, exercise name, reps/weight/hold/distance, completion, timestamp and the body-weight snapshot. | `sessionId`, `exerciseName`, `timestamp`. |
| `user_profile` | The single athlete row: name, units, goal and current weight. | Primary key only. |
| `saved_routines` | A saved routine and its seven-day plan as JSON, plus goal, frequency and schedule version. | `createdAt`. |
| `body_weight_logs` | Weigh-in history used by the timeline and the body-weight repair. | `timestamp`. |
| `chat_sessions` | AI coach conversations. | `lastUpdatedAt`. |
| `chat_messages` | Messages belonging to a conversation. | `sessionId`, `timestamp`. |
| `gemini_summaries` | Cached monthly exercise reports. The table and entity keep their historical name so existing installs keep their data; the feature itself works with every AI provider. | `createdAt`. |
| `scheduled_workout_days` | One materialized plan row per calendar date, including rest days; stores routine, weekday, day title, exercise count and a timezone-stable `yyyyMMdd` key. | Primary key `dateStart`, `routineId`. |

`workout_sessions` also stores the routine, weekday, planned local date and planned date key that a
session fulfilled. Those fields are what the schedule-adherence streak uses; they are not inferred
from the routine title.

Relationships are expressed by ids and names (`logged_sets.sessionId`, `chat_messages.sessionId`)
rather than SQLite foreign-key constraints. The repository is the only layer that writes them, and
it keeps the related rows in step.

---

## Migration history

Every step is registered in the builder; there is no gap in the chain from 1 to 14.

| Step | What it does |
|---|---|
| 1 → 2 | Creates `chat_sessions` and `chat_messages`. |
| 2 → 3 | Deduplicates exercises by lower-cased, trimmed name and adds the unique name index. |
| 3 → 4 | Adds `exercises.isUserCreated` so user movements can be edited or deleted. |
| 4 → 5 | Creates `gemini_summaries` and its `createdAt` index. |
| 5 → 6 | Adds `exercises.isAiGenerated` so AI-created movements are labelled and revertible. |
| 6 → 7 | Rebuilds `exercises` into the dataset schema (body part, target, secondary muscles, instructions, media, source id, `isCustom`), preserving row ids so logged sets are never orphaned. |
| 7 → 8 | Adds custom exercise media (`customMediaUri`, `customMediaIsVideo`) and `logged_sets.distanceKm`. |
| 8 → 9 | Adds `logged_sets.bodyWeightKg`, the weight that applied when the set was logged. |
| 9 → 10 | Adds `saved_routines.scheduleVersion` so old numbered days are bound to real weekdays once. |
| 10 → 11 | Backfills `bodyWeightKg` for history that never stored it: latest weigh-in at or before the workout end, then the earliest weigh-in, then the profile weight, then 75 kg. |
| 11 → 12 | Adds the hot-query indexes; no table or row is rewritten. |
| 12 -> 13 | Adds `routineId`, `routineWeekday` and `plannedDate` to workout sessions and creates `scheduled_workout_days` with its `routineId` index. Existing sessions keep 0, which means untracked rather than retroactively missed. |
| 13 -> 14 | Adds `dateKey` to scheduled days and `plannedDateKey` to sessions, so plans and completions survive timezone changes. Existing rows derive the key from their local date when read. |

### 11 → 12 indexes

| Index | Query it serves |
|---|---|
| `workout_sessions(startTime)` | History ordered by start time. |
| `logged_sets(sessionId)` | Reading a session with its sets, and deleting a session. |
| `logged_sets(exerciseName)` | Per-exercise progress history. |
| `logged_sets(timestamp)` | Monthly analyzer and recent-history windows. |
| `saved_routines(createdAt)` | Routine list ordering. |
| `body_weight_logs(timestamp)` | Weigh-in timeline and the body-weight repair. |
| `chat_sessions(lastUpdatedAt)` | Conversation list ordering. |
| `chat_messages(sessionId)` | Opening a conversation. |
| `chat_messages(timestamp)` | Message ordering inside a conversation. |

The same indexes are declared on the entities, so a fresh install and an upgraded install have the
same schema. Room validates that agreement when the database opens.

---

## Integrity rules

### Body weight is frozen at the workout

A set stores the body weight that applied when it was logged. The UI never resolves a zero snapshot
through today's profile weight, because doing so rewrote the volume, estimated one-rep max and
"isolated weight" tag of older workouts after a new weigh-in.

`MIGRATION_10_11` fills historical rows once. The same idempotent repair also runs on every open,
so an install that had already passed schema 11 with zero rows is fixed without waiting for another
schema version. It only touches rows whose value is still `<= 0`.

### Routine rest defaults live in the routine

`RoutineExerciseItem.restSeconds` (0–600) is stored inside `saved_routines.exercisesJson`, not on the
shared `exercises` row. The same movement can therefore rest 45 seconds in one plan and two minutes
in another. `0` means "no routine default" and keeps the rest timer manual. Routines saved before the
field existed parse as `0`, and the field is omitted from the JSON when it is zero.

### Chat deletion is one transaction

`ChatDao.deleteSessionWithMessages()` is annotated `@Transaction`: it deletes the messages and the
session together. A process death between the two deletes can no longer leave invisible orphaned
messages that every session query still scans.

### Exercise names are unique

`exercises.name` has a unique index. Migration 2 → 3 deduplicated older rows before creating it, and
the seeding path resolves an existing row rather than inserting a duplicate.

### Seeding is transactional and idempotent

The bundled dataset is applied through `DatasetSeeder`. A whole reseed runs in one transaction, so
observers see either the old library or the complete new one, never a half-applied catalog. The
dataset version (`DATASET_VERSION = 4`) decides whether the corrected spellings need to be applied on
the next open.

---

## Destructive behavior

`fallbackToDestructiveMigrationOnDowngrade()` is the only destructive path. It applies when the
installed database is **newer** than the running build, which can only happen after a downgrade. A
normal upgrade never drops a table or a row.

---

## Testing

| Test | Coverage |
|---|---|
| `DatabaseUpgradePathTest` | Opens real databases at v3, v6, v10, v11, v12 and v13, runs the full chain to 14, checks that history survives, and verifies the schedule tables, new columns and indexes. |
| `BodyWeightRepairTest` | The body-weight snapshot and the idempotent repair, including the profile fallback. |
| `DatasetSeederTest` | Seeded rows carry the canonical display spelling. |
| `RoutineRestDefaultsTest` | The routine JSON round trip, legacy JSON, clamping and the AI tool output. |

These are JVM/Robolectric tests against real SQLite databases, not mocks of Room.

---

## Adding a migration

1. Change the entity and bump `version` in `AppDatabase.kt`.
2. Add a `Migration(old, new)` that produces exactly the schema Room expects. Match column defaults
   and nullability, not just column names.
3. Register it in `addMigrations(...)`.
4. Add a path test in `DatabaseUpgradePathTest` that opens the old schema, migrates, and asserts both
   the data and the schema (indexes and columns).
5. Keep the migration additive where possible. If a table must be rebuilt, preserve row ids so
   `logged_sets` and chat messages are not orphaned.
