# Changelog

All notable changes to **IronPulse** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

Nothing yet. See [CONTRIBUTING.md](CONTRIBUTING.md) to propose a change.

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

[Unreleased]: https://github.com/JuggyBob/IronPulse/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/JuggyBob/IronPulse/compare/v1.0.1...v1.1.0
[1.0.1]: https://github.com/JuggyBob/IronPulse/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/JuggyBob/IronPulse/releases/tag/v1.0.0