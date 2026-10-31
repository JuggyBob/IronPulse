# Changelog

All notable changes to **IronPulse** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

Nothing yet. See [CONTRIBUTING.md](CONTRIBUTING.md) to propose a change.

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

[Unreleased]: https://github.com/JuggyBob/IronPulse/compare/v3.0.0...HEAD
[3.0.0]: https://github.com/JuggyBob/IronPulse/compare/v2.0.1...v3.0.0
[2.0.1]: https://github.com/JuggyBob/IronPulse/compare/v2.0.0...v2.0.1
[2.0.0]: https://github.com/JuggyBob/IronPulse/compare/v1.1.1...v2.0.0
[1.1.1]: https://github.com/JuggyBob/IronPulse/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/JuggyBob/IronPulse/compare/v1.0.1...v1.1.0
[1.0.1]: https://github.com/JuggyBob/IronPulse/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/JuggyBob/IronPulse/releases/tag/v1.0.0