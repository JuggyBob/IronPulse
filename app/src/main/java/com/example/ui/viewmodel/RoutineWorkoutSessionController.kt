/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.ui.viewmodel

import com.example.data.model.ActiveRoutineWorkoutState
import com.example.data.model.DropSetRules
import com.example.data.model.Exercise
import com.example.data.model.ExerciseField
import com.example.data.model.ExerciseType
import com.example.data.model.LocalDateKey
import com.example.data.model.LoggedSet
import com.example.data.model.RemovedActiveExercise
import com.example.data.model.RoutineExerciseItem
import com.example.data.model.RoutineParser
import com.example.data.model.sanitizedFor
import com.example.data.model.SavedRoutine
import com.example.data.model.WorkoutSession
import com.example.data.model.WorkoutStreak
import com.example.data.repository.FitnessRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Owns the dedicated routine workout session: prescription resolution, initial sets, drop-set
 * children, exercise add/remove/reorder and finish/cancel persistence.
 *
 * Reordering a routine workout is the one exercise operation that also edits the saved routine.
 * Adding or removing an exercise is session-only. The distinction is enforced here, not in the UI.
 */
class RoutineWorkoutSessionController(
    private val repository: FitnessRepository,
    private val scope: CoroutineScope,
    private val allExercises: () -> List<Exercise>,
    private val weekStartsOnMonday: () -> Boolean,
    private val currentBodyWeightKg: () -> Double,
    private val resolveBodyWeightKg: suspend () -> Double,
    private val defaultRestForExercise: (Long) -> Int,
    private val stopRestTimer: () -> Unit,
    private val clearQuickWorkout: () -> Unit,
    private val clearWorkoutDraft: () -> Unit,
    private val refreshWidgets: () -> Unit,
    private val onSessionSaved: (Long) -> Unit
) {
    private val _activeRoutineWorkout = MutableStateFlow<ActiveRoutineWorkoutState?>(null)
    val activeRoutineWorkout: StateFlow<ActiveRoutineWorkoutState?> =
        _activeRoutineWorkout.asStateFlow()

    private val routinePersistMutex = Mutex()

    fun start(
        routine: SavedRoutine,
        targetWeekday: Int? = null,
        scheduleId: Long = 0L,
        scheduleGoal: String = "",
        plannedDate: Long = 0L
    ) {
        clearQuickWorkout()

        val allEx = allExercises()
        val parsedItems = RoutineParser.parseSingleWorkout(routine.exercisesJson, allEx)
        val weekday = targetWeekday?.takeIf { it in 1..7 } ?: 0
        val dayTitle = routine.name
        val dayNote = ""

        val exMap = allEx.associateBy { it.id }
        val exByName = allEx.associateBy { it.name.trim().lowercase() }
        val resolvedExercises = mutableListOf<Exercise>()
        val resolvedItems = mutableListOf<RoutineExerciseItem>()
        for (item in parsedItems) {
            if (RoutineParser.isCoachNoteLine(item.exerciseName)) continue
            val byId = exMap[item.exerciseId]
            val byName = exByName[item.exerciseName.trim().lowercase()]
            val found = byId ?: byName ?: RoutineParser.findMatchingExercise(item.exerciseName, exByName.values)
            val exercise = when {
                found != null && resolvedExercises.none { it.id == found.id } -> found
                found == null && item.exerciseName.isNotBlank() -> Exercise(
                    id = if (item.exerciseId > 0) item.exerciseId else ((System.currentTimeMillis() % 1000000) + resolvedExercises.size),
                    name = item.exerciseName,
                    category = item.category.ifBlank { "Calisthenics" },
                    exerciseType = item.exerciseType,
                    equipment = item.equipment.ifBlank { "Bodyweight" },
                    description = "Prescribed routine exercise"
                )
                else -> null
            } ?: continue
            resolvedExercises.add(exercise)
            resolvedItems.add(item)
        }

        // A routine saved before the exercise's fields changed can still carry targets the
        // exercise no longer displays. Clear them for this session and rebuild the prescription;
        // the saved routine itself is not rewritten.
        val sanitizedItems = resolvedItems.mapIndexed { index, item ->
            item.sanitizedFor(resolvedExercises[index].exerciseFields)
        }
        resolvedItems.clear()
        resolvedItems.addAll(sanitizedItems)

        if (resolvedExercises.isEmpty() && routine.exercisesJson.isNotBlank()) {
            for (line in routine.exercisesJson.lines()) {
                val clean = line.substringBefore("(").replace("*", "").removePrefix("-").trim().lowercase()
                val matched = exByName[clean]
                if (matched != null && resolvedExercises.none { it.id == matched.id }) {
                    resolvedExercises.add(matched)
                    resolvedItems.add(defaultRoutineItem(matched, weekday, dayTitle))
                }
            }
        }

        val initialSets = mutableListOf<LoggedSet>()
        val now = System.currentTimeMillis()
        resolvedExercises.forEachIndexed { exIndex, ex ->
            val item = resolvedItems.getOrNull(exIndex)
            val numSets = item?.targetSets?.coerceIn(1, 10) ?: 3
            val fields = ex.exerciseFields
            val dropsForExercise = item?.dropSets.orEmpty()
            for (setNum in 1..numSets) {
                val main = item?.defaultForMainSet(setNum)
                val mainUid = LoggedSet.newUid()
                val mainWeight = if (ExerciseField.WEIGHT in fields) main?.targetWeight ?: 0.0 else 0.0
                val mainAdded = if (ExerciseField.ADDED_WEIGHT in fields) {
                    main?.targetAddedWeightKg ?: 0.0
                } else 0.0
                val mainHold = if (
                    ExerciseField.HOLD_SECONDS in fields || ExerciseField.TIME_SECONDS in fields
                ) {
                    main?.targetHoldSeconds ?: 0
                } else 0
                initialSets.add(
                    LoggedSet(
                        sessionId = 0L,
                        exerciseId = ex.id,
                        exerciseName = ex.name,
                        exerciseType = ex.exerciseType,
                        setNumber = setNum,
                        weight = mainWeight,
                        addedWeight = mainAdded,
                        reps = if (ExerciseField.REPS in fields) main?.targetReps ?: 0 else 0,
                        holdDurationSeconds = mainHold,
                        distanceKm = if (ExerciseField.DISTANCE_KM in fields) {
                            main?.targetDistanceKm ?: 0.0
                        } else 0.0,
                        isCompleted = false,
                        timestamp = now,
                        bodyWeightKg = currentBodyWeightKg(),
                        restSecondsOverride = main?.restSeconds?.takeIf { it > 0 }
                            ?: defaultRestForExercise(ex.id).takeIf { it > 0 },
                        setUid = mainUid
                    )
                )
                dropsForExercise
                    .filter { it.mainSetIndex == setNum }
                    .sortedBy { it.dropIndex }
                    .forEach { drop ->
                        initialSets.add(
                            LoggedSet(
                                sessionId = 0L,
                                exerciseId = ex.id,
                                exerciseName = ex.name,
                                exerciseType = ex.exerciseType,
                                setNumber = setNum,
                                weight = if (ExerciseField.WEIGHT in fields) {
                                    drop.targetWeight.takeIf { it > 0.0 } ?: mainWeight
                                } else 0.0,
                                addedWeight = if (ExerciseField.ADDED_WEIGHT in fields) {
                                    drop.targetAddedWeightKg.takeIf { it > 0.0 } ?: mainAdded
                                } else 0.0,
                                reps = if (ExerciseField.REPS in fields) {
                                    drop.targetReps.takeIf { it > 0 } ?: (main?.targetReps ?: 0)
                                } else 0,
                                holdDurationSeconds = if (
                                    ExerciseField.HOLD_SECONDS in fields ||
                                    ExerciseField.TIME_SECONDS in fields
                                ) {
                                    drop.targetHoldSeconds.takeIf { it > 0 } ?: mainHold
                                } else 0,
                                distanceKm = if (ExerciseField.DISTANCE_KM in fields) {
                                    drop.targetDistanceKm.takeIf { it > 0.0 }
                                        ?: (main?.targetDistanceKm ?: 0.0)
                                } else 0.0,
                                isCompleted = false,
                                timestamp = now,
                                bodyWeightKg = currentBodyWeightKg(),
                                setUid = LoggedSet.newUid(),
                                parentSetUid = mainUid,
                                dropIndex = drop.dropIndex.coerceAtLeast(1),
                                restSecondsOverride = drop.restSeconds.takeIf { it > 0 }
                            )
                        )
                    }
            }
        }

        val resolvedPlannedDate = if (plannedDate > 0L) {
            plannedDate
        } else if (scheduleId > 0L && weekday in 1..7) {
            WorkoutStreak.dateForWeekdayInWeek(
                weekday = weekday,
                reference = now,
                weekStartsOnMonday = weekStartsOnMonday()
            )
        } else {
            0L
        }
        _activeRoutineWorkout.value = ActiveRoutineWorkoutState(
            routineId = routine.id,
            routineName = routine.name,
            routineGoal = scheduleGoal.ifBlank { routine.goal },
            startTime = now,
            exercises = resolvedExercises,
            routineItems = resolvedItems,
            sets = initialSets,
            weekday = weekday,
            dayTitle = dayTitle,
            dayNote = dayNote,
            plannedDate = resolvedPlannedDate,
            scheduleId = scheduleId,
            scheduleGoal = scheduleGoal
        ).let { state ->
            state.copy(
                plannedDateKey = if (state.plannedDate > 0L) {
                    LocalDateKey.keyOf(state.plannedDate)
                } else {
                    0L
                }
            )
        }
    }

    fun addSet(exercise: Exercise) {
        val current = _activeRoutineWorkout.value ?: return
        val normalized = DropSetRules.normalize(current.sets)
        val existingForEx = normalized.filter { it.exerciseId == exercise.id && !it.isDropSet }
        val nextSetNum = (existingForEx.maxOfOrNull { it.setNumber } ?: 0) + 1
        val lastSet = existingForEx.maxByOrNull { it.setNumber }
        // The routine's own rest wins over the exercise default, and a rest the athlete changed
        // on the previous set is inherited first.
        val routineRest = current.routineItems
            .firstOrNull { it.exerciseId == exercise.id }
            ?.restSeconds
            ?.takeIf { it > 0 }
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
            bodyWeightKg = currentBodyWeightKg(),
            restSecondsOverride = lastSet?.restSecondsOverride
                ?: routineRest
                ?: defaultRestForExercise(exercise.id).takeIf { it > 0 },
            setUid = LoggedSet.newUid()
        )
        _activeRoutineWorkout.value = current.copy(
            sets = DropSetRules.renumberExerciseSets(normalized + newSet, exercise.id)
        )
    }

    /** Adds one direct child under an existing main set. Never nests another level. */
    fun addDropSet(parent: LoggedSet) {
        val current = _activeRoutineWorkout.value ?: return
        val normalized = DropSetRules.normalize(current.sets)
        val parentSet = normalized.firstOrNull {
            it.setUid == parent.setUid && !it.isDropSet
        } ?: return
        val siblings = DropSetRules.childrenOf(normalized, parentSet.setUid)
        val source = siblings.lastOrNull() ?: parentSet
        val newChild = LoggedSet(
            sessionId = 0L,
            exerciseId = parentSet.exerciseId,
            exerciseName = parentSet.exerciseName,
            exerciseType = parentSet.exerciseType,
            setNumber = parentSet.setNumber,
            weight = source.weight,
            addedWeight = source.addedWeight,
            reps = source.reps,
            holdDurationSeconds = source.holdDurationSeconds,
            distanceKm = source.distanceKm,
            isCompleted = false,
            timestamp = System.currentTimeMillis(),
            bodyWeightKg = currentBodyWeightKg(),
            isWarmup = parentSet.isWarmup,
            restSecondsOverride = source.restSecondsOverride,
            setUid = LoggedSet.newUid(),
            parentSetUid = parentSet.setUid,
            dropIndex = (siblings.maxOfOrNull { it.dropIndex } ?: 0) + 1
        )
        _activeRoutineWorkout.value = current.copy(sets = normalized + newChild)
    }

    fun updateSet(index: Int, updated: LoggedSet) {
        val current = _activeRoutineWorkout.value ?: return
        if (index !in current.sets.indices) return
        val mutableList = current.sets.toMutableList()
        val existing = mutableList[index]
        mutableList[index] = updated.copy(
            setUid = existing.setUid,
            parentSetUid = existing.parentSetUid,
            dropIndex = existing.dropIndex
        )
        _activeRoutineWorkout.value = current.copy(sets = mutableList)
    }

    /** Updates a set by UID, then by row id, then by the old logical key. */
    fun updateSet(setToUpdate: LoggedSet, updated: LoggedSet) {
        val current = _activeRoutineWorkout.value ?: return
        val index = current.sets.indexOfFirst { existing -> sameSet(existing, setToUpdate) }
        if (index >= 0) updateSet(index, updated)
    }

    fun toggleSetCompletion(setToUpdate: LoggedSet, completed: Boolean) {
        val current = _activeRoutineWorkout.value ?: return
        val normalized = DropSetRules.normalize(current.sets)
        val existing = normalized.firstOrNull { sameSet(it, setToUpdate) } ?: return
        _activeRoutineWorkout.value = current.copy(
            sets = DropSetRules.toggleCompletion(normalized, existing.setUid, completed)
        )
    }

    fun deleteSet(setToDelete: LoggedSet) {
        val current = _activeRoutineWorkout.value ?: return
        val normalized = DropSetRules.normalize(current.sets)
        val existing = normalized.firstOrNull { sameSet(it, setToDelete) } ?: return
        _activeRoutineWorkout.value = current.copy(
            sets = DropSetRules.removeSet(normalized, existing.setUid)
        )
    }

    fun deleteSet(index: Int) {
        val current = _activeRoutineWorkout.value ?: return
        if (index !in current.sets.indices) return
        deleteSet(current.sets[index])
    }

    fun addExercise(exercise: Exercise) {
        val current = _activeRoutineWorkout.value ?: return
        if (current.exercises.any { it.id == exercise.id }) return
        val newIndex = current.exercises.size
        val updatedExercises = current.exercises + exercise
        val updatedItems = current.routineItems.toMutableList().apply {
            // Keep the two lists parallel even for a session-only addition.
            while (size < newIndex) {
                add(defaultRoutineItem(updatedExercises[size], current.weekday ?: 0, current.dayTitle.orEmpty()))
            }
            add(defaultRoutineItem(exercise, current.weekday ?: 0, current.dayTitle.orEmpty()))
        }
        val fields = exercise.exerciseFields
        val newSet = LoggedSet(
            sessionId = 0L,
            exerciseId = exercise.id,
            exerciseName = exercise.name,
            exerciseType = exercise.exerciseType,
            setNumber = 1,
            weight = if (ExerciseField.WEIGHT in fields) 60.0 else 0.0,
            addedWeight = 0.0,
            reps = if (ExerciseField.REPS in fields) 8 else 0,
            holdDurationSeconds = 0,
            isCompleted = false,
            timestamp = System.currentTimeMillis(),
            bodyWeightKg = currentBodyWeightKg(),
            setUid = LoggedSet.newUid()
        )
        _activeRoutineWorkout.value = current.copy(
            exercises = updatedExercises,
            routineItems = updatedItems,
            sets = current.sets + newSet,
            sessionOnlyExerciseIds = current.sessionOnlyExerciseIds + exercise.id
        )
    }

    fun removeExercise(exerciseId: Long): RemovedActiveExercise? {
        val current = _activeRoutineWorkout.value ?: return null
        val index = current.exercises.indexOfFirst { it.id == exerciseId }
        if (index < 0) return null
        val removedSets = current.sets.filter { it.exerciseId == exerciseId }
        val snapshot = RemovedActiveExercise(
            exercise = current.exercises[index],
            routineItem = current.routineItems.getOrNull(index),
            sets = removedSets,
            index = index,
            wasSessionOnly = exerciseId in current.sessionOnlyExerciseIds
        )
        _activeRoutineWorkout.value = current.copy(
            exercises = current.exercises.filterIndexed { i, _ -> i != index },
            routineItems = current.routineItems.filterIndexed { i, _ -> i != index },
            sets = current.sets.filterNot { it.exerciseId == exerciseId },
            sessionOnlyExerciseIds = current.sessionOnlyExerciseIds - exerciseId
        )
        return snapshot
    }

    fun restoreExercise(snapshot: RemovedActiveExercise) {
        val current = _activeRoutineWorkout.value ?: return
        if (current.exercises.any { it.id == snapshot.exercise.id }) return
        val index = snapshot.index.coerceIn(0, current.exercises.size)
        val exercises = current.exercises.toMutableList().apply { add(index, snapshot.exercise) }
        val items = current.routineItems.toMutableList().apply {
            val item = snapshot.routineItem
                ?: defaultRoutineItem(snapshot.exercise, current.weekday ?: 0, current.dayTitle.orEmpty())
            add(index.coerceAtMost(size), item)
        }
        val existingUids = current.sets.mapNotNull { it.setUid.takeIf(String::isNotBlank) }.toHashSet()
        val restoredSets = DropSetRules.normalize(snapshot.sets)
            .filterNot { it.setUid in existingUids }
        _activeRoutineWorkout.value = current.copy(
            exercises = exercises,
            routineItems = items,
            sets = DropSetRules.renumberExerciseSets(current.sets + restoredSets, snapshot.exercise.id),
            sessionOnlyExerciseIds = if (snapshot.wasSessionOnly) {
                current.sessionOnlyExerciseIds + snapshot.exercise.id
            } else {
                current.sessionOnlyExerciseIds
            }
        )
    }

    /**
     * Reorders the live exercise sequence. For a routine workout this also rewrites the routine's
     * order without changing its id, so every Schedule slot that references it sees the new order.
     */
    fun moveExercise(fromIndex: Int, toIndex: Int) {
        val current = _activeRoutineWorkout.value ?: return
        if (fromIndex !in current.exercises.indices) return
        if (toIndex !in current.exercises.indices || fromIndex == toIndex) return
        val exercises = current.exercises.toMutableList()
        val items = current.exercises.mapIndexed { index, ex ->
            current.routineItems.getOrNull(index)
                ?: defaultRoutineItem(ex, current.weekday ?: 0, current.dayTitle.orEmpty())
        }.toMutableList()
        val exercise = exercises.removeAt(fromIndex)
        val item = items.removeAt(fromIndex)
        exercises.add(toIndex, exercise)
        items.add(toIndex, item)
        _activeRoutineWorkout.value = current.copy(exercises = exercises, routineItems = items)
        persistRoutineOrder()
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
            val repaired = repairCompletedChildren(current.sets)
            val setsToSave = repaired
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
                        ?: LocalDateKey.keyOf(current.plannedDate),
                    scheduleId = current.scheduleId,
                    scheduleGoal = current.scheduleGoal
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
        val exercises = state.exercises
        val items = state.routineItems.toMutableList()
        while (items.size < exercises.size) {
            val ex = exercises[items.size]
            items.add(defaultRoutineItem(ex, state.weekday ?: 0, state.dayTitle.orEmpty()))
        }
        _activeRoutineWorkout.value = state.copy(
            routineItems = items,
            sets = DropSetRules.normalize(state.sets)
        )
    }

    fun rename(title: String) {
        _activeRoutineWorkout.value?.let { routine ->
            _activeRoutineWorkout.value = routine.copy(routineName = title)
        }
    }

    private fun sameSet(a: LoggedSet, b: LoggedSet): Boolean =
        (b.setUid.isNotBlank() && a.setUid == b.setUid) ||
            (b.id != 0L && a.id == b.id) ||
            (a.exerciseId == b.exerciseId &&
                a.setNumber == b.setNumber &&
                a.parentSetUid == b.parentSetUid &&
                a.dropIndex == b.dropIndex)

    private fun defaultRoutineItem(exercise: Exercise, weekday: Int, dayTitle: String): RoutineExerciseItem {
        val fields = exercise.exerciseFields
        val item = RoutineExerciseItem(
            exerciseId = exercise.id,
            exerciseName = exercise.name,
            exerciseType = exercise.exerciseType,
            category = exercise.category,
            equipment = exercise.equipment,
            targetSets = 1,
            targetReps = if (ExerciseField.REPS in fields) 8 else 0,
            targetHoldSeconds = when {
                ExerciseField.HOLD_SECONDS in fields -> 30
                ExerciseField.TIME_SECONDS in fields -> 600
                else -> 0
            },
            targetDistanceKm = if (ExerciseField.DISTANCE_KM in fields) 1.0 else 0.0,
            restSeconds = defaultRestForExercise(exercise.id),
            prescription = "",
            weekday = weekday,
            dayTitle = dayTitle
        )
        return item.copy(prescription = RoutineParser.buildPrescription(item))
    }

    /**
     * Adds the persisted routine order operation to a serialized queue. The live state is read
     * inside the lock, so several fast drags converge on the final order rather than racing.
     */
    private fun persistRoutineOrder() {
        val routineId = _activeRoutineWorkout.value?.routineId ?: return
        if (routineId <= 0L) return
        scope.launch(Dispatchers.IO) {
            routinePersistMutex.withLock {
                val live = _activeRoutineWorkout.value ?: return@withLock
                if (live.routineId != routineId) return@withLock
                val routine = repository.getSavedRoutineById(routineId) ?: return@withLock
                val dbItems = RoutineParser.parseSingleWorkout(routine.exercisesJson, allExercises())
                val dbById = dbItems.associateBy { it.exerciseId }
                val currentItems = mutableListOf<RoutineExerciseItem>()
                live.exercises.forEachIndexed { index, exercise ->
                    if (exercise.id in live.sessionOnlyExerciseIds) return@forEachIndexed
                    val item = live.routineItems.getOrNull(index)
                        ?: dbById[exercise.id]
                        ?: defaultRoutineItem(exercise, live.weekday ?: 0, live.dayTitle.orEmpty())
                    currentItems.add(item)
                }
                val currentIds = currentItems.map { it.exerciseId }.toHashSet()
                val missing = dbItems.filter { it.exerciseId !in currentIds }
                val merged = currentItems.toMutableList()
                missing.forEach { item ->
                    val originalIndex = dbItems.indexOfFirst { it.exerciseId == item.exerciseId }
                    merged.add(originalIndex.coerceIn(0, merged.size), item)
                }
                val seen = HashSet<Long>()
                val deduped = merged.filter { item ->
                    item.exerciseId <= 0L || seen.add(item.exerciseId)
                }
                repository.updateSavedRoutine(
                    routine.copy(exercisesJson = RoutineParser.serializeExercises(deduped))
                )
            }
        }
    }

    private fun repairCompletedChildren(sets: List<LoggedSet>): List<LoggedSet> {
        val normalized = DropSetRules.normalize(sets)
        val completedChildParents = normalized
            .filter { it.isDropSet && it.isCompleted }
            .mapNotNull { it.parentSetUid }
            .toHashSet()
        return normalized.map { set ->
            if (!set.isDropSet && set.setUid in completedChildParents) {
                set.copy(isCompleted = true)
            } else {
                set
            }
        }
    }
}
