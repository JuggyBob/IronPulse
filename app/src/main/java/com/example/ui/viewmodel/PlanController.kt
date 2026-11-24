/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.ui.viewmodel

import com.example.data.model.LocalDateKey
import com.example.data.model.RoutineDay
import com.example.data.model.RoutineParser
import com.example.data.model.SavedRoutine
import com.example.data.model.ScheduleDaySlot
import com.example.data.model.SchedulePlan
import com.example.data.model.StreakSummary
import com.example.data.model.Weekday
import com.example.data.model.WorkoutStreak
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.FitnessRepository
import com.example.data.repository.PlanRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Owns the weekly Schedule presentation and the active-Schedule projections used by Home.
 *
 * It never duplicates Routine contents. The seven [RoutineDay] views are assembled from the
 * referenced Routine rows on every emission, so a Routine edit immediately updates every weekday.
 */
class PlanController(
    private val repository: FitnessRepository,
    private val planRepository: PlanRepository,
    private val preferences: UserPreferencesRepository,
    private val allExercises: StateFlow<List<com.example.data.model.Exercise>>,
    private val weekStartsOnMonday: Flow<Boolean>,
    private val scope: CoroutineScope,
    private val onPlanChanged: () -> Unit,
    /**
     * While a backup restore is applying, the restored schedule rows must not be rewritten: the
     * importer verifies them byte for byte before committing. Materialization resumes immediately
     * afterwards, so the normal week still appears without requiring a restart.
     */
    private val isImportApplying: () -> Boolean = { false }
) {
    val routines: StateFlow<List<SavedRoutine>> = planRepository.routines
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val schedules: StateFlow<List<SchedulePlan>> = planRepository.schedules
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val scheduleSlots: StateFlow<List<ScheduleDaySlot>> = planRepository.scheduleSlots
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeScheduleId: StateFlow<Long> = preferences.activeScheduleIdFlow
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0L)

    val activeSchedule: StateFlow<SchedulePlan?> = combine(
        schedules,
        activeScheduleId
    ) { plans, id ->
        plans.firstOrNull { it.id == id }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /** Exactly seven slots for the active Schedule; an unassigned slot is a rest day. */
    val activeScheduleDays: StateFlow<List<RoutineDay>> = combine(
        activeSchedule,
        scheduleSlots,
        routines,
        allExercises,
        weekStartsOnMonday
    ) { schedule, slots, plans, exercises, mondayFirst ->
        if (schedule == null) {
            emptyList()
        } else {
            val byId = plans.associateBy { it.id }
            val byWeekday = slots.filter { it.scheduleId == schedule.id }.associateBy { it.weekday }
            (1..Weekday.COUNT).map { weekday ->
                val slot = byWeekday[weekday]
                val routine = slot?.routineId?.let(byId::get)
                if (routine == null) {
                    RoutineDay(weekday = weekday)
                } else {
                    val items = runCatching {
                        RoutineParser.parseSingleWorkout(routine.exercisesJson, exercises)
                    }.getOrElse { emptyList() }
                    RoutineDay(
                        weekday = weekday,
                        dayTitle = routine.name,
                        exercises = items,
                        note = routine.description,
                        routineId = routine.id
                    )
                }
            }
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val dayKeyTicker = flow {
        while (true) {
            emit(LocalDateKey.keyOf(System.currentTimeMillis()))
            delay(60_000L)
        }
    }.distinctUntilChanged()

    val streakSummary: StateFlow<StreakSummary> = combine(
        planRepository.materializedDays,
        repository.getAllSessionsWithSets(),
        weekStartsOnMonday,
        dayKeyTicker
    ) { days, sessions, mondayFirst, _ ->
        WorkoutStreak.calculate(
            schedule = days,
            sessions = sessions,
            today = System.currentTimeMillis(),
            weekStartsOnMonday = mondayFirst
        )
    }.stateIn(
        scope,
        SharingStarted.WhileSubscribed(5_000),
        WorkoutStreak.calculate(emptyList(), emptyList(), System.currentTimeMillis())
    )

    init {
        scope.launch(Dispatchers.IO) {
            // Promote the old active_routine_id once, only when the new preference is empty.
            //
            // This has to wait for the Schedule rows, not just read them once. The 4.4 -> 5.0
            // routine migration runs asynchronously when Room opens, so an eager query can run
            // before the Schedule exists and silently lose the athlete's active plan. The combine
            // re-checks whenever a Schedule appears; migrateActiveRoutine clears the legacy key
            // after a successful promotion, so a later deliberate deactivation cannot re-promote
            // an old plan.
            combine(
                planRepository.schedules,
                preferences.activeRoutineIdFlow,
                preferences.activeScheduleIdFlow
            ) { schedules, legacyActive, activeSchedule ->
                Triple(schedules, legacyActive, activeSchedule)
            }.collectLatest { (schedules, legacyActive, activeSchedule) ->
                while (isImportApplying()) delay(200)
                if (activeSchedule != 0L || legacyActive <= 0L) return@collectLatest
                val migrated = schedules.firstOrNull { it.legacyRoutineId == legacyActive }
                    ?: return@collectLatest
                preferences.migrateActiveRoutine(
                    legacyRoutineId = legacyActive,
                    scheduleId = migrated.id,
                    consumeLegacyKey = true
                )
            }
        }

        scope.launch(Dispatchers.IO) {
            combine(
                activeSchedule,
                activeScheduleDays,
                weekStartsOnMonday,
                dayKeyTicker
            ) { schedule, days, mondayFirst, todayKey -> schedule to days }
                .collectLatest { (schedule, days) ->
                // Restored rows are verified exactly before the import commits; rewriting them
                // here would fail that comparison and roll the import back.
                while (isImportApplying()) delay(200)
                val today = com.example.data.model.ActivityHeatmap.startOfDay(
                    System.currentTimeMillis()
                )
                val rows = if (schedule == null) {
                    emptyList()
                } else {
                    WorkoutStreak.buildScheduleRows(
                        routineId = schedule.id,
                        days = days,
                        fromDate = today
                    )
                }
                planRepository.replaceFutureSchedule(fromDate = today, rows = rows)
                onPlanChanged()
            }
        }
    }

    suspend fun saveRoutine(routine: SavedRoutine): Long {
        val id = planRepository.saveRoutine(routine)
        onPlanChanged()
        return id
    }

    suspend fun deleteRoutine(id: Long) {
        planRepository.deleteRoutine(id)
        onPlanChanged()
    }

    suspend fun deleteRoutines(ids: Collection<Long>) {
        planRepository.deleteRoutines(ids)
        onPlanChanged()
    }

    suspend fun duplicateRoutine(id: Long): Long? {
        val copy = planRepository.duplicateRoutine(id)
        onPlanChanged()
        return copy
    }

    suspend fun createSchedule(name: String, description: String, goal: String): Long {
        val id = planRepository.createSchedule(name, description, goal)
        onPlanChanged()
        return id
    }

    suspend fun updateSchedule(schedule: SchedulePlan) {
        planRepository.updateSchedule(schedule)
        onPlanChanged()
    }

    suspend fun deleteSchedule(id: Long) {
        if (activeScheduleId.value == id) {
            preferences.setActiveScheduleId(0L)
        }
        planRepository.deleteSchedule(id)
        onPlanChanged()
    }

    suspend fun deleteSchedules(ids: Collection<Long>) {
        if (activeScheduleId.value in ids) {
            preferences.setActiveScheduleId(0L)
        }
        planRepository.deleteSchedules(ids)
        onPlanChanged()
    }

    /** Clears the normalized Schedule layer and the active selection. */
    suspend fun clearAllPlans() {
        preferences.setActiveScheduleId(0L)
        planRepository.clearAllPlans()
        onPlanChanged()
    }

    suspend fun setActiveSchedule(id: Long) {
        preferences.setActiveScheduleId(id)
        onPlanChanged()
    }

    suspend fun setDay(scheduleId: Long, weekday: Int, routineId: Long?) {
        planRepository.setScheduleDay(scheduleId, weekday, routineId)
        onPlanChanged()
    }

    suspend fun copySchedule(id: Long): Long? {
        val copy = planRepository.duplicateSchedule(id)
        onPlanChanged()
        return copy
    }

    suspend fun createPlan(
        name: String,
        description: String,
        goal: String,
        items: List<com.example.data.model.RoutineExerciseItem>
    ): PlanRepository.CreatedPlan {
        val result = planRepository.createPlanFromItems(name, description, goal, items)
        onPlanChanged()
        return result
    }
}
