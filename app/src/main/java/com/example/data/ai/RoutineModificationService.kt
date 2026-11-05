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

/** The persisted outcome of an approved new-routine proposal. */
data class RoutineCreationResult(
    val routineId: Long,
    val routineName: String,
    val trainingDays: Int,
    val exerciseCount: Int,
    val createdExercises: List<String>
)

/**
 * Applies exactly one user-approved routine proposal.
 *
 * The service never runs from the model: the ViewModel calls it only after the approval button.
 * The proposal signature makes the approval stale-safe, and the insert plus routine replacement
 * share one Room transaction, so a generated exercise can never exist without its approved use.
 */
class RoutineModificationService(private val db: AppDatabase) {

    /**
     * Applies exactly one approved new-routine proposal.
     *
     * The model can never call this: only the athlete's approval button reaches it. Every catalog
     * reference is re-read and every generated exercise is revalidated inside the same transaction,
     * so a stale proposal cannot save a broken plan or leave an orphan exercise.
     */
    suspend fun applyNewRoutine(
        proposal: NewRoutineProposal,
        accessEnabled: Boolean
    ): Result<RoutineCreationResult> {
        if (!accessEnabled) {
            return Result.failure(IllegalStateException("AI workout-data access is disabled."))
        }
        return runCatching {
            db.withTransaction {
                val catalog = db.exerciseDao().getAllExercisesList().toMutableList()
                val items = mutableListOf<RoutineExerciseItem>()
                val createdNames = mutableListOf<String>()

                for (day in proposal.days.sortedBy { it.weekday }) {
                    for (proposed in day.exercises) {
                        val exercise = when {
                            proposed.exerciseId > 0L -> {
                                db.exerciseDao().getExerciseById(proposed.exerciseId)
                                    ?: error(
                                        "The exercise \"${proposed.name}\" no longer exists in the catalog."
                                    )
                            }
                            proposed.generatedExercise != null -> {
                                val generated = proposed.generatedExercise
                                if (generated.instructions.isBlank()) {
                                    error("The new exercise \"${generated.name}\" has no instructions.")
                                }
                                val key = AiExerciseName.canonical(generated.name)
                                val existing = catalog.firstOrNull {
                                    AiExerciseName.canonical(it.name) == key
                                }
                                if (existing != null) {
                                    existing
                                } else {
                                    val id = db.exerciseDao().insert(generated.toExercise())
                                    if (id <= 0L) {
                                        error("The AI-generated exercise \"${generated.name}\" could not be created.")
                                    }
                                    val stored = db.exerciseDao().getExerciseById(id)
                                        ?: error("The created exercise could not be read back.")
                                    catalog.add(stored)
                                    createdNames.add(stored.name)
                                    stored
                                }
                            }
                            else -> {
                                catalog.firstOrNull {
                                    AiExerciseName.matches(it.name, proposed.name)
                                } ?: error(
                                    "The exercise \"${proposed.name}\" no longer exists in the catalog."
                                )
                            }
                        }
                        items.add(
                            RoutineExerciseItem(
                                exerciseId = exercise.id,
                                exerciseName = exercise.name,
                                exerciseType = exercise.exerciseType,
                                category = exercise.category,
                                equipment = exercise.equipment,
                                targetSets = proposed.targetSets,
                                targetReps = proposed.targetReps,
                                targetWeight = proposed.targetWeightKg,
                                targetAddedWeightKg = proposed.targetAddedWeightKg,
                                targetHoldSeconds = proposed.targetHoldSeconds,
                                restSeconds = proposed.restSeconds,
                                prescription = proposed.prescription,
                                weekday = day.weekday,
                                dayTitle = day.focus,
                                dayNote = day.note
                            )
                        )
                    }
                }
                if (items.isEmpty()) error("The proposed routine has no exercises.")

                val trainingDays = items.map { it.weekday }.distinct().size
                val saved = SavedRoutine(
                    name = proposal.routineName.take(120),
                    description = "AI Coach routine • ${items.size} exercises • " +
                        "$trainingDays training day${if (trainingDays == 1) "" else "s"}",
                    daysPerWeek = trainingDays,
                    goal = proposal.goal.take(160),
                    exercisesJson = RoutineParser.serializeExercises(items),
                    scheduleVersion = 1
                )
                val routineId = db.savedRoutineDao().insertRoutine(saved)
                if (routineId <= 0L) error("The routine could not be saved.")
                val stored = db.savedRoutineDao().getRoutineById(routineId)
                    ?: error("The saved routine could not be read back.")
                RoutineCreationResult(
                    routineId = stored.id,
                    routineName = stored.name,
                    trainingDays = trainingDays,
                    exerciseCount = items.size,
                    createdExercises = createdNames
                )
            }
        }
    }

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
