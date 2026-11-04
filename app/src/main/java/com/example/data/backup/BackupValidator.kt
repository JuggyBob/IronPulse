/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.data.backup

import com.example.data.ai.AiProviderId
import com.example.data.ai.AiToolMode
import com.example.data.model.ChatMessageKind
import com.example.data.model.ChatMessageStatus
import com.example.data.model.ExerciseType
import com.example.data.model.Weekday
import com.example.ui.theme.ThemeMode

/**
 * Validates a fully parsed snapshot before any destination data is modified.
 *
 * This is intentionally stricter than Room: the database has no declared foreign keys, so the
 * relationships the UI depends on are enforced here. Soft references (a workout's routine id, a
 * logged set's exercise row id, a summary's exercise name) are checked for shape but not required to
 * resolve, because the app deliberately keeps history readable after a routine or exercise is
 * deleted; history is keyed by exercise name.
 */
object BackupValidator {

    fun validate(snapshot: UserDataSnapshot) {
        validateProfile(snapshot)
        validatePreferences(snapshot)
        validateDashboard(snapshot)
        validateExercises(snapshot)
        validateWorkouts(snapshot)
        validateRoutines(snapshot)
        validateProgress(snapshot)
        validateAi(snapshot)
    }

    private fun validateProfile(snapshot: UserDataSnapshot) {
        val profile = snapshot.dataStoreProfile
        if (profile != null) {
            if (profile.isSaved == true && profile.name?.isBlank() == true) {
                invalid("The saved profile has no name.")
            }
            finiteNonNegative("profile height", profile.heightCm)
            finiteNonNegative("profile weight", profile.currentWeightKg)
            finiteNonNegative("profile target weight", profile.targetWeightKg)
            if (profile.age != null && profile.age !in 0..150) invalid("The profile age is invalid.")
            if (profile.preferredUnit != null && profile.preferredUnit !in setOf("kg", "lb")) {
                invalid("The profile weight unit is invalid.")
            }
        }
        val room = snapshot.roomProfile ?: return
        if (room.id != 1) invalid("The stored profile has an invalid identifier.")
        if (room.name.isBlank()) invalid("The stored profile has no name.")
        finiteNonNegative("stored profile height", room.heightCm)
        finiteNonNegative("stored profile weight", room.currentWeightKg)
        finiteNonNegative("stored profile target weight", room.targetWeightKg)
        if (room.age !in 0..150) invalid("The stored profile age is invalid.")
        if (room.preferredUnit !in setOf("kg", "lb")) invalid("The stored profile weight unit is invalid.")
    }

