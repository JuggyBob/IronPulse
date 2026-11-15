# Changelog

All notable changes to **IronPulse** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [5.0.0] - 2026-11-15

IronPulse 5.0.0 is the first public release since 4.4.0. It is a major release because the training
model changed: the 4.4 multi-day routine container is replaced by standalone **Routines** and
weekly **Schedules**, and the active workout was rebuilt around them. It also adds **drop sets**,
**warm-up sets**, per-set rest, explicit exercise fields, cloud-folder backups and selectable
launcher icons.

The public version code is **13**, exactly one above the public 4.4.0 build.

Existing 4.4.0 features are preserved unless a specific change is listed below: the four themes,
accent colours, Liquid Glass navigation, fullscreen mode, Material Expressive 3, the Home widget
manager, the three Android launcher widgets, the exercise library and media, Progress, the athlete
profile, the AI provider layer, the update checker and manual `.ironpulse` export/import.

### New: Routines and Schedules

- **A Routine is one workout.** The old multi-day container is gone from the active UI. A Routine has
  one ordered exercise list, one goal, one description and its own set, load and rest defaults.
- **A Schedule is a week of seven weekday references.** Each slot references a Routine or is a rest
  day. The same Routine can be assigned to several weekdays, and editing it immediately changes every
  weekday that references it.
- **One active Schedule** drives Home's Today card, the week strip, Start Today, schedule adherence
  and the Android widgets. Changing the week-start preference changes display order only.
- **Goal strategy moved to the Schedule.** The existing goal options now live on the weekly plan. A
  workout captures the strategy when it starts, so later edits cannot rewrite an active session.
- **Start any weekday** from a Schedule without changing the active Schedule.
- **Four starter templates:** Three-Day Full Body, Four-Day Upper/Lower, Five-Day Push Pull Legs and
  Six-Day Push Pull Legs. Copying one creates independent Routines and a Schedule; duplicate copies
  are guarded and progress is shown.
- **Routine search:** a relevance-ranked search over the full catalogue with category filters, the
  existing read-only exercise detail card, preserved query/filter/scroll state and duplicate
  prevention.
- **Batch management:** multi-select deletion for Routines and Schedules with confirmation.
- **Reference-safe deletion:** deleting a Routine turns its weekday slots into rest days; completed
  sessions and logged sets are untouched.
- **Legacy conversion:** each 4.4 multi-day program becomes one Schedule plus one standalone Routine
  per distinct workout day. Identical days inside one program share one Routine. The old row stays
  hidden at `modelVersion = 1` for historical session links.

### New: workout interface

- The stacked exercise cards were replaced by an **exercise-selector interface**: numbered exercise
  chips, a focused exercise card, one set list and a compact body-part preview, shared by Routine
  workouts and the Quick Workout tray.
- **Unified set rows** use stepper boxes, one aligned field header, equal completion/delete actions
  and an expandable panel attached to the same set card.
- **Whole-workout body-part completion map**, with completed/total sets per body part and a progress
  list. Material Expressive 3 uses the existing animated dot-snake treatment.
- **Add exercise / Remove exercise** actions in the active workout.
- **New-set animation** unfolds the row into place; switching exercises uses a direction-aware
  horizontal transition in both the Routine workout and Quick Workout tray. Both honour the system
  reduced-motion setting.
- **Quick Workout** keeps its resizable, non-modal tray and now uses the same interface, set rows and
  set options as a Routine workout. The selected exercise and scroll state survive minimize, resize
  and cross-tab navigation.
- **Read-only exercise details in the routine editor.** Tapping an exercise name opens the same
  media, instructions, field and body-map sheet used during a workout, without editing controls.

### New: drop sets, warm-up sets and per-set rest

- **Drop sets** are direct children of one main set. Each row stores a stable set UID, parent UID and
  1-based drop index; main sets keep `1..N` and branches number their children `D1..DN`.
- Completing a child completes its parent; un-completing a parent clears its children; deleting a
  parent removes its children; deleting a child leaves its siblings alone.
- **Drop-set defaults** are configurable per main set in the Routine editor. History shows the branch
  under its parent instead of treating drop children as top-level sets.
- **Warm-up sets** persist per set and remain visible in workout and exercise history. They count
  toward total volume but are excluded from max reps, estimated 1RM, best working sets and working
  PRs.
- **Per-set rest overrides** store a nullable duration. A non-zero value starts that timer when the
  set is completed; resetting it restores the Routine or device-local exercise default. Completing a
  set starts the existing floating rest timer with the resolved duration.

### New: explicit exercise fields and catalogue audit

- All **1,334** exercises were audited. **391** ordered field configurations changed and **943**
  were left unchanged; the matrix is `docs/exercise_field_audit.csv` and the generator is
  `tools/audit_exercise_fields.py`.
- Six stable field identifiers: `reps`, `weight`, `addedWeight`, `holdSeconds`, `timeSeconds` and
  `distanceKm`. Each exercise stores its own ordered list; the exercise type only supplies a fallback
  suggestion and the badge.
- The audit corrected weighted planks and holds, weighted calisthenics, static stretches, no-load
  props, loaded carries, conditioning work and bodyweight movements that had inherited an irrelevant
  hold.
- **Create Exercise and Edit Exercise** expose the field selector with a live inline/details preview.
  Changing the type no longer removes fields the athlete chose. Hold and time cannot both be
  configured because they share one duration column; weight and added weight remain mutually
  exclusive.
- History and analytics read the recorded values: holds, time, distance and load come from the set.
  Any recorded added weight contributes to load.

### New: cloud-folder backups and onboarding data step

- **Cloud Backup** under Settings → Data. Choose a folder with Android's document picker, including
  Google Drive or OneDrive when their provider is installed. The folder permission persists across
  restarts.
- Automatic backups are requested after a completed workout, after a restore and after
  profile/onboarding changes, with a daily WorkManager recovery run. Manual **Back up now** remains
  available.
- Each archive is written to a temporary document, read back, size- and SHA-256-verified, then renamed
  to a timestamped file. A failed attempt never overwrites an older backup, and the five newest cloud
  backups are retained.
- The **onboarding data step** lets a new install import a backup or connect a cloud folder before
  finishing setup. It is optional and skippable.

### New: selectable launcher icons

- Eight icons: IronPulse, Cool Cyan, Signal Amber, Ultra Violet, Ocean Blue, Crimson Red, Rose Quartz
  and Arctic White.
- Each has an adaptive foreground, round variant, legacy density bitmaps and a picker preview.
  Activity aliases apply the choice; a bootstrap alias keeps a fresh install launchable.
- Generated adaptive foregrounds use a 60dp centred layer-list, so circular/squircle masks no longer
  cut the dumbbell plates; the monochrome layer is wrapped the same way.
- The Recents task icon follows the selected artwork.

### Changed: Home, streak and Android widgets

- Home no longer reads a week-routine; it reads the active **Schedule**. Today's card, the seven-day
  strip, Start Today and the schedule-adherence streak now use the Schedule's weekday references.
- Quick workouts and unplanned sessions remain untracked for adherence; a planned day counts only
  when a completed linked session has at least one completed set.
- The three Android launcher widgets now read the same Schedule data and refresh after plan changes.
- The existing Home cards, widget manager, weekly goal and motivation photo are otherwise unchanged.

### Changed: Routine editor, loads and exercise cards

- The 4.4 week-planner editor was replaced by the single-workout **Routine editor** with exercise
  search, reorder/remove, set and rest controls, per-main-set defaults and drop-set defaults.
- Routine exercise names open the read-only detail sheet; exercise-level added-weight/rest defaults
  moved behind a small tune button.
- **Legacy load migration:** pre-4.8 routines that stored a dumbbell load in `addedWeight` now have it
  moved into `weight` when the exercise records only weight, including every main-set default and
  drop set. Genuine added-weight work is untouched.
- **Text and JSON prescriptions:** `3x8 @ 34kg`, `+9kg` and `lb` loads are parsed into the correct
  field, and JSON items whose weight fields are empty fall back to their prescription text.
- `Name: 3x8` exercise lines are no longer discarded as prose.
- The routine card shows a working-load badge for Standard-weight targets, next to the existing
  added-weight badge.
- Exercise cards keep Add Set usable with four-field exercises and use the detail sheet instead of a
  duplicated overflow menu.

### Changed: history, metrics and Progress

- Warm-up sets are excluded from max reps, estimated 1RM and working-set records but remain in
  volume. A workout with only warm-ups produces no fabricated record.
- Drop children are not counted as extra top-level sets in history or Progress.
- Hold-only and distance sets do not invent reps, volume or a 1RM.
- The activity heat map keeps the selected month across vertical scrolling; the initial jump to the
  newest month happens once.
- Body Weight Timeline and Weigh-in Logs are grouped with stable spacing in both collapsed and
  expanded states.
- Workout-history statistics cards share one height, and the volume value/unit no longer wraps on
  narrow screens.
- Session History keeps its five-day paging and now displays the final set numbering, WARM badges and
  drop branches.

### Changed: AI

- All AI plan paths now create **Routines first and a Schedule when the answer contains multiple
  days**; a single-workout request creates one Routine and no Schedule.
- Tool context includes the active Schedule, explicit supported fields, distance, warm-up flags,
  per-set rest and drop-set parent links.
- Added the read-only **`get_active_workout`** tool. The existing ten tools were updated rather than
  replaced.
- Routine proposals now validate against the active Schedule, and approval performs the same fresh
  permission, active-plan and stale-signature checks.
- The chat composer follows the bottom dock when the history drawer opens.
- A warning card appears under **Post-workout AI summaries** when the active provider has no key or
  endpoint, with a direct **Connect a provider** action.
- Existing provider selection, model discovery, tool modes, monthly summaries, post-workout
  summaries, data-access switches and custom-endpoint rules are preserved.

### Changed: settings, backup format and data

- Settings gained the **App Icon** page and the **Cloud Backup** section. The existing category hub,
  AI Tools pages, Data Management, profile and update settings remain.
- **Delete App Data** now also clears Schedules, weekday slots, Routines and the active Schedule
  before returning through onboarding.
- **Room schema 16–19:**
  - 16 adds `saved_routines.modelVersion`, `workout_sessions.scheduleId`/`scheduleGoal`, and the
    `schedules`/`schedule_days` tables.
  - 17 adds `logged_sets.isWarmup` and `restSecondsOverride`.
  - 18 adds `logged_sets.setUid`, `parentSetUid` and `dropIndex`.
  - 19 adds `exercises.supportedFields`.
- **Backup data schema 3–6:** Schedules, routine model versions, warm-up/rest fields, set UIDs and
  drop links, and exercise field lists. v1–v6 archives migrate forward; manual export/import keeps
  its existing replacement and rollback behaviour.
- **Active-plan promotion fix:** the 4.4 active routine is promoted to its migrated Schedule as soon
  as the asynchronous conversion creates it. The legacy preference is consumed after a normal
  promotion and left intact during backup restore.

### Changed: specific Liquid Glass and Material Expressive surfaces

- The Quick Workout action bar uses the single-tone Liquid Glass surface; the Routine action bar uses
  the real backdrop blur instead of the old white top-lit gradient.
- The dock's full-screen backdrop is captured once and reused for the 340ms tab transition instead of
  being re-recorded every frame.
- The body-part completion map uses the existing Material Expressive 3 dot-snake treatment.
- Themes, accent colours, the Liquid Glass dock itself, fullscreen mode and the appearance settings
  are otherwise unchanged from 4.4.0.

### Removed or replaced

- The multi-day routine container and the separate **Routine** tab. Plan now contains Schedules and
  Routines.
- Type-driven set inputs. The explicit per-exercise field list is authoritative; the type remains a
  fallback and badge.
- The automatic history-type rewrite on database open. Logged sets keep the type and values they were
  recorded with.
- The unreliable Google Drive package check. The document picker is the source of truth.
- The onboarding export card. A fresh install has nothing to export; import and cloud setup remain,
  and export stays in Settings.

### Fixed during the internal cycle

- **Legacy routine loads:** values stored in `addedWeight` on Standard-weight exercises were hidden
  after the 4.8.0 field audit and erased on save; they now move to the field the exercise records.
- **Text/JSON loads:** loads written only in a prescription were dropped by the 4.5 routine
  conversion; they are now parsed, including `+`/`added`/`weighted` and `lb`.
- **Launcher icons:** adaptive artwork was outside the safe zone and clipped by circular/squircle
  masks; Recents showed the default icon instead of the selected one.
- **Rest timer:** preset selection briefly rebounded one second; the reset affordance pushed the rest
  stepper down; default-rest presets wrapped onto a second line.
- **Routine editor:** main-set fields were hidden behind a second collapsed disclosure; expansion
  state was lost after scrolling; the drop-set connector was cut off; main-set and drop-set cards had
  different widths.
- **Progress:** the heat map reset to the newest month after vertical scrolling; weigh-in cards had
  an unnecessary gap; history statistics cards had different heights and wrapped volume.
