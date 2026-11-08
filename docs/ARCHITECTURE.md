# IronPulse Architecture

IronPulse is a local-first Android application built with Jetpack Compose. Screens observe state
from `MainViewModel`, which remains the application-level facade. Eight cohesive feature owners now
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
| `RoutineWorkoutSessionController` | Routine-workout lifecycle and prescribed-day execution |

`MainViewModel` exposes the same `StateFlow` types, sharing modes, initial values, coroutine scopes
and observable ordering to the UI. This keeps screens decoupled from the implementation split.

## Data flow

```
Compose screens
      │
      ▼
MainViewModel (facade)
      │
      ├── feature controllers (state holders)
      │
      ▼
FitnessRepository
      │
      ▼
Room DAOs ── SQLite (schema v15)
```

Preferences are stored in DataStore. AI providers are accessed through the provider-independent
`data/ai` layer, and no AI key is built into the application. Release updates use the public GitHub
Releases API, with download checksums verified before the Android installer is invoked.

## Persistence and backup

Room is the source of truth for workouts, sets, routines, schedule rows, body metrics and exercise
history. The `.ironpulse` backup format is a versioned ZIP with a manifest, independent container and
data-schema versions, SHA-256 checksums and forward migrations.

## Build and tests

- Kotlin + Jetpack Compose + Material 3.
- Min SDK 24; target SDK 36.
- JVM tests use JUnit and Robolectric.
- Release signing inputs are read from environment variables by the existing Gradle configuration;
  no key or password is stored in the repository.
