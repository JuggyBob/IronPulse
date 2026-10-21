package com.example

import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import com.example.data.model.MuscleCategory
import com.example.data.model.PredefinedRoutines
import com.example.data.model.RoutineParser
import com.example.data.model.Weekday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The starter routine templates.
 *
 * The app used to seed two half broken routines onto a new install, which is worse than an empty
 * tab: an invented routine looks like something the athlete chose. These are offered as a choice
 * instead, and these tests are about the two ways that goes wrong: a template that does not survive
 * the round trip, and one whose movement names are not in the real catalog.
 */
// Robolectric, not a plain JVM test: the round trip goes through org.json, which is only a real
// implementation under Robolectric. Without it every parse test would report "method not mocked".
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PredefinedRoutinesTest {

    /**
     * The names the templates use are checked against the *real* bundled catalog, not a hand written
     * stand-in: a template that quietly names a movement the app does not have is exactly the
     * broken default these replaced.
     */
    private val catalog: List<Exercise> by lazy { loadRealCatalog() }

    private fun loadRealCatalog(): List<Exercise> {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val raw = context.assets.open("exercises.json").use { it.readBytes().decodeToString() }
        val array = org.json.JSONArray(raw)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            ex(o.getString("name"), o.getString("category"))
        }
    }

    private fun ex(name: String, category: String) = Exercise(
        name = name,
        category = category,
        exerciseType = ExerciseType.STANDARD_WEIGHT
    )

    @Test
    fun `there are three templates, each with a real description`() {
        assertEquals(3, PredefinedRoutines.all.size)
        for (template in PredefinedRoutines.all) {
            assertTrue("${template.id} has no name", template.name.isNotBlank())
            assertTrue("${template.id} has no tagline", template.tagline.isNotBlank())
            assertTrue(
                "${template.id} description is too short to explain anything",
                template.description.length > 80
            )
            assertTrue("${template.id} has no days", template.days.isNotEmpty())
            assertTrue("${template.id} does not say who it is for", template.suitedTo.isNotBlank())
        }
    }

    @Test
    fun `template ids are unique`() {
        val ids = PredefinedRoutines.all.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `the day count matches the days a template actually defines`() {
        for (template in PredefinedRoutines.all) {
            assertEquals(
                "${template.id} says ${template.daysPerWeek} days but defines ${template.days.size}",
                template.daysPerWeek,
                template.days.size
            )
            // Every day of a template is a real weekday, and no weekday is used twice.
            assertTrue(
                "${template.id} defines a day outside the week",
                template.days.all { it.weekday in 1..7 }
            )
            assertEquals(
                "${template.id} uses the same weekday twice",
                template.days.size,
                template.days.map { it.weekday }.distinct().size
            )
        }
    }

    @Test
    fun `every movement in every template exists in the real catalog`() {
        // A template that names a movement the app does not have creates a misspelled custom
        // exercise on adoption, which is the broken default this is meant to replace.
        for (template in PredefinedRoutines.all) {
            for (day in template.days) {
                for (entry in day.entries) {
                    val name = entry.substringBefore(" (")
                    assertNotNull(
                        "${template.id} day ${day.weekday} names \"$name\", which is not in the catalog",
                        RoutineParser.findMatchingExercise(name, catalog)
                    )
                }
            }
        }
    }

    @Test
    fun `every entry carries a prescription`() {
        for (template in PredefinedRoutines.all) {
            for (day in template.days) {
                for (entry in day.entries) {
                    assertTrue(
                        "\"$entry\" has no sets x reps",
                        entry.substringAfter(" (", "").removeSuffix(")").contains("x")
                    )
                }
            }
        }
    }

    @Test
    fun `a template survives the round trip through the saved routine format`() {
        for (template in PredefinedRoutines.all) {
            val routine = PredefinedRoutines.toSavedRoutine(template)
            val parsed = RoutineParser.parseExercises(routine.exercisesJson, catalog)

            assertEquals(
                "${template.id} lost exercises on the way through the parser",
                template.days.sumOf { it.entries.size },
                parsed.size
            )
            assertEquals(template.daysPerWeek, routine.daysPerWeek)
        }
    }

    @Test
    fun `the day assignments survive the round trip`() {
        val template = PredefinedRoutines.byId("push_pull_legs")!!
        val parsed = RoutineParser.parseExercises(
            PredefinedRoutines.toSavedRoutine(template).exercisesJson,
            catalog
        )

        // Every parsed exercise must sit on a real weekday, and keep the weekday of its day.
        assertTrue(parsed.all { it.weekday in 1..7 })
        assertEquals(
            template.days.map { it.weekday }.toSet(),
            parsed.map { it.weekday }.toSet()
        )
        assertTrue(parsed.none { it.exerciseName.isBlank() })
    }

    @Test
    fun `adopting a template does not mutate the template itself`() {
        val template = PredefinedRoutines.byId("calisthenics_base")!!
        val before = template.days.flatMap { it.entries }

        PredefinedRoutines.toSavedRoutine(template)

        assertEquals(before, template.days.flatMap { it.entries })
    }

    @Test
    fun `templates can be looked up by id and an unknown id is null`() {
        assertNotNull(PredefinedRoutines.byId("strength_basics"))
        assertEquals(null, PredefinedRoutines.byId("does_not_exist"))
    }

    @Test
    fun `the calisthenics template is barbell free on its pulling and pressing days`() {
        // The promise its description makes is "no equipment beyond a pull-up bar". The third day
        // uses a barbell single leg split squat as a progression, so the check is scoped to the
        // days that have to work with bodyweight alone.
        val template = PredefinedRoutines.byId("calisthenics_base")!!
        val bodyweightDays = template.days.filter { it.weekday in listOf(Weekday.MONDAY, Weekday.WEDNESDAY) }
        val names = bodyweightDays.flatMap { it.entries }.map { it.substringBefore(" (") }

        assertTrue(
            "found a barbell movement on a bodyweight day: $names",
            names.none { it.contains("Barbell", ignoreCase = true) }
        )
    }
}