- **AI UI:** the composer did not follow the dock when the history drawer opened; no warning appeared
  when the selected provider had no credentials.
- **Exercise cards:** four-field exercises compressed Add Set; a duplicated overflow menu was added
  and then removed in 4.8.1 revision 2 in favour of the detail sheet.
- **Schedules:** Create/Edit dialogs clipped at a fixed height; the goal selector was not reachable.
- **Onboarding:** finishing setup or restoring a backup could crash during the onboarding-to-main
  transition.
- **Active-plan promotion:** the migrated Schedule could remain inactive because the promotion ran
  before the asynchronous Room conversion created it. Found during 5.0.0 release validation and
  fixed reactively.

### Internal revisions that corrected earlier implementations

- **4.8.1 rev 2:** removed the overflow menu introduced in 4.8.1 and moved default rest under Targets.
- **4.8.1 rev 3:** fixed the rest-timer preset rebound and kept presets on one row.
- **4.8.1 rev 4:** fixed launcher-icon switching closing the task, added the bootstrap alias and
  polished the picker/confirmation.
- **4.8.2 rev 2:** heat-map state, weigh-in spacing, editor widths/state, AI composer, image warmer,
  tab-backdrop reuse, rest-field alignment and the AI-provider warning.
- **4.8.2 rev 3:** legacy load migration, working-load badge, read-only routine details and the tune
  button.
- **4.8.3:** icon safe zone, Recents icon, text/JSON prescription loads and colon exercise lines.

### Internal release history

| Build | Code | Date | Scope |
|---|---|---|---|
| 4.5.0 | 13 | 2026-11-10 | Routine/Schedule rebuild, Room 16, backup schema 3, starter templates, AI plan paths |
| 4.5.1 | 14 | 2026-11-11 | Routine search, batch deletion, Schedule Start Workout, data-wipe reset, editor polish |
| 4.6.0 | 15 | 2026-11-12 | Exercise-selector workout, warm-up sets, per-set rest, body-part map, Room 17, backup 4 |
| 4.6.1 | 16 | 2026-11-12 | Set header/actions, body preview, warm-up motion, Liquid Glass bars |
| 4.6.2 | 17 | 2026-11-12 | New-set and exercise-switch animations with reduced-motion support |
| 4.7.0 | 18 | 2026-11-12 | Cloud-folder backups, verified writes, retention, onboarding data step |
| 4.7.1 | 19 | 2026-11-13 | Drive detection fix, onboarding export removal, history stats, static thumbnails |
| 4.8.0 | 20 | 2026-11-14 | Drop sets, exercise management, field audit, Room 18/19, backup 5/6 |
| 4.8.1 | 21 | 2026-11-14 | Exercise cards, default rest, schedule dialogs, notices, weigh-ins, four launcher icons |
| 4.8.1 rev 2 | 21 | 2026-11-14 | Card/detail simplification and default rest under Targets |
| 4.8.1 rev 3 | 21 | 2026-11-14 | Rest-timer preset rebound and one-row default-rest presets |
| 4.8.1 rev 4 | 21 | 2026-11-15 | Launcher-icon switching crash, bootstrap alias, centred picker and confirmation |
| 4.8.2 | 22 | 2026-11-15 | Workout spacing, routine disclosure fixes, AI drawer dock, four more icons |
| 4.8.2 rev 2 | 23 | 2026-11-15 | Heat-map state, weigh-in spacing, editor alignment, composer, image warmer, AI warning |
| 4.8.2 rev 3 | 24 | 2026-11-15 | Legacy `addedWeight` load migration, working-load badge, read-only routine details |
| 4.8.3 | 25 | 2026-11-15 | Icon safe zone, Recents icon, text/JSON prescription loads, colon exercise lines |

### Compatibility and upgrade notes

- **From public 4.4.0:** install in place. The package and production signing certificate are
  unchanged, so Android treats 5.0.0 (13) as an upgrade and keeps the database.
- **Database:** schema 15 → 19 runs through the full migration chain. Routines, schedules, sessions,
  sets, weigh-ins, chats, summaries and profile rows are preserved. Each 4.4 multi-day routine becomes
  a Schedule plus standalone Routines; completed sessions keep their links.
- **Backups:** v1–v6 archives import. A v2 4.4.0 archive was restored into a fresh 5.0.0 install
  during release validation, including its two logged sets and its active Schedule.
- **Device-local settings not in backups:** API keys, profile picture, exercise media, per-exercise
  rest defaults and the launcher-icon choice.
- **Known migration limitation:** a routine whose value was already stripped by an earlier migration
  cannot recover the number from its stored JSON; the value may still exist in workout history and
  can be entered once, after which it persists.
- **Internal builds:** 13–25 were debug-signed and never public. A device carrying one must uninstall
  it before installing the release-signed 5.0.0; public 4.4.0 is unaffected.

### Verification

- **906 JVM/Robolectric tests across 120 suites, 0 failures.**
- `assembleRelease` and `lintDebug` pass; the release APK is signed with the production certificate
  `46893F8E6C463E01B7B849AEF18098331E4C162325E906D86762F16A6430BB2C`.
- Real **4.4.0 → 5.0.0** upgrade on `emulator-5562` preserved the logged sets and converted the
  legacy routine into a Schedule.
- A **4.4.0 schema-2 backup** imported into a fresh 5.0.0 install preserved history and activated the
  migrated Schedule. `ActiveSchedulePromotionTest` covers the same promotion and one-time legacy key
  consumption.
- Installation, onboarding, Home, Plan, Routine editing, active and quick workouts, drop sets,
  warm-ups, per-set rest, body map, exercise details/creation, Progress, history, AI screens, cloud
  backup, Settings and the icon picker were exercised on the final release build.
- Appearance for every README screenshot: **AMOLED Dark, Liquid Glass ON, Material Expressive 3 ON**.
- Known limits: physical RedMagic/Poco devices were unavailable; live AI provider calls were not
  exercised; real Google Drive/OneDrive synchronization was not available on the emulator (the same
  SAF write path was verified with a local document provider); tablets and landscape are not
  supported (portrait-only).

---

## Internal development history (4.5.0 – 4.8.3)

The sections below record the internal builds that led to 5.0.0. They are preserved for history;
where an earlier revision was later corrected, the 5.0.0 notes above describe the final behaviour.

## [4.8.3] - Internal development build

IronPulse 4.8.3 fixes the launcher-icon clipping and the last two paths that could still erase a
prescribed routine load. No Room tables or `.ironpulse` archive fields changed.

### Launcher icons

- The generated adaptive icons referenced the raw 512px foreground as the whole 108dp layer, so
  their dumbbell rendered about 70dp wide — outside the 66dp adaptive safe zone — and circular or
  squircle masks cut the plates. Each generated foreground now uses a 60dp centered layer-list,
  giving the same visible width as the default icon (38.7dp versus 38.3dp). The monochrome layer is
  wrapped the same way.
- The recent-tasks card now follows the selected launcher icon. Activity aliases only change the
  launcher entry; `MainActivity` now sets a task description from the chosen artwork whenever the
  preference changes, so Recents no longer falls back to the default icon.

### Routine loads

- Text and AI programs keep their prescribed loads. The legacy text parser hard-coded
  `targetWeight = 0` and ignored `@ 34kg` in the prescription, so the 4.5 schedule/routine rebuild
  erased every load that existed only in the program text when it converted routines to JSON.
  Loads are now read from the prescription (`kg`/`lb`, with `+`/`added`/`weighted` meaning added
  weight) and normalized against the exercise's real fields.
- JSON items whose `weight`/`addedWeight` fields are empty also fall back to their prescription
  text.
- Exercise lines written as `Name: 3 x 8 @ 34kg` are no longer classified as prose and dropped;
  the colon without a leading space is now a valid name separator.
- A routine that was already migrated before this build cannot recover a value its stored JSON no
  longer contains; the value may still be present in workout history.

### Verification

- **904 unit tests passed**, across 119 suites. New tests cover the legacy text-to-routine
  migration, a load in a JSON prescription, `+9kg` added-weight text, a plain load on an
  added-weight exercise and a no-unit line.
- A synthetic backup generated from the athlete's 15:37 backup, with the Single Arm Band Curl
  prescription set to `3 sets x 8 reps @ 34 kg`, was imported on `emulator-5556`; the routine
  editor showed **34 kg**. The text migration unit test produced the same value from a real
  4.4-style text routine.
- Launcher icons were checked in the app drawer with **Ocean Blue**: the whole dumbbell sits inside
  the circular mask, and the recent-tasks card showed the selected variant. Evidence is in
  `docs/screenshots/4.8.3/`.

---

## [4.8.2] - Internal development build, revision 3

IronPulse 4.8.2 Revision 3 restores prescribed loads in older routines and makes the routine editor's
exercise details reachable. No exercise fields, Room tables or `.ironpulse` archive fields changed.

### Routine loads

- Fixed routines showing **0 kg** in every main set while the card still advertised `+9 kg`. Routine
  JSON written before the explicit field architecture stored dumbbell loads in `addedWeight`; after
  the field audit a Standard weight exercise only records `weight`, so the value was invisible and
  sanitizing erased it on save.
- A compatibility migration now moves such a load into the field the exercise actually records,
  before the editor, plan card or workout reads it. It also covers every main-set default and drop
  set, works in both directions (weight ↔ added weight) when only one field is supported, leaves
  exercises that record both untouched, and never rewrites the stored routine until the athlete
  saves.
- The routine card now shows a small working-load badge (`9 kg`) for Standard weight targets, next
  to the existing `+9 kg` added-weight badge, so the prescription is visible without opening the
  defaults.

### Routine editor

- Tapping an exercise name now opens the same read-only `ExerciseDetailDialog` the workout uses:
  media, instructions, logged fields, targets and body map, with **no pencil** and **no Add Set**.
- The exercise-level defaults panel (added weight and routine rest) moved to a small tune button,
  shown only for exercises that record an added weight, so the name is no longer its hidden route.
- Both the Create/Edit Routine screen and the Plan-tab routine editor use the same sheet.

### Verification

- **899 unit tests passed**, across 119 suites. New tests cover the legacy load migration (parse,
  sanitize, main sets, drops), preservation of genuine added-weight work, the name-vs-defaults
  affordances and the read-only detail sheet.
- The athlete's real 15:37 backup was imported on `emulator-5556`: the editor shows **9 kg** in
  Main set 1, **Update** persists `"weight":9,"addedWeight":0`, and starting the routine prefills
  set 1 at 9 kg. Tapping the exercise name opened the detail sheet with no pen and no Add Set.
  Evidence is in `docs/screenshots/4.8.2-rev3/`.

---

## [4.8.2] - Internal development build, revision 2

IronPulse 4.8.2 Revision 2 is a stabilization pass. It fixes two UI state-reset bugs, aligns the
routine editor's set cards, makes the AI Coach composer follow the dock, removes the remaining
unnecessary image work from tab navigation and adds the missing AI-provider warning. No exercise
fields, Room tables or `.ironpulse` archive fields changed.

### Progress

- Reduced the gap between the Body Weight Timeline card and the Weigh-in Logs card. The section
  header is shorter, and its surrounding spacers are smaller, so the two cards read as one group.
  The header height is fixed, so expanding or collapsing the logs cannot reintroduce the gap.
- Fixed the activity heat map resetting to the newest month after vertical scrolling. The card is a
  `LazyColumn` item, so scrolling it out of view disposed it and its "scroll to newest" effect ran
  again on the way back. The initial jump is now recorded with the item's saved state and happens
  exactly once; the athlete's chosen month survives any amount of vertical scrolling.

### Routine editor

- The drop-set branch arm is now anchored to the drop-set header, not the card's vertical centre.
  Expanding a drop set no longer drags the arm into the middle of the expanded fields.
- The main-set defaults panel and the drop-set card now start at the same 28dp gutter and share the
  same right edge, so their outer widths match.
- **Set defaults & drop sets** now starts collapsed in both Create Routine and Edit Routine. Opening
  an exercise shows only its summary until the athlete asks for more.
- Section, main-set and drop-set expansion are stored with the LazyColumn item's saved state. An
  exercise scrolled out of the viewport and back keeps exactly the panels that were open.

### AI Coach

- The chat composer now travels with the bottom dock. Opening the history drawer removes the 92dp
  dock clearance with the same 220ms spec the dock uses, so no empty strip is left beside the
  drawer; closing it restores the composer and the dock together.

### Performance and exercise images

- Exercise pictures remain on demand: the Exercises tab still pages 30 exercises at a time, and a
  row requests its still image only when the LazyColumn composes it. No code path downloads the
  catalog in bulk.
- Added a rate-limited warmer for exercises referenced by saved routines. It fetches only those
  still images, two at a time, after a short settle delay, and leaves the rest of the database
  alone. Coil joins duplicate requests and serves cached entries without touching the network.