    private fun validatePreferences(snapshot: UserDataSnapshot) {
        val prefs = snapshot.preferences
        if (prefs.unit != null && prefs.unit !in setOf("kg", "lb")) invalid("The weight unit is invalid.")
        if (prefs.themeMode != null && ThemeMode.values().none { it.storageKey == prefs.themeMode }) {
            invalid("The backup contains an unknown theme.")
        }
        if (prefs.autoSaveIntervalSeconds != null && prefs.autoSaveIntervalSeconds !in 5..300) {
            invalid("The autosave interval is outside the supported range.")
        }
        if (prefs.activeRoutineId != null && prefs.activeRoutineId < 0L) {
            invalid("The active routine identifier is invalid.")
        }
        prefs.activeWorkoutDraft?.let { draft ->
            if (draft.isBlank()) invalid("The saved workout draft is empty.")
            try {
                val node = JsonNode.parse(draft.toByteArray(Charsets.UTF_8))
                if (node !is JsonNode.Obj) invalid("The saved workout draft is not a JSON object.")
            } catch (e: BackupException) {
                invalid("The saved workout draft is not valid JSON.", e)
            } catch (e: Exception) {
                invalid("The saved workout draft is not valid JSON.", e)
            }
        }
        prefs.restTimers?.forEach { timer ->
            if (timer.id.isBlank()) invalid("A saved rest timer has no identifier.")
            if (timer.name.isBlank()) invalid("A saved rest timer has no name.")
            if (timer.totalSeconds <= 0) invalid("A saved rest timer has an invalid duration.")
        }
        if (prefs.restTimers != null) {
            val ids = prefs.restTimers.map { it.id }
            if (ids.size != ids.toSet().size) invalid("Two saved rest timers share an identifier.")
        }
        prefs.benchmark?.let { benchmark ->
            listOf(
                "push-up record" to benchmark.pushUps,
                "push-up added weight" to benchmark.pushUpsAddedKg,
                "pull-up record" to benchmark.pullUps,
                "pull-up added weight" to benchmark.pullUpsAddedKg,
                "split squat record" to benchmark.bulgarianSplitSquat,
                "split squat added weight" to benchmark.bulgarianSplitSquatAddedKg
            ).forEach { (label, value) ->
                if (value != null && value < 0) invalid("The $label is negative.")
            }
        }
        prefs.ai?.let { ai ->
            if (ai.activeProvider != null && AiProviderId.entries.none { it.storageKey == ai.activeProvider }) {
                invalid("The backup contains an unknown AI provider.")
            }
            for ((provider, model) in ai.modelsByProvider) {
                if (AiProviderId.entries.none { it.storageKey == provider }) {
                    invalid("The backup contains an AI model for an unknown provider.")
                }
                if (model.isBlank()) invalid("The backup contains an empty AI model name.")
            }
            for ((provider, mode) in ai.toolModesByProvider) {
                if (AiProviderId.entries.none { it.storageKey == provider }) {
                    invalid("The backup contains an AI tool mode for an unknown provider.")
                }
                if (AiToolMode.entries.none { it.name == mode }) {
                    invalid("The backup contains an unknown AI tool mode.")
                }
            }
            ai.customBaseUrl?.let { url ->
                if (url.isBlank()) invalid("The custom AI endpoint is empty.")
                if (containsEmbeddedCredentials(url)) {
                    invalid("The custom AI endpoint contains embedded credentials and cannot be imported.")
                }
            }
            if (ai.customAcknowledgedEndpoint != null && ai.customBaseUrl == null) {
                invalid("The backup has an endpoint acknowledgement without an endpoint.")
            }
        }
        prefs.widgetThemes?.let { themes ->
            for ((label, raw) in listOf(
                "streak" to themes.streak,
                "weekly" to themes.weekly,
                "profile" to themes.profile
            )) {
                if (raw != null && ThemeMode.values().none { it.storageKey == raw }) {
                    invalid("The backup contains an unknown Android widget theme for $label.")
                }
            }
        }
    }

    private fun validateDashboard(snapshot: UserDataSnapshot) {
        val dashboard = snapshot.dashboard
        dashboard.weeklyGoal?.let {
            if (it !in 1..7) invalid("The Home weekly goal is outside the supported range.")
        }
        if (dashboard.photoPresent) {
            val photo = snapshot.widgetPhoto
                ?: invalid("The Home photo is missing from the backup.")
            if (photo.bytes.isEmpty()) invalid("The Home photo in the backup is empty.")
            if (photo.mediaType !in setOf("image/jpeg", "image/png", "image/webp")) {
                invalid("The Home photo has an unsupported image type.")
            }
            val hasExpectedSignature = when (photo.mediaType) {
                "image/jpeg" -> photo.bytes.size >= 3 &&
                    photo.bytes[0] == 0xFF.toByte() &&
                    photo.bytes[1] == 0xD8.toByte() &&
                    photo.bytes[2] == 0xFF.toByte()
                "image/png" -> photo.bytes.size >= 8 &&
                    photo.bytes[0] == 0x89.toByte() &&
                    photo.bytes[1] == 0x50.toByte() &&
                    photo.bytes[2] == 0x4E.toByte() &&
                    photo.bytes[3] == 0x47.toByte()
                "image/webp" -> photo.bytes.size >= 12 &&
                    photo.bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" &&
                    photo.bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP"
                else -> false
            }
            if (!hasExpectedSignature) invalid("The Home photo does not match its declared image type.")
        } else if (snapshot.widgetPhoto != null) {
            invalid("The backup contains a Home photo its manifest does not declare.")
        }
    }

