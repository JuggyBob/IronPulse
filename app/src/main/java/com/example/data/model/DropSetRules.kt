/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.data.model

/**
 * Pure rules for a main set and its direct drop-set children.
 *
 * The database stores both as rows in `logged_sets`; this object is the single place that knows
 * how to group them, renumber them and enforce the parent/child completion invariant. Keeping the
 * rules here means the quick workout, routine workout, history, backup and AI layers cannot drift.
 */
object DropSetRules {

    /** Every row that is not a child. */
    fun mainSets(sets: List<LoggedSet>): List<LoggedSet> = sets.filter { !it.isDropSet }

    fun mainSetsFor(sets: List<LoggedSet>, exerciseId: Long): List<LoggedSet> =
        sets.filter { it.exerciseId == exerciseId && !it.isDropSet }.sortedBy { it.setNumber }

    fun childrenOf(sets: List<LoggedSet>, parentUid: String): List<LoggedSet> =
        sets.filter { it.parentSetUid == parentUid }
            .sortedWith(compareBy({ it.dropIndex }, { it.timestamp }))

    /**
     * Gives every old row a UID and drops parent links that do not point at a main set of the same
     * exercise. This is deliberately conservative: a broken link becomes a normal set rather than
     * an orphan that history cannot place.
     */
    fun normalize(sets: List<LoggedSet>): List<LoggedSet> {
        val withUids = sets.map { set ->
            if (set.setUid.isNotBlank()) set else set.copy(setUid = LoggedSet.newUid())
        }
        val mainUidsByExercise = withUids
            .filter { !it.isDropSet }
            .groupBy { it.exerciseId }
            .mapValues { (_, rows) -> rows.mapTo(HashSet()) { it.setUid } }
        return withUids.map { set ->
            val parent = set.parentSetUid
            if (parent != null && mainUidsByExercise[set.exerciseId]?.contains(parent) != true) {
                set.copy(parentSetUid = null, dropIndex = 0)
            } else {
                set
            }
        }
    }

    /**
     * Main sets stay 1..N; each parent's children stay 1..M. The UID is never changed, so a
     * renumber can happen without detaching any child.
     */
    fun renumberExerciseSets(sets: List<LoggedSet>, exerciseId: Long): List<LoggedSet> {
        val normalized = normalize(sets)
        val main = normalized.filter { it.exerciseId == exerciseId && !it.isDropSet }
            .sortedWith(compareBy({ it.setNumber }, { it.timestamp }))
        val mainNumberByUid = HashMap<String, Int>(main.size)
        main.forEachIndexed { index, set -> mainNumberByUid[set.setUid] = index + 1 }
        val children = normalized.filter { it.exerciseId == exerciseId && it.isDropSet }
            .groupBy { it.parentSetUid.orEmpty() }
        val childNumberByUid = HashMap<String, Int>(children.values.sumOf { it.size })
        children.forEach { (_, rows) ->
            rows.sortedWith(compareBy({ it.dropIndex }, { it.timestamp }))
                .forEachIndexed { index, set -> childNumberByUid[set.setUid] = index + 1 }
        }
        return normalized.map { set ->
            if (set.exerciseId != exerciseId) return@map set
            if (set.isDropSet) {
                val parentUid = set.parentSetUid
                val parentNumber = parentUid?.let { mainNumberByUid[it] }
                if (parentNumber == null) {
                    set.copy(parentSetUid = null, dropIndex = 0, setNumber = 0)
                } else {
                    set.copy(
                        setNumber = parentNumber,
                        dropIndex = childNumberByUid[set.setUid] ?: set.dropIndex.coerceAtLeast(1)
                    )
                }
            } else {
                set.copy(setNumber = mainNumberByUid[set.setUid] ?: set.setNumber)
            }
        }
    }

    /** True only when the exercise has at least one main set and every row is complete. */
    fun isExerciseComplete(sets: List<LoggedSet>, exerciseId: Long): Boolean {
        val rows = sets.filter { it.exerciseId == exerciseId }
        if (rows.none { !it.isDropSet }) return false
        return rows.all { it.isCompleted }
    }

    fun completedExerciseIds(sets: List<LoggedSet>): Set<Long> =
        sets.asSequence().map { it.exerciseId }.distinct()
            .filter { isExerciseComplete(sets, it) }
            .toSet()

    /**
     * Completing a child implies its parent was performed, so the parent is completed too.
     * Un-completing a parent clears its children; a completed child with an incomplete parent is
     * never a valid state. Completing a parent never auto-completes its children.
     */
    fun toggleCompletion(
        sets: List<LoggedSet>,
        targetUid: String,
        completed: Boolean
    ): List<LoggedSet> {
        val target = sets.firstOrNull { it.setUid == targetUid } ?: return sets
        val parentUid = target.parentSetUid
        return sets.map { set ->
            when {
                set.setUid == targetUid -> set.copy(isCompleted = completed)
                completed && parentUid != null && set.setUid == parentUid ->
                    set.copy(isCompleted = true)
                !completed && !target.isDropSet && set.parentSetUid == target.setUid ->
                    set.copy(isCompleted = false)
                else -> set
            }
        }
    }

    /** Removing a main set removes its children; removing a child touches no sibling. */
    fun removeSet(sets: List<LoggedSet>, targetUid: String): List<LoggedSet> {
        val target = sets.firstOrNull { it.setUid == targetUid } ?: return sets
        val remaining = if (target.isDropSet) {
            sets.filterNot { it.setUid == targetUid }
        } else {
            sets.filterNot { it.setUid == targetUid || it.parentSetUid == target.setUid }
        }
        return renumberExerciseSets(remaining, target.exerciseId)
    }
}