- The existing persistent Coil disk cache (`cache/exercise_media`, 80 MB) is reused unchanged, so
  updates and later sessions keep every image already downloaded and offline use keeps working.
- The dock's full-screen backdrop capture is reused for the 340ms navigation transition instead of
  being re-recorded every frame, removing the largest UI-thread cost from tab switches.

### Workout

- Fixed the rest field shifting down when a custom rest is set. The reset affordance used a
  Material `IconButton` whose 48dp minimum touch target grew the header row and pushed the stepper
  down. It is now an overlay that fills the text row without changing its height, so the rest field
  and the warm-up control stay aligned whether or not the reset button is visible.

### AI settings

- Added a red warning card directly under **Post-workout AI summaries** when the active provider has
  no saved key (or no endpoint URL for a custom provider). It reuses the Cloud Backups status card,
  explains that summaries may not work yet, and offers **Connect a provider**, which opens the API
  Key Setup page directly. The switch stays fully controllable, and the card appears and disappears
  as the configuration changes.

---

## [4.8.2] - Internal development build, revision 1

IronPulse 4.8.2 is a small corrective UI release on top of 4.8.1. It tightens workout spacing, adds
four more launcher icons, hides the bottom navigation while the AI Coach drawer is open and fixes
the routine editor's main-set fields. No exercise fields, Room tables or `.ironpulse` archive fields
changed.

### Active workout

- Moved the body-parts selector to 4dp above the exercise card, so the button and the card it
  belongs to read as one group instead of two separate blocks.
- The **Add drop set** action keeps 6dp of breathing room from the bottom edge of its set card. Its
  tap target and behaviour are unchanged.

### Routine editor

- Fixed the per-exercise field editor. The main-set fields were hidden behind a second collapsed
  disclosure, which made the field dialogs look unresponsive. **Set defaults & drop sets** now opens
  expanded for every exercise, so tapping a main-set row opens its fields immediately.
- Removed the exercise-level chevron to the left of the reorder arrows. It opened a separate panel
  at the bottom of the card rather than the field editors, so the row now offers only **Move Up**,
  **Move Down** and **Remove Exercise**.
- The drop-set branch connector now continues through an expanded main-set panel. Expanding both
  **Set defaults & drop sets** and a main set no longer leaves the bracket cut off above the drop-set
  number.

### AI Coach

- Opening the chat history drawer now hides the bottom navigation with a slide-down, fade and scale
  animation, and closing the drawer restores it. The drawer's liquid-glass backdrop is disabled
  while the dock is hidden, so the list and the version footer render cleanly.

### Launcher icons

- Added four more selectable icons: **Ocean Blue**, **Crimson Red**, **Rose Quartz** and **Arctic
  White**, for eight in total. Each ships an adaptive icon, a round variant, legacy density bitmaps
  and a preview, generated by `tools/generate_launcher_icons.py`.
- The picker shows all eight in the existing centered grid with the same selection animation and
  bottom confirmation. Each option maps to its own activity alias and only one alias is enabled at a
  time.

### Verification

- **892 unit tests passed**, across 117 suites.
- `assembleDebug` passed and the 4.8.2 debug build was installed over the existing 4.8.1 data on
  `emulator-5556`. The routine editor was checked in edit mode: main-set fields open on the first
  tap, the exercise chevron is gone, and the branch line stays connected with both disclosures
  expanded. The AI drawer was checked with 20 temporary conversations: the dock hides, the last chat
  and version footer stay reachable, and the dock returns on close. Body-parts and drop-set spacing
  and the eight-icon picker were inspected on the running app, and selecting **Ocean Blue** switched
  the launcher alias while the same task and process stayed alive. Evidence is in
  `docs/screenshots/4.8.2/`.
- Known limits: physical RedMagic/Poco devices remained unavailable. Live AI provider calls were not
  part of this release.

---

## [4.8.1] - Internal development build

IronPulse 4.8.1 is a focused UI and polish release. It fixes the exercise-card, schedule-dialog,
notification, cloud-backup and weigh-in issues found in 4.8.0 and adds a selectable launcher icon.
No exercise fields, Room tables or `.ironpulse` archive fields changed.

### Exercise cards

- The field summary now wraps inside the flexible width and the **Add Set** button keeps its
  minimum width and height, so four-field exercises no longer compress or clip the button.
- Removed the duplicated card overflow menu. The card/name opens the existing exercise detail
  dialog, **Add Set** remains on the card, and edit/delete remain available through their existing
  marker and detail controls.
- Moved the per-exercise default rest into the exercise detail dialog, directly under **Targets**:
  Off, 30s, 60s, 90s, 120s, 180s or 15-second steps, stored in DataStore. It seeds quick-workout
  sets and new routine entries; a rest saved on the routine itself still wins, and an athlete-edited
  set rest is inherited by the next set.
- The default-rest presets stay on one horizontally scrollable row. 180s no longer wraps onto a
  second line, so the control no longer reserves blank vertical space.
- Exercise field definitions, the exercise database and existing workout data are unchanged.

### Rest timer

- Fixed the rest-timer preset selection briefly showing one extra second (for example 01:00 to
  01:01 and back). Presets now apply the selected duration in a single digit transition with no
  rebound or intermediate step.

### Schedule dialogs

- Create and Edit Schedule no longer use a fixed 82% height. They wrap to their content, grow up to
  90% of the available height and fall back to scrolling on short screens or with the keyboard open.
- The dialogs open and close with a short scale/fade transition.
- Added a short explanation under **Goal Strategy** for every available goal.

### Notifications and cloud backup

- Import, export, cancellation and cloud-backup notices use one shared status card with the dismiss
  action integrated into the card instead of a detached **Dismiss** button below it.
- The cloud-backup card now shows a clear green enabled state with an icon, title and supporting
  text. The old **ON** badge was removed and blank runtime messages are hidden.
- A cloud-backup success notice now appears above the last-backup block instead of after the
  **Back up now** button.

### Weigh-ins and launcher icon

- The weigh-in header reserves the same height in both states and uses an explicit header-to-panel
  gap, so expanding or collapsing the logs no longer shifts the section or the cards below it. Only
  the expanded state adds a **Hide all** action; the collapsed card keeps its own **View all**.
- Added four selectable launcher icons: **IronPulse**, **Cool Cyan**, **Signal Amber** and **Ultra
  Violet**. They are generated from the existing IronPulse logo by
  `tools/generate_launcher_icons.py`.
- Settings → Appearance → App Icon previews each variant, marks the selection and applies it
  immediately through Android activity aliases. Exactly one launcher alias is enabled at a time,
  the choice survives restarts and updates, and the default icon is used if the saved key is
  unknown.
- Fixed the app closing when the launcher icon changed. Aliases now open a transparent bridge that
  roots the app task at `MainActivity`, so the selected alias can be swapped while the task and
  process stay alive. A bootstrap alias keeps a fresh install launchable and disables itself after
  the first app start.
- The four icon choices are centered in the picker. Selecting one animates the card's scale,
  background, border and check, and a bottom pop-up confirmation slides in, fades in, then
  auto-dismisses.
- The launcher-icon choice and per-exercise rest defaults are device-local preferences and are not
  included in `.ironpulse` backups.

### Verification

- **892 unit tests passed**, across 117 suites.
- `assembleDebug` passed and the 4.8.1 debug build was installed on `emulator-5556`. Exercise-card
  layout, removal of the overflow menu, the under-Targets default-rest control and its horizontal
  preset row, schedule Create/Edit dialogs, a real export, a real import through the system picker,
  cloud notices, the cloud enabled state, weigh-in collapsed/expanded spacing, and launcher-icon
  switching were inspected on the running app. Changing Cool Cyan → Signal Amber → IronPulse kept
  the same `MainActivity` task and process alive; the picker's centered grid, selection animation
  and bottom confirmation were checked. The timer preset transition was recorded at 30 fps and no
  longer rebounds after reaching the target. Evidence is in `docs/screenshots/4.8.1/`.
- Known limits: physical RedMagic/Poco devices remained unavailable. Live AI provider calls were
  not part of this release.

---

## [4.8.0] - Internal development build

IronPulse 4.8.0 replaces the legacy type-driven set inputs with an explicit, per-exercise field
configuration. Every exercise in the shipped catalogue was audited. The database schema, dataset
version and `.ironpulse` data schema were migrated without destructive changes.

### Exercise database

- Audited all **1,334** exercises. **391** ordered configurations changed and **943** were left
  unchanged. The complete matrix is `docs/exercise_field_audit.csv`; the reproducible generator is
  `tools/audit_exercise_fields.py`.
- Added stable field identifiers: `reps`, `weight`, `addedWeight`, `holdSeconds`, `timeSeconds` and
  `distanceKm`. Each exercise now stores its own ordered supported-field list.
- Corrected weighted planks and holds, weighted calisthenics, static stretches, no-load props,
  loaded carries, conditioning work and bodyweight movements that inherited an irrelevant hold.
- Tags/types now only suggest defaults. An explicit field list is authoritative and is never
  silently rewritten by a type change.

### Workout and routine UI

- Active workout rows render the first two configured fields inline and put any additional fields
  in the existing per-set expansion. Drop sets inherit the same fields and independent values.
- Routine creation/editing exposes all supported fields, including per-main-set defaults, drop-set
  defaults, rest and distance.
- Create Exercise and Edit Exercise allow independent field combinations without changing tags,
  equipment or muscle groups. The selector previews which fields will be inline and which will be
  expanded.
- History and Progress display reps, holds, time, load and distance from the recorded values.
  Hold-only sets no longer produce a fabricated 1RM or a zero-rep record.

### Data and compatibility

- Room **18 → 19** adds `exercises.supportedFields`; dataset version **5** seeds every catalogue row.
- `.ironpulse` data schema **5 → 6** adds optional exercise field configuration. Old backups import
  with an empty list, which keeps the legacy type fallback.
- Historical set values and recorded exercise types are preserved. The automatic history-type
  rewrite on database open was removed.
- Calculations remain value-aware: any recorded added weight contributes to load; warm-ups remain
  volume but never a PR; hold/distance values are not converted into reps or volume.

### AI

- AI exercise creation accepts `CARDIO` and explicit field lists.
- Routine generation, natural-language parsing, Coach tools, active workout/history inspection and
  proposal validation now carry per-exercise supported fields and distance.
- AI-created exercises preserve body part, target, secondary muscles, instructions and fields.
- No live AI provider credentials were available; prompt/schema/parser/tool behavior was tested
  locally only.

### Verification

- **885 unit tests passed**, across 114 suites.
- Real upgrade verified on `emulator-5556`: a schema-10 database with 1,333 exercises upgraded to
  schema 19 with all rows and ids preserved and every row given an explicit field list.
- `assembleDebug` passed; the debug APK was installed and the field, routine, drop-set, history and
  Progress screens were inspected. Evidence is in `docs/screenshots/`.
- Known limits: physical RedMagic/Poco devices were unavailable; live AI providers were not tested;
  hold and time share the existing duration column, and weight and added weight are mutually
  exclusive.

---

## [4.7.1] - Internal development build

### Changed

- Cloud backup no longer pre-detects the Google Drive package. Android's document picker is
  the only source of truth, so Drive is selectable whenever its document provider is
  available. The false "Google Drive is not installed" message was removed.
- The cloud backup integration stays on the Storage Access Framework. OAuth and the Drive
  API were evaluated, but a serverless app cannot keep long-lived refresh tokens for
  automatic background backups without a backend. The trade-offs are documented in
  `docs/CLOUD_BACKUP_ARCHITECTURE.md`.
- The onboarding data step no longer offers an export, which a new install has no data for.
  Import and cloud-folder setup remain available there; export remains in Settings.

### Fixed

- The workout-history statistics cards now share one height, and the volume value and unit
  stay on one line. The number keeps priority over the unit on narrow screens.

### Performance

- Exercise-list thumbnails now use the dataset's static 180x180 JPG instead of downloading
  the full animated GIF. The animation remains in the detail view. First-scroll image data
  dropped by roughly 5x in measurement (about 4.3 MB to 0.9 MB for the same list scroll).
- List placeholders no longer run an infinite pulse animation per row.

---

## [4.7.0] - Internal development build

IronPulse 4.7.0 adds automatic cloud-folder backups and an onboarding data-management step. It
does not change the database schema or the `.ironpulse` archive format.

### Added

- **Cloud Backup** under Settings, Data Management. Choose a folder with Android's system folder
  picker, including Google Drive or OneDrive when their document provider is installed.
- **Automatic backups** after a completed workout, after a restore, after profile or onboarding
  changes, and once a day as a recovery net. Rapid changes are grouped, and manual "Back up now"
  is always available.
- **Safe retention.** Cloud backups use timestamped `.ironpulse` files, are read back and hashed
  before being reported as successful, and keep the five most recent cloud backups. A failed
  attempt never overwrites an older backup.
- **Onboarding data step.** New installs can import an existing backup, export a backup, or connect
  a cloud folder before finishing setup. The step is optional and can be skipped.

