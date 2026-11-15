/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.db.AppDatabase
import com.example.data.model.RoutineExerciseItem
import com.example.data.model.RoutineParser
import com.example.data.model.SavedRoutine
import com.example.data.model.ScheduleDaySlot
import com.example.data.model.SchedulePlan
import com.example.data.model.Weekday
import kotlinx.coroutines.flow.Flow

/**
 * The single-workout Routine and weekly Schedule aggregate.
 *
 * Routines remain one flat exercise prescription each. A Schedule is only seven references, so
 * editing a Routine immediately changes every referencing weekday. Completed workout sessions are
 * never touched by these operations.
 */
class PlanRepository(private val db: AppDatabase) {

    private val routineDao get() = db.savedRoutineDao()
    private val scheduleDao get() = db.schedulePlanDao()

    val routines: Flow<List<SavedRoutine>> = routineDao.getUserRoutines()
    val schedules: Flow<List<SchedulePlan>> = scheduleDao.getAllSchedules()
    val scheduleSlots: Flow<List<ScheduleDaySlot>> = scheduleDao.getAllScheduleSlots()
    val materializedDays: Flow<List<com.example.data.model.ScheduledWorkoutDay>> =
        db.scheduleDao().getAllScheduleDays()

    fun daysFor(scheduleId: Long): Flow<List<ScheduleDaySlot>> =
        scheduleDao.getDaysForSchedule(scheduleId)

    suspend fun getRoutine(id: Long): SavedRoutine? = routineDao.getRoutineById(id)
    suspend fun getAllRoutinesOnce(): List<SavedRoutine> = routineDao.getUserRoutinesOnce()

    suspend fun saveRoutine(routine: SavedRoutine): Long {
        val normalized = routine.copy(
            daysPerWeek = 1,
            modelVersion = 2,
            scheduleVersion = 1
        )
        return routineDao.insertRoutine(normalized)
    }

    suspend fun createRoutine(
        name: String,
        description: String,
        goal: String,
        items: List<RoutineExerciseItem>
    ): Long = saveRoutine(
        SavedRoutine(
            name = name.trim().take(120),
            description = description.trim(),
            daysPerWeek = 1,
            goal = goal,
            exercisesJson = RoutineParser.serializeExercises(flatten(items)),
            scheduleVersion = 1,
            modelVersion = 2
        )
    )

    suspend fun deleteRoutine(routineId: Long) {
        db.withTransaction {
            scheduleDao.clearRoutineReferences(routineId)
            routineDao.deleteRoutineById(routineId)
        }
    }

    /** Deletes several Routines and clears every future Schedule reference in one transaction. */
    suspend fun deleteRoutines(routineIds: Collection<Long>) {
        if (routineIds.isEmpty()) return
        db.withTransaction {
            routineIds.distinct().forEach { routineId ->
                scheduleDao.clearRoutineReferences(routineId)
                routineDao.deleteRoutineById(routineId)
            }
        }
    }

    suspend fun duplicateRoutine(routineId: Long, copyName: String? = null): Long? {
        val source = routineDao.getRoutineById(routineId) ?: return null
        val copy = source.copy(
            id = 0L,
            name = (copyName ?: "${source.name} Copy").take(120),
            createdAt = System.currentTimeMillis(),
            modelVersion = 2,
            scheduleVersion = 1,
            daysPerWeek = 1
        )
        return routineDao.insertRoutine(copy)
    }

    suspend fun getSchedule(id: Long): SchedulePlan? = scheduleDao.getScheduleById(id)
    suspend fun getScheduleByLegacyRoutineId(id: Long): SchedulePlan? =
        scheduleDao.getByLegacyRoutineId(id)
    suspend fun getAllSchedulesOnce(): List<SchedulePlan> = scheduleDao.getAllSchedulesOnce()
    suspend fun getDaysOnce(scheduleId: Long): List<ScheduleDaySlot> =
        scheduleDao.getDaysForScheduleOnce(scheduleId)

    suspend fun replaceFutureSchedule(
        fromDate: Long,
        rows: List<com.example.data.model.ScheduledWorkoutDay>
    ) {
        db.withTransaction {
            val fromKey = com.example.data.model.LocalDateKey.keyOf(fromDate)
            db.scheduleDao().deleteScheduleFrom(fromKey = fromKey, fromDate = fromDate)
            if (rows.isNotEmpty()) db.scheduleDao().insertScheduleDays(rows)
        }
    }

