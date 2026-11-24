/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.ui.viewmodel

import com.example.data.model.DropSetRules
import com.example.data.model.EffortScale
import com.example.data.model.Exercise
import com.example.data.model.ExerciseField
import com.example.data.model.ExerciseType
import com.example.data.model.LoggedSet
import com.example.data.model.RemovedActiveExercise
import com.example.data.model.SupersetRules
import com.example.data.model.WorkoutSession
import com.example.data.repository.FitnessRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the live quick-workout session: its exercise list, sets, drop sets and save/discard
 * transitions. All exercise additions, removals and drops here are session-only.
 */
class QuickWorkoutSessionController(
    private val repository: FitnessRepository,
    private val scope: CoroutineScope,
    private val currentBodyWeightKg: () -> Double,
    private val resolveBodyWeightKg: suspend () -> Double,
    private val defaultRestForExercise: (Long) -> Int,
    private val stopRestTimer: () -> Unit,
    private val refreshBenchmarks: () -> Unit,
    private val clearWorkoutDraft: () -> Unit,
    private val refreshWidgets: () -> Unit,
    private val onSessionSaved: (Long) -> Unit
) {
    private val _activeSession = MutableStateFlow<WorkoutSession?>(null)
    val activeSession: StateFlow<WorkoutSession?> = _activeSession.asStateFlow()

    private val _activeSets = MutableStateFlow<List<LoggedSet>>(emptyList())
    val activeSets: StateFlow<List<LoggedSet>> = _activeSets.asStateFlow()

    private val _activeSessionExercises = MutableStateFlow<List<Exercise>>(emptyList())
    val activeSessionExercises: StateFlow<List<Exercise>> = _activeSessionExercises.asStateFlow()

    fun startNewSession(title: String = "Quick Workout") {
        stopRestTimer()
        val now = System.currentTimeMillis()
        _activeSession.value = WorkoutSession(
            title = title,
            startTime = now,
            endTime = now
        )
        _activeSets.value = emptyList()
        _activeSessionExercises.value = emptyList()
    }

    fun addExercise(exercise: Exercise) {
        val currentSession = _activeSession.value ?: run {
            startNewSession("Quick Workout")
            _activeSession.value!!
        }

        // The picker excludes exercises already in the session, but adding the same movement
        // again through the existing API means "another set of it", not "ignore the request".
        if (_activeSessionExercises.value.none { it.id == exercise.id }) {
            _activeSessionExercises.value = _activeSessionExercises.value + exercise
        }

        val currentList = DropSetRules.normalize(_activeSets.value).toMutableList()
        val existingMains = currentList.filter { it.exerciseId == exercise.id && !it.isDropSet }
        val nextSetNumber = (existingMains.maxOfOrNull { it.setNumber } ?: 0) + 1
        val lastSet = existingMains.maxByOrNull { it.setNumber }

        val fields = exercise.exerciseFields
        val newSet = LoggedSet(
            sessionId = currentSession.id,
            exerciseId = exercise.id,
            exerciseName = exercise.name,
            exerciseType = exercise.exerciseType,
            setNumber = nextSetNumber,
            weight = if (ExerciseField.WEIGHT in fields) lastSet?.weight ?: 60.0 else 0.0,
            addedWeight = if (ExerciseField.ADDED_WEIGHT in fields) lastSet?.addedWeight ?: 0.0 else 0.0,
            reps = if (ExerciseField.REPS in fields) {
                lastSet?.reps ?: if (exercise.exerciseType == ExerciseType.PURE_BODYWEIGHT) 10 else 8
            } else 0,
            holdDurationSeconds = if (
                ExerciseField.HOLD_SECONDS in fields || ExerciseField.TIME_SECONDS in fields
            ) {
                lastSet?.holdDurationSeconds ?: 0
            } else 0,
            distanceKm = if (ExerciseField.DISTANCE_KM in fields) lastSet?.distanceKm ?: 0.0 else 0.0,
            restSecondsOverride = lastSet?.restSecondsOverride
                ?: defaultRestForExercise(exercise.id).takeIf { it > 0 },
            isCompleted = false,
            timestamp = System.currentTimeMillis(),
            bodyWeightKg = currentBodyWeightKg(),
            setUid = LoggedSet.newUid(),
            supersetGroupId = lastSet?.supersetGroupId,
            effortRpe = lastSet?.effortRpe
        )
        currentList.add(newSet)
        _activeSets.value = DropSetRules.renumberExerciseSets(currentList, exercise.id)
    }

    fun addDropSet(parent: LoggedSet) {
        val current = DropSetRules.normalize(_activeSets.value)
        val parentSet = current.firstOrNull { it.setUid == parent.setUid && !it.isDropSet } ?: return
        val siblings = DropSetRules.childrenOf(current, parentSet.setUid)
        val source = siblings.lastOrNull() ?: parentSet
        val child = LoggedSet(
            sessionId = _activeSession.value?.id ?: 0L,
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
            dropIndex = (siblings.maxOfOrNull { it.dropIndex } ?: 0) + 1,
            supersetGroupId = parentSet.supersetGroupId,
            effortRpe = source.effortRpe
        )
        _activeSets.value = current + child
    }

    fun updateSet(index: Int, updated: LoggedSet) {
        val currentList = _activeSets.value.toMutableList()
        if (index in currentList.indices) {
            val existing = currentList[index]
            currentList[index] = updated.copy(
                setUid = existing.setUid,
                parentSetUid = existing.parentSetUid,
                dropIndex = existing.dropIndex,
                supersetGroupId = existing.supersetGroupId
            )
            _activeSets.value = currentList
        }
    }

    fun updateSet(updated: LoggedSet) {
        val currentList = _activeSets.value.toMutableList()
        val idx = currentList.indexOfFirst { existing -> sameSet(existing, updated) }
        if (idx != -1) updateSet(idx, updated)
    }

    fun toggleSetCompletion(setToUpdate: LoggedSet, completed: Boolean) {
        val current = DropSetRules.normalize(_activeSets.value)
        val existing = current.firstOrNull { sameSet(it, setToUpdate) } ?: return
        _activeSets.value = DropSetRules.toggleCompletion(current, existing.setUid, completed)
    }

    fun deleteSet(index: Int) {
        val currentList = _activeSets.value.toMutableList()
        if (index in currentList.indices) {
            val removed = currentList[index]
            val remaining = DropSetRules.removeSet(currentList, removed.setUid)
            if (remaining.none { it.exerciseId == removed.exerciseId }) {
                _activeSets.value = SupersetRules.unlinkSets(remaining, removed.exerciseId)
                _activeSessionExercises.value =
                    _activeSessionExercises.value.filter { it.id != removed.exerciseId }
            } else {
                _activeSets.value = remaining
            }
        }
    }

    fun deleteSet(setToDel: LoggedSet) {
        val current = DropSetRules.normalize(_activeSets.value)
        val existing = current.firstOrNull { sameSet(it, setToDel) } ?: return
        val remaining = DropSetRules.removeSet(current, existing.setUid)
        if (remaining.none { it.exerciseId == existing.exerciseId }) {
            _activeSets.value = SupersetRules.unlinkSets(remaining, existing.exerciseId)
            _activeSessionExercises.value =
                _activeSessionExercises.value.filter { it.id != existing.exerciseId }
        } else {
            _activeSets.value = remaining
        }
    }

    fun removeExercise(exerciseId: Long): RemovedActiveExercise? {
        val index = _activeSessionExercises.value.indexOfFirst { it.id == exerciseId }
        if (index < 0) return null
        val linked = SupersetRules.groupIdForExercise(_activeSets.value, exerciseId) != null
        val nextSets = if (linked) {
            SupersetRules.unlinkSets(_activeSets.value, exerciseId)
        } else {
            _activeSets.value
        }
        val snapshot = RemovedActiveExercise(
            exercise = _activeSessionExercises.value[index],
            routineItem = null,
            sets = nextSets.filter { it.exerciseId == exerciseId }
                .map { it.copy(supersetGroupId = null) },
            index = index,
            wasSessionOnly = true
        )
        _activeSessionExercises.value = _activeSessionExercises.value.filterIndexed { i, _ -> i != index }
        _activeSets.value = nextSets.filterNot { it.exerciseId == exerciseId }
        return snapshot
    }

    fun restoreExercise(snapshot: RemovedActiveExercise) {
        if (_activeSessionExercises.value.any { it.id == snapshot.exercise.id }) return
        val index = snapshot.index.coerceIn(0, _activeSessionExercises.value.size)
        _activeSessionExercises.value = _activeSessionExercises.value.toMutableList().apply {
            add(index, snapshot.exercise)
        }
        val existingUids = _activeSets.value.mapNotNull { it.setUid.takeIf(String::isNotBlank) }.toHashSet()
        val restored = DropSetRules.normalize(snapshot.sets)
            .filterNot { it.setUid in existingUids }
            .map { it.copy(supersetGroupId = null) }
        _activeSets.value = DropSetRules.renumberExerciseSets(
            _activeSets.value + restored,
            snapshot.exercise.id
        )
    }

    /** Links two exercises in a quick workout. Returns false when the pair is not valid. */
    fun linkSuperset(exerciseId: Long, direction: Int): Boolean {
        val exercises = _activeSessionExercises.value
        val index = exercises.indexOfFirst { it.id == exerciseId }
        if (index < 0) return false
        val partnerIndex = index + direction
        if (partnerIndex !in exercises.indices) return false
        val partnerId = exercises[partnerIndex].id
        if (!SupersetRules.canLinkSets(_activeSets.value, exerciseId, partnerId)) return false
        _activeSets.value = SupersetRules.linkSets(_activeSets.value, exerciseId, partnerId)
        return true
    }

    fun unlinkSuperset(exerciseId: Long): Boolean {
        if (SupersetRules.groupIdForExercise(_activeSets.value, exerciseId) == null) return false
        _activeSets.value = SupersetRules.unlinkSets(_activeSets.value, exerciseId)
        return true
    }

    fun moveExercise(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in _activeSessionExercises.value.indices) return
        if (toIndex !in _activeSessionExercises.value.indices || fromIndex == toIndex) return
        val exercises = _activeSessionExercises.value
        val groupId = SupersetRules.groupIdForExercise(_activeSets.value, exercises[fromIndex].id)
        val partner = if (groupId == null) {
            null
        } else {
            exercises.indexOfFirst { candidate ->
                candidate.id != exercises[fromIndex].id &&
                    _activeSets.value.any {
                        it.exerciseId == candidate.id && it.supersetGroupId == groupId
                    }
            }.takeIf { it >= 0 }
        }
        val start = if (partner == null) fromIndex else minOf(fromIndex, partner)
        val size = if (partner == null) 1 else 2
        val end = start + size
        if (toIndex in start until end) return
        if (size == 2) {
            val movingGroup = _activeSets.value.firstOrNull { it.exerciseId == exercises[fromIndex].id }
                ?.supersetGroupId
            val targetGroup = _activeSets.value.firstOrNull { it.exerciseId == exercises[toIndex].id }
                ?.supersetGroupId
            if (targetGroup != null && targetGroup != movingGroup) return
        }
        val list = _activeSessionExercises.value.toMutableList()
        val block = list.subList(start, end).toList()
        repeat(size) { list.removeAt(start) }
        val insertAt = (if (toIndex > start) toIndex - size + 1 else toIndex)
            .coerceIn(0, list.size)
        list.addAll(insertAt, block)
        _activeSessionExercises.value = list
    }

    fun finishAndSave(notes: String = "") {
        val currentSession = _activeSession.value ?: return
        val currentSets = _activeSets.value
        stopRestTimer()
        scope.launch(Dispatchers.IO) {
            val savedAt = System.currentTimeMillis()
            val finalBodyWeightKg = resolveBodyWeightKg()
            val repaired = SupersetRules.normalizeSets(repairCompletedChildren(currentSets))
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
                val finalSession = currentSession.copy(
                    endTime = System.currentTimeMillis(),
                    notes = notes
                )
                val sessionId = repository.insertSessionWithSets(finalSession, setsToSave)
                savedSessionId = sessionId
                refreshBenchmarks()
            }
            withContext(Dispatchers.Main) {
                clearLiveState()
            }
            clearWorkoutDraft()
            refreshWidgets()
            savedSessionId?.let(onSessionSaved)
        }
    }

    fun discard() {
        stopRestTimer()
        clearLiveState()
        clearWorkoutDraft()
    }

    fun clearLiveState() {
        _activeSession.value = null
        _activeSets.value = emptyList()
        _activeSessionExercises.value = emptyList()
    }

    fun restore(session: WorkoutSession, exercises: List<Exercise>, sets: List<LoggedSet>) {
        _activeSession.value = session
        _activeSessionExercises.value = exercises
        _activeSets.value = SupersetRules.normalizeSets(DropSetRules.normalize(sets))
    }

    fun rename(title: String) {
        _activeSession.value?.let { session ->
            _activeSession.value = session.copy(title = title)
        }
    }

    private fun sameSet(a: LoggedSet, b: LoggedSet): Boolean =
        (b.setUid.isNotBlank() && a.setUid == b.setUid) ||
            (b.id != 0L && a.id == b.id) ||
            (a.exerciseId == b.exerciseId &&
                a.setNumber == b.setNumber &&
                a.parentSetUid == b.parentSetUid &&
                a.dropIndex == b.dropIndex)

    /** A completed child implies a performed parent; fix corrupted old data before saving. */
    private fun repairCompletedChildren(sets: List<LoggedSet>): List<LoggedSet> {
        val normalized = DropSetRules.normalize(sets)
        val completedChildren = normalized.filter { it.isDropSet && it.isCompleted }
        val parentUids = completedChildren.mapNotNull { it.parentSetUid }.toHashSet()
        return normalized.map { set ->
            if (!set.isDropSet && set.setUid in parentUids) set.copy(isCompleted = true) else set
        }
    }
}
