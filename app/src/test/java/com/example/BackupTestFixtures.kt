/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import com.example.data.backup.ArchivePayload
import com.example.data.backup.BackupArchiveReader
import com.example.data.backup.BackupFileEntry
import com.example.data.backup.BackupFormat
import com.example.data.backup.BackupMapper
import com.example.data.backup.BackupSectionEntry
import com.example.data.backup.BackupManifest
import com.example.data.backup.DataStoreProfileV1
import com.example.data.backup.DashboardSectionV1
import com.example.data.backup.ExerciseV1
import com.example.data.backup.UserDataSnapshot
import com.example.data.backup.WidgetPhotoData
import com.example.data.backup.AiPreferencesV1
import com.example.data.backup.AiSectionV1
import com.example.data.backup.BenchmarkPreferencesV1
import com.example.data.backup.BodyWeightLogV1
import com.example.data.backup.ChatMessageV1
import com.example.data.backup.ChatSessionV1
import com.example.data.backup.GeminiSummaryV1
import com.example.data.backup.LoggedSetV1
import com.example.data.backup.PreferencesSectionV1
import com.example.data.backup.ProfileSectionV1
import com.example.data.backup.ProgressSectionV1
import com.example.data.backup.RestTimerV1
import com.example.data.backup.RoomProfileV1
import com.example.data.backup.RoutinesSectionV1
import com.example.data.backup.SavedRoutineV1
import com.example.data.backup.ScheduledWorkoutDayV1
import com.example.data.backup.WorkoutSessionV1
import com.example.data.backup.WorkoutsSectionV1
import com.example.data.backup.ExerciseSectionV1
import java.io.File

/** Deterministic snapshots for archive, migration, integration and performance tests. */
object BackupTestFixtures {

    const val EXPORT_EPOCH = 1_760_000_000_000L

