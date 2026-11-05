# Changelog

All notable changes to **IronPulse** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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

[Unreleased]: https://github.com/JuggyBob/IronPulse/compare/v4.2.0...HEAD
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