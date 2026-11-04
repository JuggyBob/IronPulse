# IronPulse user-data inventory

This inventory was built by reading the source at the start of the export/import task. It records
where each persistent user-owned value actually lives, how it is backed up, and how the result is
verified. It is the coverage contract for the `.ironpulse` format.

## 1. Persistence mechanisms found

| Mechanism | Exact location | User data? | Backup treatment |
|---|---|---|---|
| Room database | `ironpulse_database`, schema v15, `AppDatabase` | Yes | Tables below are exported section by section inside one Room transaction. |
| Preferences DataStore | `user_preferences.preferences_pb` via `Context.userDataStore` | Mixed | Explicit allowlist. Credentials and the avatar path are never read into the archive. |
| SharedPreferences `ironpulse_library_edits` | `edited_exercise_ids` | Yes | Exported as `editedLibraryExerciseIds`; used to stop the dataset reseed overwriting restored edits. |
| SharedPreferences `rest_timer_alert_prefs` | `vibration_enabled`, `sound_enabled` | Derived | Mirror of the two DataStore alert settings. Rebuilt from the imported preferences. |
| SharedPreferences `ironpulse_dataset` | `dataset_version` | No | Installation cache; not exported. |
| `files/home_widget_media/` | One JPEG/PNG/WebP referenced by `home_widget_photo_path` | Yes | The referenced image is copied into the archive as `files/widget_photo.<ext>`. Other files in that directory are unreferenced leftovers and are not exported. |
| `files/profile_media/` | `avatar.png` | Yes, but explicitly excluded | Never read or written. The DataStore path key is also excluded. |
| `files/exercise_media/` | `exercise_<id>.<ext>` | Yes, but explicitly excluded | Never read. The exercise row's `customMediaUri`/`customMediaIsVideo` fields are omitted and reset on import. |
| `files/updates/` | Downloaded update APK | No | Disposable; excluded. |
| `cacheDir/` | Coil exercise animation cache and temporary files | No | Disposable; excluded. |

No other SQLite database, SharedPreferences file, external-storage store, or file-backed user
content was found in `app/src/main/java`.

## 2. Room tables

Schema version: **15**. Relationships are logical (the schema declares indexes, not foreign keys),
so the importer validates them explicitly.

### 2.1 `exercises`

| Field | Detail |
|---|---|
| Data category | Built-in exercise catalog, user-created exercises, AI-generated exercises, athlete edits to built-in rows |
| Model | `Exercise` |
| Relationships | `LoggedSet.exerciseId` (soft: history also stores the exercise name and type); routine JSON references ids and names |
| Export strategy | Only rows where `isCustom = 1`, `isAiGenerated = 1`, or `id` is in `LibraryEditTracker`. All textual/structured fields are copied. |
| Import strategy | Replace all user-owned rows with the archive's ids preserved; restore edited built-in rows by id; reseed the untouched shipped catalog. |
| Exclusions | `customMediaUri`, `customMediaIsVideo`; custom `mediaPath` is cleared. Built-in rows not edited by the athlete are not user data. |
| Verification | Imported ids/fields are compared after replacement; the reseed must not alter an imported edit because the imported edited-id set is installed first. |

### 2.2 `workout_sessions`

| Field | Detail |
|---|---|
| Data category | Every finished workout, title, start/end time, notes, routine-day linkage, planned date and timezone-stable key |
| Model | `WorkoutSession` |
| Relationships | `LoggedSet.sessionId` (hard in the UI: a set without its session is an orphan) |
| Export strategy | Every row with all fields and the original id. |
| Import strategy | Delete all destination sessions, insert the archive rows with the same ids. |
| Exclusions | None. |
| Verification | Session list equality by id; orphan-set validation; counts in the manifest. |

### 2.3 `logged_sets`

