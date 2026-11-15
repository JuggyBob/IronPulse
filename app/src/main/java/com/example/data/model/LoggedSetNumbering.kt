package com.example.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Single source of truth for how a logged set is numbered inside the Progress page history.
 *
 * Set numbers are always derived from the chronological order of the sets and restart at 1 for
 * every training day. Deriving them instead of trusting the stored value guarantees a continuous
 * 1..N sequence even when a set (or a whole day) was deleted in the middle of the history.
 */
object LoggedSetNumbering {

    private val DAY_KEY_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    }

    fun dayKey(timestamp: Long): String {
        val format = DAY_KEY_FORMAT.get() ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return format.format(Date(timestamp))
    }

    /**
     * Maps every set id to its correct 1-based position within its own training day.
     *
     * @param sets all remaining sets of a single exercise (any order).
     * @return setId -> set number for the day it belongs to.
     */
    fun positionsBySetId(sets: List<LoggedSet>): Map<Long, Int> {
        val positions = mutableMapOf<Long, Int>()
        sets.sortedWith(compareBy({ it.timestamp }, { it.id }))
            .groupBy { dayKey(it.timestamp) }
            .forEach { (_, daySets) ->
                daySets.forEachIndexed { index, set -> positions[set.id] = index + 1 }
            }
        return positions
    }

    /**
     * The same day-local numbering, but only main sets receive a top-level number.
     *
     * Drop sets are children of their parent and carry a local D-number instead. Counting them in
     * this map made Progress show "Set 2, D1, D2, Set 5, Set 6" for three main sets.
     */
    fun mainSetPositionsBySetId(sets: List<LoggedSet>): Map<Long, Int> =
        positionsBySetId(sets.filter { !it.isDropSet })

    /**
     * Main-set numbering per workout session, not per calendar day.
     *
     * Two workouts on the same date each restart at set 1 and keep their own drop branches. This
     * is the numbering the Progress cards show.
     */
    fun mainSetPositionsBySession(sets: List<LoggedSet>): Map<Long, Int> =
        sets.groupBy { it.sessionId }
            .flatMap { (_, sessionSets) -> mainSetPositionsBySetId(sessionSets).entries }
            .associate { it.key to it.value }
}