    fun sampleSnapshot(withPhoto: Boolean = true): UserDataSnapshot {
        val photoBytes = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
            0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01
        )
        return UserDataSnapshot(
            dataStoreProfile = DataStoreProfileV1(
                isSaved = true,
                name = "Test Athlete",
                heightCm = 182.4,
                currentWeightKg = 81.25,
                targetWeightKg = 84.0,
                age = 31,
                preferredUnit = "kg",
                fitnessLevel = "Advanced"
            ),
            roomProfile = RoomProfileV1(
                id = 1,
                name = "Test Athlete",
                heightCm = 182.4,
                currentWeightKg = 81.25,
                targetWeightKg = 84.0,
                age = 31,
                preferredUnit = "kg",
                fitnessLevel = "Advanced"
            ),
            roomCustomApiKey = "destination-room-key",
            preferences = PreferencesSectionV1(
                unit = "kg",
                restTimerVibrationEnabled = true,
                restTimerSoundEnabled = false,
                shareAthleteDataWithAi = true,
                autoplayExerciseDemos = false,
                liquidGlassEnabled = true,
                fullscreenEnabled = false,
                themeMode = "amoled",
                onboardingCompleted = true,
                activeWorkoutDraft = """{"kind":"quick","title":"Draft","startTime":123}""",
                weekStartsOnMonday = false,
                autoSaveIntervalSeconds = 15,
                autoCheckUpdatesOnStart = false,
                activeRoutineId = 20L,
                restTimers = listOf(
                    RestTimerV1("timer-1", "Warm up", 45),
                    RestTimerV1("timer-2", "Strength", 180)
                ),
                benchmark = BenchmarkPreferencesV1(
                    pushUps = 40,
                    pushUpsAddedKg = 5,
                    pullUps = 15,
                    pullUpsAddedKg = 10,
                    bulgarianSplitSquat = 25,
                    bulgarianSplitSquatAddedKg = 20
                ),
                ai = AiPreferencesV1(
                    activeProvider = "openai",
                    modelsByProvider = mapOf("google" to "gemini-2.5-pro", "openai" to "gpt-test"),
                    toolModesByProvider = mapOf("openai" to "NATIVE"),
                    customBaseUrl = "https://example.invalid/v1",
                    customAcknowledgedEndpoint = "https://example.invalid/v1"
                )
            ),
            dashboard = DashboardSectionV1(
                widgetOrder = listOf("today", "streak", "photo"),
                hiddenWidgetIds = listOf("week", "recent"),
                weeklyGoal = 5,
                photoPresent = withPhoto,
                photoArchivePath = if (withPhoto) "files/widget_photo.jpg" else null,
                photoMediaType = if (withPhoto) "image/jpeg" else null
            ),
            editedLibraryExerciseIds = setOf(9001L),
            exercises = listOf(
                ExerciseV1(
                    id = 9001L,
                    name = "Edited Library Press",
                    category = "Chest",
                    bodyPart = "Chest",
                    target = "Pectorals",
                    secondaryMuscles = "[\"Triceps\"]",
                    exerciseType = "STANDARD_WEIGHT",
                    instructions = "Edited by the athlete.",
                    description = "Edited library row",
                    equipment = "Barbell",
                    mediaPath = "videos/9001.gif",
                    sourceId = "9001",
                    isCustom = false,
                    isAiGenerated = false
                ),
                ExerciseV1(
                    id = 1001L,
                    name = "Custom Cable Row",
                    category = "Back",
                    bodyPart = "Back",
                    target = "Lats",
                    secondaryMuscles = "[\"Biceps\"]",
                    exerciseType = "STANDARD_WEIGHT",
                    instructions = "Pull to the sternum.",
                    description = "Athlete-created",
                    equipment = "Cable",
                    mediaPath = "",
                    sourceId = "",
                    isCustom = true,
                    isAiGenerated = false
                ),
                ExerciseV1(
                    id = 1002L,
                    name = "AI Sled Push",
                    category = "Legs",
                    bodyPart = "Legs",
                    target = "Quadriceps",
                    secondaryMuscles = "[]",
                    exerciseType = "CARDIO",
                    instructions = "Push hard.",
                    description = "Registered by the AI parser",
                    equipment = "Sled",
                    mediaPath = "",
                    sourceId = "",
                    isCustom = true,
                    isAiGenerated = true
                )
            ),
            sessions = listOf(
                WorkoutSessionV1(
                    id = 10L,
                    title = "Upper A",
                    startTime = 1_759_000_000_000L,
                    endTime = 1_759_003_600_000L,
                    notes = "Felt strong",
                    routineId = 20L,
                    routineWeekday = 1,
                    plannedDate = 1_758_988_800_000L,
                    plannedDateKey = 20250930L
                ),
                WorkoutSessionV1(
                    id = 11L,
                    title = "Lower B",
                    startTime = 1_759_300_000_000L,
                    endTime = 1_759_303_600_000L,
                    notes = "",
                    routineId = 0L,
                    routineWeekday = 0,
                    plannedDate = 0L,
                    plannedDateKey = 0L
                )
            ),
            sets = listOf(
                LoggedSetV1(100L, 10L, 9001L, "Edited Library Press", "STANDARD_WEIGHT", 1, 82.5, 0.0, 5, 0, 0.0, true, 1_759_000_100_000L, 81.25),
                LoggedSetV1(101L, 10L, 1001L, "Custom Cable Row", "STANDARD_WEIGHT", 2, 60.0, 0.0, 8, 0, 0.0, true, 1_759_000_200_000L, 81.25),
                LoggedSetV1(102L, 11L, 1002L, "AI Sled Push", "CARDIO", 1, 0.0, 0.0, 0, 0, 1.25, true, 1_759_300_100_000L, 81.25),
                LoggedSetV1(103L, 11L, 1001L, "Custom Cable Row", "STANDARD_WEIGHT", 1, 57.5, 2.5, 10, 0, 0.0, false, 1_759_300_200_000L, 81.25)
            ),
            routines = listOf(
                SavedRoutineV1(
                    id = 20L,
                    name = "Four Day Split",
                    description = "Upper/lower",
                    daysPerWeek = 4,
                    goal = "Hypertrophy & Strength",
                    exercisesJson = """[{"exerciseId":9001,"exerciseName":"Edited Library Press","weekday":1,"targetSets":4,"targetReps":6}]""",
                    createdAt = 1_758_000_000_000L,
                    scheduleVersion = 1
                )
            ),
            scheduleDays = listOf(
                ScheduledWorkoutDayV1(1_758_988_800_000L, 20250930L, 20L, 2, "Upper A", 4, 1_758_900_000_000L),
                ScheduledWorkoutDayV1(1_758_988_800_000L + 86_400_000L, 20251001L, 20L, 3, "Rest", 0, 1_758_900_000_000L)
            ),
            bodyWeightLogs = listOf(
                BodyWeightLogV1(30L, 81.25, 1_759_000_000_000L, "Morning"),
                BodyWeightLogV1(31L, 80.95, 1_759_500_000_000L, "")
            ),
            chatSessions = listOf(
                ChatSessionV1("chat-1", "Pressing plan", 1_759_100_000_000L, 1_759_200_000_000L),
                ChatSessionV1("chat-2", "Deload", 1_759_300_000_000L, 1_759_400_000_000L)
            ),
            chatMessages = listOf(
                ChatMessageV1("msg-1", "chat-1", "USER", "How should I press?", 1_759_100_100_000L),
                ChatMessageV1("msg-2", "chat-1", "AI", "Start with 4x6.", 1_759_100_200_000L),
                ChatMessageV1("msg-3", "chat-2", "USER", "Deload week?", 1_759_300_100_000L)
            ),
            geminiSummaries = listOf(
                GeminiSummaryV1(60L, "Edited Library Press", "# 30 Day Summary\nStrong.", 30, 4, 12, 102.5, 3.75, 1_759_600_000_000L)
            ),
            widgetPhoto = if (withPhoto) WidgetPhotoData(photoBytes, "image/jpeg", "jpg") else null,
            widgetPhotoPath = null
        )
    }

    fun writeArchive(
        snapshot: UserDataSnapshot,
        file: File,
        appVersionName: String = "9.9.9",
        appVersionCode: Int = 99,
        exportedAtEpoch: Long = EXPORT_EPOCH
    ) {
        val sections = BackupMapper.toSections(snapshot)
        val counts = snapshot.sectionCounts
        val manifestSections = LinkedHashMap<String, BackupSectionEntry>()
        for ((name, path) in BackupFormat.REQUIRED_SECTIONS) {
            manifestSections[name] = BackupSectionEntry(path, true, counts[name] ?: 0)
        }
        val photo = snapshot.widgetPhoto
        val path = photo?.let { "${BackupFormat.FILE_WIDGET_PHOTO}.${it.extension}" }
        val files = if (photo != null && path != null) {
            mapOf(path to photo.bytes)
        } else {
            emptyMap()
        }
        val fileEntries = if (photo != null && path != null) {
            listOf(
                BackupFileEntry(
                    path = path,
                    role = BackupFormat.ROLE_WIDGET_PHOTO,
                    mediaType = photo.mediaType,
                    size = photo.bytes.size.toLong(),
                    sha256 = BackupArchiveReader.sha256(photo.bytes)
                )
            )
        } else {
            emptyList()
        }
        val manifest = BackupManifest(
            appVersionName = appVersionName,
            appVersionCode = appVersionCode,
            exportedAt = com.example.data.backup.UserDataBackupManager.isoUtc(exportedAtEpoch),
            exportedAtEpochMillis = exportedAtEpoch,
            sections = manifestSections,
            files = fileEntries
        )
        BackupArchiveReader.writeToFile(file, ArchivePayload(manifest, sections, files))
    }
}
