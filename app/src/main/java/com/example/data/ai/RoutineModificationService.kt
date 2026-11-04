/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * New standalone code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.data.ai

import androidx.room.withTransaction
import com.example.data.db.AppDatabase
import com.example.data.model.Exercise
import com.example.data.model.RoutineExerciseItem
import com.example.data.model.RoutineParser
import com.example.data.model.SavedRoutine

data class RoutineChangeResult(
    val routineId: Long,
    val routineName: String,
    val weekdayName: String,
    val removedExercise: String,
    val addedExercise: String,
    val generatedExerciseId: Long?
)

/**
 * Applies exactly one user-approved routine proposal.
 *
 * The service never runs from the model: the ViewModel calls it only after the approval button.
 * The proposal signature makes the approval stale-safe, and the insert plus routine replacement
 * share one Room transaction, so a generated exercise can never exist without its approved use.
 */
class RoutineModificationService(private val db: AppDatabase) {

    suspend fun apply(
        proposal: RoutineModificationProposal,
        accessEnabled: Boolean
    ): Result<RoutineChangeResult> {
        if (!accessEnabled) {
            return Result.failure(
                IllegalStateException("AI workout-data access is disabled.")
            )
        }
        return runCatching {
            db.withTransaction {
                val routine = db.savedRoutineDao().getRoutineById(proposal.routineId)
                    ?: error("The routine no longer exists.")
                if (RoutineModificationProposal.signatureOf(routine) != proposal.routineSignature) {
                    error("The routine changed after this proposal was created.")
                }
                val exercises = db.exerciseDao().getAllExercisesList()
                val days = RoutineParser.parseWeek(routine.exercisesJson, exercises)
                val day = days.firstOrNull { it.weekday == proposal.weekday }
                    ?: error("The proposed weekday is no longer in the routine.")
                val removed = day.exercises.firstOrNull {
                    it.exerciseName.trim().equals(proposal.removeExerciseName, ignoreCase = true)
                } ?: error("The exercise to remove is no longer in that day.")

                val replacementExercise: Exercise = when {
                    proposal.replacementExerciseId != null -> {
                        db.exerciseDao().getExerciseById(proposal.replacementExerciseId)
                            ?: error("The replacement exercise no longer exists.")
                    }
                    proposal.generatedExercise != null -> {
                        val generated = proposal.generatedExercise
                        if (db.exerciseDao().getExerciseByName(generated.name) != null) {
                            error("An exercise with that name now exists; approve an existing one instead.")
                        }
                        val id = db.exerciseDao().insert(generated.toExercise())
                        if (id <= 0L) error("The AI-generated exercise could not be created.")
                        db.exerciseDao().getExerciseById(id)
                            ?: error("The AI-generated exercise could not be read back.")
                    }
                    else -> error("The proposal has no replacement exercise.")
                }

                val replacementItem = RoutineExerciseItem(
                    exerciseId = replacementExercise.id,
                    exerciseName = replacementExercise.name,
                    exerciseType = replacementExercise.exerciseType,
                    category = replacementExercise.category,
                    equipment = replacementExercise.equipment,
                    // Keep the athlete's prescription and rest default from the slot being replaced.
                    targetSets = removed.targetSets,
                    targetReps = removed.targetReps,
                    targetWeight = removed.targetWeight,
                    targetAddedWeightKg = removed.targetAddedWeightKg,
                    targetHoldSeconds = removed.targetHoldSeconds,
                    restSeconds = removed.restSeconds,
                    prescription = removed.prescription,
                    weekday = proposal.weekday,
                    dayTitle = removed.dayTitle,
                    dayNote = removed.dayNote
                )

                val updatedItems = days.flatMap { routineDay ->
                    routineDay.exercises.map { item ->
                        if (routineDay.weekday == proposal.weekday &&
                            item.exerciseName.trim().equals(removed.exerciseName, ignoreCase = true)
                        ) {
                            replacementItem
                        } else {
                            item
                        }
                    }
                }
                val updatedJson = RoutineParser.serializeExercises(updatedItems)
                db.savedRoutineDao().insertRoutine(
                    routine.copy(
                        exercisesJson = updatedJson,
                        daysPerWeek = updatedItems.map { it.weekday }.distinct().size,
                        scheduleVersion = 1
                    )
                )

                val verified = db.savedRoutineDao().getRoutineById(routine.id)
                    ?: error("The routine could not be read after the change.")
                if (verified.exercisesJson != updatedJson) {
                    error("The routine change did not persist correctly.")
                }
                RoutineChangeResult(
                    routineId = routine.id,
                    routineName = routine.name,
                    weekdayName = day.dayName,
                    removedExercise = removed.exerciseName,
                    addedExercise = replacementExercise.name,
                    generatedExerciseId = proposal.generatedExercise?.let { replacementExercise.id }
                )
            }
        }
    }
}