### Fixed

- Finishing onboarding or restoring a backup no longer crashes while the app switches from the
  onboarding composition to the main interface.

### Notes

- Cloud backups use the same `.ironpulse` archive as manual export. They are not encrypted.
  API keys, the profile picture and exercise media are not included, matching manual export.
- A folder permission is stored only on this device and is not written into backups.
- Google Drive synchronization itself is performed by the Google Drive app/document provider.
  IronPulse writes through Android's Storage Access Framework and does not use Google OAuth.

---

## [4.6.2] - Internal development build

IronPulse 4.6.2 adds motion to the active workout interface on top of 4.6.1. It does not change
the database schema or backup format.

### Added

- **New-set animation.** Adding a set unfolds the new row from the top and slides it down into
  place while the Add Set button moves with it.
- **Exercise-switch swipe.** Selecting another exercise slides the old exercise out and the new
  one in horizontally, in the direction of the selector movement, in both the Routine workout and
  the compact Quick Workout tray.
- Both transitions use the app's reduced-motion setting: with system animations disabled, the set
  list and exercise content change instantly.

---

## [4.6.1] - Internal development build

IronPulse 4.6.1 is an internal workout-interface refinement on top of 4.6.0. It does not change
the database schema or backup format.

### Changed

- **Set column header.** Field names are shown once above the set cards instead of on every row.
  The header labels align directly over the numeric inputs.
- **Set actions.** Completion and delete are smaller rounded-square actions with precise hit
  targets, vertically aligned with the field row and no touching/overlapping touch areas.
- **Body-part preview.** The compact anatomy preview sits further inside the top-right corner.
- **Quick Workout.** The compact interface scrolls as one surface at short tray heights, and the
  selected exercise survives minimize, resize and cross-tab navigation.

### Fixed

- **Warm-up toggle.** Marking a set now animates the check with a spring scale/rotation and an
  animated warm-up box.
- **Liquid Glass workout controls.** The Quick Workout action bar uses the same single-tone glass
  as the Routine bar, and the Routine bar now uses the real backdrop blur instead of the old
  white top-lit glass gradient.

---

## [4.6.0] - Internal development build

IronPulse 4.6.0 replaces the cramped card-based active workout with an exercise-selector
interface, adds persistent warm-up sets and per-set rest overrides, and carries both through
history, progress, AI, drafts and backups. This build is internal and is not a published release.

### Added

- **Exercise-selector workout interface.** A horizontal numbered selector, a focused exercise
  overview, one set list and a compact body-part preview replace the stacked exercise cards in both
  saved Routine workouts and the Quick Workout popup.
- **Warm-up sets.** Every logged set has its own persisted `isWarmup` flag, toggled from the
  expandable per-set panel. Warm-up sets remain visible in history and contribute to volume.
- **Per-set rest overrides.** Each set can store its own rest duration. Inherited Routine defaults
  remain the fallback, and completing a set starts the existing floating rest timer with the
  resolved duration.
- **Body-part completion map.** The workout header shows the existing anatomy illustration coloured
  by completed/total sets for every body part in the whole workout, with a compact dialog listing
  per-part counts and progress bars. Material Expressive 3 uses the existing animated dot-snake
  progress treatment.
- **Read-only workout details.** Tapping an exercise name opens the existing exercise-detail card
  with editing disabled while the workout is running.

### Changed

- **Set rows.** Fields are grouped into unified dark stepper boxes with centred labels and compact
  plus/minus controls. Completion and delete use equal rounded-square actions. The set-number
  circle rotates through a full turn when its panel opens and reverses when it closes.
- **Set options panel.** Rest and warm-up controls are attached to the same set card, split evenly,
  and use the same stepper styling. The inherited default is labelled, and a reset action restores
  it without changing global settings.
- **Quick Workout tray.** The popup keeps its existing minimize/half/full resize modes and non-modal
  coexistence with navigation, but now hosts the same interface in a compact, fully scrollable
  form so set rows remain reachable at small heights.
- **Progress metrics.** Maximum reps, estimated one-rep max and best working sets exclude warm-up
  sets. Total volume still includes them. A workout with only warm-up sets shows no working-set
  record instead of a fabricated maximum.
- **History.** Completed workout details and per-exercise session history mark warm-up sets with a
  readable `WARM` badge.
- **AI context.** Gemini monthly summaries receive explicit per-set warm-up flags and working-set
  volume/1RM rules; AI Coach retrieval tools return `isWarmup`, rest overrides and warm-up/working
  set counts, and their instructions distinguish preparation from working-set PRs.
- **Backup schema 4.** Workout payloads carry per-set warmth and rest. Schema 3 and older archives
  migrate with every legacy set treated as a normal, inherited set.

### Fixed

- Warm-up and rest choices survive exercise switching, set add/delete, draft restore, workout
  completion, history retrieval and backup round trips.
- Quick Workout state and the selected exercise survive minimizing, resizing and navigating to
  another tab and back.
- Set rows no longer use a card-wide click target that merged badges and made the panel controls
  ambiguous.

---

## [4.5.1] - Internal development build

IronPulse 4.5.1 is an internal refinement build on top of 4.5.0. It polishes Routine and Schedule
management and does not change the Room schema. This build is internal and is not a published
release.

### Added

- **Routine exercise search.** A dedicated, relevance-ranked search over the full catalog with
  category filters, a scrollable result list and the same exercise details card used by the
  Exercises tab.
- **Read-only details in Routine search.** Instructions, tracked fields, animation, target muscles
  and the body map are available before adding an exercise. The edit pen is hidden in this context;
  **Add to Routine** remains available.
- **Batch management.** Routine and Schedule batch selection with confirmation dialogs and
  reference-safe deletion.
- **Schedule Start Workout.** Every Schedule can start a workout for one of its assigned weekdays
  without changing the active Schedule.
- **Starter Schedule feedback.** Copy progress, success feedback and a duplicate-copy guard.

### Changed

- **Routine editor.** Reused the full-height details card, added a sticky Save action and a clearer
  empty state, and restored expandable rest/added-weight controls on each exercise row.
- **Schedule goal selector.** Restored the animated flag dropdown inside a full-height create/edit
  dialog so the options are not clipped.
- **Delete App Data.** Now clears Schedules, weekday slots, routines and the active Schedule, then
  returns through onboarding to Home.
- **Onboarding and Settings.** Updated copy for the Routine/Schedule model, including the data
  deletion warning.
- **Quick Workout dock.** The tray and bottom navigation join correctly with Liquid Glass on and
  off.

### Fixed

- Avoided duplicate Routine entries when adding from the search details for an exercise that is
  already selected.
- Preserved the search query, category filter and result scroll position while opening and closing
  exercise details.
- Kept Schedule references safe when deleting Routines: affected weekday slots become rest days
  and completed workout history is retained.
- Restored accessible exercise-card badges and expansion controls after removing the card-wide
  click target.

---

## [4.5.0] - Internal development build

IronPulse 4.5.0 replaces the legacy multi-day routine architecture with standalone single-workout
Routines and weekly Schedules. This build is internal and is not a published release.

### Changed

- **Routine means one workout.** The former multi-day container is gone from the active UI. A
  Routine is one ordered exercise list with its own sets, reps, rest and added-weight defaults.
- **Plan tab.** The temporary Routine destination is now Plan, with Schedules on the left and
  Routines on the right. Schedules show seven weekday references, an active state, a goal strategy,
  rest days and starter templates.
- **Active Schedule drives the week.** Home, Next Workout, Start Today and the streak use the active
  Schedule's stable weekday references. Changing the week-start preference changes display order
  only.
- **Schedule goal strategy.** The existing goal options moved to the Schedule. A workout started
  through a Schedule captures that strategy at start; an already running workout is unaffected by
  later edits.
- **Automatic migration.** Room schema 16 converts each legacy multi-day program into standalone
  Routines plus a Schedule, deduplicating identical workout days inside the program and preserving
  the old active selection. Completed sessions and logged sets are untouched.
- **Backup schema 3.** `.ironpulse` archives now carry Schedules, weekday slots, Routine model
  versions and the active Schedule id. v1/v2 archives are converted on restore by the same
  deterministic migration.
- **AI workflows.** Coach tools, Routine Generator, the parser and prompt workouts now create
  Routines first and then a Schedule; single-workout requests create one Routine and no Schedule.

### Added

- Four built-in Schedule templates: Three-Day Full Body, Four-Day Upper/Lower, Five-Day Push Pull
  Legs and Six-Day Push Pull Legs. Copying a template duplicates each unique Routine exactly once.
- Routine and Schedule expansion animations, assignment popups, copy/edit/delete actions and
  empty states.
- `PlanArchitectureTest` coverage for migration, deduplication, reference cleanup, Schedule copying
  and single-workout generation.

---

## [4.4.0] - 2026-11-08

IronPulse 4.4.0 is the Settings, Progress and routine-navigation release. It reorganizes the
Settings screen into a category hub, gives Saved Routines its own bottom-navigation tab, adds the
Exercise Progression page and five-day Session History paging, and redraws the interactive muscle
map. It also completes the MainViewModel decoupling that moved eight feature areas into dedicated
state holders. The Room schema remains at v15, so there is no database migration to run.

### Added

- **Routine tab.** Saved Routines now has a dedicated bottom-navigation destination, using a
  checklist icon and retaining the existing Saved Routines cards, expand/collapse behaviour,
  routine creation, editing, copying, deletion, weekday picking and workout launch.
- **Settings hub.** Settings is now organized around six categories: Appearance, User Preferences,
  AI Tools, Data, About and Support. Each category opens a dedicated subscreen with a consistent
  slide-and-fade transition and Android Back support.
- **Athlete Profile card at the top of Settings.** The existing card keeps its layout, pen icon,
  metrics and editing flow, now reachable without a Profile tab.
- **Appearance settings.** Theme, accent color, Android home-screen widgets, Home tab widget
  configuration, Liquid Glass and Fullscreen mode now live in one Appearance subscreen.
- **Saved custom accent colors.** Custom colors can be previewed, applied and saved for reuse
  alongside the existing preset swatches. Saved custom accents persist across restarts and theme
  changes.
- **Material Expressive 3 option.** An optional, persisted appearance setting shows an
  accent-colored animated preview of countdown lines and the completed-set snake treatment. When it
  is off, the existing animation behavior is unchanged.
- **AI Tools subscreens.** AI Data Access and API Key Setup are separate destinations. The three
  existing data-access toggles and the complete provider/key/model card retain their original
  behaviour.
- **Data subscreens.** Exercise Database Options and Data Management are separate destinations,
  including the existing export, import and Delete App Data safeguards.
- **Exercise Progression page.** The selected-exercise dropdown, statistics, graphs, progression
  calculations and AI Monthly Summary now live on a dedicated page opened from Progress.
- **Selected-exercise muscle illustration.** The Exercise Progression page shows the anatomical map
  between the exercise selector and the AI Monthly Summary, highlighting the exercise's actual
  primary muscles and showing secondary muscles where the database provides them.
- **Collapsible Session History with five-day paging.** Session History starts collapsed, loads the
  first five distinct logged days, and exposes **Load More** for the next five. Pagination is backed
  by a DAO query rather than loading the full history into memory.
- **Animated Session History expansion.** The section expands with a height/fade transition and a
  rotating indicator, and new pages append with a smooth size transition.

### Improved

- **Settings navigation and interaction.** The crowded single-page Settings layout is replaced by a
  coherent hierarchy with per-subpage scrolling, preserved hub scroll position and accent-colored
  active controls.
- **Home entry points.** The Home widget-configuration button is replaced by a Settings gear, and
  widget configuration now lives in Appearance.
- **Anatomical muscle map.** The body illustration now uses a human silhouette with separated arms,
  hands and legs, correctly shaped pectorals, deltoids, arms, forearms, abdominals, obliques,
  quadriceps, shins, trapezius, lats, erector spinae, glutes, hamstrings and calves. Muscle regions
  are defined once per side and mirrored once, eliminating the overlapping doubled ovals.
- **Muscle highlighting.** Resting muscles use the neutral theme palette; selected muscles use the
  current accent, with an accent-derived outline that adapts to light and dark themes. Interactive
  chest, back and leg filtering was re-tested.
- **Progress layout.** Body Weight Timeline and Weigh-in Logs now sit below the Activity heatmap,
  preserving the existing chart, units, history, editing and persistence.
- **Profile cleanup.** The Profile tab is gone; the athlete profile card lives in Settings and body
  weight/weigh-in history lives in Progress.
- **AI Coach selector.** The selector now has exactly two evenly weighted, centered pages: AI Coach
  Chat and Routine Generator. The existing animated underline and transitions are preserved.
- **About ordering.** About IronPulse, Check for updates and the startup update toggle now appear in
  the requested order inside the About subscreen.