| Field | Detail |
|---|---|
| Data category | Every set, including incomplete sets, order number, weight, added weight, reps, hold duration, cardio distance, completion state, timestamp and body-weight snapshot |
| Model | `LoggedSet` |
| Relationships | Belongs to one session by `sessionId`. |
| Export strategy | Every row, original id and all numeric fields. Doubles are serialized as JSON numbers and are not recalculated. |
| Import strategy | Delete all destination sets, insert with the same ids and numeric values. |
| Exclusions | None. |
| Verification | Session membership, set-number validity, numeric precision round trip, no orphan rows. |

### 2.4 `user_profile`

| Field | Detail |
|---|---|
| Data category | The Room copy of the athlete profile |
| Model | `UserProfile` |
| Relationships | One row with `id = 1`. |
| Export strategy | Name, height, current/target weight, age, unit, fitness level. |
| Import strategy | Replace the row and preserve the destination's Room `customApiKey` value. |
| Exclusions | `customApiKey` (legacy credential). |
| Verification | Field equality except `customApiKey`, which is asserted to be the destination value. |

### 2.5 `saved_routines`

| Field | Detail |
|---|---|
| Data category | Saved routines, names, descriptions, goals, days per week, the complete exercise/day JSON blob, creation time and schedule-version flag |
| Model | `SavedRoutine` |
| Relationships | `ScheduledWorkoutDay.routineId`; routine JSON contains exercise ids/names |
| Export strategy | Every row, including the JSON string exactly as stored. |
| Import strategy | Delete all destination routines and insert the archive rows with the same ids; the JSON is validated as JSON but not reinterpreted. |
| Exclusions | None. |
| Verification | List equality by id; JSON syntax validation; schedule link validation where applicable. |

### 2.6 `scheduled_workout_days`

| Field | Detail |
|---|---|
| Data category | Materialized per-date plan: planned/rest days, weekday, title, exercise count, capture time, date key |
| Model | `ScheduledWorkoutDay` |
| Relationships | Soft `routineId`; historical rows may outlive a deleted routine and are still valid history. |
| Export strategy | Every row. |
| Import strategy | Delete all destination rows and insert with the same `dateStart` primary keys. |
| Exclusions | None. |
| Verification | Unique dates; list equality; this is the source data from which streaks and adherence are recomputed. |

### 2.7 `body_weight_logs`

| Field | Detail |
|---|---|
| Data category | Weigh-in history |
| Model | `BodyWeightLog` |
| Relationships | None; the latest value feeds profile/progress displays. |
| Export strategy | Every row with id, weight, timestamp and note. |
| Import strategy | Delete all destination logs and insert with the same ids. |
| Exclusions | None. |
| Verification | Positive finite weights, unique ids, list equality. |

### 2.8 `chat_sessions`

| Field | Detail |
|---|---|
| Data category | Every AI conversation title and creation/update time, across all providers (the table is not provider-scoped) |
| Model | `ChatSession` |
| Relationships | `ChatMessageEntity.sessionId` |
| Export strategy | Every session. |
| Import strategy | Delete all destination sessions and insert with the same string ids. |
| Exclusions | None. Provider selection is a preference, not a credential and is exported separately. |
| Verification | Unique ids; message-to-session validation; list equality. |

### 2.9 `chat_messages`

| Field | Detail |
|---|---|
| Data category | Full user/AI message text, sender, ordering timestamp, id, message kind, routine-proposal payload and one-time approval status |
| Model | `ChatMessageEntity` |
| Relationships | Belongs to one `ChatSession`; a routine proposal also references a routine and two exercise names through its payload. |
| Export strategy | Every message, including `kind`, `payloadJson` and `status`, ordered by session, timestamp and id. |
| Import strategy | Delete all destination messages and insert with the same ids and proposal metadata. |
| Exclusions | None. |
| Verification | Sender is `USER` or `AI`; kind and approval state are known values; every message has its session; exact list equality. |

### 2.10 `gemini_summaries`

| Field | Detail |
|---|---|
| Data category | Saved monthly exercise reports, exercise name, window, training days, set count, best estimated 1RM, trend and creation time |
| Model | `GeminiSummary` |
| Relationships | Soft exercise-name reference. |
| Export strategy | Every saved summary. |
| Import strategy | Delete all destination summaries and insert with the same ids. |
| Exclusions | None. |
| Verification | Nonblank exercise name, valid counts, finite numbers, list equality. |

