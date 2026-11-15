/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.data.model

/** Goals offered by the Schedule editor, matching the existing routine builder vocabulary. */
object PlanGoals {
    val all = listOf(
        "Strength & Muscle Growth",
        "Strength & Power",
        "Hypertrophy & Muscle Growth",
        "Calisthenics Mastery",
        "Conditioning & Fat Loss"
    )

    /** A short explanation of what the strategy changes in the plan. */
    fun description(goal: String): String = when (goal) {
        "Strength & Power" ->
            "Heavy compound sets, longer rests and an emphasis on explosive intent."
        "Hypertrophy & Muscle Growth" ->
            "Moderate reps, controlled tempo and higher weekly volume."
        "Calisthenics Mastery" ->
            "Bodyweight progressions, holds and skill practice."
        "Conditioning & Fat Loss" ->
            "Denser sessions with shorter rests and more time-based work."
        else ->
            "Low-rep compound work with enough volume to add muscle."
    }
}

data class ScheduleTemplate(
    val id: String,
    val name: String,
    val description: String,
    val goal: String,
    val days: List<ScheduleTemplateDay>
) {
    val trainingDays: Int get() = days.count { it.exercises.isNotEmpty() }
    val uniqueRoutines: Int get() = days.count { it.exercises.isNotEmpty() }
}

data class ScheduleTemplateDay(
    val weekday: Int,
    val focus: String,
    val exercises: List<ScheduleTemplateExercise>
)

data class ScheduleTemplateExercise(
    val name: String,
    val sets: Int,
    val reps: Int,
    val restSeconds: Int = 90,
    val holdSeconds: Int = 0
)

object ScheduleTemplates {
    private fun ex(name: String, sets: Int, reps: Int, rest: Int = 90) =
        ScheduleTemplateExercise(name, sets, reps, rest)

    private val fullBodyA = ScheduleTemplateDay(
        Weekday.MONDAY, "Full Body A", listOf(
            ex("Barbell Full Squat", 4, 6, 150),
            ex("Barbell Bench Press", 4, 6, 150),
            ex("Barbell Bent Over Row", 4, 8, 120),
            ex("Front Plank with Twist", 3, 60, 60)
        )
    )
    private val fullBodyB = ScheduleTemplateDay(
        Weekday.WEDNESDAY, "Full Body B", listOf(
            ex("Barbell Deadlift", 4, 5, 180),
            ex("Barbell Seated Overhead Press", 4, 6, 150),
            ex("Cable Lat Pulldown Full Range of Motion", 3, 10, 90),
            ex("Hanging Leg Raise", 3, 15, 60)
        )
    )
    private val fullBodyC = ScheduleTemplateDay(
        Weekday.FRIDAY, "Full Body C", listOf(
            ex("Barbell Front Squat", 3, 8, 150),
            ex("Barbell Incline Bench Press", 4, 8, 120),
            ex("Barbell Bent Over Row", 3, 10, 90),
            ex("Push-Up", 3, 15, 60)
        )
    )

    private val upperA = ScheduleTemplateDay(
        Weekday.MONDAY, "Upper A", listOf(
            ex("Barbell Bench Press", 4, 6, 150),
            ex("Barbell Bent Over Row", 4, 8, 120),
            ex("Dumbbell Seated Lateral Raise", 3, 12, 60),
            ex("Barbell Lying Triceps Extension", 3, 10, 60)
        )
    )
    private val lowerA = ScheduleTemplateDay(
        Weekday.TUESDAY, "Lower A", listOf(
            ex("Barbell Full Squat", 4, 6, 150),
            ex("Barbell Romanian Deadlift", 3, 10, 120),
            ex("Barbell Lying Leg Curl", 3, 12, 60),
            ex("Bodyweight Standing Calf Raise", 4, 15, 60)
        )
    )
    private val upperB = ScheduleTemplateDay(
        Weekday.THURSDAY, "Upper B", listOf(
            ex("Barbell Seated Overhead Press", 4, 6, 150),
            ex("Cable Lat Pulldown Full Range of Motion", 4, 10, 90),
            ex("Dumbbell Incline Bench Press", 3, 10, 90),
            ex("Barbell Curl", 3, 12, 60)
        )
    )
    private val lowerB = ScheduleTemplateDay(
        Weekday.FRIDAY, "Lower B", listOf(
            ex("Barbell Deadlift", 3, 5, 180),
            ex("Barbell Single Leg Split Squat", 3, 10, 90),
            ex("Bodyweight Drop Jump Squat", 3, 20, 60),
            ex("Hanging Leg Raise", 3, 15, 60)
        )
    )

