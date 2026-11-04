/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * New standalone code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.AndroidWidgetKind
import com.example.data.model.AndroidWidgetThemes
import com.example.data.model.ExerciseType
import com.example.data.model.LocalDateKey
import com.example.data.model.LoggedSet
import com.example.data.model.ProfileMetrics
import com.example.data.model.RoutineExerciseItem
import com.example.data.model.RoutineParser
import com.example.data.model.SavedRoutine
import com.example.data.model.ScheduledWorkoutDay
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutSession
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.preferences.userDataStore
import com.example.ui.theme.ThemeMode
import com.example.widget.WidgetTheme
import com.example.widget.WorkoutWidgetDataSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Native launcher widget data and independent appearance.
 *
 * The data test uses the same singleton database and preference repository as the app, so it proves
 * the widget is reading the real persistence layer rather than a parallel cache.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidWidgetTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var preferences: UserPreferencesRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.userDataStore.edit { it.clear() }
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferences = UserPreferencesRepository(context)
        val todayKey = LocalDateKey.keyOf(System.currentTimeMillis())
        val todayWeekday = LocalDateKey.isoDayOfWeek(todayKey)
        db.savedRoutineDao().insertRoutine(
            SavedRoutine(
                id = 40L,
                name = "Widget Plan",
                description = "",
                daysPerWeek = 1,
                exercisesJson = RoutineParser.serializeExercises(
                    listOf(
                        RoutineExerciseItem(
                            exerciseId = 1,
                            exerciseName = "Push-up",
                            exerciseType = ExerciseType.PURE_BODYWEIGHT,
                            category = "Chest",
                            equipment = "Bodyweight",
                            targetSets = 2,
                            targetReps = 12,
                            weekday = todayWeekday,
                            dayTitle = "Chest Day"
                        )
                    )
                )
            )
        )
        preferences.setActiveRoutineId(40L)
    }

    @After
    fun tearDown() = runBlocking {
        context.userDataStore.edit { it.clear() }
        db.close()
    }

    @Test
    fun `the three widget themes are independent`() {
        val initial = AndroidWidgetThemes()
        assertEquals(ThemeMode.IRONPULSE, initial.streak)
        assertEquals(ThemeMode.IRONPULSE, initial.weekly)
        assertEquals(ThemeMode.IRONPULSE, initial.profile)

        val changed = initial
            .with(AndroidWidgetKind.STREAK, ThemeMode.AMOLED)
            .with(AndroidWidgetKind.WEEKLY, ThemeMode.LIGHT)
            .with(AndroidWidgetKind.PROFILE, ThemeMode.DARK)

        assertEquals(ThemeMode.AMOLED, changed.forKind(AndroidWidgetKind.STREAK))
        assertEquals(ThemeMode.LIGHT, changed.forKind(AndroidWidgetKind.WEEKLY))
        assertEquals(ThemeMode.DARK, changed.forKind(AndroidWidgetKind.PROFILE))
    }

    @Test
    fun `all twelve widget theme combinations stay scoped to one widget`() {
        for (kind in AndroidWidgetKind.entries) {
            for (mode in ThemeMode.entries) {
                val themes = AndroidWidgetThemes().with(kind, mode)
                assertEquals(mode, themes.forKind(kind))
                AndroidWidgetKind.entries.filter { it != kind }.forEach { other ->
                    assertEquals(ThemeMode.IRONPULSE, themes.forKind(other))
                }
            }
        }
    }

    @Test
    fun `widget theme choices survive a new repository instance`() = runBlocking {
        preferences.setAndroidWidgetTheme(AndroidWidgetKind.STREAK, ThemeMode.AMOLED)
        preferences.setAndroidWidgetTheme(AndroidWidgetKind.WEEKLY, ThemeMode.LIGHT)
        preferences.setAndroidWidgetTheme(AndroidWidgetKind.PROFILE, ThemeMode.DARK)

        val reopened = UserPreferencesRepository(context)
        val themes = reopened.androidWidgetThemesFlow.first()
        assertEquals(ThemeMode.AMOLED, themes.streak)
        assertEquals(ThemeMode.LIGHT, themes.weekly)
        assertEquals(ThemeMode.DARK, themes.profile)
    }

    @Test
    fun `the four palettes are distinct and AMOLED uses true black`() {
        val light = WidgetTheme.palette(ThemeMode.LIGHT)
        val dark = WidgetTheme.palette(ThemeMode.DARK)
        val amoled = WidgetTheme.palette(ThemeMode.AMOLED)
        val ironPulse = WidgetTheme.palette(ThemeMode.IRONPULSE)

        assertEquals(0xFF000000.toInt(), amoled.background)
        assertNotEquals(light.background, dark.background)
        assertNotEquals(dark.background, ironPulse.background)
        assertNotEquals(amoled.background, dark.background)
        assertNotEquals(light.textPrimary, amoled.textPrimary)
    }

    @Test
    fun `BMI is unavailable rather than misleading when the inputs are missing`() {
        assertEquals(0.0, ProfileMetrics.bmi(0.0, 180.0), 0.0)
        assertEquals(0.0, ProfileMetrics.bmi(80.0, 0.0), 0.0)
        assertEquals("—", ProfileMetrics.bmiText(0.0, 180.0))
        assertEquals("24.2", ProfileMetrics.bmiText(80.0, 182.0))
    }

    @Test
    fun `widget data uses the real streak, seven day window, today plan and profile`() = runBlocking {
        val now = System.currentTimeMillis()
        val todayStart = com.example.data.model.ActivityHeatmap.startOfDay(now)
        val todayKey = LocalDateKey.keyOf(now)

        preferences.saveUserProfile(
            UserProfile(
                name = "Widget Athlete",
                heightCm = 180.0,
                currentWeightKg = 81.0,
                targetWeightKg = 85.0,
                preferredUnit = "kg"
            )
        )
        db.scheduleDao().insertScheduleDays(
            listOf(
                ScheduledWorkoutDay(
                    dateStart = todayStart,
                    dateKey = todayKey,
                    routineId = 40L,
                    weekday = LocalDateKey.isoDayOfWeek(todayKey),
                    dayTitle = "Chest Day",
                    exerciseCount = 2
                )
            )
        )
        val sessionId = db.workoutDao().insertSession(
            WorkoutSession(
                title = "Chest Day",
                startTime = now - 30L * 60L * 1000L,
                endTime = now,
                routineId = 40L,
                routineWeekday = LocalDateKey.isoDayOfWeek(todayKey),
                plannedDate = todayStart,
                plannedDateKey = todayKey
            )
        )
        db.workoutDao().insertSets(
            listOf(
                LoggedSet(
                    sessionId = sessionId,
                    exerciseId = 1,
                    exerciseName = "Push-up",
                    exerciseType = ExerciseType.PURE_BODYWEIGHT,
                    setNumber = 1,
                    reps = 12,
                    isCompleted = true
                )
            )
        )
        preferences.setAndroidWidgetTheme(AndroidWidgetKind.STREAK, ThemeMode.AMOLED)
        preferences.setAndroidWidgetTheme(AndroidWidgetKind.WEEKLY, ThemeMode.LIGHT)
        preferences.setAndroidWidgetTheme(AndroidWidgetKind.PROFILE, ThemeMode.DARK)

        val snapshot = WorkoutWidgetDataSource.load(db, preferences, now)

        assertTrue(snapshot.streak.current >= 1)
        assertEquals(7, snapshot.weekly.days.size)
        assertTrue(snapshot.weekly.days.last().completed)
        assertTrue(snapshot.weekly.days.last().isToday)
        assertTrue(snapshot.weekly.workoutDay)
        assertEquals("Chest Day", snapshot.weekly.workoutName)
        assertEquals("Widget Athlete", snapshot.profile.name)
        assertEquals("81 kg", snapshot.profile.weight)
        assertEquals("180 cm", snapshot.profile.height)
        assertEquals("25.0", snapshot.profile.bmi)
        assertEquals(ThemeMode.AMOLED, snapshot.themes.streak)
        assertEquals(ThemeMode.LIGHT, snapshot.themes.weekly)
        assertEquals(ThemeMode.DARK, snapshot.themes.profile)
    }

    @Test
    fun `a planned rest row is shown as a rest day`() = runBlocking {
        val now = System.currentTimeMillis()
        val todayStart = com.example.data.model.ActivityHeatmap.startOfDay(now)
        val todayKey = LocalDateKey.keyOf(now)
        db.scheduleDao().insertScheduleDays(
            listOf(
                ScheduledWorkoutDay(
                    dateStart = todayStart,
                    dateKey = todayKey,
                    routineId = 40L,
                    weekday = LocalDateKey.isoDayOfWeek(todayKey),
                    dayTitle = "",
                    exerciseCount = 0
                )
            )
        )
        val snapshot = WorkoutWidgetDataSource.load(db, preferences, now)
        assertFalse(snapshot.weekly.workoutDay)
        assertEquals("", snapshot.weekly.workoutName)
    }

    @Test
    fun `a day with no completed set is not shown as a workout day`() = runBlocking {
        val now = System.currentTimeMillis()
        val todayStart = com.example.data.model.ActivityHeatmap.startOfDay(now)
        val todayKey = LocalDateKey.keyOf(now)
        db.scheduleDao().insertScheduleDays(
            listOf(
                ScheduledWorkoutDay(
                    dateStart = todayStart,
                    dateKey = todayKey,
                    routineId = 40L,
                    weekday = LocalDateKey.isoDayOfWeek(todayKey),
                    dayTitle = "Chest Day",
                    exerciseCount = 2
                )
            )
        )
        val snapshot = WorkoutWidgetDataSource.load(db, preferences, now)
        assertFalse(snapshot.weekly.days.last().completed)
        assertTrue(snapshot.weekly.workoutDay)
    }
}
