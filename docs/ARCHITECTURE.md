# IronPulse Architecture

IronPulse is a local-first Android application built with Jetpack Compose. Screens observe state
from `MainViewModel`, which remains the application-level facade. Nine cohesive feature owners
implement the work that previously lived in one large ViewModel.

## State ownership

| Component | Responsibility |
|---|---|
| `RestTimerController` | Rest countdown, pause/resume, exact-alarm scheduling and saved presets |
| `AppUpdateController` | GitHub release lookup, download verification and About/startup update state |
| `DataTransferController` | `.ironpulse` export/import state and archive handling |
| `AiConfigurationController` | AI provider, endpoint, key and model configuration |
| `HomeDashboardController` | Home dashboard derivation and widget-facing snapshot |
| `ProgressAnalyticsController` | Progress charts, body-weight timeline and exercise analytics |
| `QuickWorkoutSessionController` | Quick-workout lifecycle, active session state and set logging |
| `RoutineWorkoutSessionController` | Routine-workout lifecycle and prescribed-workout execution |
| `PlanController` | Routines, Schedules, the active Schedule, weekday projections, streak input and schedule materialization |

`MainViewModel` exposes the same `StateFlow` types, sharing modes, initial values, coroutine scopes
and observable ordering to the UI. This keeps screens decoupled from the implementation split.

## Data flow

```text
Compose screens
      │
      ▼
MainViewModel (facade)
      │
      ├── feature controllers (state holders)
      │
      ▼
FitnessRepository / PlanRepository
      │
      ▼
Room DAOs ── SQLite (schema 19)
```

Preferences are stored in DataStore. AI providers are accessed through the provider-independent
`data/ai` layer, and no AI key is built into the application. Release updates use the public GitHub
Releases API, with download checksums verified before the Android installer is invoked.

## Plan model

- `saved_routines` stores one standalone workout per row. The ordered exercise list, per-main-set
  defaults, drop sets, rest defaults and `modelVersion` live in `exercisesJson`.
- `schedules` stores the named weekly plan. `schedule_days` stores exactly seven weekday slots, each
  referencing a Routine or null for a rest day.
- The active Schedule id is a DataStore preference. `PlanController` assembles the seven
  `RoutineDay` projections from the referenced routines on every emission.
- `scheduled_workout_days` materializes one row per calendar date for the streak. It is written from
  today forward, while historical rows stay as they were.
- The 4.4 migration converts every legacy multi-day routine into a Schedule plus standalone Routines.
  The old row remains hidden at `modelVersion = 1` for historical session links.

## Workout model

- A logged set stores its exercise, set number, weight, added weight, reps, hold/time, distance,
  completion state, timestamp, body-weight snapshot, `isWarmup`, `restSecondsOverride`, `setUid`,
  `parentSetUid` and `dropIndex`.
- A drop set is any row with a non-null parent UID. `DropSetRules` owns grouping, renumbering, the
  completion invariant and deletion semantics so the workout, history, backup and AI layers cannot
  drift.
- An exercise stores an ordered `supportedFields` list. The type is only a fallback and badge.

## Persistence and backup

Room is the source of truth for workouts, sets, routines, schedule rows, body metrics and exercise
history. The `.ironpulse` backup format is a versioned ZIP with a manifest, independent container and
data-schema versions, SHA-256 checksums and forward migrations. Cloud Backup writes the same archive
through Android's Storage Access Framework with read-back verification and five-file retention.

## Build and tests

- Kotlin 2.2 + Jetpack Compose + Material 3.
- Min SDK 24; target SDK 36.
- 906 JVM tests across 120 suites, using JUnit and Robolectric.
- Release signing inputs are read from environment variables by the existing Gradle configuration;
  no key or password is stored in the repository.
