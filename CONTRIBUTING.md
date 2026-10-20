# Contributing to IronPulse

Thanks for taking the time. This document covers how to report a bug, suggest a feature, and submit
a pull request.

**By participating you agree to the [CC BY-NC-SA 4.0 licence](LICENSE) covering your contribution.**

> **Security issues do not go here.** Report them privately through [SECURITY.md](SECURITY.md).

---

## Table of contents

- [Reporting a bug](#reporting-a-bug)
- [Suggesting a feature](#suggesting-a-feature)
- [Setting up](#setting-up)
- [Coding standards](#coding-standards)
- [Branch naming](#branch-naming)
- [Commit messages](#commit-messages)
- [Submitting a pull request](#submitting-a-pull-request)
- [What CI checks](#what-ci-checks)
- [Review process](#review-process)
- [Adding tests](#adding-tests)
- [Reporting a security issue](#reporting-a-security-issue)
- [Releasing](#releasing)

---

## Reporting a bug

Open an issue and pick the **Bug report** template. Please include:

- **What you did** — the exact steps, not a summary.
- **What you expected** to happen.
- **What actually happened**, including the exact wording of any error.
- **Device and Android version**, and the build from the footer of any screen.
- **Screenshots or a screen recording** if it is a visual problem.
- **Whether it is a regression** — did it work in an earlier release? Which one?

### A good bug report is reproducible

Before filing, please check that the problem still happens after a fresh install. A bug that
disappears on a clean install is usually a stale database, and that is useful information but not a
bug.

If you suspect stale local data is involved, try **Settings → Erase all app data** and retry. Do
**not** report it as a bug if erasing the data fixes it.

---

## Suggesting a feature

Open an issue and pick the **Feature request** template.

Describe **the problem first, then the solution.** A request that starts with the solution is much
harder to evaluate, because the same problem usually has several valid answers. "Logging a set takes
me four taps when I am between sets" is a better report than "add a quick-log button".

Please also check the [open issues](https://github.com/JuggyBob/IronPulse/issues) first — your idea
may already be there, or already have an answer explaining why it is hard.

---

## Setting up

### Prerequisites
- JDK 17+ (Android Studio Ladybug / 2024.2+ bundles one)
- Android SDK, platform 35

### Steps
```bash
git clone https://github.com/JuggyBob/IronPulse.git
cd IronPulse
cp local.properties.example local.properties   # set sdk.dir
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Open the project in Android Studio and let Gradle sync, or work entirely from the command line.

### Project conventions you need to know

These are load-bearing. Breaking them causes real bugs:

- **Kilograms are the storage unit, always.** Convert for display, never for storage. It is what
  makes switching units safe: no history is ever rewritten.
- **All DAOs are called from `FitnessRepository` and nowhere else.**
- **A library row the athlete edited is remembered** in `LibraryEditTracker` and excluded from the
  dataset reseed. Do not clear that set outside an explicit revert.
- **Exercise animations are streamable URLs**, never bundled assets.
- **A gallery pick must be copied into app storage**, never persisted as the `content://` uri the
  picker returned. That grant is revoked as soon as the picker closes. This covers the profile
  picture as well as the exercise media, and both go through `MediaStore`.
- **A set keeps the body weight it was done at.** `LoggedSet.bodyWeightKg` is a snapshot taken when
  the set is created. Never read the athlete's current weight for a historical set, or a weigh-in
  today rewrites the history of everything logged before it.

---

## Coding standards

### Language and style
- **Kotlin**, 4-space indent, no tabs.
- Follow **Kotlin coding conventions**: `lowerCamelCase` for values, `UpperCamelCase` for types and
  composables, `UPPER_SNAKE` for `const val`.
- **Compose functions are PascalCase** and take a `modifier` parameter, applied to the root element
  as the first optional parameter.
- Trailing commas at the end of multi-line argument and parameter lists.

### Comments
The house style, and it matters: **comment the decision, not the syntax.**

```kotlin
// Good — explains why this is the way it is.
val candidates = exercises.filter { it.id !in editedIds }

// Bad — says what the line already says.
val candidates = exercises.filter { it.id !in editedIds }  // filter out edited ones
```

Every non-obvious workaround should say **what was broken, and why this fixes it**. A future
contributor who cannot tell why a line exists will eventually delete it and reintroduce the bug.

### Things to avoid
- **No business logic in Composables.** Move it to the ViewModel.
- **No direct DAOs from a screen.**
- **No hardcoded colours or dimensions.** Use the theme in `ui/theme/` and `MaterialTheme`.
- **No blocking I/O on the main thread.** Use coroutines and `Dispatchers.IO`.
- **No new binary assets.** The APK stays small; stream what you can.

---

## Branch naming

Branch from `main` and name your branch:

| Prefix | Use it for | Example |
|---|---|---|
| `feat/` | A new feature | `feat/hold-timer-per-exercise` |
| `fix/` | A bug fix | `fix/lb-chip-in-onboarding` |
| `docs/` | Documentation only | `docs/add-contributing-guide` |
| `test/` | Tests only | `test/cover-routine-day-clamp` |
| `refactor/` | No behaviour change | `refactor/split-workout-tray` |
| `chore/` | Build, deps, tooling | `chore/bump-agle-to-9.3` |
| `hotfix/` | Urgent fix to released code | `hotfix/null-npe-on-revert` |

Use kebab-case. One logical change per branch — a PR that fixes a bug and reformats a file is hard to
review and hard to revert.

---

## Commit messages

[Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<optional scope>): <subject>

<optional body>

<optional footer>
```

**Types:** `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`

```
fix(onboarding): read the unit from the live preference key

weightUnit was derived from userProfile, which emits nothing until a profile
row exists. During onboarding there is no row, so the kg/lb chips never moved.

Also writes the live key from updateProfile, because the profile edit dialog
has its own unit selector and would otherwise appear to do nothing.

Closes #42
```

Rules: subject in the imperative mood, under 72 characters, no trailing full stop. Explain **why** in
the body — the diff already shows what changed.

---

## Submitting a pull request

1. **Sync first**
   ```bash
   git checkout main
   git pull --rebase
   git checkout -b fix/short-description
   ```
2. **Make your change**, keeping commits small and focused.
3. **Run the checks locally**
   ```bash
   ./gradlew testDebugUnitTest assembleDebug
   ```
4. **Update the docs.** A behaviour change means `README.md` and `CHANGELOG.md` change with it.
5. **Push and open a PR** against `main` using the template.
6. **Wait for CI.** All required checks must pass before a review is requested.
7. **Respond to review.** Push follow-up commits rather than force-pushing, so reviewers can read
   what changed.

### What CI checks

| Check | What it does |
|---|---|
| **Build** | Compiles debug and release variants |
| **Unit tests** | Runs all 293 JVM tests; fails the PR on any failure |
| **Lint** | Android Lint; fails on errors, warns are reported |
| **Secret scan** | Fails if a key, token or `.env` file was committed |
| **APK size** | Reports the debug and release APK sizes as a build artifact |

---

## Review process

`main` is protected. Concretely:

| Rule | Why |
|---|---|
| **Build, Unit tests, Lint and Secret scan must all pass** | The real gate. Nothing merges without them. |
| **Checks must be up to date with the branch** | A green run from an earlier commit does not count. |
| **Force pushes and branch deletion are blocked** | `main` is the release branch. |
| **Every review conversation must be resolved** | No merging with an unanswered question. |
| **A pull request is required** | Even for the maintainer. |

**An approving review is not required**, because this project has exactly one maintainer and GitHub
does not let you approve your own pull request. Requiring one would make the repository permanently
unmergeable rather than stricter. If outside contributions pick up, turn it back on in
**Settings → Branches → Branch protection rules**.

The trade-off is honest: for the maintainer's own changes, CI is the only external check. For
everyone else's, CI plus a maintainer read before merge is the norm here.

A maintainer squash-merges, so write commit messages for the person reading the log later.
**Disagreeing with a review is welcome** — say why you think something is better; a review is a
conversation, not an order. Reviews may take a few days: this is a small project maintained in
someone's spare time.

---

## Adding tests

All tests live in `app/src/test/java/com/example/` and run on the JVM with Robolectric. They do not
need a device or an emulator.

```bash
./gradlew testDebugUnitTest                                   # everything
./gradlew testDebugUnitTest --tests "com.example.CardioExerciseTest"   # one class
```

**Add a test for every bug you fix.** A test named after the bug it prevents is the clearest
possible regression guard:

```kotlin
@Test
fun `edited library row is never re-flagged as ai generated`() {
    // rename a library exercise, then run the backfill
    // assert it is still a library row
}
```

Screenshot tests use Roborazzi:

```bash
./gradlew recordRoborazziDebug --tests "com.example.<TestClass>"
```

---

## Code of conduct

Be decent to each other. Assume good faith, critique the code rather than the person, and accept that
you may be wrong. Report unacceptable behaviour to the maintainer privately.

---

## Reporting a security issue

**Do not open a public issue.** Follow [SECURITY.md](SECURITY.md).

---

## Releasing

Releases are published **manually**, not by CI. This is deliberate.

A release build needs the upload keystore (``my-upload-key.jks``), which lives on the maintainer's
machine and is never committed -- that is the whole point of it. A workflow cannot sign an APK with a
key it does not have, so an automated release job can only ever fail on that step. Rather than leave a
workflow that reddens on every tag, the release is built and attached by hand.

To cut a release:

1. **Bump the version** in ``app/build.gradle.kts`` (``versionCode``, ``versionName``) on its own
   branch and merge it.
2. **Tag it.** The tag drives the version:

   ```bash
   git tag -a v1.0.2 -m "IronPulse v1.0.2"
   git push origin v1.0.2
   ```

3. **Build the release APK** locally with the upload key and record its checksum.
4. **Attach both to the release**, writing the notes from ``CHANGELOG.md``:

   ```bash
   gh release create v1.0.2 IronPulse-v1.0.2.apk IronPulse-v1.0.2.apk.sha256 \
     --title "IronPulse v1.0.2" \
     --notes-file CHANGELOG.md \
     --verify-tag
   ```

5. **Update the README** download table and the version badge in the same branch as the bump.

``*.apk`` files are gitignored, so the release artefact is attached to the release and never checked
into the repository.

CI still guards the code: ``Build``, ``Unit tests``, ``Lint`` and ``Secret scan`` run on every push
and pull request, so what goes into a release is what passed them.

Supported versions are listed in [SECURITY.md](SECURITY.md). A security release follows the same
manual process; only the newest release is supported, and older ones stay downloadable for anyone who
needs them.