    private fun validateExercises(snapshot: UserDataSnapshot) {
        val exercises = snapshot.exercises
        val ids = exercises.map { it.id }
        if (ids.any { it <= 0L }) invalid("An exercise has an invalid identifier.")
        if (ids.size != ids.toSet().size) invalid("Two exercises share an identifier.")
        val names = exercises.map { it.name.trim().lowercase() }
        if (names.any { it.isBlank() }) invalid("An exercise has no name.")
        if (names.size != names.toSet().size) invalid("Two exercises have the same name.")

        val edited = snapshot.editedLibraryExerciseIds
        for (id in edited) {
            if (id <= 0L) invalid("An edited library exercise identifier is invalid.")
            val exercise = exercises.firstOrNull { it.id == id }
                ?: invalid("The backup lists an edited library exercise that is not included.")
            if (exercise.isCustom || exercise.isAiGenerated) {
                invalid("An edited library exercise is marked as user-owned.")
            }
        }
        for (exercise in exercises) {
            if (exercise.name.isBlank()) invalid("An exercise has no name.")
            if (exercise.category.isBlank()) invalid("An exercise has no category.")
            if (exercise.exerciseType !in ExerciseType.ALL) invalid("An exercise has an unknown type.")
            if ((exercise.isCustom || exercise.isAiGenerated) && exercise.mediaPath.isNotBlank()) {
                invalid("A custom exercise still references an excluded media file.")
            }
            if (exercise.mediaPath.contains('\\') ||
                exercise.mediaPath.startsWith('/') ||
                exercise.mediaPath.contains(':') ||
                exercise.mediaPath.split('/').any { it == ".." }
            ) {
                invalid("An exercise contains an unsafe media path.")
            }
            if (exercise.id in edited && (exercise.isCustom || exercise.isAiGenerated)) {
                invalid("An edited library exercise is marked as user-owned.")
            }
            if (!exercise.isCustom && !exercise.isAiGenerated && exercise.id !in edited) {
                invalid("The backup contains a library exercise that is not part of the user's data.")
            }
        }
    }

    private fun validateWorkouts(snapshot: UserDataSnapshot) {
        val sessions = snapshot.sessions
        val sessionIds = sessions.map { it.id }
        if (sessionIds.any { it <= 0L }) invalid("A workout session has an invalid identifier.")
        if (sessionIds.size != sessionIds.toSet().size) invalid("Two workout sessions share an identifier.")
        for (session in sessions) {
            if (session.title.isBlank()) invalid("A workout session has no title.")
            if (session.startTime < 0L || session.endTime < 0L) invalid("A workout session has an invalid timestamp.")
            if (session.routineId < 0L) invalid("A workout session has an invalid routine reference.")
            if (session.routineWeekday !in 0..Weekday.COUNT) invalid("A workout session has an invalid weekday.")
            if (session.plannedDate < 0L) invalid("A workout session has an invalid planned date.")
            if (session.plannedDateKey < 0L) invalid("A workout session has an invalid planned date key.")
        }

        val setIds = snapshot.sets.map { it.id }
        if (setIds.any { it <= 0L }) invalid("A logged set has an invalid identifier.")
        if (setIds.size != setIds.toSet().size) invalid("Two logged sets share an identifier.")
        val knownSessions = sessionIds.toSet()
        for (set in snapshot.sets) {
            if (set.sessionId !in knownSessions) {
                invalid("A logged set belongs to a workout that is not in the backup.", code = BackupErrorCode.RELATIONSHIP_ERROR)
            }
            if (set.exerciseId < 0L) invalid("A logged set has an invalid exercise reference.")
            if (set.exerciseName.isBlank()) invalid("A logged set has no exercise name.")
            if (set.exerciseType !in ExerciseType.ALL) invalid("A logged set has an unknown exercise type.")
            if (set.setNumber <= 0) invalid("A logged set has an invalid set number.")
            finiteNonNegative("set weight", set.weight)
            finiteNonNegative("set added weight", set.addedWeight)
            finiteNonNegative("set distance", set.distanceKm)
            finiteNonNegative("set body weight", set.bodyWeightKg)
            if (set.reps < 0 || set.holdDurationSeconds < 0) invalid("A logged set has a negative measurement.")
            if (set.timestamp < 0L) invalid("A logged set has an invalid timestamp.")
        }
    }

