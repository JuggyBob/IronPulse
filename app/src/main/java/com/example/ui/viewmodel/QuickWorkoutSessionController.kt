/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.ui.viewmodel

import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import com.example.data.model.LoggedSet
import com.example.data.model.WorkoutMetrics
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
 * Owns the live quick-workout session: its exercise list, sets and save/discard transitions.
 *
 * Routine workouts have their own state holder and remain in [MainViewModel] for now. This
 * controller deliberately keeps persistence and widget/AI-summary side effects behind callbacks so
 * it has one responsibility and the existing ViewModel facade stays unchanged.
 */
class QuickWorkoutSessionController(
    private val repository: FitnessRepository,
    private val scope: CoroutineScope,
    private val currentBodyWeightKg: () -> Double,
    private val resolveBodyWeightKg: suspend () -> Double,
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

        if (_activeSessionExercises.value.none { it.id == exercise.id }) {
            _activeSessionExercises.value = _activeSessionExercises.value + exercise
        }

        val currentList = _activeSets.value.toMutableList()
        val existingSetsOfThisExercise = currentList.filter { it.exerciseId == exercise.id }
        val nextSetNumber = existingSetsOfThisExercise.size + 1
        val lastSet = existingSetsOfThisExercise.lastOrNull()

        val newSet = LoggedSet(
            sessionId = currentSession.id,
            exerciseId = exercise.id,
            exerciseName = exercise.name,
            exerciseType = exercise.exerciseType,
            setNumber = nextSetNumber,
            weight = lastSet?.weight
                ?: if (exercise.exerciseType == ExerciseType.STANDARD_WEIGHT) 60.0 else 0.0,
            addedWeight = lastSet?.addedWeight ?: 0.0,
            reps = lastSet?.reps
                ?: if (exercise.exerciseType == ExerciseType.PURE_BODYWEIGHT) 10 else 8,
            holdDurationSeconds = lastSet?.holdDurationSeconds ?: 0,
            isCompleted = false,
            timestamp = System.currentTimeMillis(),
            bodyWeightKg = currentBodyWeightKg()
        )
        currentList.add(newSet)
        _activeSets.value = currentList
    }

    fun updateSet(index: Int, updated: LoggedSet) {
        val currentList = _activeSets.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = updated
            _activeSets.value = currentList
        }
    }

    fun updateSet(updated: LoggedSet) {
        val currentList = _activeSets.value.toMutableList()
        val idx = currentList.indexOfFirst {
            (it.id != 0L && it.id == updated.id) ||
                (it.exerciseId == updated.exerciseId && it.setNumber == updated.setNumber)
        }
        if (idx != -1) {
            currentList[idx] = updated
            _activeSets.value = currentList
        }
    }

    fun deleteSet(index: Int) {
        val currentList = _activeSets.value.toMutableList()
        if (index in currentList.indices) {
            val removed = currentList.removeAt(index)
            var currentSetNum = 1
            val reindexed = currentList.map { set ->
                if (set.exerciseId == removed.exerciseId) {
                    set.copy(setNumber = currentSetNum++)
                } else {
                    set
                }
            }
            _activeSets.value = reindexed
            if (reindexed.none { it.exerciseId == removed.exerciseId }) {
                _activeSessionExercises.value =
                    _activeSessionExercises.value.filter { it.id != removed.exerciseId }
            }
        }
    }

    fun deleteSet(setToDel: LoggedSet) {
        val currentList = _activeSets.value.toMutableList()
        val idx = currentList.indexOfFirst {
            (it.id != 0L && it.id == setToDel.id) ||
                (it.exerciseId == setToDel.exerciseId && it.setNumber == setToDel.setNumber)
        }
        if (idx != -1) {
            deleteSet(idx)
        }
    }

    fun finishAndSave(notes: String = "") {
        val currentSession = _activeSession.value ?: return
        val currentSets = _activeSets.value
        stopRestTimer()
        scope.launch(Dispatchers.IO) {
            val savedAt = System.currentTimeMillis()
            val finalBodyWeightKg = resolveBodyWeightKg()
            val setsToSave = currentSets
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
                val sessionId = repository.insertSession(finalSession)
                val setsWithSessionId = setsToSave.map { it.copy(sessionId = sessionId) }
                repository.insertSets(setsWithSessionId)
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

    /** Clears only the in-memory session, used by data replacement and full-reset flows. */
    fun clearLiveState() {
        _activeSession.value = null
        _activeSets.value = emptyList()
        _activeSessionExercises.value = emptyList()
    }

    fun restore(session: WorkoutSession, exercises: List<Exercise>, sets: List<LoggedSet>) {
        _activeSession.value = session
        _activeSessionExercises.value = exercises
        _activeSets.value = sets
    }

    fun rename(title: String) {
        _activeSession.value?.let { session ->
            _activeSession.value = session.copy(title = title)
        }
    }
}