### 2.11 `workout_ai_summaries`

| Field | Detail |
|---|---|
| Data category | One immutable AI comparison per completed workout: exact Markdown, provider/model metadata, optional previous-workout id and creation time |
| Model | `WorkoutAiSummary` |
| Relationships | `workoutId` is the exact `WorkoutSession.id`; unique index prevents a second summary for the same workout. `previousWorkoutId` is a soft reference to the compared session. |
| Export strategy | Every saved summary with its original id, exact Markdown and metadata. |
| Import strategy | Delete all destination summaries and insert with the same ids after the workout rows, then verify the workout references. |
| Exclusions | None. Summaries are never regenerated during import. |
| Verification | Unique positive ids, unique workout ids, nonblank Markdown, valid timestamps, and every `workoutId`/`previousWorkoutId` references a session in the archive. |

## 3. DataStore allowlist

The DataStore also contains credentials and the avatar path. The backup never serializes the store as
a whole. These keys are exported:

| Group | Keys |
|---|---|
| Profile mirror | `profile_is_saved`, `profile_name`, `profile_height_cm`, `profile_current_weight_kg`, `profile_target_weight_kg`, `profile_age`, `profile_preferred_unit`, `profile_fitness_level` |
| Units and appearance | `weight_unit`, `theme_mode`, `liquid_glass_enabled`, `fullscreen_mode` |
| Rest timer | `rest_timer_vibration_enabled`, `rest_timer_sound_enabled`, `saved_rest_timers_json` |
| Workout behavior | `week_starts_on_monday`, `auto_save_interval_seconds`, `active_workout_draft`, `share_athlete_data_with_ai`, `autoplay_exercise_demos`, `auto_check_updates_on_start` |
| Onboarding | `onboarding_completed` |
| Personal records | `pr_push_ups`, `pr_push_ups_added_kg`, `pr_pull_ups`, `pr_pull_ups_added_kg`, `pr_bulgarian_split_squat`, `pr_bulgarian_split_squat_added_kg` |
| Routine | `active_routine_id` |
| Home dashboard | `home_widget_order`, `home_widget_hidden`, `home_widget_weekly_goal`; the image is carried as a file and `home_widget_photo_path` is rebuilt locally |
| AI data and summaries | `ai_workout_data_access`, `post_workout_ai_summaries` |
| Android widgets | `android_widget_theme_streak`, `android_widget_theme_weekly`, `android_widget_theme_profile` |
| AI selection | `ai_provider`, `ai_model_<provider>`, `ai_tool_mode_<provider>`, `ai_custom_base_url`, `ai_custom_acknowledged_endpoint`, legacy `gemini_model_name` |

These keys are deliberately **not** exported:

- `gemini_api_key` and every `ai_api_key_<provider>`.
- `profile_avatar_path`.
- `cached_github_release` (disposable network cache).
- `dismissed_update_prompt_version` (installation-specific UI suppression).

Custom AI base URLs containing userinfo, a query string, a fragment, or obvious token parameters are
redacted from the export and its acknowledgement is dropped. This prevents a URL from becoming a
credential leak.

## 4. Derived information

The Home streak, activity heat map, weekly totals, personal-record displays and progress charts are
computed at runtime. They have no separate storage. The backup carries their source rows
(`scheduled_workout_days`, `workout_sessions`, `logged_sets`, `body_weight_logs`, benchmark
preferences), so the same calculations reconstruct the same state after import.

## 5. Device-specific state that is not user data

- Android runtime permissions, notification permission state, exact-alarm permission state and
  fullscreen window policy.
- The downloaded update APK.
- The dataset-version cache.
- Coil's animation cache.
- Pending alarm/broadcast state for a running rest timer.

None of these is restored. In-progress workouts are user data and **are** carried in
`active_workout_draft`.
