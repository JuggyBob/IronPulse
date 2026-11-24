package com.example.data.model

import java.util.UUID
import kotlin.math.abs

/**
 * Pure rules for two-exercise supersets.
 *
 * A superset is an exercise-level pair. It is deliberately separate from drop sets: a drop set is
 * a child of one main set, while a superset links the two exercises' main sets. Every group must
 * contain exactly two adjacent, unlinked exercises.
 */
object SupersetRules {
    const val MAX_MEMBERS = 2

    fun newGroupId(): String = "ss-" + UUID.randomUUID().toString()

    fun partnerIndex(items: List<RoutineExerciseItem>, index: Int): Int? {
        val groupId = items.getOrNull(index)?.supersetGroupId?.takeIf { it.isNotBlank() } ?: return null
        return items.indices.firstOrNull { it != index && items[it].supersetGroupId == groupId }
    }

    fun canLink(items: List<RoutineExerciseItem>, index: Int, direction: Int): Boolean {
        val partner = index + direction
        if (index !in items.indices || partner !in items.indices) return false
        val first = items[index]
        val second = items[partner]
        return first.supersetGroupId.isNullOrBlank() &&
            second.supersetGroupId.isNullOrBlank() &&
            first.weekday == second.weekday
    }

    fun link(items: List<RoutineExerciseItem>, index: Int, direction: Int): List<RoutineExerciseItem> {
        if (!canLink(items, index, direction)) return items
        val groupId = newGroupId()
        val partner = index + direction
        return items.mapIndexed { i, item ->
            if (i == index || i == partner) item.copy(supersetGroupId = groupId) else item
        }.let(::normalize)
    }

    fun unlink(items: List<RoutineExerciseItem>, index: Int): List<RoutineExerciseItem> {
        val groupId = items.getOrNull(index)?.supersetGroupId?.takeIf { it.isNotBlank() } ?: return items
        return items.map { if (it.supersetGroupId == groupId) it.copy(supersetGroupId = null) else it }
    }

    /**
     * Clears groups that are no longer exactly two adjacent same-day exercises. Old routines and
     * historical data never contain a group id, so this is a no-op for them.
     */
    fun normalize(items: List<RoutineExerciseItem>): List<RoutineExerciseItem> {
        val groups = items.indices
            .filter { !items[it].supersetGroupId.isNullOrBlank() }
            .groupBy { items[it].supersetGroupId!! }
        val invalid = mutableSetOf<Int>()
        for ((groupId, indexes) in groups) {
            val valid = indexes.size == MAX_MEMBERS &&
                indexes.sorted().let { it[1] - it[0] == 1 } &&
                items[indexes[0]].weekday == items[indexes[1]].weekday
            if (!valid) invalid.addAll(indexes)
        }
        if (invalid.isEmpty()) return items
        return items.mapIndexed { i, item ->
            if (i in invalid) item.copy(supersetGroupId = null) else item
        }
    }

    /**
     * Moves the whole superset block when either member is dragged. Returns the original list when
     * the destination would split an existing pair.
     */
    fun moveBlock(items: List<RoutineExerciseItem>, from: Int, to: Int): List<RoutineExerciseItem> {
        if (from !in items.indices || to !in items.indices) return items
        val partner = partnerIndex(items, from)
        val start = if (partner == null) from else minOf(from, partner)
        val size = if (partner == null) 1 else 2
        val end = start + size
        if (to in start until end) return items
        if (size == 2 &&
            items.getOrNull(to)?.supersetGroupId?.let { it != items[from].supersetGroupId } == true
        ) {
            return items
        }
        val block = items.subList(start, end).toList()
        val without = items.filterIndexed { index, _ -> index !in start until end }
        val insertAt = (if (to > start) to - size + 1 else to).coerceIn(0, without.size)
        val moved = without.toMutableList().apply { addAll(insertAt, block) }
        val normalized = normalize(moved)
        // A move that invalidates a pair is rejected rather than silently splitting it.
        val groupsBefore = items.count { !it.supersetGroupId.isNullOrBlank() }
        val groupsAfter = normalized.count { !it.supersetGroupId.isNullOrBlank() }
        return if (groupsAfter == groupsBefore) normalized else items
    }

    // ---------------------------------------------------------------- session sets

    fun groupIdForExercise(sets: List<LoggedSet>, exerciseId: Long): String? =
        sets.firstOrNull { it.exerciseId == exerciseId }
            ?.supersetGroupId
            ?.takeIf { it.isNotBlank() }

    fun pairedExerciseIds(sets: List<LoggedSet>): Map<String, List<Long>> =
        sets.filter { !it.supersetGroupId.isNullOrBlank() }
            .groupBy { it.supersetGroupId!! }
            .mapValues { (_, rows) -> rows.map { it.exerciseId }.distinct() }
            .filterValues { it.size == 2 }

    fun canLinkSets(sets: List<LoggedSet>, firstExerciseId: Long, secondExerciseId: Long): Boolean {
        if (firstExerciseId == secondExerciseId) return false
        val first = sets.filter { it.exerciseId == firstExerciseId }
        val second = sets.filter { it.exerciseId == secondExerciseId }
        if (first.isEmpty() || second.isEmpty()) return false
        return first.none { !it.supersetGroupId.isNullOrBlank() } &&
            second.none { !it.supersetGroupId.isNullOrBlank() }
    }

    fun linkSets(
        sets: List<LoggedSet>,
        firstExerciseId: Long,
        secondExerciseId: Long
    ): List<LoggedSet> {
        if (!canLinkSets(sets, firstExerciseId, secondExerciseId)) return sets
        val groupId = newGroupId()
        return sets.map {
            if (it.exerciseId == firstExerciseId || it.exerciseId == secondExerciseId) {
                it.copy(supersetGroupId = groupId)
            } else {
                it
            }
        }
    }

    fun unlinkSets(sets: List<LoggedSet>, exerciseId: Long): List<LoggedSet> {
        val groupId = groupIdForExercise(sets, exerciseId) ?: return sets
        return sets.map { if (it.supersetGroupId == groupId) it.copy(supersetGroupId = null) else it }
    }

    /** Removes groups that no longer contain exactly two exercises. */
    fun normalizeSets(sets: List<LoggedSet>): List<LoggedSet> {
        val groups = sets
            .filter { !it.supersetGroupId.isNullOrBlank() }
            .groupBy { it.supersetGroupId!! }
        val invalid = groups.filterValues { rows ->
            rows.map { it.exerciseId }.distinct().size != 2
        }.keys
        if (invalid.isEmpty()) return sets
        return sets.map {
            if (it.supersetGroupId in invalid) it.copy(supersetGroupId = null) else it
        }
    }
}
