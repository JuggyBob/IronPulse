/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.ui.viewmodel

import com.example.data.model.BodyWeightTimeline
import com.example.data.model.LoggedSet
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutMetrics
import com.example.data.repository.FitnessRepository
import com.example.ui.components.ChartPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Owns the Progress tab's selected exercise/weight filter and the derived chart series.
 *
 * The selection remains exposed as [MutableStateFlow] because the existing Progress UI writes it
 * directly. All calculations and the auto-selection collectors moved here unchanged.
 */
class ProgressAnalyticsController(
    private val repository: FitnessRepository,
    private val userProfile: StateFlow<UserProfile>,
    private val scope: CoroutineScope
) {
    val distinctLoggedExercises: StateFlow<List<String>> = repository.getDistinctLoggedExerciseNames()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedExerciseForProgress = MutableStateFlow<String>("")
    val selectedMetric = MutableStateFlow(ProgressMetric.ESTIMATED_1RM)
    val selectedAddedWeightFilter = MutableStateFlow<Double?>(null)

    val exerciseHistorySets: StateFlow<List<LoggedSet>> = selectedExerciseForProgress
        .flatMapLatest { exerciseName ->
            if (exerciseName.isBlank()) flowOf(emptyList())
            else repository.getSetsForExercise(exerciseName)
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableAddedWeights: StateFlow<List<Double>> = combine(
        exerciseHistorySets,
        userProfile
    ) { sets, profile ->
        if (sets.isEmpty()) return@combine emptyList()

        val weights = sortedSetOf<Double>()
        for (set in sets) {
            if (set.addedWeight > 0.0) weights.add(set.addedWeight)
            if (set.weight > 0.0) weights.add(set.weight)
        }

        for (set in sets) {
            if (BodyWeightTimeline.isBodyweightTagCandidate(set)) {
                BodyWeightTimeline.bodyWeightForSet(set, profile.currentWeightKg)?.let { weights.add(it) }
            }
        }

        weights.toList()
    }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val progressChartPoints: StateFlow<List<ChartPoint>> = combine(
        exerciseHistorySets,
        selectedMetric,
        selectedAddedWeightFilter,
        userProfile
    ) { sets, metric, addedWeightFilter, profile ->
        if (sets.isEmpty()) return@combine emptyList()

        val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
        val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        fun bodyWeightOf(set: LoggedSet): Double? =
            BodyWeightTimeline.bodyWeightForSet(set, profile.currentWeightKg)

        fun effectiveWeightOrNull(set: LoggedSet): Double? =
            WorkoutMetrics.effectiveWeightKg(set, bodyWeightOf(set))

        val filteredSets = addedWeightFilter?.let { tag ->
            sets.filter { set ->
                BodyWeightTimeline.matchesIsolatedWeight(set, tag, bodyWeightOf(set))
            }
        } ?: sets

        if (filteredSets.isEmpty()) return@combine emptyList()

        val groupedByDate = filteredSets.groupBy {
            dayFormat.format(Date(it.timestamp))
        }

        groupedByDate.mapNotNull { (_, daySets) ->
            val sampleSet = daySets.first()
            val timestamp = sampleSet.timestamp
            val label = dateFormat.format(Date(timestamp))

            val value = when (metric) {
                ProgressMetric.MAX_REPS -> {
                    daySets.maxOfOrNull { it.reps.toDouble() } ?: 0.0
                }
                ProgressMetric.ESTIMATED_1RM -> {
                    val measurable = daySets.filter { effectiveWeightOrNull(it) != null }
                    measurable.maxOfOrNull { set ->
                        val effectiveWeight = effectiveWeightOrNull(set) ?: 0.0
                        WorkoutMetrics.estimateOneRepMax(effectiveWeight, set.reps)
                    } ?: return@mapNotNull null
                }
                ProgressMetric.TOTAL_VOLUME -> {
                    val measurable = daySets.filter { effectiveWeightOrNull(it) != null }
                    if (measurable.isEmpty()) return@mapNotNull null
                    measurable.sumOf { set -> (effectiveWeightOrNull(set) ?: 0.0) * set.reps }
                }
            }

            val detail = "${daySets.size} sets (${daySets.joinToString { "${it.reps}r" }})"
            ChartPoint(
                timestamp = timestamp,
                value = value,
                label = label,
                detailText = detail
            )
        }.sortedBy { it.timestamp }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        scope.launch {
            distinctLoggedExercises.collect { list ->
                val currentSelection = selectedExerciseForProgress.value
                when {
                    list.isEmpty() -> {
                        selectedExerciseForProgress.value = ""
                        selectedAddedWeightFilter.value = null
                    }
                    currentSelection.isBlank() -> {
                        selectedExerciseForProgress.value = list.first()
                    }
                    list.none { it == currentSelection } -> {
                        selectedExerciseForProgress.value = list.first()
                        selectedAddedWeightFilter.value = null
                    }
                }
            }
        }

        scope.launch {
            availableAddedWeights.collect { available ->
                val selected = selectedAddedWeightFilter.value
                if (selected != null && available.isNotEmpty() && available.none { it == selected }) {
                    selectedAddedWeightFilter.value = null
                }
            }
        }
    }

    fun resetSelection() {
        selectedExerciseForProgress.value = ""
        selectedAddedWeightFilter.value = null
    }
}
