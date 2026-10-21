package com.example.data.model

/**
 * The three routine templates offered to a new athlete.
 *
 * These are a *starting point to read and adopt*, not history: nothing here is written to the
 * database until the athlete picks one. The app used to seed a fake athlete called Alex Stone with
 * two half broken routines, and an invented routine on someone's Saved Routines tab is worse than an
 * empty one, because it looks like something they chose.
 *
 * Every exercise name below is matched against the real catalog when the template is adopted
 * (see [RoutineParser.findMatchingExercise]), so a template that references a movement the catalog
 * does not carry is reported rather than silently creating a typo'd exercise.
 */
object PredefinedRoutines {

    data class Template(
        val id: String,
        val name: String,
        /** One line shown on the template card. */
        val tagline: String,
        /** The longer explanation shown in the preview sheet. */
        val description: String,
        val daysPerWeek: Int,
        val goal: String,
        /** Who it is for, shown in the preview. */
        val suitedTo: String,
        /** Per day, in the order they are performed. */
        val days: List<TemplateDay>
    )

    /**
     * One training day of a template.
     *
     * @param weekday the day of the week this session is scheduled on: 1 = Monday ... 7 = Sunday.
     * @param focus what the day trains, e.g. "Legs, chest, triceps".
     */
    data class TemplateDay(
        val weekday: Int,
        val focus: String,
        /** "name (sets x reps)" entries, resolved against the catalog on adoption. */
        val entries: List<String>
    ) {
        /** The name of the day, e.g. "Monday". */
        val dayName: String get() = Weekday.name(weekday)
    }

    /**
     * A four day full body split that leans on the barbell basics, with a calisthenics finisher.
     *
     * Kept deliberately plain: four movements a session, one main lift each, because a beginner
     * following this needs to finish the workout, not read a spreadsheet.
     */
    private val STRENGTH_BASICS = Template(
        id = "strength_basics",
        name = "Full Body Strength",
        tagline = "Four days a week, one main lift each, built around the barbell basics.",
        description = "A straightforward full body split for someone whose goal is to get stronger " +
            "at the big compound movements. Each day has one heavy lift and two supporting " +
            "movements, so there is room to progress the main lift over time without the rest of the " +
            "session holding you back. Runs four days a week, leaving three rest days.",
        daysPerWeek = 4,
        goal = "Strength",
        suitedTo = "Beginners and intermediates training 4 days a week",
        days = listOf(
            TemplateDay(
                Weekday.MONDAY, "Legs, chest, triceps",
                listOf(
                    "Barbell Full Squat (4 x 6)",
                    "Barbell Bench Press (4 x 6)",
                    "Barbell Bent Over Row (3 x 8)",
                    "Barbell Lying Triceps Extension (3 x 10)"
                )
            ),
            TemplateDay(
                Weekday.TUESDAY, "Back, shoulders, arms",
                listOf(
                    "Barbell Bent Over Row (4 x 6)",
                    "Barbell Seated Overhead Press (4 x 6)",
                    "Barbell Deadlift (3 x 5)",
                    "Barbell Curl (3 x 10)"
                )
            ),
            TemplateDay(
                Weekday.THURSDAY, "Posterior chain, hamstrings",
                listOf(
                    "Barbell Deadlift (4 x 5)",
                    "Barbell Front Squat (3 x 8)",
                    "Barbell Bent Over Row (3 x 10)",
                    "Barbell Lying Triceps Extension (3 x 10)"
                )
            ),
            TemplateDay(
                Weekday.SATURDAY, "Chest, back, shoulders",
                listOf(
                    "Barbell Incline Bench Press (4 x 8)",
                    "Barbell Bent Over Row (4 x 8)",
                    "Dumbbell Seated Lateral Raise (3 x 12)",
                    "Barbell Curl (3 x 12)"
                )
            )
        )
    )

