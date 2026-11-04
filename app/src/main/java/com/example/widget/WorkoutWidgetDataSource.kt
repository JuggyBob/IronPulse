/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * New standalone code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.widget

import android.content.Context
import androidx.room.withTransaction
import com.example.data.db.AppDatabase
import com.example.data.model.ActivityHeatmap
import com.example.data.model.AndroidWidgetThemes
import com.example.data.model.HomeDashboard
import com.example.data.model.LocalDateKey
import com.example.data.model.ProfileMetrics
import com.example.data.model.RoutineParser
import com.example.data.model.SessionWithSets
import com.example.data.model.Weekday
import com.example.data.model.WeightConversion
import com.example.data.model.WeightUnit
import com.example.data.model.WorkoutStreak
import com.example.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

data class WidgetStreakData(
    val current: Int,
    val best: Int,
    val alive: Boolean
)

data class WidgetWeeklyDay(
    val label: String,
    val completed: Boolean,
    val isToday: Boolean
)

data class WidgetWeeklyData(
    val days: List<WidgetWeeklyDay>,
    val workoutDay: Boolean,
    val workoutName: String
)

data class WidgetProfileData(
    val name: String,
    val weight: String,
    val height: String,
    val bmi: String,
    val hasProfile: Boolean
)

data class WidgetSnapshot(
    val streak: WidgetStreakData,
    val weekly: WidgetWeeklyData,
    val profile: WidgetProfileData,
    val themes: AndroidWidgetThemes
)

/**
 * One read of the app's real persisted state for all three launcher widgets.
 *
 * The streak uses [WorkoutStreak.calculate], the same function as the in-app streak, and the seven
 * days use [ActivityHeatmap.startOfDay], the same local calendar boundary as Progress and Home.
 */
object WorkoutWidgetDataSource {

    suspend fun load(context: Context): WidgetSnapshot = load(
        db = AppDatabase.getInstance(context),
        prefs = UserPreferencesRepository(context)
    )

    /**
     * Testable core. Production supplies the app singleton and preference repository; tests can
     * supply an in-memory database without touching the process-wide app database.
     */
    internal suspend fun load(
        db: AppDatabase,
        prefs: UserPreferencesRepository,
        now: Long = System.currentTimeMillis()
    ): WidgetSnapshot {
        val settings = prefs.appSettingsFlow.first()
        val themes = prefs.androidWidgetThemesFlow.first()
        val activeRoutineId = prefs.activeRoutineIdFlow.first()

        val room = db.withTransaction {
            WidgetRoomData(
                schedule = db.scheduleDao().getAllScheduleDaysOnce(),
                sessions = db.workoutDao().getAllSessionsWithSetsOnce(),
                routines = db.savedRoutineDao().getAllRoutinesOnce(),
                exercises = db.exerciseDao().getAllExercisesList(),
                roomProfile = db.userProfileDao().getProfileDirect()
            )
        }
        val streak = WorkoutStreak.calculate(
            schedule = room.schedule,
            sessions = room.sessions,
            today = now,
            weekStartsOnMonday = settings.weekStartsOnMonday
        )

        val todayKey = LocalDateKey.keyOf(now)
        val activeRoutine = room.routines.firstOrNull { it.id == activeRoutineId }
        val scheduleToday = room.schedule.firstOrNull { day ->
            (day.dateKey.takeIf { it > 0L } ?: LocalDateKey.keyOf(day.dateStart)) == todayKey &&
                activeRoutine != null &&
                day.routineId == activeRoutineId
        }
        val parsedToday = if (scheduleToday == null && activeRoutine != null) {
            val todayWeekday = LocalDateKey.isoDayOfWeek(todayKey)
            RoutineParser.parseWeek(
                activeRoutine.exercisesJson,
                room.exercises,
                settings.weekStartsOnMonday
            ).firstOrNull { it.weekday == todayWeekday }
        } else {
            null
        }
        val workoutDay = (scheduleToday?.exerciseCount ?: parsedToday?.exercises?.size ?: 0) > 0
        val workoutName = when {
            scheduleToday != null && scheduleToday.exerciseCount > 0 ->
                scheduleToday.dayTitle.ifBlank { Weekday.name(scheduleToday.weekday) }
            parsedToday != null && parsedToday.exercises.isNotEmpty() ->
                parsedToday.dayTitle.ifBlank { parsedToday.dayName }
            else -> ""
        }

        val week = HomeDashboard.rollingWindow(now)
        val weeklyDays = week.map { dayStart ->
            val completed = room.sessions.any { wrapper ->
                wrapper.sets.any { it.isCompleted } &&
                    ActivityHeatmap.startOfDay(wrapper.session.startTime) == dayStart
            }
            WidgetWeeklyDay(
                label = dayLabel(dayStart),
                completed = completed,
                isToday = dayStart == ActivityHeatmap.startOfDay(now)
            )
        }

        // Match MainViewModel.userProfile exactly: the DataStore copy is live, but its weight can
        // be 0 on an upgraded install, so the Room row supplies that one missing number.
        val prefProfile = prefs.userProfileFlow.first()
        val roomProfile = room.roomProfile
        val profile = when {
            prefProfile != null -> prefProfile.copy(
                currentWeightKg = prefProfile.currentWeightKg.takeIf { it > 0.0 }
                    ?: roomProfile?.currentWeightKg?.takeIf { it > 0.0 }
                    ?: 0.0
            )
            roomProfile != null -> roomProfile
            else -> null
        }
        val unit = WeightUnit.fromLabel(profile?.preferredUnit)
        val weightKg = profile?.currentWeightKg?.takeIf { it > 0.0 }
        val heightCm = profile?.heightCm?.takeIf { it > 0.0 }
        val profileData = WidgetProfileData(
            name = profile?.name?.takeIf { it.isNotBlank() }
                ?: if (profile != null) "Athlete" else "No profile yet",
            weight = if (weightKg != null && weightKg > 0.0) {
                WeightConversion.format(weightKg, unit)
            } else {
                "—"
            },
            height = if (heightCm != null && heightCm > 0.0) "${heightCm.roundToInt()} cm" else "—",
            bmi = ProfileMetrics.bmiText(weightKg ?: 0.0, heightCm ?: 0.0),
            hasProfile = profile != null
        )

        return WidgetSnapshot(
            streak = WidgetStreakData(
                current = streak.currentStreak,
                best = streak.bestStreak,
                alive = streak.isAlive
            ),
            weekly = WidgetWeeklyData(
                days = weeklyDays,
                workoutDay = workoutDay,
                workoutName = workoutName
            ),
            profile = profileData,
            themes = themes
        )
    }

    private fun dayLabel(dayStart: Long): String =
        java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault())
            .format(java.util.Date(dayStart))

    private data class WidgetRoomData(
        val schedule: List<com.example.data.model.ScheduledWorkoutDay>,
        val sessions: List<SessionWithSets>,
        val routines: List<com.example.data.model.SavedRoutine>,
        val exercises: List<com.example.data.model.Exercise>,
        val roomProfile: com.example.data.model.UserProfile?
    )
}
