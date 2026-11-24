/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * New code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.ui.components

import com.example.data.model.LoggedSet

/** One rendered line in the Last Workout card: a main set or one of its drop children. */
data class RecentWorkoutSetLine(
    val set: LoggedSet,
    val label: String,
    val isDrop: Boolean
)

/** One exercise heading and its ordered set lines. */
data class RecentWorkoutExerciseBlock(
    val exerciseName: String,
    val supersetLabel: String?,
    val rows: List<RecentWorkoutSetLine>
)

/**
 * Presentation rules for the Home "Last Workout" card.
 *
 * The card must not infer parentage from row order or from duplicate set numbers. It reads the
 * persisted `setUid` / `parentSetUid` / `dropIndex` / `supersetGroupId` fields through the same
 * helpers the redesigned history screen uses, then labels a drop child `D1`, `D2`, ... under its
 * main set. Supersets use the existing A1/A2 paperclip convention and never absorb a drop child.
 */
object RecentWorkoutFormatter {

    fun blocks(sets: List<LoggedSet>): List<RecentWorkoutExerciseBlock> {
        if (sets.isEmpty()) return emptyList()

        val grouped = LinkedHashMap<String, MutableList<LoggedSet>>()
        sets.sortedWith(compareBy({ it.timestamp }, { it.id })).forEach { set ->
            grouped.getOrPut(set.exerciseName) { mutableListOf() }.add(set)
        }
        val labels = supersetLabels(sets)

        return grouped.map { (exerciseName, exerciseSets) ->
            var dropOrdinal = 0
            val rows = orderedSetRows(exerciseSets).map { row ->
                if (row.isDrop) {
                    dropOrdinal++
                    RecentWorkoutSetLine(
                        set = row.set,
                        label = "D${row.set.dropIndex.takeIf { it > 0 } ?: dropOrdinal}",
                        isDrop = true
                    )
                } else {
                    dropOrdinal = 0
                    RecentWorkoutSetLine(
                        set = row.set,
                        label = "Set ${row.set.setNumber}",
                        isDrop = false
                    )
                }
            }
            RecentWorkoutExerciseBlock(
                exerciseName = exerciseName,
                supersetLabel = labels[exerciseName],
                rows = rows
            )
        }
    }

    /**
     * A1/A2 is derived from the stored superset group id, exactly like Workout History.
     *
     * Only groups with two distinct exercise names are labelled. Drop rows are excluded so a child
     * can never be mistaken for a superset member. A historical set with a null or blank group id
     * simply gets no badge.
     */
    fun supersetLabels(sets: List<LoggedSet>): Map<String, String> {
        val groups = LinkedHashMap<String, LinkedHashSet<String>>()
        sets.forEach { set ->
            val groupId = set.supersetGroupId
            if (!set.isDropSet && !groupId.isNullOrBlank()) {
                groups.getOrPut(groupId) { LinkedHashSet() }.add(set.exerciseName)
            }
        }
        return buildMap {
            groups.values.filter { it.size == 2 }.forEach { names ->
                names.forEachIndexed { index, name -> put(name, "A${index + 1}") }
            }
        }
    }
}
