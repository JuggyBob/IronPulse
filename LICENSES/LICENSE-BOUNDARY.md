# Licensing boundary

This document records the evidence used for the IronPulse license transition and the exact scope
that was applied. It is an engineering record, not legal advice.

## 1. The boundary that was requested

Code at or before IronPulse **3.0.0** must remain under its previous license. Code newly authored
after that boundary is intended to use the **PolyForm Noncommercial License 1.0.0**. Historical code
must not be retroactively relicensed, and files containing both old and new code must not receive a
blanket PolyForm header.

## 2. Verified evidence for the boundary

The official release repository history and the published GitHub release establish the boundary:

- Official tag: `v3.0.0`
- Tag/source commit: `784fc4d3553dc286db85e576641e4f067993f577` (`Release IronPulse 3.0.0`)
- Published: 2026-10-31
- `app/build.gradle.kts` at that commit: `versionCode = 7`, `versionName = "3.0.0"`
- Published APK asset: `IronPulse3.0.0-release.apk`
- Independent APK metadata (`aapt dump badging`): package
  `com.aistudio.fitness.ironpulse`, `versionCode='7'`, `versionName='3.0.0'`
- Previous release notes also state `versionCode = 7`, `versionName = "3.0.0"`

The 4.0.0 development work was synchronized into this release repository for this release. The
4.0.0 build uses `versionCode = 8` (exactly 7 + 1) and `versionName = "4.0.0"`.

The previous license was the only repository license at `v3.0.0`: the CC BY-NC-SA 4.0 notice
preserved below.

### Result

The boundary is verified. The scope applied is:

1. Every file present in the `v3.0.0` tag remains under its historical license.
2. New standalone source files created after `v3.0.0` carry the PolyForm identification.
3. Modifications made inside pre-existing files remain part of those historical files under
   CC BY-NC-SA 4.0, including its ShareAlike requirement. They receive no PolyForm header.
4. Third-party material keeps its own terms.

## 3. Previous license

The previous license was:

**Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International Public License
(CC BY-NC-SA 4.0)**

- Full preserved text: `LICENSES/CC-BY-NC-SA-4.0.txt`
- That file is a byte-for-byte copy of the pre-transition root `LICENSE`.
- SHA-256 of the preserved file: `FA85B61281939B794CFE70E6C71EB5C18495E592994F43C1A7211CB6310E52AE`
- Canonical license: <https://creativecommons.org/licenses/by-nc-sa/4.0/legalcode>

The historical terms were not edited. The root `LICENSE` is now an index, not a replacement of the
terms. The complete historical text remains available at the path above.

The CC BY-NC-SA 4.0 ShareAlike requirement is not overridden by PolyForm. A modified historical file
is distributed under the historical license.

## 4. New license

The new license is the official:

**PolyForm Noncommercial License 1.0.0**

- Full preserved text: `LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt`
- Canonical license: <https://polyformproject.org/licenses/noncommercial/1.0.0>

The text was obtained from the canonical PolyForm plain-text URL and is preserved without rewriting
or simplification.

No `Required Notice:` line was added. The repository does not establish a verified copyright-holder
designation that could be used for one, and inventing one would be incorrect.

## 5. New standalone files covered by PolyForm

The following source and test files did not exist at `v3.0.0`. Each carries the PolyForm header:

Liquid Glass and streak work:

- `app/src/main/java/com/example/ui/components/LiquidGlass.kt`
- `app/src/main/java/com/example/ui/components/WorkoutStreakCard.kt`
- `app/src/main/java/com/example/data/model/WorkoutStreak.kt`
- `app/src/main/java/com/example/data/model/LocalDateKey.kt`
- `app/src/test/java/com/example/FullscreenPreferencesTest.kt`
- `app/src/test/java/com/example/LiquidGlassLensTest.kt`
- `app/src/test/java/com/example/LiquidGlassPreferencesTest.kt`
- `app/src/test/java/com/example/NavigationSelectionTest.kt`
- `app/src/test/java/com/example/RoutineRestFormatTest.kt`
- `app/src/test/java/com/example/ThemePreferencesTest.kt`
- `app/src/test/java/com/example/WorkoutStreakTest.kt`

User-data export/import work:

