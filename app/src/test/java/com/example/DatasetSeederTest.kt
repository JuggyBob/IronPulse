package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import com.example.data.model.MuscleCategory
import com.example.data.seed.DatasetSeeder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The dataset seeder against the real bundled asset.
 *
 * The rules that matter and are easy to break:
 *  - a full catalog gets imported, with media, instructions and targets intact,
 *  - a custom exercise is never overwritten and never duplicated by a dataset row of the same name,
 *  - reseeding is atomic and idempotent, so a second run changes nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DatasetSeederTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seed() =
        DatasetSeeder.seedFresh(ApplicationProvider.getApplicationContext<Context>(), db.exerciseDao())

    @Test
    fun `the whole dataset is imported with its media and instructions`() = runBlocking {
        seed()

        val all = db.exerciseDao().getAllExercisesList()
        assertTrue("expected a large catalog, got ${all.size}", all.size > 1000)

        val bench = all.firstOrNull { it.name == "Barbell Bench Press" }
        assertNotNull("the dataset's own bench press must be present", bench)
        assertEquals("chest", bench!!.bodyPart)
        assertTrue("target must come from the dataset", bench.target.isNotBlank())
        assertTrue("English instructions must come from the dataset", bench.instructions.isNotBlank())
        assertTrue("the GIF path must come from the dataset", bench.mediaPath.endsWith(".gif"))
        assertTrue("every dataset row keeps its source id", bench.sourceId.isNotBlank())
        assertFalse("the library is never custom", bench.isCustom)
    }

    @Test
    fun `bodyweight exercises are split into the two calisthenics types`() = runBlocking {
        seed()

        val all = db.exerciseDao().getAllExercisesList()

        // "Front Plank with Twist" is bodyweight in the dataset and stays reps-only...
        val plank = all.first { it.name == "Front Plank with Twist" }
        assertEquals(ExerciseType.PURE_BODYWEIGHT, plank.exerciseType)

        // ...while the same movement loaded with a plate logs added weight instead.
        val weightedPlank = all.first { it.name == "Weighted Front Plank" }
        assertEquals(ExerciseType.ADDED_WEIGHT, weightedPlank.exerciseType)

        // Weighted calisthenics must survive the catalog swap. Matched case insensitively: the
        // dataset spells it "Weighted Pull-Up" while the IronPulse additions use "Weighted Pull-up",
        // and the seeder keeps exactly one of the two.
        val weightedPullUp = all.firstOrNull { it.name.equals("Weighted Pull-up", ignoreCase = true) }
        assertNotNull("weighted calisthenics must survive the catalog swap", weightedPullUp)
        assertEquals(ExerciseType.ADDED_WEIGHT, weightedPullUp!!.exerciseType)

        // Only one row may hold that name, whichever spelling won.
        assertEquals(
            "the two spellings must not both end up in the catalog",
            1,
            all.count { it.name.equals("Weighted Pull-up", ignoreCase = true) }
        )

        // A barbell movement must not be mistaken for either bodyweight kind.
        assertEquals(ExerciseType.STANDARD_WEIGHT, all.first { it.name == "Barbell Bench Press" }.exerciseType)
    }

    @Test
    fun `a custom exercise survives a reseed even when the dataset has the same name`() = runBlocking {
        val dao = db.exerciseDao()
        dao.insert(
            Exercise(
                name = "Barbell Bench Press",
                category = MuscleCategory.CHEST,
                exerciseType = ExerciseType.PURE_BODYWEIGHT,
                equipment = "My own bar",
                description = "The user's own version",
                isCustom = true
            )
        )

        seed()

        val matches = dao.getAllExercisesList().filter { it.name == "Barbell Bench Press" }
        assertEquals("the custom row must win the name outright", 1, matches.size)
        val row = matches.single()
        assertTrue("the custom row is still custom", row.isCustom)
        assertEquals("the user's edit must not be overwritten", "My own bar", row.equipment)
        assertEquals("The user's own version", row.description)
    }

    @Test
    fun `reseeding twice leaves the catalog byte for byte identical`() = runBlocking {
        seed()
        // Keyed by name, not by row id: the point is that a second reseed changes no data at all,
        // not that it happens to allocate the same auto increment ids.
        suspend fun snapshot(): Map<String, String> =
            db.exerciseDao().getAllExercisesList().associate { row ->
                row.name to listOf(
                    row.category, row.bodyPart, row.target, row.secondaryMuscles,
                    row.exerciseType, row.instructions, row.equipment,
                    row.mediaPath, row.sourceId,
                    row.isCustom.toString(), row.isAiGenerated.toString()
                ).joinToString("|")
            }

        val first = snapshot()
        assertTrue(first.size > 1000)

        seed()
        val second = snapshot()

        assertEquals("a reseed must not duplicate rows", first.keys, second.keys)
        val changed = first.keys.filter { first[it] != second[it] }
        assertEquals("a reseed must not change any row", emptyList<String>(), changed)
    }

    @Test
    fun `a library row is refreshed in place so logged sets keep their exercise`() = runBlocking {
        val dao = db.exerciseDao()
        // A stale library row under the name the dataset uses, carrying none of its data. It stands
        // in for a row an older app version inserted, whose id already has logged sets pointing at it.
        val legacyId = dao.insert(
            Exercise(
                name = "Barbell Bench Press",
                category = MuscleCategory.CHEST,
                exerciseType = ExerciseType.PURE_BODYWEIGHT,
                equipment = "Pull-up Bar"
            )
        )
        assertTrue(legacyId > 0)

        seed()

        val row = dao.getAllExercisesList().single { it.name == "Barbell Bench Press" }
        assertEquals("the row id must survive, or history is orphaned", legacyId, row.id)
        assertTrue("the dataset must fill in the media", row.mediaPath.endsWith(".gif"))
        assertTrue("the dataset must fill in the target", row.target.isNotBlank())
        assertTrue(row.instructions.isNotBlank())
        assertEquals(
            "the stale calisthenics type must be corrected to the dataset's",
            ExerciseType.STANDARD_WEIGHT,
            row.exerciseType
        )
    }

    @Test
    fun `every category the app filters on is represented in the imported catalog`() = runBlocking {
        seed()

        val categories = db.exerciseDao().getAllExercisesList().map { it.category }.toSet()
        for (chip in MuscleCategory.ALL) {
            assertTrue("no exercise landed in the $chip chip", chip in categories)
        }
    }
}
