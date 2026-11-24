/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.LoggedSet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExerciseHistoryPaginationTest {

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

    @Test
    fun `history pages by distinct local day and never duplicates a day`() = runBlocking {
        val base = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val dayMillis = 24L * 60L * 60L * 1000L
        val sets = buildList {
            repeat(12) { day ->
                repeat(2) { setIndex ->
                    add(
                        LoggedSet(
                            sessionId = 1L,
                            exerciseId = 1L,
                            exerciseName = "Push-Up",
                            exerciseType = com.example.data.model.ExerciseType.PURE_BODYWEIGHT,
                            setNumber = setIndex + 1,
                            reps = 10,
                            isCompleted = true,
                            timestamp = base + day * dayMillis + setIndex * 1000L
                        )
                    )
                }
            }
        }
        db.workoutDao().insertSets(sets)

        assertEquals(12, db.workoutDao().countExerciseHistoryDays("Push-Up"))

        val first = db.workoutDao().getSetsForExerciseHistoryPage("Push-Up", 5, 0)
        val second = db.workoutDao().getSetsForExerciseHistoryPage("Push-Up", 5, 5)
        val third = db.workoutDao().getSetsForExerciseHistoryPage("Push-Up", 5, 10)
        assertEquals(10, first.size)
        assertEquals(10, second.size)
        assertEquals(4, third.size)

        fun dayKey(timestamp: Long): Long {
            val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
            return (cal.get(Calendar.YEAR) * 10000L +
                (cal.get(Calendar.MONTH) + 1) * 100L +
                cal.get(Calendar.DAY_OF_MONTH))
        }

        val firstDays = first.map { dayKey(it.timestamp) }.distinct()
        val secondDays = second.map { dayKey(it.timestamp) }.distinct()
        val thirdDays = third.map { dayKey(it.timestamp) }.distinct()
        assertEquals(5, firstDays.size)
        assertEquals(5, secondDays.size)
        assertEquals(2, thirdDays.size)
        assertTrue(firstDays.intersect(secondDays.toSet()).isEmpty())
        assertTrue(secondDays.intersect(thirdDays.toSet()).isEmpty())
    }

    @Test
    fun `exercise history filter returns distinct matching session ids`() = runBlocking {
        val base = System.currentTimeMillis()
        db.workoutDao().insertSets(
            listOf(
                LoggedSet(
                    sessionId = 10L,
                    exerciseId = 1L,
                    exerciseName = "Push-Up",
                    exerciseType = com.example.data.model.ExerciseType.PURE_BODYWEIGHT,
                    setNumber = 1,
                    reps = 10,
                    isCompleted = true,
                    timestamp = base
                ),
                LoggedSet(
                    sessionId = 10L,
                    exerciseId = 1L,
                    exerciseName = "Push-Up",
                    exerciseType = com.example.data.model.ExerciseType.PURE_BODYWEIGHT,
                    setNumber = 2,
                    reps = 8,
                    isCompleted = true,
                    timestamp = base + 1000L
                ),
                LoggedSet(
                    sessionId = 20L,
                    exerciseId = 1L,
                    exerciseName = "Push-Up",
                    exerciseType = com.example.data.model.ExerciseType.PURE_BODYWEIGHT,
                    setNumber = 1,
                    reps = 12,
                    isCompleted = true,
                    timestamp = base + 2000L
                ),
                LoggedSet(
                    sessionId = 20L,
                    exerciseId = 2L,
                    exerciseName = "Squat",
                    exerciseType = com.example.data.model.ExerciseType.PURE_BODYWEIGHT,
                    setNumber = 1,
                    reps = 10,
                    isCompleted = true,
                    timestamp = base + 3000L
                )
            )
        )

        assertEquals(
            setOf(10L, 20L),
            db.workoutDao().getSessionIdsForExercise("Push-Up").first().toSet()
        )
        assertTrue(db.workoutDao().getSessionIdsForExercise("Missing").first().isEmpty())
    }
}