    suspend fun createSchedule(
        name: String,
        description: String,
        goal: String
    ): Long = db.withTransaction {
        val id = scheduleDao.insertSchedule(
            SchedulePlan(
                name = name.trim().take(120),
                description = description.trim(),
                goal = goal
            )
        )
        scheduleDao.insertDays(
            (1..Weekday.COUNT).map { weekday ->
                ScheduleDaySlot(scheduleId = id, weekday = weekday, routineId = null)
            }
        )
        id
    }

    suspend fun updateSchedule(schedule: SchedulePlan) {
        scheduleDao.updateSchedule(schedule)
    }

    suspend fun deleteSchedule(scheduleId: Long) {
        db.withTransaction {
            scheduleDao.deleteDaysForSchedule(scheduleId)
            scheduleDao.getScheduleById(scheduleId)?.let { scheduleDao.deleteSchedule(it) }
        }
    }

    /** Deletes several Schedules and their slots without touching the Routines they reference. */
    suspend fun deleteSchedules(scheduleIds: Collection<Long>) {
        if (scheduleIds.isEmpty()) return
        db.withTransaction {
            scheduleIds.distinct().forEach { scheduleId ->
                scheduleDao.deleteDaysForSchedule(scheduleId)
                scheduleDao.getScheduleById(scheduleId)?.let { scheduleDao.deleteSchedule(it) }
            }
        }
    }

    /** Complete plan-layer wipe used by Delete App Data. */
    suspend fun clearAllPlans() {
        db.withTransaction {
            scheduleDao.deleteAllScheduleSlots()
            scheduleDao.deleteAllSchedules()
        }
    }

    suspend fun setScheduleDay(scheduleId: Long, weekday: Int, routineId: Long?) {
        if (weekday !in 1..Weekday.COUNT) return
        scheduleDao.insertDay(
            ScheduleDaySlot(
                scheduleId = scheduleId,
                weekday = weekday,
                routineId = routineId?.takeIf { it > 0L }
            )
        )
    }

    suspend fun clearScheduleDay(scheduleId: Long, weekday: Int) {
        setScheduleDay(scheduleId, weekday, null)
    }

    /**
     * Copies a Schedule and every distinct referenced Routine exactly once. Repeated weekday
     * assignments continue to point at the same copied Routine.
     */
    suspend fun duplicateSchedule(scheduleId: Long, copyName: String? = null): Long? =
        db.withTransaction {
            val source = scheduleDao.getScheduleById(scheduleId) ?: return@withTransaction null
            val sourceDays = scheduleDao.getDaysForScheduleOnce(scheduleId)
            val routineIdMap = mutableMapOf<Long, Long>()
            for (oldId in sourceDays.mapNotNull { it.routineId }.distinct()) {
                val sourceRoutine = routineDao.getRoutineById(oldId) ?: continue
                val newId = routineDao.insertRoutine(
                    sourceRoutine.copy(
                        id = 0L,
                        name = "${sourceRoutine.name} Copy".take(120),
                        createdAt = System.currentTimeMillis(),
                        modelVersion = 2,
                        scheduleVersion = 1,
                        daysPerWeek = 1
                    )
                )
                routineIdMap[oldId] = newId
            }
            val newScheduleId = scheduleDao.insertSchedule(
                source.copy(
                    id = 0L,
                    name = (copyName ?: "${source.name} Copy").take(120),
                    createdAt = System.currentTimeMillis(),
                    legacyRoutineId = 0L
                )
            )
            scheduleDao.insertDays(
                (1..Weekday.COUNT).map { weekday ->
                    val sourceDay = sourceDays.firstOrNull { it.weekday == weekday }
                    ScheduleDaySlot(
                        scheduleId = newScheduleId,
                        weekday = weekday,
                        routineId = sourceDay?.routineId?.let { routineIdMap[it] }
                    )
                }
            )
            newScheduleId
        }

    data class CreatedPlan(
        val routineIds: List<Long>,
        val scheduleId: Long?
    )