    private val push = ScheduleTemplateDay(
        Weekday.MONDAY, "Push", listOf(
            ex("Barbell Bench Press", 4, 8, 120),
            ex("Dumbbell Incline Bench Press", 3, 10, 90),
            ex("Barbell Seated Overhead Press", 3, 10, 90),
            ex("Barbell Lying Triceps Extension", 3, 12, 60)
        )
    )
    private val pull = ScheduleTemplateDay(
        Weekday.TUESDAY, "Pull", listOf(
            ex("Barbell Deadlift", 4, 5, 180),
            ex("Barbell Bent Over Row", 4, 8, 120),
            ex("Cable Lat Pulldown Full Range of Motion", 3, 10, 90),
            ex("Barbell Curl", 3, 12, 60)
        )
    )
    private val legs = ScheduleTemplateDay(
        Weekday.WEDNESDAY, "Legs", listOf(
            ex("Barbell Full Squat", 4, 8, 150),
            ex("Barbell Romanian Deadlift", 3, 10, 120),
            ex("Barbell Lying Leg Curl", 3, 12, 60),
            ex("Bodyweight Standing Calf Raise", 4, 15, 60)
        )
    )
    private val upper = ScheduleTemplateDay(
        Weekday.THURSDAY, "Upper", listOf(
            ex("Barbell Incline Bench Press", 4, 8, 120),
            ex("Cable Lat Pulldown Full Range of Motion", 4, 10, 90),
            ex("Dumbbell Seated Lateral Raise", 3, 12, 60),
            ex("Barbell Curl", 3, 12, 60)
        )
    )
    private val lower = ScheduleTemplateDay(
        Weekday.FRIDAY, "Lower", listOf(
            ex("Barbell Front Squat", 4, 8, 150),
            ex("Barbell Romanian Deadlift", 3, 10, 120),
            ex("Barbell Single Leg Split Squat", 3, 10, 90),
            ex("Front Plank with Twist", 3, 60, 60)
        )
    )

    val all: List<ScheduleTemplate> = listOf(
        ScheduleTemplate(
            id = "full_body_3",
            name = "Three-Day Full Body",
            description = "Three balanced full-body sessions with a rest day between each.",
            goal = "Strength & Muscle Growth",
            days = listOf(fullBodyA, fullBodyB, fullBodyC)
        ),
        ScheduleTemplate(
            id = "upper_lower_4",
            name = "Four-Day Upper / Lower",
            description = "Two upper-body and two lower-body sessions, each repeated once a week.",
            goal = "Hypertrophy & Muscle Growth",
            days = listOf(upperA, lowerA, upperB, lowerB)
        ),
        ScheduleTemplate(
            id = "ppl_5",
            name = "Five-Day Push Pull Legs",
            description = "Push, pull and legs plus an upper and lower day for higher weekly volume.",
            goal = "Hypertrophy & Muscle Growth",
            days = listOf(push, pull, legs, upper, lower)
        ),
        ScheduleTemplate(
            id = "ppl_6",
            name = "Six-Day Push Pull Legs",
            description = "The classic push/pull/legs week repeated twice, with Sunday as rest.",
            goal = "Hypertrophy & Muscle Growth",
            days = listOf(push, pull, legs, push, pull, legs)
        )
    )
}