- `app/src/main/java/com/example/data/backup/BackupSchema.kt`
- `app/src/main/java/com/example/data/backup/JsonNode.kt`
- `app/src/main/java/com/example/data/backup/BackupSchemaMigrations.kt`
- `app/src/main/java/com/example/data/backup/BackupJson.kt`
- `app/src/main/java/com/example/data/backup/BackupArchiveCodec.kt`
- `app/src/main/java/com/example/data/backup/BackupMapper.kt`
- `app/src/main/java/com/example/data/backup/BackupValidator.kt`
- `app/src/main/java/com/example/data/backup/BackupPreferencesStore.kt`
- `app/src/main/java/com/example/data/backup/UserDataBackupManager.kt`
- `app/src/test/java/com/example/BackupTestFixtures.kt`
- `app/src/test/java/com/example/BackupRoundTripTest.kt`
- `app/src/test/java/com/example/BackupArchiveCodecTest.kt`
- `app/src/test/java/com/example/BackupSchemaMigrationTest.kt`
- `app/src/test/java/com/example/BackupValidationTest.kt`
- `app/src/test/java/com/example/BackupV1FixtureTest.kt`
- `app/src/test/java/com/example/BackupPerformanceTest.kt`
- `app/src/test/java/com/example/UserDataBackupIntegrationTest.kt`

The binary compatibility fixture
`app/src/test/resources/backups/v1-minimal.ironpulse` is generated test data from the same work.

New documentation added after `v3.0.0` (not software) includes the theme, streak, bug-fix, backup and
inventory reports under `docs/`, and the `LICENSES/` texts and index added for this transition.

## 6. Pre-existing files modified after the boundary

These files existed at `v3.0.0`. They keep their historical license and receive no PolyForm header.
The post-boundary changes are part of each historical file under CC BY-NC-SA 4.0.

Representative list (not exhaustive):

- `app/src/main/java/com/example/MainActivity.kt`
- `app/src/main/java/com/example/ui/theme/Color.kt`
- `app/src/main/java/com/example/ui/theme/Theme.kt`
- `app/src/main/java/com/example/data/preferences/UserPreferencesRepository.kt`
- `app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt`
- `app/src/main/java/com/example/ui/screens/SettingsScreen.kt`
- `app/src/main/java/com/example/ui/screens/HomeScreen.kt`
- `app/src/main/java/com/example/ui/screens/ProfileScreen.kt`
- `app/src/main/java/com/example/ui/screens/ProgressScreen.kt`
- `app/src/main/java/com/example/ui/screens/CreateRoutineScreen.kt`
- `app/src/main/java/com/example/ui/screens/AiCoachScreen.kt`
- `app/src/main/java/com/example/ui/screens/LogWorkoutScreen.kt`
- `app/src/main/java/com/example/ui/screens/RoutineWorkoutScreen.kt`
- `app/src/main/java/com/example/ui/components/HomeComponents.kt`
- `app/src/main/java/com/example/ui/components/HomeWidgetCards.kt`
- `app/src/main/java/com/example/ui/components/HomeWidgetManagerSheet.kt`
- `app/src/main/java/com/example/ui/components/ActivityHeatmapCard.kt`
- `app/src/main/java/com/example/ui/components/RoutineComponents.kt`
- `app/src/main/java/com/example/ui/components/RestTimerComponents.kt`
- `app/src/main/java/com/example/data/dao/Daos.kt`
- `app/src/main/java/com/example/data/db/AppDatabase.kt`
- `app/src/main/java/com/example/data/model/Entities.kt`
- `app/src/main/java/com/example/data/model/RoutineModels.kt`
- `app/src/main/java/com/example/data/model/WorkoutDraft.kt`
- `app/src/main/java/com/example/data/repository/FitnessRepository.kt`
- `app/src/main/java/com/example/data/seed/DatasetSeeder.kt`
- `app/src/main/java/com/example/util/WidgetPhotoStore.kt`
- `app/build.gradle.kts`
- `README.md`, `CHANGELOG.md`, and the root `LICENSE` index

No historical file was given a blanket "PolyForm" header.

## 7. Third-party components

Third-party dependencies, the bundled exercise dataset, and streamed exercise animations retain their
own licenses and attribution requirements. They are not relicensed by this transition. See the
existing in-app attribution and README credits.

## 8. Rules for future contributors

1. Do not remove or edit the preserved historical license text.
2. Do not move historical code under PolyForm merely because a file was touched recently.
3. A completely new standalone file may carry the PolyForm header. Use the exact wording already in
   the files listed in section 5.
4. A change inside a historical file stays under the historical license. Do not add a PolyForm
   header to that file.
5. The verified boundary is tag `v3.0.0` at commit `784fc4d3553dc286db85e576641e4f067993f577`.
   Do not move files that existed at that commit under PolyForm.
6. Do not add a `Required Notice:` line until a verified copyright-holder designation exists.
7. Keep `LICENSES/CC-BY-NC-SA-4.0.txt`, `LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt`, this document,
   and the root `LICENSE` index consistent.
