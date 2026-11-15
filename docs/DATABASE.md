# Database and migrations

IronPulse stores everything on the device in a Room (SQLite) database named
`ironpulse_database`. The current schema is **version 19**. The database is created and upgraded by
[`AppDatabase.kt`](../app/src/main/java/com/example/data/db/AppDatabase.kt); the entities live in
[`Entities.kt`](../app/src/main/java/com/example/data/model/Entities.kt).

There is no account and no server-side copy. An uninstall or **Delete App Data** removes the
database, so the app never depends on a remote restore.

---

## Tables

| Table | Holds | Indexes |
|---|---|---|
| `exercises` | The built-in catalogue, user-created movements and AI-created movements, including the ordered `supportedFields` list. | Unique `name`. |
| `workout_sessions` | One logged workout: start/end time, duration, notes and the routine/schedule/planned-date link. | `startTime`. |
| `logged_sets` | Every set: exercise, number, weight, added weight, reps, hold/time, distance, completion, timestamp, body-weight snapshot, `isWarmup`, `restSecondsOverride`, `setUid`, `parentSetUid`, `dropIndex`. | `sessionId`, `exerciseName`, `timestamp`. |
| `user_profile` | The single athlete row: name, units, goal and current weight. | Primary key only. |
| `saved_routines` | One standalone workout Routine: ordered exercises as JSON, description, goal and `modelVersion` (`0` = legacy awaiting conversion, `1` = hidden legacy history row, `2` = current single-workout Routine). | `createdAt`. |
| `schedules` | One weekly Schedule: name, description, goal strategy, creation time and the legacy routine it was migrated from. | `createdAt`. |
| `schedule_days` | Seven stable weekday references per Schedule, `weekday` 1 = Monday … 7 = Sunday. `routineId` is null for a rest day and may repeat the same Routine on multiple days. | Primary key `(scheduleId, weekday)`, `routineId`. |
| `scheduled_workout_days` | One materialized plan row per calendar date, including rest days; stores routine, weekday, day title, exercise count and a timezone-stable `yyyyMMdd` key. | Primary key `dateStart`, `routineId`. |
| `body_weight_logs` | Weigh-in history used by the timeline and the body-weight repair. | `timestamp`. |
| `chat_sessions` | AI coach conversations. | `lastUpdatedAt`. |
| `chat_messages` | Messages belonging to a conversation; a routine-approval card also stores its kind, structured payload and one-time approval status. | `sessionId`, `timestamp`. |
| `gemini_summaries` | Cached monthly exercise reports. The table and entity keep their historical name so existing installs keep their data; the feature works with every AI provider. | `createdAt`. |
| `workout_ai_summaries` | One immutable post-workout AI comparison per completed workout: Markdown, provider/model metadata, comparison workout id and creation time. | Unique `workoutId`, `createdAt`. |

`workout_sessions` stores the routine, weekday, planned local date and planned date key that a
session fulfilled. Those fields are what the schedule-adherence streak uses; they are not inferred
from the routine title. Sessions started through a Schedule also store the Schedule id and the goal
strategy captured at start, so editing the Schedule later cannot rewrite an active or completed
workout.

A Schedule stores references only. Editing a Routine immediately changes every weekday that
references it, while deleting a Routine clears those references and leaves the completed session and
its logged sets untouched. The one active Schedule is kept in DataStore (`active_schedule_id`), which
makes the single-active rule a data-layer invariant rather than a UI convention.

Relationships are expressed by ids and names (`logged_sets.sessionId`, `chat_messages.sessionId`)
rather than SQLite foreign-key constraints. The repository is the only layer that writes them, and it
keeps the related rows in step.

---

## Drop sets and warm-up sets

`logged_sets` stores a main set and its drop children as ordinary rows:

- `setUid` is stable while the set is in memory and across draft/backup round trips.
- `parentSetUid` is null for a main set and points at a main set for a drop child.
- `dropIndex` is 1-based among the children of one parent, 0 for a main set.
- `isWarmup` is independent: a warm-up can itself be a main set with drop children.
- `restSecondsOverride` is nullable. Null means "inherit the Routine or exercise default"; a value
  applies only to that set.

`DropSetRules` is the single authority for grouping, renumbering and completion. A child never
exists without a same-exercise parent. Completing a child completes its parent; un-completing a
parent clears its children; deleting a parent deletes its children.

Migration 18 gives every existing set a deterministic `legacy-<id>` UID and `parentSetUid = null`.
No historical set is guessed into a drop set from its position, load or number.

## Exercise fields

`exercises.supportedFields` is a compact, ordered, comma-separated list of field identifiers:

`reps`, `weight`, `addedWeight`, `holdSeconds`, `timeSeconds`, `distanceKm`.

