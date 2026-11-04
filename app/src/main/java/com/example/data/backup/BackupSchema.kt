/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.data.backup

import com.squareup.moshi.JsonClass

/**
 * Frozen wire format for IronPulse user-data backups.
 *
 * The DTOs in this file are the on-disk schema, not the Room schema. They must never be renamed or
 * repurposed. A future change creates a new versioned DTO and a migration in [BackupSchemaMigrations];
 * it does not edit these classes.
 */
object BackupFormat {
    const val MAGIC = "ironpulse-backup"
    const val EXTENSION = "ironpulse"
    /**
     * The archive is a ZIP, but the create-document picker is given the neutral binary type so
     * Android document providers do not append their own `.zip` to the athlete's `.ironpulse`
     * filename. Import accepts ZIP and binary types as well.
     */
    const val MIME_TYPE = "application/octet-stream"

    const val MANIFEST_PATH = "manifest.json"
    const val CHECKSUMS_PATH = "checksums.json"

    const val CURRENT_FORMAT_VERSION = 1
    const val MIN_SUPPORTED_FORMAT_VERSION = 1
    const val CURRENT_DATA_SCHEMA_VERSION = 1
    const val MIN_SUPPORTED_DATA_SCHEMA_VERSION = 1

    const val SECTION_PROFILE = "profile"
    const val SECTION_PREFERENCES = "preferences"
    const val SECTION_DASHBOARD = "dashboard"
    const val SECTION_EXERCISES = "exercises"
    const val SECTION_WORKOUTS = "workouts"
    const val SECTION_ROUTINES = "routines"
    const val SECTION_PROGRESS = "progress"
    const val SECTION_AI = "ai"

    val REQUIRED_SECTIONS = linkedMapOf(
        SECTION_PROFILE to "payload/profile.json",
        SECTION_PREFERENCES to "payload/preferences.json",
        SECTION_DASHBOARD to "payload/dashboard.json",
        SECTION_EXERCISES to "payload/exercises.json",
        SECTION_WORKOUTS to "payload/workouts.json",
        SECTION_ROUTINES to "payload/routines.json",
        SECTION_PROGRESS to "payload/progress.json",
        SECTION_AI to "payload/ai.json"
    )

    const val FILE_WIDGET_PHOTO = "files/widget_photo"
    const val ROLE_WIDGET_PHOTO = "home_widget_photo"

    const val SERIALIZATION_JSON = "json"
    const val ARCHIVE_ZIP = "zip"
}

@JsonClass(generateAdapter = true)
data class BackupManifest(
    val format: String = BackupFormat.MAGIC,
    val formatVersion: Int = BackupFormat.CURRENT_FORMAT_VERSION,
    val dataSchemaVersion: Int = BackupFormat.CURRENT_DATA_SCHEMA_VERSION,
    val appVersionName: String,
    val appVersionCode: Int,
    val exportedAt: String,
    val exportedAtEpochMillis: Long,
    val serialization: String = BackupFormat.SERIALIZATION_JSON,
    val archive: String = BackupFormat.ARCHIVE_ZIP,
    val checksumsFile: String = BackupFormat.CHECKSUMS_PATH,
    val sections: Map<String, BackupSectionEntry> = emptyMap(),
    val files: List<BackupFileEntry> = emptyList(),
    val migrationNote: String = "Backups are migrated forward by dataSchemaVersion."
)

@JsonClass(generateAdapter = true)
data class BackupSectionEntry(
    val path: String,
    val required: Boolean = true,
    val recordCount: Int = 0
)

@JsonClass(generateAdapter = true)
data class BackupFileEntry(
    val path: String,
    val role: String,
    val mediaType: String = "application/octet-stream",
    val size: Long,
    val sha256: String
)

@JsonClass(generateAdapter = true)
data class BackupChecksums(
    val algorithm: String = "SHA-256",
    val entries: Map<String, String> = emptyMap()
)

// ---------------------------------------------------------------------------
// Payload schema v1
// ---------------------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class ProfileSectionV1(
    val dataStoreProfile: DataStoreProfileV1? = null,
    val roomProfile: RoomProfileV1? = null
)

@JsonClass(generateAdapter = true)
data class DataStoreProfileV1(
    val isSaved: Boolean? = null,
    val name: String? = null,
    val heightCm: Double? = null,
    val currentWeightKg: Double? = null,
    val targetWeightKg: Double? = null,
    val age: Int? = null,
    val preferredUnit: String? = null,
    val fitnessLevel: String? = null
)