    private fun validateRoutines(snapshot: UserDataSnapshot) {
        val routines = snapshot.routines
        val ids = routines.map { it.id }
        if (ids.any { it <= 0L }) invalid("A saved routine has an invalid identifier.")
        if (ids.size != ids.toSet().size) invalid("Two saved routines share an identifier.")
        for (routine in routines) {
            if (routine.name.isBlank()) invalid("A saved routine has no name.")
            if (routine.daysPerWeek !in 0..Weekday.COUNT) invalid("A saved routine has an invalid day count.")
            if (routine.createdAt < 0L) invalid("A saved routine has an invalid timestamp.")
            if (routine.exercisesJson.isNotBlank()) {
                try {
                    val node = JsonNode.parse(routine.exercisesJson.toByteArray(Charsets.UTF_8))
                    if (node !is JsonNode.Obj && node !is JsonNode.Arr) {
                        invalid("A saved routine's exercise data is not a JSON object or array.")
                    }
                } catch (e: BackupException) {
                    invalid("A saved routine's exercise data is not valid JSON.", e)
                } catch (e: Exception) {
                    invalid("A saved routine's exercise data is not valid JSON.", e)
                }
            }
        }

        val scheduleDates = snapshot.scheduleDays.map { it.dateStart }
        if (scheduleDates.any { it <= 0L }) invalid("A scheduled day has an invalid date.")
        if (scheduleDates.size != scheduleDates.toSet().size) invalid("Two scheduled days share a date.")
        for (day in snapshot.scheduleDays) {
            if (day.routineId < 0L) invalid("A scheduled day has an invalid routine reference.")
            if (day.weekday !in 1..Weekday.COUNT) invalid("A scheduled day has an invalid weekday.")
            if (day.exerciseCount < 0) invalid("A scheduled day has a negative exercise count.")
            if (day.capturedAt < 0L) invalid("A scheduled day has an invalid timestamp.")
        }
    }

    private fun validateProgress(snapshot: UserDataSnapshot) {
        val logs = snapshot.bodyWeightLogs
        val ids = logs.map { it.id }
        if (ids.any { it <= 0L }) invalid("A weigh-in has an invalid identifier.")
        if (ids.size != ids.toSet().size) invalid("Two weigh-ins share an identifier.")
        for (log in logs) {
            if (!log.weightKg.isFinite() || log.weightKg <= 0.0) invalid("A weigh-in has an invalid weight.")
            if (log.timestamp < 0L) invalid("A weigh-in has an invalid timestamp.")
        }
    }

