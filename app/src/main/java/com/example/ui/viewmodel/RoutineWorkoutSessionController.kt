/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.ui.viewmodel

import com.example.data.model.ActiveRoutineWorkoutState
import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import com.example.data.model.LocalDateKey
import com.example.data.model.LoggedSet
import com.example.data.model.RoutineExerciseItem
import com.example.data.model.RoutineParser
import com.example.data.model.SavedRoutine
import com.example.data.model.Weekday
import com.example.data.model.WorkoutSession
import com.example.data.model.WorkoutStreak
import com.example.data.repository.FitnessRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the dedicated routine workout session: prescription resolution, initial sets, mutations and
 * finish/cancel persistence. The quick-workout and routine sessions remain separate state holders.
 */
class RoutineWorkoutSessionController(
    private val repository: FitnessRepository,
    private val scope: CoroutineScope,
    private val allExercises: () -> List<Exercise>,
    private val weekStartsOnMonday: () -> Boolean,
    private val currentBodyWeightKg: () -> Double,
    private val resolveBodyWeightKg: suspend () -> Double,
    private val stopRestTimer: () -> Unit,
    private val clearQuickWorkout: () -> Unit,
    private val clearWorkoutDraft: () -> Unit,
    private val refreshWidgets: () -> Unit,
    private val onSessionSaved: (Long) -> Unit
) {
    private val _activeRoutineWorkout = MutableStateFlow<ActiveRoutineWorkoutState?>(null)
    val activeRoutineWorkout: StateFlow<ActiveRoutineWorkoutState?> =
        _activeRoutineWorkout.asStateFlow()

    private fun withRoutineAddedWeight(exercise: Exercise, item: RoutineExerciseItem): Exercise {
        if (item.targetAddedWeightKg <= 0.0) return exercise
        return exercise.copy(exerciseType = ExerciseType.ADDED_WEIGHT)
    }

    fun start(routine: SavedRoutine, targetWeekday: Int? = null) {
        clearQuickWorkout()

        val allEx = allExercises()
        val days = RoutineParser.parseDays(routine.exercisesJson, allEx, weekStartsOnMonday())
        val targetDay = if (targetWeekday != null && days.isNotEmpty()) {
            days.firstOrNull { it.weekday == targetWeekday && it.exercises.isNotEmpty() }
                ?: days.firstOrNull { it.exercises.isNotEmpty() }
        } else if (days.isNotEmpty()) {
            days.firstOrNull { it.exercises.isNotEmpty() }
        } else {
            null
        }

        val routineItems = targetDay?.exercises
            ?: RoutineParser.parseExercises(routine.exercisesJson, allEx, weekStartsOnMonday())
        val weekday = targetDay?.weekday ?: Weekday.MONDAY
        val dayTitle = targetDay?.dayTitle.orEmpty()
        val dayNote = targetDay?.note.orEmpty()

        val exMap = allEx.associateBy { it.id }
        val exByName = allEx.associateBy { it.name.trim().lowercase() }
        val resolvedExercises = mutableListOf<Exercise>()
        for (item in routineItems) {
            if (RoutineParser.isCoachNoteLine(item.exerciseName)) continue
            val byId = exMap[item.exerciseId]
            val byName = exByName[item.exerciseName.trim().lowercase()]
            val found = byId ?: byName ?: RoutineParser.findMatchingExercise(item.exerciseName, exByName.values)
            if (found != null && !resolvedExercises.contains(found)) {
                resolvedExercises.add(withRoutineAddedWeight(found, item))
            } else if (found == null && item.exerciseName.isNotBlank()) {
                val tempEx = Exercise(
                    id = if (item.exerciseId > 0) item.exerciseId else ((System.currentTimeMillis() % 1000000) + resolvedExercises.size),
                    name = item.exerciseName,
                    category = item.category.ifBlank { "Calisthenics" },
                    exerciseType = item.exerciseType,
                    equipment = item.equipment.ifBlank { "Bodyweight" },
                    description = "Prescribed routine exercise"
                )
                resolvedExercises.add(withRoutineAddedWeight(tempEx, item))
            }
        }

        if (resolvedExercises.isEmpty() && routine.exercisesJson.isNotBlank()) {
            for (line in routine.exercisesJson.lines()) {
                val clean = line.substringBefore("(").replace("*", "").removePrefix("-").trim().lowercase()
                val matched = exByName[clean]
                if (matched != null && !resolvedExercises.contains(matched)) {
                    resolvedExercises.add(matched)
                }
            }
        }

        val initialSets = mutableListOf<LoggedSet>()
        val now = System.currentTimeMillis()
        resolvedExercises.forEachIndexed { exIndex, ex ->
            val item = routineItems.getOrNull(exIndex)
            val numSets = item?.targetSets?.coerceIn(1, 10) ?: 3
            val reps = item?.targetReps ?: 8
            val hold = item?.targetHoldSeconds ?: 0
            val weight = item?.targetWeight ?: 0.0
            val addedTarget = item?.targetAddedWeightKg ?: 0.0

            for (setNum in 1..numSets) {
                initialSets.add(
                    LoggedSet(
                        sessionId = 0L,
                        exerciseId = ex.id,
                        exerciseName = ex.name,
                        exerciseType = ex.exerciseType,
                        setNumber = setNum,
                        weight = if (ex.exerciseType == ExerciseType.STANDARD_WEIGHT) weight else 0.0,
                        addedWeight = if (ex.exerciseType == ExerciseType.ADDED_WEIGHT) {
                            if (addedTarget > 0.0) addedTarget else weight
                        } else 0.0,
                        reps = reps,
                        holdDurationSeconds = hold,
                        isCompleted = false,
                        timestamp = now,
                        bodyWeightKg = currentBodyWeightKg()
                    )
                )
            }
        }

        _activeRoutineWorkout.value = ActiveRoutineWorkoutState(
            routineId = routine.id,
            routineName = routine.name,
            routineGoal = routine.goal,
            startTime = now,
            exercises = resolvedExercises,
            routineItems = routineItems,
            sets = initialSets,
            weekday = weekday,
            dayTitle = dayTitle,
            dayNote = dayNote,
            plannedDate = WorkoutStreak.dateForWeekdayInWeek(
                weekday = weekday,
                reference = now,
                weekStartsOnMonday = weekStartsOnMonday()
            )
        ).let { state ->
            state.copy(plannedDateKey = LocalDateKey.keyOf(state.plannedDate))
        }
    }

    fun addSet(exercise: Exercise) {
        val current = _activeRoutineWorkout.value ?: return
        val existingForEx = current.sets.filter { it.exerciseId == exercise.id }
        val nextSetNum = existingForEx.size + 1
        val lastSet = existingForEx.lastOrNull()
        val newSet = LoggedSet(
            sessionId = 0L,
            exerciseId = exercise.id,
            exerciseName = exercise.name,
            exerciseType = exercise.exerciseType,
            setNumber = nextSetNum,
            weight = lastSet?.weight ?: 0.0,
            addedWeight = lastSet?.addedWeight ?: 0.0,
            reps = lastSet?.reps ?: 8,
            holdDurationSeconds = lastSet?.holdDurationSeconds ?: 0,
            isCompleted = false,
            timestamp = System.currentTimeMillis(),
            bodyWeightKg = currentBodyWeightKg()
        )
        _activeRoutineWorkout.value = current.copy(sets = current.sets + newSet)
    }

    fun updateSet(index: Int, updated: LoggedSet) {
        val current = _activeRoutineWorkout.value ?: return
        if (index !in current.sets.indices) return
        val mutableList = current.sets.toMutableList()
        mutableList[index] = updated
        _activeRoutineWorkout.value = current.copy(sets = mutableList)
    }

    fun deleteSet(index: Int) {
        val current = _activeRoutineWorkout.value ?: return
        if (index !in current.sets.indices) return
        val mutableList = current.sets.toMutableList()
        val deletedSet = mutableList.removeAt(index)
        var currentSetNum = 1
        val reindexed = mutableList.map { set ->
            if (set.exerciseId == deletedSet.exerciseId) {
                set.copy(setNumber = currentSetNum++)
            } else {
                set
            }
        }
        _activeRoutineWorkout.value = current.copy(sets = reindexed)
    }

    fun stop() {
        stopRestTimer()
        _activeRoutineWorkout.value = null
        clearQuickWorkout()
        clearWorkoutDraft()
    }

    fun finish(notes: String) {
        val current = _activeRoutineWorkout.value ?: return
        stopRestTimer()
        scope.launch(Dispatchers.IO) {
            val savedAt = System.currentTimeMillis()
            val finalBodyWeightKg = resolveBodyWeightKg()
            val setsToSave = current.sets
                .filter { it.isCompleted }
                .mapIndexed { index, set ->
                    set.copy(
                        timestamp = savedAt + index,
                        bodyWeightKg = finalBodyWeightKg.takeIf { it > 0.0 } ?: set.bodyWeightKg
                    )
                }

            var savedSessionId: Long? = null
            if (setsToSave.isNotEmpty()) {
                val sessionTitle = if (!current.dayTitle.isNullOrBlank() &&
                    !current.routineName.contains(current.dayTitle, ignoreCase = true)
                ) {
                    "${current.routineName} (${current.dayTitle})"
                } else {
                    current.routineName
                }
                val session = WorkoutSession(
                    title = sessionTitle,
                    startTime = current.startTime,
                    endTime = System.currentTimeMillis(),
                    notes = notes.trim(),
                    routineId = current.routineId,
                    routineWeekday = current.weekday ?: 0,
                    plannedDate = current.plannedDate,
                    plannedDateKey = current.plannedDateKey.takeIf { it > 0L }
                        ?: LocalDateKey.keyOf(current.plannedDate)
                )
                val sessionId = repository.insertSession(session)
                repository.insertSets(setsToSave.map { it.copy(sessionId = sessionId) })
                savedSessionId = sessionId
            }

            withContext(Dispatchers.Main) {
                _activeRoutineWorkout.value = null
                clearQuickWorkout()
            }
            clearWorkoutDraft()
            refreshWidgets()
            savedSessionId?.let(onSessionSaved)
        }
    }

    fun clearLiveState() {
        _activeRoutineWorkout.value = null
    }

    fun restore(state: ActiveRoutineWorkoutState) {
        _activeRoutineWorkout.value = state
    }

    fun rename(title: String) {
        _activeRoutineWorkout.value?.let { routine ->
            _activeRoutineWorkout.value = routine.copy(routineName = title)
        }
    }
}