- **Home and Settings accessibility.** The Home gear and Settings category rows use the existing
  IronPulse iconography, card styling and accent behavior in both Liquid Glass states.

### Changed

- **Profile is replaced by Routine in the bottom navigation.** The old Profile destination was
  removed. Routine contains the complete existing Saved Routines interface as its primary content.
- **Saved Routines moved out of AI Coach.** AI Coach now contains only AI Coach Chat and Routine
  Generator; the Saved Routines tab and its obsolete tab-request state were removed.
- **Home Saved Routines navigation.** The Home Saved Routines card and the Android widget
  Saved Routines action now open the Routine tab.
- **Profile widget action.** The Workout Profile launcher widget now opens Settings, where the
  Athlete Profile card lives.
- **AI generation completion.** Saving a generated routine now routes to the Routine tab through the
  same callback instead of switching the AI Coach sub-tab.
- **Docs and project metadata** now describe the Settings hierarchy, Routine tab, Progress
  relocation and 4.4.0 release.

### Fixed

- **Doubled muscle-map shapes.** Abs, biceps and leg groups were being drawn twice because paths
  already containing both sides were mirrored again. Each region now has one authoritative path per
  side.
- **Stale muscle-map tap callback.** The tap handler now observes the latest selection callback, so
  selecting the same muscle twice reliably deselects it in editable callers.
- **Date-dependent streak test.** The future-planned-day test no longer assumes tomorrow is always
  inside the twelve-week heatmap; it uses a fixed midweek anchor and assert future days on every run
  day. The streak calculation itself was unchanged.
- **Legacy and future streak behavior** remains covered by the existing schedule, rest-day, missed-
  day, timezone and daylight-saving tests.

### Architectural Improvements

- **MainViewModel decoupling.** Eight feature responsibilities were extracted from the 5,221-line
  ViewModel into dedicated state holders: `RestTimerController`, `AppUpdateController`,
  `DataTransferController`, `AiConfigurationController`, `HomeDashboardController`,
  `ProgressAnalyticsController`, `QuickWorkoutSessionController` and
  `RoutineWorkoutSessionController`.
- **Compatibility facade.** `MainViewModel` remains the application-level facade and preserves the
  existing UI-facing `StateFlow` types, sharing modes, initial values, coroutine scopes and observable
  ordering. Screens continue to consume the same state and actions.
- **Separation of concerns.** Rest timers, updates, backup/transfer, AI configuration, Home
  derivation, progress derivation, quick workouts and routine workouts now have explicit owners
  instead of sharing one implementation block.
- **Database access.** Session History pagination adds read-only DAO/repository methods for distinct
  logged days and does not alter stored history or persistence semantics.

### Other Changes

- `versionCode = 12`, `versionName = "4.4.0"`.
- Room schema remains **v15**; no migration is required for this release.
- No application ID, package name or signing configuration changes.
- The unit suite now reports **756 tests across 93 classes**, all passing.
- This release includes no weekly Schedule or Plan restructuring, no single-workout routine
  migration and no workout-screen redesign.

---

## [4.3.0] - 2026-11-06

IronPulse 4.3.0 adds app-wide accent color customization and carries the 4.2.x workout, streak,
heatmap and Markdown reliability fixes. The 4.2.0 data format and settings remain compatible; no
Room migration is required.

### Added

- **Accent Color in Settings.** A new Accent Color section inside the existing Preferences card
  offers IronPulse Blue, Purple, Cyan, Green, Orange, Red, Pink and Yellow circular swatches, with a
  clear selected ring/checkmark and a "Selected: ..." label.
- **Custom accent picker.** A dedicated dialog provides a circular hue ring, a
  saturation/brightness square, a live preview, HEX input and separate R/G/B inputs. Apply commits
  the color; Cancel discards it.
- **Application-wide accent propagation.** The selected accent drives primary buttons, selected
  bottom-navigation and tab items, focused text-field borders and cursors, checkboxes, sliders,
  progress indicators, selected chips and other active controls that already use the standard
  accent, through the existing centralized Material 3 palette.
- **Contrast-aware rendering.** Custom colors are adjusted only when necessary to remain readable
  against the active theme's surfaces while preserving the chosen hue. Unusual colors are accepted,
  not rejected.
- **Accent backup support.** `.ironpulse` backups now include the selected accent alongside the
  existing appearance preferences.

### Changed

- The accent defaults to the original IronPulse blue. Existing installations and fresh installs
  keep the current per-theme blue until the athlete chooses another color.
- Accent selection persists independently of the theme and Liquid Glass, applies immediately without
  a restart, and survives a full app restart.
- The workout control bar and docked rest timer are back at the bottom edge, and their Liquid Glass
  surface uses the same uniform single-tone treatment as the navigation dock.
- The AI Parser now sits directly under **Generate Structured Routine** in the Routine Generator
  card, in both standard and Liquid Glass themes.

### Fixed

- Historical routine sessions saved without a `plannedDate` are now recognized as scheduled
  completions, so old workout history contributes to the current and best streak. Scheduled rest days
  still remain neutral.
- The Home streak heatmap now sizes its cells from the available width, keeping the final column
  visible on narrow screens and alternate display sizes.
- Post-workout AI summaries now render Markdown tables as real tables with header/body rows and
  horizontal scrolling, and support H1-H6, horizontal rules, nested lists, strikethrough, bold+italic,
  inline code, links and escaped characters.
- The AI Parser button is no longer obscured by the bottom navigation dock.

### Technical

- `versionCode = 11`, `versionName = "4.3.0"`.
- The accent is stored as `#RRGGBB` in the existing DataStore preferences and is exported through the
  existing allowlisted backup preference section. No database migration was added.
- Unit coverage includes accent parsing, preset metadata, contrast enforcement, palette isolation,
  DataStore round-trips, backup round-trips, legacy streak sessions and Markdown tables.

---

## [4.2.0] - 2026-11-05

IronPulse 4.2.0 is a reliability and polish release focused on the AI Coach, scheduled streak and
heatmap correctness, native launcher widgets, and navigation appearance. The 4.1.0 data format and
provider configuration remain compatible.

### Added

- **Routine creation from AI Coach chat.** The Coach can propose a complete new weekly routine,
  show every training day and prescription in an approval card, and save it only after the athlete
  approves. Rejecting a proposal writes nothing.
- **Bounded AI tool correction.** A malformed text-protocol tool request now receives a correction
  turn containing the exact envelope, available tool names and argument schemas. Repeated failure
  ends with a clear user-facing error instead of an unreadable answer.
- **Structured exercise suggestions.** Empty history results now say whether the exercise exists in
  the catalog and return clearly labelled closest matches rather than inventing data.
- **Human-readable session dates.** Retrieved workouts include both `yyyy-MM-dd` and a `dateLabel`
  so small models do not shift a session to the wrong day.

### Improved

- **AI Coach reliability.** Gemini tool schemas are reduced to the provider-supported subset, and
  compatibility-mode tool calls/results stay in the text protocol instead of being sent as native
  Gemini function parts. This fixes the `thought_signature` rejection and the “answer the app could
  not read” failure on natural-language history questions.
- **Exercise-name matching.** History and catalog lookups now normalize case, punctuation, spacing,
  compound spellings and simple plurals, so “push ups” matches “Push-Up” without merging unrelated
  movements such as “Pike Push Up”.
- **Post-workout summaries.** An empty stored model now resolves to the provider’s visible default
  (Gemini 3.5 Flash Lite), so a configured Google key works without a separate model selection.
- **Scheduled streak and Home week.** The week card marks only completed scheduled workouts, quick
  workouts never tick a scheduled day, and scheduled completions update the streak immediately.
  Calendar arithmetic is daylight-saving safe.
- **Home activity heatmap.** A workout completed today appears immediately, including after a save
  without leaving the Home tab. Activity and scheduled adherence remain separate calculations.
- **Native widgets.** Weekly and Profile widgets now target 3×1. The weekly widget uses a full-width
  heatmap row with larger rounded day cells, today’s workout text and a clear Start/Open button.
  The profile widget uses four centered metric cards. The streak widget adds a lightweight value
  transition, and the in-app previews mirror the native structure and current theme.
- **Liquid Glass.** The floating navigation dock keeps real backdrop blur and transparency while a
  uniform tint and stronger blur remove the pale top band. Scrollable pages and fixed action bars
  reserve room for the dock.
- **AI data access settings.** Athlete-data sharing and AI Coach workout-data access are now two
  clearly separated permissions in the same card, with an Info dialog explaining both and the
  removed benchmark information.

### Fixed

- Gemini function declarations containing `additionalProperties` or `default` are no longer rejected.
- Compatibility-mode tool results no longer trigger Gemini’s missing-`thought_signature` HTTP 400.
- “How much have I improved in push ups?” now finds `Push-Up` history instead of reporting no data.
- Post-workout summaries no longer fail with “No AI model is selected” when the visible default model
  is being used.
- Quick workouts no longer mark a scheduled Home day as done or increase the scheduled streak.
- The Home heatmap no longer misses a workout completed today, including near a DST transition.
- Benchmark Records is removed from the Profile tab; its explanatory content remains available from
  AI Data Access.
- The AI Coach workout-data toggle now uses the app’s green switch styling.
- AI Coach input now enables Android sentence capitalization.
- Routine Workout Finish/Exit, AI Coach input, routine results and starter-routine actions are no
  longer covered by the floating navigation dock.
- The Liquid Glass dock no longer shows a separate whitish top strip.

### Changed

- Weekly Workout Overview and Workout Profile launcher widgets changed from 4×2 to 3×1.
- The weekly widget now puts the complete seven-day heatmap on the first row, with today’s workout
  and its action below.
- Profile widget metrics are presented as separate centered cards.
- Widget previews use the same rolling days and visual hierarchy as the installed widgets.

### Technical

- Added protocol, schema-sanitization, correction-loop, exercise-normalization, routine-proposal,
  DST heatmap and streak regression tests.
- Updated `docs/AI_TOOLS.md` with the new routine tool, correction behavior, normalization rules,
  permission boundaries and provider compatibility notes.
- Updated `docs/USER_DATA_INVENTORY.md` for `NEW_ROUTINE_PROPOSAL` chat payloads.
- Current test suite: 714 JVM tests across 86 test classes.
---

## [4.1.0] - 2026-11-04

IronPulse 4.1.0 turns the AI Coach into a permission-gated workout-data assistant, adds an explicit
approval workflow for routine changes, stores post-workout AI comparisons permanently, and brings
three native Android launcher widgets with independent themes. It also extends the `.ironpulse`
backup format and remains compatible with older backups.

### Added

- **Database-aware AI Coach.** Four bounded retrieval capabilities cover recent completed workouts,
  the current weekly routine including rest days, the exercise catalog, and exact-name completed
  exercise history with sets, reps, weight, hold duration, cardio duration and distance.
- **Explicit AI workout-data permission.** New Settings switch, off by default, with a detailed
  information dialog listing what the Coach can retrieve and what it is prohibited from doing.
- **Inline routine-change approval cards.** The Coach can propose one routine substitution, with an
  optional single AI-generated exercise, and nothing is written until the athlete presses
  **Grant permission** in the conversation.
- **Post-workout AI summaries.** Optional automatic comparison of a finished quick or routine
  workout against the previous comparable session, using the stable routine id and weekday where
  available and disclosed partial matching for quick workouts, then persisted against the exact
  workout.
- **Workout History summary access.** A clipboard-check action opens the saved summary without
  contacting the AI provider; workouts without a summary show a clearly disabled control.
- **Native Android home screen widgets:** Workout Streak (2x2), Weekly Workout Overview (4x2) and
  Workout Profile (4x2), with real app data and launcher navigation.
- **Independent widget appearance.** Light, Dark, AMOLED Dark and IronPulse Theme can be selected
  separately for each widget under Settings -> Android Home Screen Widgets -> Widget appearance.

### Improved

- AI tool execution supports multiple bounded tool rounds and duplicate-call caching while retaining
  provider-independent Auto/Native/Text behaviour.
- AI-created exercise validation now uses one shared parser for the routine generator, single
  exercise creator and routine proposals.
- The Profile tab keeps editable profile and benchmark records while the AI data explanation now
  lives in Settings.
- Widget data reuses the same `WorkoutStreak`, calendar-day, profile, unit and BMI logic as the app.

### Changed

- AI personal-data access is denied unless explicitly enabled; disabling it removes personal-data
  tools and invalidates pending routine approvals.
- `.ironpulse` data schema is now v2. Exports include immutable post-workout summaries,
  proposal-card metadata, AI access/summary switches and the three widget themes.
- Room schema is now version 15 with the new summary table and chat-message approval metadata.

### Fixed

- Older `.ironpulse` backups import with safe defaults for all new fields.
- Import verification treats hidden Home widget IDs as a set instead of depending on archive order,
  keeping the committed v1 fixture importable.
- Generated routine changes no longer delete the removed movement from the exercise catalog.

### Security