    private fun validateAi(snapshot: UserDataSnapshot) {
        val chatSessionIds = snapshot.chatSessions.map { it.id }
        if (chatSessionIds.any { it.isBlank() }) invalid("A conversation has no identifier.")
        if (chatSessionIds.size != chatSessionIds.toSet().size) invalid("Two conversations share an identifier.")
        for (session in snapshot.chatSessions) {
            if (session.createdAt < 0L || session.lastUpdatedAt < 0L) {
                invalid("A conversation has an invalid timestamp.")
            }
        }

        val messageIds = snapshot.chatMessages.map { it.id }
        if (messageIds.any { it.isBlank() }) invalid("An AI message has no identifier.")
        if (messageIds.size != messageIds.toSet().size) invalid("Two AI messages share an identifier.")
        val knownSessions = chatSessionIds.toSet()
        for (message in snapshot.chatMessages) {
            if (message.sessionId !in knownSessions) {
                invalid("An AI message belongs to a conversation that is not in the backup.", code = BackupErrorCode.RELATIONSHIP_ERROR)
            }
            if (message.sender !in setOf("USER", "AI")) invalid("An AI message has an unknown sender.")
            if (message.timestamp < 0L) invalid("An AI message has an invalid timestamp.")
            if (message.kind !in setOf(ChatMessageKind.TEXT, ChatMessageKind.ROUTINE_PROPOSAL)) {
                invalid("An AI message has an unknown kind.")
            }
            if (message.status !in setOf(
                    ChatMessageStatus.NONE,
                    ChatMessageStatus.PENDING,
                    ChatMessageStatus.APPROVED,
                    ChatMessageStatus.REJECTED,
                    ChatMessageStatus.EXPIRED
                )
            ) {
                invalid("An AI message has an unknown approval state.")
            }
            if (message.kind == ChatMessageKind.ROUTINE_PROPOSAL &&
                message.status == ChatMessageStatus.NONE
            ) {
                invalid("A routine proposal has no approval state.")
            }
        }

        val geminiSummaryIds = snapshot.geminiSummaries.map { it.id }
        if (geminiSummaryIds.any { it <= 0L }) invalid("A saved AI summary has an invalid identifier.")
        if (geminiSummaryIds.size != geminiSummaryIds.toSet().size) invalid("Two saved AI summaries share an identifier.")
        for (summary in snapshot.geminiSummaries) {
            if (summary.exerciseName.isBlank()) invalid("A saved AI summary has no exercise name.")
            if (summary.windowDays <= 0) invalid("A saved AI summary has an invalid window.")
            if (summary.daysLogged < 0 || summary.setCount < 0) invalid("A saved AI summary has negative counts.")
            finiteNonNegative("saved AI summary estimate", summary.bestOneRm)
            if (!summary.trendPercent.isFinite()) invalid("A saved AI summary has an invalid trend.")
            if (summary.createdAt < 0L) invalid("A saved AI summary has an invalid timestamp.")
        }

        val workoutSessionIds = snapshot.sessions.map { it.id }.toSet()
        val workoutSummaryIds = snapshot.workoutSummaries.map { it.id }
        if (workoutSummaryIds.any { it <= 0L }) invalid("A post-workout summary has an invalid identifier.")
        if (workoutSummaryIds.size != workoutSummaryIds.toSet().size) {
            invalid("Two post-workout summaries share an identifier.")
        }
        val summaryWorkouts = snapshot.workoutSummaries.map { it.workoutId }
        if (summaryWorkouts.size != summaryWorkouts.toSet().size) {
            invalid("Two post-workout summaries are attached to the same workout.")
        }
        for (summary in snapshot.workoutSummaries) {
            if (summary.workoutId !in workoutSessionIds) {
                invalid(
                    "A post-workout summary belongs to a workout that is not in the backup.",
                    code = BackupErrorCode.RELATIONSHIP_ERROR
                )
            }
            if (summary.summaryMarkdown.isBlank()) invalid("A post-workout summary has no text.")
            if (summary.previousWorkoutId != 0L && summary.previousWorkoutId !in workoutSessionIds) {
                invalid(
                    "A post-workout summary references a missing comparison workout.",
                    code = BackupErrorCode.RELATIONSHIP_ERROR
                )
            }
            if (summary.createdAt < 0L) invalid("A post-workout summary has an invalid timestamp.")
        }
    }

    fun containsEmbeddedCredentials(rawUrl: String): Boolean {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return false
        val withoutScheme = trimmed.substringAfter("://", trimmed)
        val authority = withoutScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        if ('@' in authority) return true
        if ('?' in trimmed || '#' in trimmed) return true
        val lower = trimmed.lowercase()
        return lower.contains("api_key=") ||
            lower.contains("apikey=") ||
            lower.contains("access_token=") ||
            lower.contains("token=") ||
            lower.contains("key=")
    }

    private fun finiteNonNegative(label: String, value: Double?) {
        if (value == null) return
        if (!value.isFinite() || value < 0.0) invalid("The $label is invalid.")
    }

    private fun invalid(message: String, cause: Throwable? = null, code: BackupErrorCode = BackupErrorCode.INVALID_DATA): Nothing {
        throw BackupException(message, code, cause)
    }
}
