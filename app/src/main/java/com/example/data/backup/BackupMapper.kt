/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.data.backup

import com.example.data.model.BodyWeightLog
import com.example.data.model.ChatMessageEntity
import com.example.data.model.ChatSession
import com.example.data.model.Exercise
import com.example.data.model.GeminiSummary
import com.example.data.model.LoggedSet
import com.example.data.model.SavedRoutine
import com.example.data.model.ScheduledWorkoutDay
import com.example.data.model.WorkoutSession

/** Converts the runtime snapshot to frozen v1 DTOs and back. */
object BackupMapper {

    fun toSections(snapshot: UserDataSnapshot): Map<String, ByteArray> {
        val result = LinkedHashMap<String, ByteArray>()
        result[BackupFormat.SECTION_PROFILE] = BackupJson.toBytes(
            ProfileSectionV1(
                dataStoreProfile = snapshot.dataStoreProfile,
                roomProfile = snapshot.roomProfile
            ),
            ProfileSectionV1::class.java
        )
        result[BackupFormat.SECTION_PREFERENCES] = BackupJson.toBytes(
            snapshot.preferences,
            PreferencesSectionV1::class.java
        )
        result[BackupFormat.SECTION_DASHBOARD] = BackupJson.toBytes(
            snapshot.dashboard,
            DashboardSectionV1::class.java
        )
        result[BackupFormat.SECTION_EXERCISES] = BackupJson.toBytes(
            ExerciseSectionV1(
                editedLibraryExerciseIds = snapshot.editedLibraryExerciseIds.toList().sorted(),
                exercises = snapshot.exercises
            ),
            ExerciseSectionV1::class.java
        )
        result[BackupFormat.SECTION_WORKOUTS] = BackupJson.toBytes(
            WorkoutsSectionV1(sessions = snapshot.sessions, sets = snapshot.sets),
            WorkoutsSectionV1::class.java
        )
        result[BackupFormat.SECTION_ROUTINES] = BackupJson.toBytes(
            RoutinesSectionV1(routines = snapshot.routines, scheduleDays = snapshot.scheduleDays),
            RoutinesSectionV1::class.java
        )
        result[BackupFormat.SECTION_PROGRESS] = BackupJson.toBytes(
            ProgressSectionV1(bodyWeightLogs = snapshot.bodyWeightLogs),
            ProgressSectionV1::class.java
        )
        result[BackupFormat.SECTION_AI] = BackupJson.toBytes(
            AiSectionV1(
                chatSessions = snapshot.chatSessions,
                chatMessages = snapshot.chatMessages,
                geminiSummaries = snapshot.geminiSummaries
            ),
            AiSectionV1::class.java
        )
        return result
    }

    fun fromPayload(payload: ArchivePayload): UserDataSnapshot {
        val profile = BackupJson.parse(payload.sections.getValue(BackupFormat.SECTION_PROFILE), ProfileSectionV1::class.java)
        val parsedPreferences = BackupJson.parse(payload.sections.getValue(BackupFormat.SECTION_PREFERENCES), PreferencesSectionV1::class.java)
        val preferences = parsedPreferences.copy(
            benchmark = parsedPreferences.benchmark?.takeIf {
                it.pushUps != null || it.pushUpsAddedKg != null || it.pullUps != null ||
                    it.pullUpsAddedKg != null || it.bulgarianSplitSquat != null ||
                    it.bulgarianSplitSquatAddedKg != null
            },
            ai = parsedPreferences.ai?.takeIf {
                it.activeProvider != null || it.modelsByProvider.isNotEmpty() ||
                    it.toolModesByProvider.isNotEmpty() || it.customBaseUrl != null
            }
        )
        val dashboard = BackupJson.parse(payload.sections.getValue(BackupFormat.SECTION_DASHBOARD), DashboardSectionV1::class.java)
        val exerciseSection = BackupJson.parse(payload.sections.getValue(BackupFormat.SECTION_EXERCISES), ExerciseSectionV1::class.java)
        val workouts = BackupJson.parse(payload.sections.getValue(BackupFormat.SECTION_WORKOUTS), WorkoutsSectionV1::class.java)
        val routines = BackupJson.parse(payload.sections.getValue(BackupFormat.SECTION_ROUTINES), RoutinesSectionV1::class.java)
        val progress = BackupJson.parse(payload.sections.getValue(BackupFormat.SECTION_PROGRESS), ProgressSectionV1::class.java)
        val ai = BackupJson.parse(payload.sections.getValue(BackupFormat.SECTION_AI), AiSectionV1::class.java)

        val widgetFile = payload.manifest.files.firstOrNull { it.role == BackupFormat.ROLE_WIDGET_PHOTO }
        if (dashboard.photoPresent && widgetFile != null &&
            dashboard.photoArchivePath != null &&
            dashboard.photoArchivePath != widgetFile.path
        ) {
            throw BackupException(
                "The Home photo reference in the backup does not match its manifest.",
                BackupErrorCode.INVALID_MANIFEST
            )
        }
        val widgetPhoto = widgetFile?.let { entry ->
            val bytes = payload.files[entry.path]
                ?: throw BackupException("The backup is missing its widget photo.", BackupErrorCode.MISSING_SECTION)
            val extension = when {
                entry.mediaType == "image/png" -> "png"
                entry.mediaType == "image/webp" -> "webp"
                else -> "jpg"
            }
            WidgetPhotoData(bytes, entry.mediaType, extension)
        }

        return UserDataSnapshot(
            dataStoreProfile = profile.dataStoreProfile?.takeIf {
                it.isSaved != null || it.name != null || it.heightCm != null ||
                    it.currentWeightKg != null || it.targetWeightKg != null || it.age != null ||
                    it.preferredUnit != null || it.fitnessLevel != null
            },
            roomProfile = profile.roomProfile,
            roomCustomApiKey = "",
            preferences = preferences,
            dashboard = dashboard,
            editedLibraryExerciseIds = exerciseSection.editedLibraryExerciseIds.toSet(),
            exercises = exerciseSection.exercises,
            sessions = workouts.sessions,
            sets = workouts.sets,
            routines = routines.routines,
            scheduleDays = routines.scheduleDays,
            bodyWeightLogs = progress.bodyWeightLogs,
            chatSessions = ai.chatSessions,
            chatMessages = ai.chatMessages,
            geminiSummaries = ai.geminiSummaries,
            widgetPhoto = widgetPhoto,
            widgetPhotoPath = null
        )
    }