- AI retrieval is read-only and enforced at the tool dispatcher/service layer, not only in prompts.
- Routine writes require the Settings permission, a still-active routine, a matching routine
  signature, an unchanged weekday/exercise and an explicit one-time approval.
- Closing or revoking the permission during an active tool loop blocks the pending tool call.
- Backups continue to exclude API keys, signing material and other local credentials.

### Technical

- Added `workout_ai_summaries`, `WorkoutAiSummaryDao`, `MIGRATION_14_15`, shared `AiExerciseFactory`
  and shared `ProfileMetrics`.
- Added widget providers, metadata, layouts, drawables, navigation actions, midnight/boot/timezone
  refresh handling and independent DataStore theme preferences.
- Added focused tests for permission gating, mid-loop revocation, retrieval, proposals, stale
  approval, generated exercises, summary comparison/immutability, widget data/theming and v1/v2
  backup compatibility. The suite now has 705 JVM tests across 85 classes.

---

## [4.0.0] - 2026-11-04

This is the first release since 3.0.0. It adds complete user-data backup and restore, the four-theme
system, the Liquid Glass floating navigation surface, the fullscreen display mode, and the
schedule-adherence workout streak.

### Added

#### Data management
- **Portable `.ironpulse` backups.** Settings -> Data Management -> Export writes a versioned,
  checksummed ZIP archive of the athlete's data. It can be saved anywhere Android's Storage Access
  Framework can write.
- **Replacement import.** Import validates the complete archive before changing anything, shows a
  destructive confirmation with the backup's source version, date, size and category counts, and
  then replaces the destination user-data state. It is not a merge: workouts, routines, schedules,
  weigh-ins, AI conversations, saved summaries, custom exercises, preferences and Home
  customization all come from the backup, and empty categories clear the destination category.
- **Excluded local data is preserved.** API keys, the profile picture, and exercise images/GIFs are
  never exported. Import keeps the destination's API keys, profile picture and avatar path, and
  clears restored custom-exercise media references so the app's missing-media fallback is used.
- **Archive integrity and compatibility.** The manifest carries separate container and data-schema
  versions, section paths and record counts. SHA-256 checksums, path traversal and ZIP-bomb limits,
  duplicate/unknown-entry rejection, relationship validation and manifest/count checks run before
  any database write.
- **Safe replacement and rollback.** Room is replaced in one transaction; DataStore and the Home
  widget photo are staged and verified. A failure at any stage attempts to restore the previous
  Room, preference and file state, and the UI only claims preservation when rollback succeeds.
- **Documentation.** `docs/BACKUP_FORMAT.md`, `docs/BACKUP_ARCHITECTURE.md`,
  `docs/BACKUP_MIGRATION_GUIDE.md`, `docs/USER_DATA_INVENTORY.md` and
  `docs/BACKUP_IMPLEMENTATION_CHECKLIST.md`.

#### Liquid Glass and navigation
- **Real backdrop blur on Android 12+.** The floating dock records the page behind it into a
  graphics layer, blurs it with a RenderEffect and clips it to the dock silhouette. Android 7.1-11
  use the documented translucent glass fallback rather than a fake foreground blur.
- **Floating navigation surface.** The dock is an overlay instead of a reserved Scaffold bottom bar,
  so page content is visible behind the glass and the dock can sit above a visible system navigation
  bar.
- **Magnification lens.** Holding the dock activates a smooth lens that magnifies neighbouring
  destinations. Releasing over an item navigates to that item; releasing outside or cancelling does
  not navigate, and normal taps still work.
- **Drag-to-navigate fixes.** The gesture now uses the latest navigation callback and the true
  pre-lens item bounds, so dragging Profile -> Home works and a magnified Home slot cannot steal
  Profile's hit area.
- **Readable floating surfaces.** A content-heavy glass variant is used for the Home widget manager,
  keeping long labels and controls readable while preserving the glass chrome.
- **Liquid Glass setting.** The treatment and the bottom-bar lens can be disabled independently of
  the selected theme.

#### Four complete themes
- **IronPulse, Dark, Light and AMOLED Dark.** Every theme has a complete semantic palette covering
  surfaces, borders, text, accents, danger states, charts, activity heat maps, the muscle map and the
  Liquid Glass panel/lens.
- **One palette architecture.** `ThemeRuntime`, `AppPalette`, `LocalAppPalette` and dynamic legacy
  colour getters route the existing screens through the selected theme without per-screen theme
  branches.
- **Immediate, persistent selection.** The Settings theme selector applies without a restart,
  survives relaunch, and has a dedicated `themeModeFlow` so unrelated preference writes do not reset
  it. Unknown stored values fall back to IronPulse.
- **Contrast fixes.** `OnAccent` and `OnDanger` replace dark-page foregrounds on accent and
  destructive buttons, and the heat-map, chart, muscle and glass tokens are theme-specific.

#### Fullscreen and display insets
- **Fullscreen Mode setting**, default on. Enabling it hides the status and navigation bars and
  enables short-edge cutout rendering; disabling it shows both bars and restores the default cutout
  mode. The policy is reapplied when the window regains focus.
- **Cutout-aware top inset.** On API 33+ the app reserves the actual cutout path bottom rather than
  the full status-bar safe rectangle; older versions use the platform safe top inset. Non-fullscreen
  pages use `safeDrawing`.
- **Top spacing correction.** Main page headers were moved up from the old 16dp page padding to 8dp
  inside the measured cutout inset, removing the oversized blank band while keeping content below
  the camera.

#### Workout streak and schedule adherence
- **Materialized schedule history.** A new `scheduled_workout_days` table stores one row per date,
  including rest days, with a timezone-stable `yyyyMMdd` key. Past rows remain history when the active
  routine changes; only today and future rows are regenerated.
- **Session-to-plan linkage.** Workout sessions record the routine, weekday, planned local date and
  date key they fulfilled. The streak credits the planned date, not the moment the athlete pressed
  start, so a late completed Monday session still counts for Monday.
- **Adherence rules.** A planned day counts when a finished linked session has at least one completed
  set. Rest days neither break nor advance the streak. A missed planned day breaks it. Today's
  unfinished planned day is pending, not missed. Multiple completions on one date count once.
  Historical days before the schedule table are untracked, not retroactively missed.
- **Current and best streak.** Both are derived from the schedule and completed sessions, never from
  a counter incremented on app open.
- **Streak visualisation.** The new Workout Streak Home widget shows current/best streak,
  ALIVE/BROKEN/PENDING/NO PLAN state, a 12-week adherence heat map, a legend and Done/Missed/Best/Next
  totals. Alive uses a layered animated flame; broken/no-plan uses a static ash and smoke treatment.
- **Database upgrades.** Room 12 -> 13 adds the session link columns and schedule table; 13 -> 14 adds
  the timezone-stable date keys. Existing installations migrate without a manual step.

#### Home dashboard
- The Workout Streak widget is added to the Home widget catalog and defaults to visible.
- The existing widget manager, drag ordering, Motivation Photo and Weekly Goal continue to apply to
  the new card.
- Home, Progress, Profile, Settings, AI Coach and Create Routine share the corrected top inset and
  8dp page padding.

### Improved

- **Progress heat map empty days** now use a stronger empty fill and a 1dp border on every cell, so
  the grid remains visible in Dark and AMOLED instead of disappearing into the card surface. Active
  intensity levels and all calculations are unchanged.
- **Profile -> Settings navigation** keeps Profile highlighted. Settings maps to Profile for the
  dock's selected state while remaining a normal back-stack entry.
- **Routine Generator day selector** uses equal, expanding day-count tiles instead of clipped filter
  chips, so all three-to-seven-day options remain readable at normal and 1.5x font scale.
- **Saved routine rest tags** show the per-entry rest default, for example `Rest 1m 30s`; zero-rest
  entries show no tag.
- **Customize Home sheet** uses an opaque page-coloured baseline under the glass, so the dashboard no
  longer bleeds through long labels and buttons. The sheet also respects Fullscreen Mode.
- **Version footer** is available on the main tabs and reads the installed version from the build.
- **Settings layout** was reorganised into clear General, AI, App, Home Widgets and Data Management
  cards, with consistent section descriptions and test tags.

### Fixed

- **The Liquid Glass dock had no real backdrop.** It was previously a near-opaque gradient inside a
  reserved bottom-bar strip. The dock is now an overlay with a real blurred backdrop on API 31+.
- **Release-to-navigate was discarded.** The lens only dismissed on release; it now hit-tests the
  measured tab bounds and navigates to the item under the finger.
- **Dragging back to Home did nothing.** A long-lived pointer block captured the original route
  callback, and lens-scaled bounds made Home overlap Profile. Both causes are fixed.
- **The selected indicator vanished in Settings.** Settings is not a tab route; it now maps to
  Profile for selection only.
- **Empty heat-map cells disappeared** in Dark and AMOLED. Empty fills, borders and legend cells were
  corrected in every theme.
- **Fullscreen top spacing was excessive.** The app reserved the full 128px cutout safe rectangle and
  added 16dp page padding; it now uses the actual cutout path on API 33+ and 8dp page padding.
- **The Customize Home sheet was see-through.** Home text could be read through the sheet; the
  content-heavy surface fixes it without removing the glass styling.
- **Theme contrast issues** on accent-filled buttons, destructive buttons and custom-drawn charts
  were corrected with the new palette roles.

### AI and exercise database

- **No new providers were added in 4.0.0.** The existing six-provider layer remains: Google, OpenAI,
  Anthropic, OpenRouter, xAI and a custom OpenAI-compatible endpoint, with per-provider model
  selection, live model discovery, per-provider API keys, native tool calling with Auto/Native/Text
  fallback, and read-only exercise/routine lookup tools.
- **AI data is now portable.** All conversations and messages are included in backups, as are saved
  Gemini monthly summaries, custom and AI-generated exercises, and AI-created exercise metadata. API
  keys are deliberately excluded.
- **The 1,333-exercise catalog and its attribution are unchanged.** Exercise animations remain
  streamed from the dataset and retain their existing third-party attribution.

### Internal changes

- Room schema moved from v12 to v14 with migrations 12 -> 13 and 13 -> 14.
- New centralized theme token model and palette-driven chart, heat-map, muscle and glass drawing.
- New timezone-stable local date key helpers and schedule materialization.
- New backup package with archive codec, manifest/checksum validation, DTO mapping, schema
  migrations, semantic validation, preference allowlist and replacement/rollback orchestration.
- New `ScheduledWorkoutDay` persistence and schedule-aware session fields.
- Test coverage grew to 672 JVM tests across 78 classes, including theme, navigation, streak,
  fullscreen, Liquid Glass and backup round-trip/replacement/corruption/performance tests.
- Lint remains at 0 errors; the existing warning baseline is unchanged.

### Licensing

- The official `v3.0.0` tag is the licensing boundary: commit
  `784fc4d3553dc286db85e576641e4f067993f577`, version name `3.0.0`, version code `7`.
- Code at or before that boundary remains under CC BY-NC-SA 4.0, preserved verbatim in
  `LICENSES/CC-BY-NC-SA-4.0.txt`.
- New standalone source files authored after 3.0.0 are under PolyForm Noncommercial 1.0.0, with the
  official text in `LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt`.
- Files that existed at 3.0.0 and were modified later remain historical files under CC BY-NC-SA 4.0;
  they were not blanket-relicensed. See `LICENSES/LICENSE-BOUNDARY.md`.

---

## [3.0.0] — 2026-10-31

This is the first release since 2.0.1. It consolidates every change that was developed under the
unpublished 2.1.0 working label; no 2.1.0 or 2.2.0 build was ever released.

### Added

#### Autosave you can see and schedule
- **The autosave indicator is now a small cloud inside the workout-time box, to the left of the
  running timer.** It is the only thing on screen that says the session is being saved: there is no
  separate banner and no "Saved" line taking up a row of the workout.
- When a save lands, the cloud plays a short pop and becomes a check mark, then settles into a
  lighter green until the next save.
- **Settings → Autosave interval** chooses how often the workout in progress is written: 5, 10, 15,
  30 or 60 seconds. The default is 10 seconds. The session is also written the moment the app goes
  to the background, so leaving the app between two autosaves does not lose the last change.

#### A startup update prompt
- **Settings → Check for updates on app start** (on by default under the manual Check for updates
  entry). When IronPulse opens and a newer official release exists, it shows a short prompt with
  that release's version and changes, and a **Download** button that runs the same verified
  download as the manual check.
- **Close** dismisses the prompt for the rest of that launch; it comes back the next time the app
  starts. **Close and do not show again** suppresses it until the app is updated, because the
  suppression is stored against the installed version.
- The prompt only appears once the app is actually usable: it waits for onboarding, a pending
  workout draft and a running workout to be dealt with, so it never covers those screens.
- Toggling the setting back off stops the automatic lookup; the manual entry and About IronPulse
  are unaffected.

