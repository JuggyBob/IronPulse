# Changelog

All notable changes to **IronPulse** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

Nothing yet. See [CONTRIBUTING.md](CONTRIBUTING.md) to propose a change.

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

[Unreleased]: https://github.com/JuggyBob/IronPulse/compare/v2.0.0...HEAD
[2.0.0]: https://github.com/JuggyBob/IronPulse/compare/v1.1.1...v2.0.0
[1.1.1]: https://github.com/JuggyBob/IronPulse/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/JuggyBob/IronPulse/compare/v1.0.1...v1.1.0
[1.0.1]: https://github.com/JuggyBob/IronPulse/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/JuggyBob/IronPulse/releases/tag/v1.0.0