    fun exerciseToDto(exercise: Exercise): ExerciseV1 = ExerciseV1(
        id = exercise.id,
        name = exercise.name,
        category = exercise.category,
        bodyPart = exercise.bodyPart,
        target = exercise.target,
        secondaryMuscles = exercise.secondaryMuscles,
        exerciseType = exercise.exerciseType,
        instructions = exercise.instructions,
        description = exercise.description,
        equipment = exercise.equipment,
        // The dataset path is shipped metadata and is safe to keep. A custom row's media slot is
        // the excluded local image/GIF, so its path must never travel with the backup.
        mediaPath = if (exercise.isCustom || exercise.isAiGenerated) "" else exercise.mediaPath,
        sourceId = exercise.sourceId,
        isCustom = exercise.isCustom,
        isAiGenerated = exercise.isAiGenerated
    )

    fun exerciseFromDto(dto: ExerciseV1): Exercise = Exercise(
        id = dto.id,
        name = dto.name,
        category = dto.category,
        bodyPart = dto.bodyPart,
        target = dto.target,
        secondaryMuscles = dto.secondaryMuscles,
        exerciseType = dto.exerciseType,
        instructions = dto.instructions,
        description = dto.description,
        equipment = dto.equipment,
        // Custom rows never carry a media path from the source device. Library rows keep the
        // dataset-relative animation path, which is not a local file reference.
        mediaPath = if (dto.isCustom || dto.isAiGenerated) "" else dto.mediaPath,
        // Exercise images and GIFs are excluded from the backup. A restored exercise must not keep
        // a source-device file reference, so the local media slot starts empty.
        customMediaUri = "",
        customMediaIsVideo = false,
        sourceId = dto.sourceId,
        isCustom = dto.isCustom,
        isAiGenerated = dto.isAiGenerated
    )

    fun sessionToDto(session: WorkoutSession): WorkoutSessionV1 = WorkoutSessionV1(
        id = session.id,
        title = session.title,
        startTime = session.startTime,
        endTime = session.endTime,
        notes = session.notes,
        routineId = session.routineId,
        routineWeekday = session.routineWeekday,
        plannedDate = session.plannedDate,
        plannedDateKey = session.plannedDateKey
    )

    fun sessionFromDto(dto: WorkoutSessionV1): WorkoutSession = WorkoutSession(
        id = dto.id,
        title = dto.title,
        startTime = dto.startTime,
        endTime = dto.endTime,
        notes = dto.notes,
        routineId = dto.routineId,
        routineWeekday = dto.routineWeekday,
        plannedDate = dto.plannedDate,
        plannedDateKey = dto.plannedDateKey
    )

    fun setToDto(set: LoggedSet): LoggedSetV1 = LoggedSetV1(
        id = set.id,
        sessionId = set.sessionId,
        exerciseId = set.exerciseId,
        exerciseName = set.exerciseName,
        exerciseType = set.exerciseType,
        setNumber = set.setNumber,
        weight = set.weight,
        addedWeight = set.addedWeight,
        reps = set.reps,
        holdDurationSeconds = set.holdDurationSeconds,
        distanceKm = set.distanceKm,
        isCompleted = set.isCompleted,
        timestamp = set.timestamp,
        bodyWeightKg = set.bodyWeightKg
    )