#### Reorder and expand on Home
- **Long-press any Home widget and drag it.** Every card can be moved, the order is saved in
  DataStore, and it survives restarts. A widget that did not exist when the order was saved is
  inserted at its default place rather than dropped.
- **The page rearranges live under your finger.** The other widgets slide out of the way as the
  card moves, so the gap it hovers over is exactly the order you will get, and a cyan outline on a
  raised, opaque card makes it obvious which widget is selected.
- **Holding the card near the top or bottom edge scrolls the list.** The closer to the edge, the
  faster it scrolls, so a widget can be carried from the bottom of the dashboard to the top in one
  drag. The card floats above the list, stays fully visible at either edge, and follows the finger
  while the other cards move past it, even after the slot it came from has scrolled off screen.
- **The drop settles instead of snapping.** On release the card glides the last few pixels into the
  gap and the saved order changes once, under the same picture.
- **Last workout expands in place.** Tapping the card opens it without leaving Home: exercise names
  in bold, one bullet per logged set with its exact numbers, and **See full history** for the whole
  session. The arrow rotates smoothly as the panel opens and closes.

#### Customize Home
- **The tune button in Home's top-right corner opens the widget manager.** Every card is listed
  with its name and what it shows, so the dashboard is discoverable rather than a fixed page.
- **Add, remove and reorder widgets.** The manager lists what is on Home and what is available.
  **Up** and **Down** move a card one slot (the accessible alternative to the long-press drag),
  and removing a card only hides it: the workouts, weigh-ins and routines it displays are untouched.
  Adding a widget puts it back at its default place, and adding one twice is a no-op.
- **Two new widgets.** **Motivation Photo** uses Android's photo picker, copies the picture into
  app-private storage and centre-crops it to the card's 16:9 shape, so deleting the original from
  the gallery does not break it. It can be replaced or removed from Home or from the manager, and a
  missing or unreadable copy shows a clear placeholder with **Choose another** instead of a broken
  image. **Weekly Goal** counts this week's finished workouts against a target of 1 to 7, set from
  the card's tune button or the manager.
- **The selection, order, photo and target survive navigation and restarts**, stored together in
  DataStore so the order and the removed set cannot disagree after a crash. A page with every
  widget removed is a valid empty state with a direct **Add widgets** way back.
- **Settings → Home Widgets → Restore Default Home Widgets** brings back the original eight-card
  dashboard. The confirmation says exactly what will be discarded (selection, order, weekly target,
  photo and its app-private copy) and that it cannot be undone; **Cancel** leaves everything as it
  was. Workouts, weigh-ins, routines, the profile, the exercise library and every other setting are
  not touched. **Delete App Data** also clears the widget layout and saved widget photos.

#### Motion
- The bottom navigation is highlighted by one pill that slides between destinations instead of a
  highlight that disappears and reappears. Home's selected state is a circle, the other tabs are a
  pill that fits the label.
- The AI Coach's three top tabs use the same idea: a single underline that slides to the selected
  tab.
- Starting a workout from Home slides and fades the session in over the dashboard, and leaving it
  returns the other way. A retained snapshot feeds the animation, so the workout is not rebuilt
  halfway through it.

#### Your own AI provider
- **Six providers, one AI layer.** Google Gemini, OpenAI, Anthropic, OpenRouter, xAI and a custom
  OpenAI-compatible endpoint now share the same coach, routine generator, parser and monthly
  summary. Switching provider no longer switches to a second implementation.
- **A model selector for every provider.** **Choose model** loads the provider's live catalog,
  shows loading, empty and error states, can refresh, and always accepts a manually typed model id.
  The selected model is stored per provider. Capability badges report whether a model supports
  native tool calling, and **Auto**, **Native only** and **Text fallback** control how tools are
  offered.
- **Custom endpoints are a deliberate choice.** The URL is validated and normalized, plain HTTP is
  allowed only for loopback or a private-network host, and the first use shows a privacy/security
  confirmation naming the exact endpoint. The key is sent only there, redirects are not followed,
  and TLS certificate checks are never disabled. A local endpoint may need no key at all.
- **The AI can look things up instead of guessing.** A read-only tool registry exposes exercise
  search and details, exercise lists, saved routines and routine details. There is no SQL,
  filesystem, network or code-execution tool. Calls are validated, capped and executed
  sequentially, with per-conversation deduplication and a bounded number of rounds.
- **Native tools with a bounded fallback.** OpenAI, Anthropic, Gemini and compatible endpoints use
  their native tool protocol when available; a provider that rejects tools can fall back to a
  constrained text protocol. Malformed calls, multiple calls, provider errors, timeouts, retries and
  cancellation are handled without exposing raw provider payloads.

#### Per-exercise rest defaults in routines
- **Every routine entry can carry its own rest default** from 0 to 600 seconds. It belongs to the
  exercise inside that routine, not to the shared exercise definition, so the same movement can
  rest 45 seconds in one plan and 2 minutes in another. 0 keeps the timer manual, and routines
  saved before this change keep working unchanged.
- When a set is ticked in a routine workout, a non-zero default starts the rest timer automatically.
  Changing the timer by hand never rewrites the routine.

### Changed
- **IronPulse is portrait-only.** The launcher activity declares `android:screenOrientation="portrait"`,
  so rotating the phone no longer turns the dashboard, workout or dialogs sideways. Layouts and
  navigation are unchanged; they simply keep the orientation they were designed for.
- **Body parts and target muscles are one vocabulary, stored and displayed in Title Case.** Body
  parts read `Chest`, `Back`, `Legs`, `Shoulders`, `Arms`, `Core`; targets read `Pectorals`,
  `Deltoids`, `Triceps`, `Abdominals` and the rest of the editor's list. The exercise editor, the
  detail dialog and the AI-created exercises all use those values, so a value can no longer appear
  in one spelling in the dropdown and another in the details.
- The bundled dataset is canonicalised when it is seeded: short dataset words (`abs`, `delts`,
  `quads`, `traps`, `spine`) and lower-case body parts are folded onto the editor vocabulary, for
  the primary target and every synergist. `DATASET_VERSION` moves to 4, which is what makes the
  existing library pick the corrected spelling up on the next open.
- **Instructions are broken into steps.** The detail dialog renders one bullet per sentence or per
  line instead of one paragraph: existing numbered or bulleted lists are preserved, sentences are
  split on their boundaries, and abbreviation dots are not mistaken for the end of one. The
  splitter is shared, so every screen that shows instructions shows the same steps.
- **AI settings follow the selected provider.** The key help, key placeholder and default model are
  provider-aware: Google keeps the AI Studio/phone-verification guide, the other services link to
  their own consoles, and a custom endpoint says the key is optional. User-facing AI copy no longer
  names Gemini for features that work with any provider.
- `versionCode = 7`, `versionName = "3.0.0"`. Room schema v12 with a data migration, no manual step.

### Fixed
- **The 12-month activity heat map uses local calendar days.** The old code stepped between dates
  with a fixed 24-hour millisecond constant. Across a daylight-saving change that lands at 23:00 or
  01:00, so a Monday evening workout could paint Sunday's square and a week could lose or repeat a
  day. The grid now builds local midnights with `Calendar`, so day keys, week columns, month labels
  and totals agree across DST and time zones.
- **Bodyweight no longer reads as `0KG` after updating.** This was the tester's report: every
  custom bodyweight movement showed `0KG` in the workout history, and Progress offered a bodyweight
  tag that showed `0 kg` when it was tapped. Three faults combined:
  - A custom exercise saved as a standard exercise at 0 kg was treated as external weight, so its
    sets rendered as `0 kg` instead of the athlete's body weight. A custom bodyweight exercise whose
    history is all zero-load is now retagged with Bodyweight equipment and handled as added-weight
    work.
  - The bodyweight tag was offered for any zero-load set and labelled with the profile weight, so
    Progress listed a `BW: 80 kg` tag that then showed `0 kg` and drew a phantom point in the chart.
    The tag now only matches sets whose exercise is actually bodyweight work.
  - The profile-weight preference defaulted to 75 kg and could shadow a stored database weight.
    The app now merges both sources and treats 0 as "not set"; a new install no longer starts at a
    made-up 75 kg.
- **An existing upgrade repairs its own history.** On the next open, any logged set still carrying
  `bodyWeightKg = 0` is filled from the weigh-in timeline and then the profile weight, whatever
  schema version the install is on. This fixes installs that had already run the 10 → 11 migration
  before the snapshot rules were complete.
- **Home's selected highlight was weaker than the other tabs.** Home used an 18% cyan wash so its
  icon could stay cyan; against the full-strength indicator on the other destinations it read as
  barely selected. Home now uses the same full `ElectricCyan` fill with a dark icon.
- **The Profile weigh-in history no longer jumps when it opens.** The summary and the full log used
  to be separate list branches: opening the list moved the heading and gave every entry its own
  card, so the gaps changed with the number of entries. The panel now keeps one slot in the page and
  slides open, and every weigh-in shares one card separated by a hairline divider. The heading row
  also keeps its height, so **Hide all** no longer shifts it.
- **Home was the only tab without the build stamp.** `IronPulse 3.0.0` now sits after the last
  widget, so it stays at the bottom of the dashboard and moves with the cards when they are
  reordered.
- **Changing your body weight no longer rewrites old workouts.** This was the serious one: every
  set logged before the snapshot existed carried `bodyWeightKg = 0`, and the UI resolved that
  through the *current* profile weight. A new weigh-in therefore changed the body weight, the
  volume, the estimated one-rep max and the "isolated weight" tag of every earlier workout while
  the newest one looked correct.
  - Finishing either a quick or a routine workout now stamps the final profile weight on **every**
    saved set, so the session is frozen at the weight it was done at.
  - `MIGRATION_10_11` backfills the rows that predate the snapshot: the latest weigh-in at or before
    that workout's end, then the earliest weigh-in, then the profile weight, and finally 75.0 kg.
    It is a one-time best effort for history that never recorded the number; once written, the row
    is stable.
  - Profile edits now affect only new workouts. The body-weight timeline, the volume maths and the
    isolated weight tags of history all read the stored set weight.
- **The first target muscle was lower case in exercise details.** The dataset's `target` values
  (`pectorals`, `deltoids`, …) were outside the dropdown's vocabulary, so they were shown as
  written. Canonicalisation fixes both the stored value and every display of it.
- **Body Part was lower case in exercise details.** Same cause, same fix: the dataset's body part is
  folded onto the Title Case list when seeded and when an exercise is edited or created.
- **The bottom navigation highlight flickered.** It was a different composable per destination, so
  it was removed and inserted on every tap. One indicator now stays on screen and animates its
  position, width and colour.
- **The AI Coach tab underline flickered for the same reason.** It is one indicator now.

### Database
- Room 11 → 12. The migration adds indexes for the hot queries: workout sessions by start time,
  logged sets by session/exercise/timestamp, routines by creation time, weigh-ins by timestamp, and
  chat sessions/messages by their lookup columns. No table or column changes.
- Chat deletion now removes a session and its messages in one transaction, so a process death
  cannot leave orphaned messages behind.
- Room 10 → 11. No table or column changes: the migration freezes the body weight of historical
  sets so the profile weight can never rewrite them.
- The same idempotent repair runs on every open, including installs already on v11 or v12, so a row
  that is still `0` is filled from the weigh-in timeline and the profile weight without waiting for
  another schema version.

---

## [2.0.1] — 2026-10-22

### Added

#### Check for updates
- **Settings now has a "Check for updates" entry.** Tap it and the app looks at the official
  releases of this repository on GitHub, and tells you whether the build you are running is the
  newest one there is.
- When a newer release is published, the dialog says which version it is, and offers to download it.
  The download only starts when you ask for it, and it runs in the background with a progress
  indicator, so nothing ever blocks the screen.
- When the download is finished, the app checks the file and then opens the Android installer for
  it. Android asks you to confirm the installation, and from Android 8 it also asks you to allow
  IronPulse to install packages: the app takes you to that setting and checks again when you come
  back, rather than assuming you said yes.
- The downloaded file is checked before the installer sees it. If GitHub published a checksum for
  the release, the file is compared against it, and the package inside the file is always checked, so
  a file that is not an update of IronPulse is never handed over.
- The downloaded file also has to be signed with the same key IronPulse itself is signed with. The
  app checks that before it opens the installer, so a file Android would refuse is never presented
  as an update in the first place.
- A release that does not carry an installable Android package says so and opens the official
  release page, rather than pretending the update can be installed.

#### About IronPulse
- **Settings has an "About IronPulse" entry** that shows the version you are running, the newest
  published version, when it was published, and what changed in it, taken from the official release
  notes.
- The installed version and the newest release are always labelled separately, so the notes of a
  release you do not have yet are never shown as if you were already running it.
- The notes are read from the same place the updater reads them, and a copy is kept so they are
  still there when there is no network. The copy says that it is a saved one.

### Fixed
- **The update file could not have been opened.** The folder the download is shared from was
  configured with a trailing separator, and the system file provider never matches a root that
  ends in one, so the installer would have been handed a link to nothing.

