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
}
