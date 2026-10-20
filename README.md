# IronPulse — Workout Logger

[![License: CC BY-NC-SA 4.0](https://img.shields.io/badge/License-CC%20BY--NC--SA%204.0-lightgrey.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF.svg)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-7.1%2B-3DDC84.svg)](https://developer.android.com)
[![Release](https://img.shields.io/badge/release-v1.1.0-blue.svg)](https://github.com/JuggyBob/IronPulse/releases)

A dark-themed workout logger for strength training and calisthenics, built for the gym:
offline by default, no account, no tracking, and a 1,300+ movement catalog that streams its
animations on demand instead of shipping them in the APK.

<p align="center">
  <img src="docs/screenshots/03-progress-analytics.png" width="30%" alt="Progress analytics" />
  <img src="docs/screenshots/02-exercise-detail.png" width="30%" alt="Exercise detail" />
  <img src="docs/screenshots/06-logging-quick-workout.png" width="30%" alt="Logging a quick workout" />
</p>

### What IronPulse is for

| You want to... | How IronPulse handles it |
|---|---|
| Log a workout fast | Quick workout from the Exercises tab, with rest timer and automatic set numbering |
| Run a routine | Saved routines with any number of training days; pick which day you are on |
| Start somewhere | Three pre-built routines with a full preview before you adopt one |
| See progress | Volume, estimated one-rep max, training streak, weigh-ins plotted over time |
| Check a movement | An animation for nearly every exercise, and full instructions |
| Ask a coach | Optional Gemini chat, routine generator and monthly summary |
| Fix something the app got wrong | A low-level editor for any exercise, plus a revert to the shipped catalog |

---

## What it does

### Exercise library
1,333 movements seeded into a local SQLite database — every exercise with its equipment, muscle
group, body part, target muscles and coaching instructions. The animation GIFs are **streamed and
cached**, not bundled, so the app stays small and an exercise you never open costs nothing.

<p align="center">
  <img src="docs/screenshots/02-exercise-detail.png" width="45%" alt="Exercise detail with animation, instructions and targets" />
</p>

### Logging
- **Quick workouts** — tap *Start*, add exercises, log sets. A live tray keeps the session in view.
- **Routine workouts** — follow a saved split day by day.
- **Every set type is a first-class citizen.** Standard weight, added weight, pure bodyweight and
  cardio each have their own fields, their own column headers and their own Progress-tab
  description. Cardio logs minutes and distance and never asks for a barbell weight.
- Rest timer with vibration and sound, autoplaying or hidden into the border.
- Every exercise is editable — including the shipped library — so a row with wrong fields can be
  fixed instead of being lived with. Library edits ask first and survive catalog updates.
- **The workout is saved while you do it.** Every few seconds the session is written to disk and a
  small indicator says when. If the app is closed by accident, the next launch offers the workout
  back with every set, every number and the elapsed time intact, to continue or to discard.
- **Name your workout** by tapping its title, so a session is easy to find in the history later.
- **Set a default added weight** for an exercise in a split — tap the exercise name in the routine
  editor and the panel opens, out of the way until you ask for it. The split starts with that load
  already filled in.

<p align="center">
  <img src="docs/screenshots/06-logging-quick-workout.png" width="45%" alt="A quick workout in progress" />
</p>

### Your own exercises
- Attach a photo or video of your own variation. It replaces the built-in animation, so you see
  *your* movement rather than someone else's.
- The pick is copied into the app's own storage, so it keeps working after a restart rather than
  expiring with the gallery's permission.
- Saving an exercise says so: a tick draws itself, and the editor stays open until you close it.
- Exercises the AI created are always labelled **AI generated**, never as your own.

<p align="center">
  <img src="docs/screenshots/07-create-custom-exercise.png" width="45%" alt="Create a custom exercise, with the media picker" />
</p>

### Progress
- Volume, estimated one-rep max, max reps, training streak and a 12-month activity heatmap.
- Per-exercise analysis with an interactive graph you can tap to inspect any point.
- Weigh-ins on the Profile tab, plotted over time, feeding every body-weight calculation in the app.
- Units follow your preference everywhere; the storage unit never changes and no history is ever
  rewritten when you switch.
- **A set keeps the body weight it was done at.** Weighing in today does not rewrite what last
  month's pull-ups weighed, so the isolated weight tags and the history stay true.

<p align="center">
  <img src="docs/screenshots/03-progress-analytics.png" width="45%" alt="Analytics and progress" />
</p>

### AI (optional, bring your own key)
The app is fully usable without Gemini. Add a key and you get:
- Paste any workout in any format and get a structured routine back.
- Describe a goal and get a plan built from movements the app actually has.
- A coach chat that answers questions about the session you just logged.
- Automatic creation of missing movements, including target muscles, secondary muscles and
  short coaching-cue instructions.
- **Data sharing is off by default.** No part of your profile is sent with a request until you turn
  it on in Settings, and the setting says plainly what is and is not sent.
- Generated routines are short and readable: every prescription is just `4 sets x 8 reps`, with the
  load left to you.

<p align="center">
  <img src="docs/screenshots/05-ai-coach.png" width="45%" alt="AI coach chat" />
</p>

### Profile & metrics
Body-weight timeline with a target reference line, BMI, goal strategy, and three read-only
benchmark records (push-ups, pull-ups, single-leg squat) with the load each was done at. Those are
handed to the AI so a progression is based on what you actually lifted.

**Profile picture:** tap the avatar and pick one from your gallery. The copy is kept in the app's own
storage, so it keeps working even after you delete the original from your gallery.

<p align="center">
  <img src="docs/screenshots/04-profile-metrics.png" width="45%" alt="Profile and metrics" />
</p>

---

## Privacy

- **No account. No tracking. No analytics.**
- Every workout, weigh-in and routine lives in a SQLite database on your device.
- AI features are optional and require your own Gemini key.
- Athlete data sharing is **off** until you explicitly enable it.
- "Erase all app data" in Settings wipes everything on the device.

---

## Development Methodology & Transparency

- **Maintainer Role:** System architecture, product specifications, domain model design, API
  integrations, prompt engineering, and quality assurance/testing.
- **Code Generation:** Built with AI assistant workflows.

---

## Tech stack

| Component | Technology |
|---|---|
| **Language** | Kotlin (100%) |
| **UI** | Jetpack Compose + Material 3 |
| **Architecture** | MVVM — Compose screens → `MainViewModel` → `FitnessRepository` → Room DAOs |
| **Database** | Room (SQLite), schema v9, versioned migrations |
| **Preferences** | DataStore |
| **Async** | Coroutines + Flow |
| **Images** | Coil (animated GIF + still-frame loaders) |
| **Video** | Media3 / ExoPlayer |
| **AI** | Gemini REST API (`GeminiClient`), bring your own key |
| **Min SDK** | 24 (Android 7.1) · **Target SDK** 35 |

---

## Getting started

### Requirements
- JDK 17 or newer (Android Studio Ladybug / 2024.2+ bundles one)
- Android SDK with platform 35
- A device or emulator on Android 7.1 (API 24) or newer

### Setup
1. **Clone**
   ```bash
   git clone https://github.com/JuggyBob/IronPulse.git
   cd IronPulse
   ```
2. **Point the SDK at your machine.** Android Studio does this for you; otherwise copy
   `local.properties.example` to `local.properties` and set `sdk.dir`.
3. **Build**
   ```bash
   ./gradlew assembleDebug
   ```
   The APK lands in `app/build/outputs/apk/debug/`.
4. **Install on a device**
   ```bash
   ./gradlew installDebug
   ```

### Gemini API key (optional)
The app works fully without one. To enable the AI features, add your key in
**Settings → API Key** at runtime — it is never committed to the repository. For a local
`.env`, copy `.env.example` and fill it in; `.env` is gitignored.

### Running the tests
```bash
./gradlew testDebugUnitTest      # 293 JVM unit tests across 42 classes
./gradlew recordRoborazziDebug   # regenerate reference screenshots
```

---

## Download

Prebuilt APKs are attached to the
[releases page](https://github.com/JuggyBob/IronPulse/releases).

| Version | File |
|---|---|
| **v1.1.0** | [`IronPulse1.1.0-release.apk`](https://github.com/JuggyBob/IronPulse/releases/download/v1.1.0/IronPulse1.1.0-release.apk) |
| v1.0.1 | [`IronPulse-v1.0.1.apk`](https://github.com/JuggyBob/IronPulse/releases/download/v1.0.1/IronPulse-v1.0.1.apk) |
| v1.0.0 | [`IronPulse-v1.0.0.apk`](https://github.com/JuggyBob/IronPulse/releases/download/v1.0.0/IronPulse-v1.0.0.apk) |

Requires Android 7.1 (API 24) or newer.

---

## Project layout

```
IronPulse/
├── app/
│   ├── src/main/
│   │   ├── assets/exercises.json     # 1,333-movement catalog seed
│   │   ├── java/com/example/
│   │   │   ├── data/                 # Room entities, DAOs, repository, seeders
│   │   │   ├── ui/
│   │   │   │   ├── components/       # Dialogs, editors, charts, sheets
│   │   │   │   ├── screens/          # The six tabs + onboarding
│   │   │   │   ├── theme/            # Colours, type, Material 3 theme
│   │   │   │   └── viewmodel/        # MainViewModel — the single source of truth
│   │   │   └── util/                 # Coil loaders, media store, timers
│   │   └── res/                      # Icons, strings, themes, backup rules
│   └── src/test/                     # 275 JVM tests (Robolectric + JUnit)
├── docs/screenshots/                 # Images used by this README
├── gradle/wrapper/                   # Pinned Gradle distribution
├── .github/                          # Issue & PR templates, CI workflows
├── CHANGELOG.md
├── CONTRIBUTING.md
├── SECURITY.md
└── LICENSE                           # CC BY-NC-SA 4.0
```

---

## Credits

- Exercise animations are © [Gym visual](https://gymvisual.com/) and stream from the
  [hasaneyldrm/exercises-dataset](https://github.com/hasaneyldrm/exercises-dataset) repository. The
  required attribution is shown on-screen next to every dataset animation.
- Built with [Jetpack Compose](https://developer.android.com/jetpack/compose) and
  [Material 3](https://m3.material.io/).

---

## Contributing

Bug reports, feature requests and pull requests are all welcome — see
[CONTRIBUTING.md](CONTRIBUTING.md). Security issues go through
[SECURITY.md](SECURITY.md) instead of a public issue.

---

## License

Licensed under the **Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International
Public License (CC BY-NC-SA 4.0)**. See [LICENSE](LICENSE).

**Not an official product of Google, Gym visual, or any exercise dataset author.**