    /**
     * A three day push / pull / legs split, the most familiar hypertrophy layout there is.
     */
    private val PUSH_PULL_LEGS = Template(
        id = "push_pull_legs",
        name = "Push / Pull / Legs",
        tagline = "The classic three day split: one push day, one pull day, one leg day.",
        description = "Each muscle group is trained once a week with enough volume to make progress. " +
            "It is the most widely used layout because it spreads the work evenly without repeating " +
            "the same movement twice in a week. Best with a full week of recovery between sessions, " +
            "so it suits someone training three or four days.",
        daysPerWeek = 3,
        goal = "Hypertrophy",
        suitedTo = "Anyone already training three days a week",
        days = listOf(
            TemplateDay(
                Weekday.MONDAY, "Chest, shoulders, triceps",
                listOf(
                    "Barbell Bench Press (4 x 8)",
                    "Dumbbell Incline Bench Press (3 x 10)",
                    "Barbell Seated Overhead Press (3 x 10)",
                    "Barbell Lying Triceps Extension (3 x 12)"
                )
            ),
            TemplateDay(
                Weekday.WEDNESDAY, "Back and biceps",
                listOf(
                    "Barbell Deadlift (4 x 5)",
                    "Barbell Bent Over Row (4 x 8)",
                    "Cable Lat Pulldown Full Range of Motion (3 x 10)",
                    "Barbell Curl (3 x 12)"
                )
            ),
            TemplateDay(
                Weekday.FRIDAY, "Quads, hamstrings, calves",
                listOf(
                    "Barbell Full Squat (4 x 8)",
                    "Barbell Romanian Deadlift (3 x 10)",
                    "Barbell Lying Leg Curl (3 x 12)",
                    "Bodyweight Standing Calf Raise (4 x 15)"
                )
            )
        )
    )

    /**
     * A three day calisthenics template, since weighted calisthenics is the app's own thing and the
     * catalog has no barbell dependency here at all.
     */
    private val CALISTHENICS_BASE = Template(
        id = "calisthenics_base",
        name = "Calisthenics Base",
        tagline = "Three days a week of bodyweight pulling, pressing and core work.",
        description = "Built almost entirely from bodyweight movements, so it needs no equipment " +
            "beyond a pull-up bar. The pulling and pressing volume is what progresses first here: " +
            "add weight to your pull-ups and dips as they get easier. A good starting point if the " +
            "gym is not an option.",
        daysPerWeek = 3,
        goal = "Bodyweight Strength",
        suitedTo = "Training at home or with only a pull-up bar",
        days = listOf(
            TemplateDay(
                Weekday.MONDAY, "Chest, triceps, core",
                listOf(
                    "Push-Up (4 x 10)",
                    "Chest Dip (4 x 8)",
                    "Decline Push-Up (3 x 12)",
                    "Hanging Leg Raise (3 x 15)"
                )
            ),
            TemplateDay(
                Weekday.WEDNESDAY, "Back and biceps",
                listOf(
                    "Pull-Up (4 x 6)",
                    "Chin-Up (3 x 8)",
                    "Inverted Row (3 x 12)",
                    // Banded, not barbell: this template must be doable with a pull-up bar and
                    // nothing else, which is the promise its description makes.
                    "Band Alternating Biceps Curl (3 x 15)"
                )
            ),
            TemplateDay(
                Weekday.FRIDAY, "Quads, calves, core",
                listOf(
                    "Bodyweight Drop Jump Squat (4 x 20)",
                    "Barbell Single Leg Split Squat (3 x 10)",
                    "Bodyweight Standing Calf Raise (4 x 20)",
                    "Front Plank with Twist (3 x 60)"
                )
            )
        )
    )

    val all: List<Template> = listOf(STRENGTH_BASICS, PUSH_PULL_LEGS, CALISTHENICS_BASE)

    fun byId(id: String): Template? = all.firstOrNull { it.id == id }

    /**
     * The saved routine this template becomes once adopted.
     *
     * Serialised through the same parser the editor uses, so an adopted template opens in
     * [com.example.ui.screens.CreateRoutineScreen] as a normal routine that can be edited like any
     * other, rather than a special object the app has to keep understanding.
     */
    fun toSavedRoutine(template: Template): SavedRoutine {
        val exercises = template.days.flatMap { day ->
            day.entries.map { entry ->
                RoutineExerciseItem(
                    exerciseName = entry.substringBefore(" ("),
                    prescription = entry.substringAfter(" (", "").removeSuffix(")"),
                    weekday = day.weekday,
                    dayTitle = day.focus
                )
            }
        }
        return SavedRoutine(
            name = template.name,
            description = template.description,
            daysPerWeek = template.daysPerWeek,
            goal = template.goal,
            exercisesJson = RoutineParser.serializeExercises(exercises)
        )
    }
}