    fun setFromDto(dto: LoggedSetV1): LoggedSet = LoggedSet(
        id = dto.id,
        sessionId = dto.sessionId,
        exerciseId = dto.exerciseId,
        exerciseName = dto.exerciseName,
        exerciseType = dto.exerciseType,
        setNumber = dto.setNumber,
        weight = dto.weight,
        addedWeight = dto.addedWeight,
        reps = dto.reps,
        holdDurationSeconds = dto.holdDurationSeconds,
        distanceKm = dto.distanceKm,
        isCompleted = dto.isCompleted,
        timestamp = dto.timestamp,
        bodyWeightKg = dto.bodyWeightKg
    )

    fun routineToDto(routine: SavedRoutine): SavedRoutineV1 = SavedRoutineV1(
        id = routine.id,
        name = routine.name,
        description = routine.description,
        daysPerWeek = routine.daysPerWeek,
        goal = routine.goal,
        exercisesJson = routine.exercisesJson,
        createdAt = routine.createdAt,
        scheduleVersion = routine.scheduleVersion
    )

    fun routineFromDto(dto: SavedRoutineV1): SavedRoutine = SavedRoutine(
        id = dto.id,
        name = dto.name,
        description = dto.description,
        daysPerWeek = dto.daysPerWeek,
        goal = dto.goal,
        exercisesJson = dto.exercisesJson,
        createdAt = dto.createdAt,
        scheduleVersion = dto.scheduleVersion
    )

    fun scheduleDayToDto(day: ScheduledWorkoutDay): ScheduledWorkoutDayV1 = ScheduledWorkoutDayV1(
        dateStart = day.dateStart,
        dateKey = day.dateKey,
        routineId = day.routineId,
        weekday = day.weekday,
        dayTitle = day.dayTitle,
        exerciseCount = day.exerciseCount,
        capturedAt = day.capturedAt
    )

    fun scheduleDayFromDto(dto: ScheduledWorkoutDayV1): ScheduledWorkoutDay = ScheduledWorkoutDay(
        dateStart = dto.dateStart,
        dateKey = dto.dateKey,
        routineId = dto.routineId,
        weekday = dto.weekday,
        dayTitle = dto.dayTitle,
        exerciseCount = dto.exerciseCount,
        capturedAt = dto.capturedAt
    )

    fun weightLogToDto(log: BodyWeightLog): BodyWeightLogV1 = BodyWeightLogV1(
        id = log.id,
        weightKg = log.weightKg,
        timestamp = log.timestamp,
        note = log.note
    )

    fun weightLogFromDto(dto: BodyWeightLogV1): BodyWeightLog = BodyWeightLog(
        id = dto.id,
        weightKg = dto.weightKg,
        timestamp = dto.timestamp,
        note = dto.note
    )

    fun chatSessionToDto(session: ChatSession): ChatSessionV1 = ChatSessionV1(
        id = session.id,
        title = session.title,
        createdAt = session.createdAt,
        lastUpdatedAt = session.lastUpdatedAt
    )

    fun chatSessionFromDto(dto: ChatSessionV1): ChatSession = ChatSession(
        id = dto.id,
        title = dto.title,
        createdAt = dto.createdAt,
        lastUpdatedAt = dto.lastUpdatedAt
    )

    fun chatMessageToDto(message: ChatMessageEntity): ChatMessageV1 = ChatMessageV1(
        id = message.id,
        sessionId = message.sessionId,
        sender = message.sender,
        text = message.text,
        timestamp = message.timestamp
    )

    fun chatMessageFromDto(dto: ChatMessageV1): ChatMessageEntity = ChatMessageEntity(
        id = dto.id,
        sessionId = dto.sessionId,
        sender = dto.sender,
        text = dto.text,
        timestamp = dto.timestamp
    )

    fun geminiSummaryToDto(summary: GeminiSummary): GeminiSummaryV1 = GeminiSummaryV1(
        id = summary.id,
        exerciseName = summary.exerciseName,
        report = summary.report,
        windowDays = summary.windowDays,
        daysLogged = summary.daysLogged,
        setCount = summary.setCount,
        bestOneRm = summary.bestOneRm,
        trendPercent = summary.trendPercent,
        createdAt = summary.createdAt
    )

    fun geminiSummaryFromDto(dto: GeminiSummaryV1): GeminiSummary = GeminiSummary(
        id = dto.id,
        exerciseName = dto.exerciseName,
        report = dto.report,
        windowDays = dto.windowDays,
        daysLogged = dto.daysLogged,
        setCount = dto.setCount,
        bestOneRm = dto.bestOneRm,
        trendPercent = dto.trendPercent,
        createdAt = dto.createdAt
    )
}