### Changed
- The release notes are shown as headings, points and bold text rather than as the raw Markdown they
  were written in. Nothing else in a release description is interpreted, so a release can never do
  anything but be read.
- The app now says in one place which domains it talks to. The new network security configuration
  names the repository's own domains, refuses cleartext for them, and leaves the device's own
  trust of certificates as it was.

---

## [2.0.0] — 2026-10-21

### Added

#### Home dashboard
- **Home is the landing screen.** It is the first thing the app shows after the onboarding, the
  first thing it shows on every later launch, and it is a destination of its own in the bottom
  navigation, between Progress and Profile.
- The dashboard answers the questions an athlete has when they open a workout logger: what day it
  is, what is planned for today, whether they can start right now, what they have been doing, when
  they last weighed in, and how their week is going.
  - **Today's workout.** The workout the scheduled routine assigns to today, with a **Start Workout**
    button that opens the real workout session. A rest day says so and offers another workout, a
    routine that was never scheduled asks which one is the week being followed, and a workout that
    is already running offers to resume it rather than starting a second one.
  - **Your week.** All seven days of the scheduled routine, with the date and the weekday under
    each one, what is trained on it, and which days are rest days. Tapping a training day starts it.
  - **Saved Routines** is a shortcut on the dashboard itself, so the list of routines is one tap
    away instead of two.
  - **Last 7 days** as a heat map, with a **Volume / Duration** switch. Both metrics cover the same
    seven dates, and tapping a day says exactly what was done on it.
  - **Training days**, a lifetime count of the days on which a qualifying workout was completed, with
    a dialog that explains exactly what qualifies.
  - **This week**: workouts, time and volume, for the calendar week the athlete is in.
  - **Body weight**: the latest weigh-in, the change since the one before it, and a **Log Weight**
    button that saves through the same weigh-in the Profile page uses.
  - **Last workout** with its time and its set count, and a line for the day.

#### Seven day routines
- A routine is now a **week of seven days**, one slot per weekday, instead of a list of numbered
  "Day 1, Day 2, Day 3" slots.
- **A day with no exercise in it is a rest day.** There is no separate rest flag to keep in step
  with the exercises: an empty day is a rest day, so a day can never claim to be a training day
  while holding nothing.
- The routine editor is a **week planner**: all seven weekdays are shown with how many exercises
  each one holds, and tapping a day opens it. New exercises land on the day that is open.
- The **week can start on Monday or on Sunday**, in Settings. It decides where a week is displayed
  and where a sequence of workouts with no day of its own begins. It never moves a workout that
  already sits on a weekday.

### Changed
- **The AI now plans a week.** The routine prompts ask for the weekday of each day of the week
  rather than a number, and the app applies the scheduling rules itself: a weekday the answer named
  is kept, a workout with no weekday named is laid out from the start of the athlete's week, two
  entries that claim the same weekday are merged into it, and every weekday nothing was assigned to
  becomes a rest day.
- **One definition of volume.** The activity heat map counted only the external load while the
  Progress chart counted the load actually lifted, so two screens could disagree about the same
  week. Both now count the same thing, and the duration of a session comes from one place.
- The routines that were saved before the seven day model existed are **migrated to real weekdays**
  the first time the app opens: the first training day of a split becomes the first day of the
  week, the second the second day, and so on. Everything that says what to train is kept — the
  exercise names and their ids, the order, the sets and reps and loads, the coach notes, the name
  and the description. The migration runs once per routine and is safe to interrupt.

### Fixed
- **An empty weight field made a bodyweight workout count as nothing.** The isolated weight tag
  said "BW: 80 kg" while the volume and the estimated one rep max said zero, and the workout
  history showed no volume at all. The load a set moved is now measured the same way everywhere:
  the external load for a barbell set, the body weight plus the added load for a weighted bodyweight
  set, and the body weight for a plain bodyweight set.
- **The body weight is the one from the time of the workout.** A set carries the body weight it was
  done at, and the volume, the one rep max and the "BW" label all use it. A set logged before the
  snapshot existed falls back to the profile weight, as before.
- **A restored workout kept its body weight.** The auto save blob did not carry it, so a workout
  that was app killed and restored was measured against whatever the athlete weighs today rather
  than the weight of the day they trained.
- **The Progress tab and the workout history described the same set two different ways.** The
  Progress tab showed "+0 kg x 8 reps" for a bodyweight set while the history showed "80 kg BW".
  Both now use the same text.
- **A cardio set no longer counts as the body weight.** A run is not lifted, so it has no load: it
  no longer adds phantom volume and can no longer win an estimated one rep max against a real set.
- **The "BW: 80 kg" tag now filters the session history by the body weight it names.** Picking
  the tag used to show every bodyweight set, including the ones done at a different weight.
- The session volume in the workout history is labelled in kilogram-reps, like every other volume
  in the app.

---

## [1.1.0] — 2026-10-20

### Added

#### The workout in progress is saved while you do it
- The session is written to disk every few seconds, and a small indicator says when. If the app is
  closed by accident, the next launch offers the workout back — every set, every number, the
  exercise count and the elapsed time — to continue or to discard.
- A quick workout can be named by tapping its title, so a session is easy to find in the history.
- A routine exercise can carry a **default added weight**. Tap the exercise name in the routine
  editor and the panel opens; the split starts with that load already filled in.

#### Profile picture
- Tap the avatar on the Profile tab and pick one from your gallery. The copy is kept in the app's own
  storage, so it keeps working even after you delete the original from your gallery.

### Fixed
- **The keyboard came back on its own.** A text field kept its focus after the keyboard was
  dismissed, so the field stayed selected and the keyboard reappeared every time the app was opened
  from the recents, with nothing left to dismiss. The focus is now dropped with the keyboard, and
  a focus restored with the saved state is dropped on the first frame.
- **AI generated exercises were labelled "Created by you"** on the exercise card and in the details
  dialog. An AI registered exercise is custom too, so the AI flag now wins.
- **Logging a new weight rewrote the isolated weight tag.** A body weight set carried no weight of
  its own, so the app read your *current* weight and weighing in today changed what last month's
  pull-ups weighed. Every set now keeps the body weight it was done at.
- **The green prescription tag was too long.** It now shows only `4 sets x 8 reps`, and the AI is
  asked for exactly that.

### Changed
- Saving an exercise now shows a tick that confirms the change landed, and the editor stays open
  until you close it, instead of vanishing the instant you press Save.
- **Lint is a hard gate again.** Four errors that predated CI are fixed, so `lintDebug`
  fails the build on an error rather than only reporting one:
  - The Coil `ImageDecoderDecoder` is now registered only on API 28+, matching what its
    comment always claimed; below 28 the GIF decoder remains the only one.
  - The API 26 `Vibrator.vibrate(effect, attributes)` overload is now explicitly
    suppressed as `NewApi`, because the only callers sit behind an API 26 check that lint
    cannot see through a separate function.
  - The two Media3 `@UnstableApi` members (`controllerAutoShow`,
    `setShutterBackgroundColor`) are opted into in a single isolated helper.

  No user-visible behaviour changed. Roughly forty warnings remain (obsolete dependencies,
  unused resources) and stay non-fatal.

---

## [1.0.1] — 2026-10-11

### Fixed
- **Choosing "lb" during onboarding had no effect.** The unit chips on the "About you" page wrote to
  the live preference key but the screen read a different, profile-only key that nothing writes
  until the form is submitted — so selecting "lb" appeared to do nothing at all. The two keys now
  agree, and the profile edit dialog's own unit selector writes the live key too, so changing the
  unit there no longer appears to be ignored.
- Weights already typed into the onboarding form are converted in place when the unit changes, so
  switching from lb to kg re-reads the number correctly instead of keeping the stale figure.

### Changed
- Entering a unit in the "About you" page now also updates the live setting, so the app never ships
  a profile whose stored unit disagrees with the one the athlete picked.

---

## [1.0.0] — 2026-10-11

First public release.

### Added

#### Exercise library
- 1,333-movement catalog seeded into a local SQLite database, each with equipment, muscle group,
  body part, target muscles and coaching instructions.
- Animation GIFs **streamed and cached on demand** rather than bundled in the APK, with the required
  Gym visual attribution rendered on-screen next to every one.
- Search by name, filter by muscle group, and filter by body part through an interactive anatomy map.
- Two search flags, `user_generated` and `ai_generated`, that list exercises by who created them.

#### Logging
- **Quick workouts** from the Exercises tab with a live session tray, rest timer, and set-level
  editing including renumbering after a deletion.
- **Routine workouts** that follow a saved split day by day.
- Four exercise types, each with its own fields, column headers and Progress description:
  standard weight, added weight, pure bodyweight and cardio. Cardio logs minutes and distance and
  never asks for a barbell weight.
- Every exercise's details are reachable by tapping its name — inside a routine workout and inside a
  quick workout alike.
- The editor is intentionally unavailable during a workout so the fields being logged against cannot
  change mid-session.

#### Your own exercises
- Create exercises from the Exercises tab or from the routine builder when a movement you perform is
  not in the library.
- Attach a photo or video of your own variation, while creating the exercise or at any time
  afterwards. The pick is **copied into the app's own storage**, so it survives restarts and updates
  instead of expiring with the gallery's permission grant.
- Attaching, replacing and removing media is confirmed immediately inside the exercise details.
- Full editor for every exercise, including the shipped library: name, muscle group, type, body part,
  target muscle, secondary muscles, equipment and instructions.
- **"Revert to Original Database"** restores the shipped catalog and lets you keep or discard the
  exercises you and the AI created.

#### Progress
- Volume, estimated one-rep max, max reps, training streak and a 12-month activity heatmap.
- Per-exercise analysis with an interactive graph; tap any point to inspect it.
- Weigh-ins on the Profile tab with a target reference line, feeding every body-weight calculation.
- Graph axis units follow the metric: kilograms for one-rep max and volume, reps for max reps.

#### AI (optional, bring your own Gemini key)
- Paste a workout in any format and get a structured routine back.
- Describe a goal and get a plan built from movements the app actually has.
- Coach chat that answers questions about the session you just logged.
- Automatic creation of missing movements, including target muscles, secondary muscles and short
  coaching-cue instructions.
- The AI is given your three benchmark records **with the load each was done at**, so a progression
  is based on what you actually lifted.
- **Data sharing is off by default**; nothing leaves the device until you enable it.

#### Profile & metrics
- Body-weight timeline, BMI and goal strategy.
- Three read-only benchmark records — push-ups, pull-ups and single-leg squat — scored on the
  strongest set ever logged, where more added weight wins and equal load is decided by reps.
- Records only ever go up: deleting the workout that set one keeps it.

#### Interface
- Material Design 3, high-contrast dark theme built for low-light gyms.
- Edge-to-edge with the system bars hidden.
- Units chosen on first launch are used for every field on that page, and weights already typed are
  converted the moment the unit changes.

### Security
- Gemini API key is entered at runtime and never committed.
- `.env`, `local.properties`, keystores and `*.jks` are gitignored.
- Release builds are signed with a key that is not in the repository.

### Known limitations
- Exercise catalog reseeds run on database open when the bundled dataset version has moved. Edits to
  library rows are remembered and excluded, but reverting is the only way to take them back.
- AI-created exercises created before v1.0.0 may have a blank target muscle; new ones are guaranteed
  one and existing rows can be filled by editing them.

---

## [Unreleased Format]

Entries are grouped under **Added**, **Changed**, **Deprecated**, **Removed**, **Fixed** and
**Security**, each newest first. Versions follow [SemVer](https://semver.org/): bump the patch for a
fix, the minor for a feature, the major for a breaking change.

[Unreleased]: https://github.com/JuggyBob/IronPulse/compare/v4.4.0...HEAD
[4.4.0]: https://github.com/JuggyBob/IronPulse/compare/v4.3.0...v4.4.0
[4.3.0]: https://github.com/JuggyBob/IronPulse/compare/v4.2.0...v4.3.0
[4.2.0]: https://github.com/JuggyBob/IronPulse/compare/v4.1.0...v4.2.0
[4.1.0]: https://github.com/JuggyBob/IronPulse/compare/v4.0.0...v4.1.0
[4.0.0]: https://github.com/JuggyBob/IronPulse/compare/v3.0.0...v4.0.0
[3.0.0]: https://github.com/JuggyBob/IronPulse/compare/v2.0.1...v3.0.0
[2.0.1]: https://github.com/JuggyBob/IronPulse/compare/v2.0.0...v2.0.1
[2.0.0]: https://github.com/JuggyBob/IronPulse/compare/v1.1.1...v2.0.0
[1.1.1]: https://github.com/JuggyBob/IronPulse/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/JuggyBob/IronPulse/compare/v1.0.1...v1.1.0
[1.0.1]: https://github.com/JuggyBob/IronPulse/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/JuggyBob/IronPulse/releases/tag/v1.0.0