@JsonClass(generateAdapter = true)
data class RoomProfileV1(
    val id: Int = 1,
    val name: String,
    val heightCm: Double,
    val currentWeightKg: Double,
    val targetWeightKg: Double,
    val age: Int,
    val preferredUnit: String,
    val fitnessLevel: String
)

@JsonClass(generateAdapter = true)
data class PreferencesSectionV1(
    val unit: String? = null,
    val restTimerVibrationEnabled: Boolean? = null,
    val restTimerSoundEnabled: Boolean? = null,
    val shareAthleteDataWithAi: Boolean? = null,
    val autoplayExerciseDemos: Boolean? = null,
    val liquidGlassEnabled: Boolean? = null,
    val fullscreenEnabled: Boolean? = null,
    val themeMode: String? = null,
    val onboardingCompleted: Boolean? = null,
    val activeWorkoutDraft: String? = null,
    val weekStartsOnMonday: Boolean? = null,
    val autoSaveIntervalSeconds: Int? = null,
    val autoCheckUpdatesOnStart: Boolean? = null,
    val activeRoutineId: Long? = null,
    val restTimers: List<RestTimerV1>? = null,
    val benchmark: BenchmarkPreferencesV1? = null,
    val ai: AiPreferencesV1? = null
)

@JsonClass(generateAdapter = true)
data class RestTimerV1(
    val id: String,
    val name: String,
    val totalSeconds: Int
)

@JsonClass(generateAdapter = true)
data class BenchmarkPreferencesV1(
    val pushUps: Int? = null,
    val pushUpsAddedKg: Int? = null,
    val pullUps: Int? = null,
    val pullUpsAddedKg: Int? = null,
    val bulgarianSplitSquat: Int? = null,
    val bulgarianSplitSquatAddedKg: Int? = null
)

@JsonClass(generateAdapter = true)
data class AiPreferencesV1(
    val activeProvider: String? = null,
    val modelsByProvider: Map<String, String> = emptyMap(),
    val toolModesByProvider: Map<String, String> = emptyMap(),
    val customBaseUrl: String? = null,
    val customAcknowledgedEndpoint: String? = null
)

@JsonClass(generateAdapter = true)
data class DashboardSectionV1(
    val widgetOrder: List<String>? = null,
    val hiddenWidgetIds: List<String>? = null,
    val weeklyGoal: Int? = null,
    val photoPresent: Boolean = false,
    val photoArchivePath: String? = null,
    val photoMediaType: String? = null
)

