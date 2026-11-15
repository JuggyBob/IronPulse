/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.data.db

import androidx.room.withTransaction
import com.example.data.model.RoutineDay
import com.example.data.model.RoutineExerciseItem
import com.example.data.model.RoutineParser
import com.example.data.model.SavedRoutine
import com.example.data.model.ScheduleDaySlot
import com.example.data.model.SchedulePlan
import com.example.data.model.Weekday

/**
 * Converts every old multi-day SavedRoutine into one Schedule plus one standalone Routine per
 * distinct workout day.
 *
 * The conversion is transactional per legacy routine. The old row is retained with
 * `modelVersion = 1` so historical workout links still resolve, but it is hidden from the new
 * Routines page. Running the migration again is safe: only `modelVersion = 0` rows are considered.
 */
object RoutineModelMigration {

    const val LEGACY_HIDDEN = 1
    const val SINGLE_WORKOUT = 2

    suspend fun migrate(database: AppDatabase) {
        val routineDao = database.savedRoutineDao()
        val scheduleDao = database.schedulePlanDao()
        val legacy = routineDao.getRoutinesNeedingModelMigration()
        for (routine in legacy) {
            database.withTransaction {
                val days = runCatching {
                    RoutineParser.parseWeek(
                        rawText = routine.exercisesJson,
                        allExercises = emptyList(),
                        weekStartsOnMonday = RoutineScheduleMigration.MIGRATION_WEEK_STARTS_ON_MONDAY
                    )
                }.getOrElse { emptyList() }

                val uniqueRoutines = linkedMapOf<String, Pair<Long, RoutineDay>>()
                val weekdayRoutineIds = mutableMapOf<Int, Long>()
                var createdIndex = 0

                for (day in days.filter { it.exercises.isNotEmpty() }.sortedBy { it.weekday }) {
                    val key = normalizedDayKey(day)
                    val existing = uniqueRoutines[key]
                    val routineId = if (existing != null) {
                        existing.first
                    } else {
                        val name = day.dayTitle.trim().ifBlank {
                            if (days.count { it.exercises.isNotEmpty() } == 1) {
                                routine.name
                            } else {
                                "${routine.name} - ${Weekday.name(day.weekday)}"
                            }
                        }
                        val items = day.exercises.map { item ->
                            item.copy(weekday = 0, dayTitle = "", dayNote = "")
                        }
                        val id = routineDao.insertRoutine(
                            SavedRoutine(
                                name = name.take(120),
                                description = routine.description,
                                daysPerWeek = 1,
                                goal = routine.goal,
                                exercisesJson = RoutineParser.serializeExercises(items),
                                createdAt = routine.createdAt + createdIndex,
                                scheduleVersion = 1,
                                modelVersion = SINGLE_WORKOUT
                            )
                        )
                        createdIndex++
                        uniqueRoutines[key] = id to day
                        id
                    }
                    weekdayRoutineIds[day.weekday] = routineId
                }

                val scheduleId = scheduleDao.insertSchedule(
                    SchedulePlan(
                        name = routine.name,
                        description = routine.description,
                        goal = routine.goal,
                        createdAt = routine.createdAt,
                        legacyRoutineId = routine.id
                    )
                )
                scheduleDao.insertDays(
                    (1..Weekday.COUNT).map { weekday ->
                        ScheduleDaySlot(
                            scheduleId = scheduleId,
                            weekday = weekday,
                            routineId = weekdayRoutineIds[weekday]
                        )
                    }
                )

                routineDao.updateRoutineModelVersion(routine.id, LEGACY_HIDDEN)
            }
        }
    }

    /** Meaningful workout configuration, independent of titles, ids and JSON formatting. */
    internal fun normalizedDayKey(day: RoutineDay): String = day.exercises.joinToString("|") { item ->
        normalizedItemKey(item)
    }

    internal fun normalizedItemsKey(items: List<RoutineExerciseItem>): String =
        items.joinToString("|") { normalizedItemKey(it) }

    private fun normalizedItemKey(item: RoutineExerciseItem): String = buildString {
        append(item.exerciseId)
        append(':')
        append(item.exerciseName.trim().lowercase())
        append(':')
        append(item.exerciseType)
        append(':')
        append(item.targetSets)
        append(':')
        append(item.targetReps)
        append(':')
        append(item.targetWeight)
        append(':')
        append(item.targetAddedWeightKg)
        append(':')
        append(item.targetHoldSeconds)
        append(':')
        append(item.targetDistanceKm)
        append(':')
        append(item.restSeconds)
        append(':')
        append(item.prescription.trim().lowercase())
        append(':')
        append(item.dropSets.sortedWith(compareBy({ it.mainSetIndex }, { it.dropIndex })).joinToString(",") {
            "${it.mainSetIndex}:${it.dropIndex}:${it.targetReps}:${it.targetHoldSeconds}:" +
                "${it.targetWeight}:${it.targetAddedWeightKg}:${it.targetDistanceKm}:${it.restSeconds}"
        })
        append(':')
        append(item.mainSetDefaults.sortedBy { it.mainSetIndex }.joinToString(",") {
            "${it.mainSetIndex}:${it.targetReps}:${it.targetHoldSeconds}:" +
                "${it.targetWeight}:${it.targetAddedWeightKg}:${it.targetDistanceKm}:${it.restSeconds}"
        })
    }
}
