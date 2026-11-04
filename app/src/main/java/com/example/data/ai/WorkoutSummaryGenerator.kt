/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * New standalone code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.data.ai

import com.example.data.model.ActivityHeatmap
import com.example.data.model.LoggedSet
import com.example.data.model.SessionWithSets
import com.example.data.model.WorkoutAiSummary
import com.example.data.repository.FitnessRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class WorkoutComparisonMatch { EXACT, PARTIAL, NONE }

data class WorkoutComparison(
    val current: SessionWithSets,
    val previous: SessionWithSets?,
    val match: WorkoutComparisonMatch
)

/**
 * Selects the previous session that is actually comparable.
 *
 * Routine sessions use the stable routine id and weekday link. Quick workouts have no stable
 * template, so a previous session is accepted only when its title matches on the same weekday, or
 * when it shares exercise content with the current session. Every fallback is marked PARTIAL so the
 * generated summary can disclose the limitation instead of presenting it as an exact repeat.
 */
object WorkoutComparisonSelector {

    private const val GENERIC_QUICK_TITLES = "quick workout,workout,training session"

    fun select(current: SessionWithSets, all: List<SessionWithSets>): WorkoutComparison {
        val currentSets = current.sets.filter { it.isCompleted }
        if (currentSets.isEmpty()) return WorkoutComparison(current, null, WorkoutComparisonMatch.NONE)
        val candidates = all.filter {
            it.session.id != current.session.id &&
                it.session.startTime < current.session.startTime &&
                it.sets.any { set -> set.isCompleted }
        }
        val currentWeekday = ActivityHeatmap.isoDayOfWeek(current.session.startTime)

        if (current.session.routineId > 0L && current.session.routineWeekday > 0) {
            val exact = candidates
                .filter {
                    it.session.routineId == current.session.routineId &&
                        it.session.routineWeekday == current.session.routineWeekday
                }
                .maxByOrNull { it.session.startTime }
            return WorkoutComparison(
                current,
                exact,
                if (exact != null) WorkoutComparisonMatch.EXACT else WorkoutComparisonMatch.NONE
            )
        }

        val title = current.session.title.trim().lowercase()
        val nonGenericTitle = title.isNotBlank() && title !in GENERIC_QUICK_TITLES
        if (nonGenericTitle) {
            val sameTitleSameWeekday = candidates
                .filter {
                    it.session.title.trim().equals(current.session.title.trim(), ignoreCase = true) &&
                        ActivityHeatmap.isoDayOfWeek(it.session.startTime) == currentWeekday
                }
                .maxByOrNull { it.session.startTime }
            if (sameTitleSameWeekday != null) {
                return WorkoutComparison(current, sameTitleSameWeekday, WorkoutComparisonMatch.PARTIAL)
            }
            val sameTitleAnyWeekday = candidates
                .filter {
                    it.session.title.trim().equals(current.session.title.trim(), ignoreCase = true)
                }
                .maxByOrNull { it.session.startTime }
            if (sameTitleAnyWeekday != null) {
                return WorkoutComparison(current, sameTitleAnyWeekday, WorkoutComparisonMatch.PARTIAL)
            }
        }

        val currentNames = currentSets.map { normalize(it.exerciseName) }.toSet()
        val overlap = candidates
            .map { candidate ->
                val names = candidate.sets
                    .filter { it.isCompleted }
                    .map { normalize(it.exerciseName) }
                    .toSet()
                candidate to names.intersect(currentNames).size
            }
            .filter { (_, shared) -> shared > 0 }
            .sortedWith(
                compareByDescending<Pair<SessionWithSets, Int>> {
                    ActivityHeatmap.isoDayOfWeek(it.first.session.startTime) == currentWeekday
                }
                    .thenByDescending { it.second }
                    .thenByDescending { it.first.session.startTime }
            )
            .firstOrNull()
        return if (overlap == null) {
            WorkoutComparison(current, null, WorkoutComparisonMatch.NONE)
        } else {
            WorkoutComparison(current, overlap.first, WorkoutComparisonMatch.PARTIAL)
        }
    }

    private fun normalize(value: String): String = value.trim().lowercase()
}

/**
 * Generates and persists the immutable post-workout comparison.
 *
 * The workout is already saved before this class is called. A failure here can never undo or block
 * the workout; it only produces no summary.
 */