    /**
     * Converts an AI or imported multi-day plan into independent Routines and a Schedule. A single
     * workout becomes one standalone Routine and no Schedule.
     */
    suspend fun createPlanFromItems(
        name: String,
        description: String,
        goal: String,
        items: List<RoutineExerciseItem>
    ): CreatedPlan = db.withTransaction {
        val clean = items.filter { it.exerciseName.isNotBlank() }
        if (clean.isEmpty()) return@withTransaction CreatedPlan(emptyList(), null)
        val grouped = clean.groupBy { it.weekday.takeIf { day -> day in 1..Weekday.COUNT } }
        if (grouped.size <= 1) {
            val routineId = routineDao.insertRoutine(
                SavedRoutine(
                    name = name.trim().ifBlank { "AI Workout" }.take(120),
                    description = description.trim(),
                    daysPerWeek = 1,
                    goal = goal,
                    exercisesJson = RoutineParser.serializeExercises(flatten(clean)),
                    scheduleVersion = 1,
                    modelVersion = 2
                )
            )
            return@withTransaction CreatedPlan(listOf(routineId), null)
        }

        val createdByKey = linkedMapOf<String, Long>()
        val dayRoutine = mutableMapOf<Int, Long>()
        var index = 0
        for ((weekday, dayItems) in grouped.entries.sortedBy { it.key ?: 8 }) {
            val key = RoutineModelKey.of(dayItems)
            val routineId = createdByKey.getOrPut(key) {
                val dayLabel = dayItems.firstOrNull { it.dayTitle.isNotBlank() }?.dayTitle
                    ?: Weekday.name(weekday ?: Weekday.MONDAY)
                routineDao.insertRoutine(
                    SavedRoutine(
                        name = "$name - $dayLabel".take(120),
                        description = description.trim(),
                        daysPerWeek = 1,
                        goal = goal,
                        exercisesJson = RoutineParser.serializeExercises(flatten(dayItems)),
                        createdAt = System.currentTimeMillis() + index,
                        scheduleVersion = 1,
                        modelVersion = 2
                    )
                ).also { index++ }
            }
            if (weekday != null) dayRoutine[weekday] = routineId
        }

        val scheduleId = scheduleDao.insertSchedule(
            SchedulePlan(
                name = name.trim().ifBlank { "AI Schedule" }.take(120),
                description = description.trim(),
                goal = goal
            )
        )
        scheduleDao.insertDays(
            (1..Weekday.COUNT).map { weekday ->
                ScheduleDaySlot(scheduleId, weekday, dayRoutine[weekday])
            }
        )
        CreatedPlan(createdByKey.values.toList(), scheduleId)
    }

    /** A single workout routine always has weekday-agnostic items. */
    private fun flatten(items: List<RoutineExerciseItem>): List<RoutineExerciseItem> =
        items.map { it.copy(weekday = 0, dayTitle = "", dayNote = "") }
}

internal object RoutineModelKey {
    fun of(items: List<RoutineExerciseItem>): String = items.joinToString("|") { item ->
        listOf(
            item.exerciseId.toString(),
            item.exerciseName.trim().lowercase(),
            item.exerciseType,
            item.targetSets.toString(),
            item.targetReps.toString(),
            item.targetWeight.toString(),
            item.targetAddedWeightKg.toString(),
            item.targetHoldSeconds.toString(),
            item.targetDistanceKm.toString(),
            item.restSeconds.toString(),
            item.prescription.trim().lowercase(),
            item.dropSets.sortedWith(compareBy({ it.mainSetIndex }, { it.dropIndex })).joinToString(",") {
                "${it.mainSetIndex}:${it.dropIndex}:${it.targetReps}:${it.targetHoldSeconds}:" +
                    "${it.targetWeight}:${it.targetAddedWeightKg}:${it.targetDistanceKm}:${it.restSeconds}"
            },
            item.mainSetDefaults.sortedBy { it.mainSetIndex }.joinToString(",") {
                "${it.mainSetIndex}:${it.targetReps}:${it.targetHoldSeconds}:" +
                    "${it.targetWeight}:${it.targetAddedWeightKg}:${it.targetDistanceKm}:${it.restSeconds}"
            }
        ).joinToString(":")
    }
}