An empty stored list means a legacy row: the exercise type supplies the fallback configuration until
the catalogue reseed or the athlete configures fields. The first two fields render inline; the rest
are in the set details. Hold and time share one duration column and cannot both be configured;
weight and added weight are mutually exclusive.

---

## Migration history

Every step is registered in the builder; there is no gap in the chain from 1 to 19.

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
| 12 → 13 | Adds `routineId`, `routineWeekday` and `plannedDate` to workout sessions and creates `scheduled_workout_days` with its `routineId` index. Existing sessions keep 0, which means untracked rather than retroactively missed. |
| 13 → 14 | Adds `dateKey` to scheduled days and `plannedDateKey` to sessions, so plans and completions survive timezone changes. Existing rows derive the key from their local date when read. |
| 14 → 15 | Creates `workout_ai_summaries` with a unique workout id, and adds `kind`, `payloadJson` and `status` to chat messages for inline routine-approval cards. Existing messages default to `TEXT`/`NONE`. |
| 15 → 16 | Adds `saved_routines.modelVersion`, `workout_sessions.scheduleId`/`scheduleGoal`, and creates `schedules` and `schedule_days`. On first open, every legacy multi-day routine is converted transactionally into standalone single-workout Routines plus a seven-slot Schedule. Identical workout days inside one legacy program share one Routine; the legacy row stays behind with `modelVersion = 1` so historical session links keep resolving. The conversion is idempotent and only processes `modelVersion = 0` rows. |
| 16 → 17 | Adds `logged_sets.isWarmup` and `restSecondsOverride`. Legacy sets become normal, inherited sets. |
| 17 → 18 | Adds `logged_sets.setUid`, `parentSetUid` and `dropIndex`; every existing set becomes a top-level set with a deterministic `legacy-<id>` UID. |
| 18 → 19 | Adds `exercises.supportedFields`; the dataset reseed fills every catalogue row while preserving custom and AI-created exercises. |

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

`MIGRATION_10_11` fills historical rows once. The same idempotent repair also runs on every open, so
an install that had already passed schema 11 with zero rows is fixed without waiting for another
schema version. It only touches rows whose value is still `<= 0`.

### Routine defaults live in the routine

`RoutineExerciseItem` stores the sets, reps, weight, added weight, hold, distance, rest, per-main-set
defaults and drop-set defaults inside `saved_routines.exercisesJson`, not on the shared `exercises`
row. The same movement can therefore rest differently or have different targets in two plans.

### Schedule references are validated

Every Schedule must have exactly seven distinct weekdays. A slot's `routineId` must reference an
existing Routine or be null. Deleting a Routine clears every slot that references it. Completed
sessions keep their historical routine id and their own copies of the exercise name and values.

### Chat deletion is one transaction

`ChatDao.deleteSessionWithMessages()` is annotated `@Transaction`: it deletes the messages and the
session together. A process death between the two deletes can no longer leave invisible orphaned
messages that every session query still scans.

### Exercise names are unique

`exercises.name` has a unique index. Migration 2 → 3 deduplicated older rows before creating it, and
the seeding path resolves an existing row rather than inserting a duplicate.

### Seeding is transactional and idempotent

The bundled dataset is applied through `DatasetSeeder`. A whole reseed runs in one transaction, so
observers see either the old library or the complete new one, never a half-applied catalogue. The
dataset version (`DATASET_VERSION = 5`) decides whether the corrected field configurations need to
be applied on the next open.

---

## Destructive behavior

`fallbackToDestructiveMigrationOnDowngrade()` is the only destructive path. It applies when the
installed database is **newer** than the running build, which can only happen after a downgrade. A
normal upgrade never drops a table or a row.

---

## Testing

| Test | Coverage |
|---|---|
| `DatabaseUpgradePathTest` | Opens real databases at older schemas, runs the full chain, checks that history survives, and verifies the schedule tables, new columns and indexes. |
| `DropSetMigrationTest` | Schema 17 → 18 and backup v4 → v5 UID/parent defaults. |
| `ExerciseFieldBackupTest` | Schema 18 → 19 and backup v5 → v6 field defaults. |
| `BodyWeightRepairTest` | The body-weight snapshot and the idempotent repair, including the profile fallback. |
| `DatasetSeederTest` | Seeded rows carry the canonical display spelling and field configuration. |
| `RoutineLegacyLoadTest` | Legacy `addedWeight`, text prescriptions and JSON prescription fallback. |
| `PlanArchitectureTest` | Routine/Schedule migration, deduplication, reference cleanup, Schedule copying and single-workout generation. |
| `ActiveSchedulePromotionTest` | The 4.4 active routine becomes the active Schedule once, without overwriting a chosen Schedule. |

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