class WorkoutSummaryGenerator(
    private val repository: FitnessRepository,
    private val aiService: AiService
) {

    suspend fun generate(workoutId: Long): Result<WorkoutAiSummary> {
        return try {
            val existing = repository.getWorkoutAiSummary(workoutId)
            if (existing != null) {
                Result.success(existing)
            } else {
                val current = repository.getSessionWithSetsOnce(workoutId)
                val config = aiService.activeConfiguration()
                when {
                    current == null ->
                        Result.failure(IllegalStateException("The workout could not be read."))
                    config.model.isBlank() ->
                        Result.failure(IllegalStateException("No AI model is selected."))
                    else -> {
                        val comparison = WorkoutComparisonSelector.select(
                            current,
                            repository.getAllSessionsWithSetsOnce()
                        )
                        val markdown = aiService.completeText(
                            prompt = buildPrompt(comparison),
                            systemInstruction = SYSTEM_INSTRUCTION,
                            provider = config.provider,
                            apiKeyFallback = config.apiKey
                        ).trim()
                        if (markdown.isBlank()) {
                            Result.failure(
                                IllegalStateException("The AI returned an empty summary.")
                            )
                        } else {
                            val summary = WorkoutAiSummary(
                                workoutId = workoutId,
                                summaryMarkdown = markdown,
                                provider = config.provider.storageKey,
                                model = config.model,
                                previousWorkoutId = comparison.previous?.session?.id ?: 0L,
                                createdAt = System.currentTimeMillis()
                            )
                            val id = repository.insertWorkoutAiSummary(summary)
                            if (id < 0L) {
                                // Another generation won the race. Never replace the existing
                                // immutable summary.
                                repository.getWorkoutAiSummary(workoutId)
                                    ?.let { Result.success(it) }
                                    ?: Result.failure(
                                        IllegalStateException("The summary could not be saved.")
                                    )
                            } else {
                                Result.success(summary.copy(id = id))
                            }
                        }
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun buildPrompt(comparison: WorkoutComparison): String = buildString {
        appendLine("Compare this completed IronPulse workout with the previous comparable session.")
        appendLine("Use only the data below. Do not invent records, dates, personal records or percentages.")
        appendLine("If the comparison is partial or absent, state that clearly.")
        appendLine()
        appendWorkout("CURRENT WORKOUT", comparison.current)
        appendLine()
        if (comparison.previous == null) {
            appendLine("PREVIOUS COMPARABLE WORKOUT: NONE FOUND")
            appendLine("The current session has no suitable historical comparison.")
            appendLine(
                "Do not invent a previous session or an improvement. Summarize only the current " +
                    "session and state plainly that no comparable history is available."
            )
        } else {
            appendLine("COMPARISON QUALITY: ${comparison.match.name}")
            if (comparison.match == WorkoutComparisonMatch.PARTIAL) {
                appendLine("This is a partial match; explain the limitation in the summary.")
            }
            appendWorkout("PREVIOUS WORKOUT", comparison.previous)
        }
        appendLine()
        appendLine(
            "Write a concise Markdown summary with: an overall assessment, exercises that improved, " +
                "declined or stayed similar with the actual numbers, observations and limitations, and " +
                "a short conclusion. If only some exercises appear in both sessions, say which ones " +
                "could not be compared. Only claim a personal record if the supplied history establishes one."
        )
    }

    private fun StringBuilder.appendWorkout(label: String, wrapper: SessionWithSets) {
        val session = wrapper.session
        appendLine("$label")
        appendLine("title: ${session.title}")
        appendLine(
            "date: " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                .format(Date(session.startTime))
        )
        appendLine("weekday: ${weekdayName(session.startTime)}")
        appendLine("durationMinutes: ${((session.endTime - session.startTime).coerceAtLeast(0L) / 60_000L)}")
        appendLine("notes: ${session.notes.take(500).ifBlank { "(none)" }}")
        val sets = wrapper.sets.filter { it.isCompleted }.take(MAX_SETS)
        appendLine("completedSets: ${sets.size}")
        for (set in sets) {
            appendLine(set.promptLine())
        }
        if (wrapper.sets.count { it.isCompleted } > MAX_SETS) {
            appendLine("note: additional sets were omitted to keep the context bounded.")
        }
    }

    private fun LoggedSet.promptLine(): String = buildString {
        append("- ")
        append(exerciseName)
        append(" | set ").append(setNumber)
        append(" | ").append(exerciseType)
        append(" | reps ").append(reps)
        append(" | weightKg ").append(weight)
        append(" | addedWeightKg ").append(addedWeight)
        append(" | holdSeconds ").append(holdDurationSeconds)
        append(" | cardioMinutes ").append(
            if (exerciseType == com.example.data.model.ExerciseType.CARDIO) {
                holdDurationSeconds / 60.0
            } else {
                0.0
            }
        )
        append(" | distanceKm ").append(distanceKm)
        append(" | bodyWeightKg ").append(bodyWeightKg)
    }

    private fun weekdayName(epochMillis: Long): String = when (ActivityHeatmap.isoDayOfWeek(epochMillis)) {
        1 -> "Monday"
        2 -> "Tuesday"
        3 -> "Wednesday"
        4 -> "Thursday"
        5 -> "Friday"
        6 -> "Saturday"
        else -> "Sunday"
    }

    companion object {
        private const val MAX_SETS = 80
        const val SYSTEM_INSTRUCTION =
            "You are IronPulse AI, a factual strength-training analyst. You compare only the " +
                "supplied sessions. You never invent data, never claim a personal record without " +
                "explicit evidence, and you distinguish observed changes from interpretation. Use " +
                "Markdown headings, short paragraphs, bullet lists and tables where useful. Do not " +
                "emit raw HTML."
    }
}