@JsonClass(generateAdapter = true)
data class ExerciseSectionV1(
    val editedLibraryExerciseIds: List<Long> = emptyList(),
    val exercises: List<ExerciseV1> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ExerciseV1(
    val id: Long,
    val name: String,
    val category: String,
    val bodyPart: String,
    val target: String,
    val secondaryMuscles: String,
    val exerciseType: String,
    val instructions: String,
    val description: String,
    val equipment: String,
    val mediaPath: String,
    val sourceId: String,
    val isCustom: Boolean,
    val isAiGenerated: Boolean
)

@JsonClass(generateAdapter = true)
data class WorkoutsSectionV1(
    val sessions: List<WorkoutSessionV1> = emptyList(),
    val sets: List<LoggedSetV1> = emptyList()
)

@JsonClass(generateAdapter = true)
data class WorkoutSessionV1(
    val id: Long,
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val notes: String,
    val routineId: Long,
    val routineWeekday: Int,
    val plannedDate: Long,
    val plannedDateKey: Long
)

@JsonClass(generateAdapter = true)
data class LoggedSetV1(
    val id: Long,
    val sessionId: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val exerciseType: String,
    val setNumber: Int,
    val weight: Double,
    val addedWeight: Double,
    val reps: Int,
    val holdDurationSeconds: Int,
    val distanceKm: Double,
    val isCompleted: Boolean,
    val timestamp: Long,
    val bodyWeightKg: Double
)

@JsonClass(generateAdapter = true)
data class RoutinesSectionV1(
    val routines: List<SavedRoutineV1> = emptyList(),
    val scheduleDays: List<ScheduledWorkoutDayV1> = emptyList()
)

@JsonClass(generateAdapter = true)
data class SavedRoutineV1(
    val id: Long,
    val name: String,
    val description: String,
    val daysPerWeek: Int,
    val goal: String,
    val exercisesJson: String,
    val createdAt: Long,
    val scheduleVersion: Int
)

@JsonClass(generateAdapter = true)
data class ScheduledWorkoutDayV1(
    val dateStart: Long,
    val dateKey: Long,
    val routineId: Long,
    val weekday: Int,
    val dayTitle: String,
    val exerciseCount: Int,
    val capturedAt: Long
)

@JsonClass(generateAdapter = true)
data class ProgressSectionV1(
    val bodyWeightLogs: List<BodyWeightLogV1> = emptyList()
)

@JsonClass(generateAdapter = true)
data class BodyWeightLogV1(
    val id: Long,
    val weightKg: Double,
    val timestamp: Long,
    val note: String
)

@JsonClass(generateAdapter = true)
data class AiSectionV1(
    val chatSessions: List<ChatSessionV1> = emptyList(),
    val chatMessages: List<ChatMessageV1> = emptyList(),
    val geminiSummaries: List<GeminiSummaryV1> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ChatSessionV1(
    val id: String,
    val title: String,
    val createdAt: Long,
    val lastUpdatedAt: Long
)

@JsonClass(generateAdapter = true)
data class ChatMessageV1(
    val id: String,
    val sessionId: String,
    val sender: String,
    val text: String,
    val timestamp: Long
)

@JsonClass(generateAdapter = true)
data class GeminiSummaryV1(
    val id: Long,
    val exerciseName: String,
    val report: String,
    val windowDays: Int,
    val daysLogged: Int,
    val setCount: Int,
    val bestOneRm: Double,
    val trendPercent: Double,
    val createdAt: Long
)

// ---------------------------------------------------------------------------
// Runtime-only models. These are never serialized directly; their DTOs above are.
// ---------------------------------------------------------------------------

data class WidgetPhotoData(
    val bytes: ByteArray,
    val mediaType: String,
    val extension: String
) {
    override fun equals(other: Any?): Boolean =
        other is WidgetPhotoData &&
            bytes.contentEquals(other.bytes) &&
            mediaType == other.mediaType &&
            extension == other.extension

    override fun hashCode(): Int =
        ((bytes.contentHashCode() * 31 + mediaType.hashCode()) * 31) + extension.hashCode()
}

data class UserDataSnapshot(
    val dataStoreProfile: DataStoreProfileV1?,
    val roomProfile: RoomProfileV1?,
    val roomCustomApiKey: String,
    val preferences: PreferencesSectionV1,
    val dashboard: DashboardSectionV1,
    val editedLibraryExerciseIds: Set<Long>,
    val exercises: List<ExerciseV1>,
    val sessions: List<WorkoutSessionV1>,
    val sets: List<LoggedSetV1>,
    val routines: List<SavedRoutineV1>,
    val scheduleDays: List<ScheduledWorkoutDayV1>,
    val bodyWeightLogs: List<BodyWeightLogV1>,
    val chatSessions: List<ChatSessionV1>,
    val chatMessages: List<ChatMessageV1>,
    val geminiSummaries: List<GeminiSummaryV1>,
    val widgetPhoto: WidgetPhotoData?,
    /**
     * Destination path of the current Home photo. Runtime-only: it is never serialized and is used
     * solely to restore the previous preference if an import has to roll back.
     */
    val widgetPhotoPath: String? = null
) {
    val sectionCounts: Map<String, Int>
        get() = linkedMapOf(
            BackupFormat.SECTION_PROFILE to ((if (dataStoreProfile == null) 0 else 1) + (if (roomProfile == null) 0 else 1)),
            BackupFormat.SECTION_PREFERENCES to preferences.filledFieldCount(),
            BackupFormat.SECTION_DASHBOARD to dashboard.filledFieldCount(),
            BackupFormat.SECTION_EXERCISES to exercises.size,
            BackupFormat.SECTION_WORKOUTS to (sessions.size + sets.size),
            BackupFormat.SECTION_ROUTINES to (routines.size + scheduleDays.size),
            BackupFormat.SECTION_PROGRESS to bodyWeightLogs.size,
            BackupFormat.SECTION_AI to (chatSessions.size + chatMessages.size + geminiSummaries.size)
        )
}

private fun PreferencesSectionV1.filledFieldCount(): Int {
    var count = 0
    if (unit != null) count++
    if (restTimerVibrationEnabled != null) count++
    if (restTimerSoundEnabled != null) count++
    if (shareAthleteDataWithAi != null) count++
    if (autoplayExerciseDemos != null) count++
    if (liquidGlassEnabled != null) count++
    if (fullscreenEnabled != null) count++
    if (themeMode != null) count++
    if (onboardingCompleted != null) count++
    if (activeWorkoutDraft != null) count++
    if (weekStartsOnMonday != null) count++
    if (autoSaveIntervalSeconds != null) count++
    if (autoCheckUpdatesOnStart != null) count++
    if (activeRoutineId != null) count++
    if (restTimers != null) count++
    if (benchmark != null) count++
    if (ai != null) count++
    return count
}

private fun DashboardSectionV1.filledFieldCount(): Int {
    var count = 0
    if (widgetOrder != null) count++
    if (hiddenWidgetIds != null) count++
    if (weeklyGoal != null) count++
    if (photoPresent) count++
    return count
}

data class BackupPreview(
    val fileName: String?,
    val exportedAt: String,
    val exportedAtEpochMillis: Long,
    val appVersionName: String,
    val appVersionCode: Int,
    val formatVersion: Int,
    val dataSchemaVersion: Int,
    val archiveSizeBytes: Long,
    val sectionCounts: Map<String, Int>
)

data class ExportSummary(
    val fileName: String,
    val sizeBytes: Long,
    val exportedAtEpochMillis: Long,
    val sectionCounts: Map<String, Int>
)

data class ImportSummary(
    val preview: BackupPreview,
    val restoredCounts: Map<String, Int>
)

data class PreparedImport(
    val preview: BackupPreview,
    val snapshot: UserDataSnapshot,
    val archiveFile: java.io.File
) {
    fun cleanup() {
        runCatching { archiveFile.delete() }
    }
}

data class ArchivePayload(
    val manifest: BackupManifest,
    val sections: Map<String, ByteArray>,
    val files: Map<String, ByteArray>
)

enum class BackupPhase(val label: String) {
    PREPARING("Preparing"),
    READING_USER_DATA("Reading your data"),
    SERIALIZING("Serializing"),
    COLLECTING_FILES("Collecting files"),
    COMPRESSING("Compressing archive"),
    VERIFYING("Verifying archive"),
    VALIDATING("Validating backup"),
    STAGING("Staging replacement"),
    APPLYING_DATABASE("Replacing database"),
    RESTORING_FILES("Restoring files"),
    APPLYING_PREFERENCES("Restoring preferences"),
    CLEANING_UP("Cleaning up"),
    COMPLETED("Completed"),
    FAILED("Failed")
}

data class BackupProgress(
    val phase: BackupPhase,
    val completed: Int,
    val total: Int
) {
    val fraction: Float
        get() = if (total <= 0) 0f else (completed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

enum class BackupErrorCode {
    NOT_AN_ARCHIVE,
    MALFORMED_ARCHIVE,
    UNSUPPORTED_FORMAT,
    UNSUPPORTED_SCHEMA,
    CHECKSUM_MISMATCH,
    MISSING_SECTION,
    INVALID_MANIFEST,
    INVALID_DATA,
    RELATIONSHIP_ERROR,
    TOO_LARGE,
    IO_ERROR,
    ROLLBACK_FAILED,
    UNKNOWN
}

class BackupException(
    message: String,
    val code: BackupErrorCode,
    cause: Throwable? = null,
    val dataPreserved: Boolean = false
) : Exception(message, cause)

enum class BackupOperation {
    EXPORT,
    IMPORT
}

/** UI-facing state of the Settings backup section. */
sealed interface DataTransferState {
    data object Idle : DataTransferState
    data class Running(
        val operation: BackupOperation,
        val progress: BackupProgress
    ) : DataTransferState

    data class ReadyToImport(val preview: BackupPreview) : DataTransferState

    data class Success(
        val operation: BackupOperation,
        val title: String,
        val detail: String
    ) : DataTransferState

    data class Error(
        val message: String,
        val destinationPreserved: Boolean
    ) : DataTransferState

    data class Cancelled(val message: String) : DataTransferState
}
