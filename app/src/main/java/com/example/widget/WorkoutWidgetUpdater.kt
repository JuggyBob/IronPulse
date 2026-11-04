/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * New standalone code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/** Re-renders every installed widget from the latest persisted IronPulse state. */
object WorkoutWidgetUpdater {

    suspend fun updateAll(context: Context) = withContext(Dispatchers.IO) {
        val manager = AppWidgetManager.getInstance(context)
        // Do not read the database for a refresh when none of the three widgets is installed.
        if (manager.getAppWidgetIds(streakComponent(context)).isEmpty() &&
            manager.getAppWidgetIds(weeklyComponent(context)).isEmpty() &&
            manager.getAppWidgetIds(profileComponent(context)).isEmpty()
        ) {
            return@withContext
        }
        val snapshot = runCatching { WorkoutWidgetDataSource.load(context) }.getOrNull()
            ?: return@withContext
        renderStreak(context, manager, snapshot)
        renderWeekly(context, manager, snapshot)
        renderProfile(context, manager, snapshot)
    }

    fun requestUpdate(context: Context) {
        CoroutineScope(Dispatchers.IO).launch { updateAll(context) }
    }

    private fun renderStreak(context: Context, manager: AppWidgetManager, data: WidgetSnapshot) {
        val ids = manager.getAppWidgetIds(streakComponent(context))
        if (ids.isEmpty()) return
        val palette = WidgetTheme.palette(data.themes.streak)
        val views = RemoteViews(context.packageName, R.layout.widget_streak)
        views.setInt(R.id.widget_streak_root, "setBackgroundResource", palette.backgroundRes)
        views.setTextColor(R.id.widget_streak_current, palette.textPrimary)
        views.setTextColor(R.id.widget_streak_label, palette.textSecondary)
        views.setTextColor(R.id.widget_streak_best, palette.textSecondary)
        views.setTextViewText(R.id.widget_streak_current, data.streak.current.toString())
        views.setTextViewText(
            R.id.widget_streak_label,
            if (data.streak.alive) "workout days" else "streak broken"
        )
        views.setTextViewText(R.id.widget_streak_best, "Best: ${data.streak.best} workout days")
        views.setImageViewResource(
            R.id.widget_streak_flame,
            if (data.streak.alive) R.drawable.widget_flame_alive else R.drawable.widget_flame_out
        )
        views.setOnClickPendingIntent(
            R.id.widget_streak_root,
            activityPending(context, WidgetNavigation.ACTION_OPEN_STREAK, 3101)
        )
        manager.updateAppWidget(ids, views)
    }

    private fun renderWeekly(context: Context, manager: AppWidgetManager, data: WidgetSnapshot) {
        val ids = manager.getAppWidgetIds(weeklyComponent(context))
        if (ids.isEmpty()) return
        val palette = WidgetTheme.palette(data.themes.weekly)
        val views = RemoteViews(context.packageName, R.layout.widget_weekly)
        views.setInt(R.id.widget_weekly_root, "setBackgroundResource", palette.backgroundRes)
        views.setTextColor(R.id.widget_weekly_title, palette.textSecondary)
        views.setTextColor(R.id.widget_weekly_today, palette.textPrimary)
        views.setTextColor(R.id.widget_weekly_action, palette.onAccent)
        views.setInt(R.id.widget_weekly_action, "setBackgroundResource", palette.buttonRes)
        val dayIds = intArrayOf(
            R.id.widget_day_0, R.id.widget_day_1, R.id.widget_day_2, R.id.widget_day_3,
            R.id.widget_day_4, R.id.widget_day_5, R.id.widget_day_6
        )
        val labelIds = intArrayOf(
            R.id.widget_day_label_0, R.id.widget_day_label_1, R.id.widget_day_label_2,
            R.id.widget_day_label_3, R.id.widget_day_label_4, R.id.widget_day_label_5,
            R.id.widget_day_label_6
        )
        data.weekly.days.take(7).forEachIndexed { index, day ->
            views.setTextViewText(labelIds[index], day.label)
            views.setTextColor(labelIds[index], palette.textSecondary)
            views.setInt(
                dayIds[index],
                "setBackgroundColor",
                if (day.completed) palette.dayOn else palette.dayOff
            )
        }
        views.setTextViewText(
            R.id.widget_weekly_today,
            if (data.weekly.workoutDay) {
                context.getString(R.string.widget_weekly_today_workout, data.weekly.workoutName)
            } else {
                context.getString(R.string.widget_weekly_today_rest)
            }
        )
        views.setTextViewText(
            R.id.widget_weekly_action,
            if (data.weekly.workoutDay) {
                context.getString(R.string.widget_weekly_start)
            } else {
                context.getString(R.string.widget_weekly_choose)
            }
        )
        val weeklyAction = if (data.weekly.workoutDay) {
            WidgetNavigation.ACTION_START_TODAY
        } else {
            WidgetNavigation.ACTION_SAVED_ROUTINES
        }
        views.setOnClickPendingIntent(
            R.id.widget_weekly_root,
            activityPending(context, weeklyAction, 3102)
        )
        views.setOnClickPendingIntent(
            R.id.widget_weekly_action,
            activityPending(context, weeklyAction, 3103)
        )
        manager.updateAppWidget(ids, views)
    }

    private fun renderProfile(context: Context, manager: AppWidgetManager, data: WidgetSnapshot) {
        val ids = manager.getAppWidgetIds(profileComponent(context))
        if (ids.isEmpty()) return
        val palette = WidgetTheme.palette(data.themes.profile)
        val views = RemoteViews(context.packageName, R.layout.widget_profile)
        views.setInt(R.id.widget_profile_root, "setBackgroundResource", palette.backgroundRes)
        views.setTextColor(R.id.widget_profile_name, palette.textPrimary)
        views.setTextColor(R.id.widget_profile_subtitle, palette.textSecondary)
        views.setTextColor(R.id.widget_profile_weight_value, palette.textPrimary)
        views.setTextColor(R.id.widget_profile_height_value, palette.textPrimary)
        views.setTextColor(R.id.widget_profile_bmi_value, palette.textPrimary)
        views.setTextColor(R.id.widget_profile_initial, palette.onAccent)
        views.setInt(R.id.widget_profile_initial, "setBackgroundResource", palette.buttonRes)
        views.setTextViewText(R.id.widget_profile_name, data.profile.name)
        views.setTextViewText(R.id.widget_profile_initial, data.profile.name.take(1).uppercase())
        views.setTextViewText(R.id.widget_profile_weight_value, data.profile.weight)
        views.setTextViewText(R.id.widget_profile_height_value, data.profile.height)
        views.setTextViewText(R.id.widget_profile_bmi_value, data.profile.bmi)
        val profileLabels = intArrayOf(
            R.id.widget_profile_weight_value,
            R.id.widget_profile_height_value,
            R.id.widget_profile_bmi_value
        )
        profileLabels.forEach { views.setTextColor(it, palette.textPrimary) }
        views.setOnClickPendingIntent(
            R.id.widget_profile_root,
            activityPending(context, WidgetNavigation.ACTION_OPEN_PROFILE, 3104)
        )
        manager.updateAppWidget(ids, views)
    }

    private fun activityPending(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = action
            putExtra(WidgetNavigation.EXTRA_ACTION, action)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun streakComponent(context: Context) =
        ComponentName(context, WorkoutStreakWidgetProvider::class.java)

    private fun weeklyComponent(context: Context) =
        ComponentName(context, WeeklyWorkoutWidgetProvider::class.java)

    private fun profileComponent(context: Context) =
        ComponentName(context, WorkoutProfileWidgetProvider::class.java)
}

/** Schedules the one lightweight midnight refresh that keeps the rolling week correct. */
object WidgetMidnightScheduler {
    private const val REQUEST_CODE = 9201
    const val ACTION_MIDNIGHT = "com.example.widget.MIDNIGHT"

    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val intent = Intent(context, WidgetUpdateReceiver::class.java).apply {
            action = ACTION_MIDNIGHT
        }
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pending)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pending)
        }
    }
}

/** Base provider: one implementation, three registered widget kinds. */
abstract class BaseWorkoutWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WorkoutWidgetUpdater.updateAll(context)
                WidgetMidnightScheduler.schedule(context)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        WorkoutWidgetUpdater.requestUpdate(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WorkoutWidgetUpdater.requestUpdate(context)
        WidgetMidnightScheduler.schedule(context)
    }
}

class WorkoutStreakWidgetProvider : BaseWorkoutWidgetProvider()
class WeeklyWorkoutWidgetProvider : BaseWorkoutWidgetProvider()
class WorkoutProfileWidgetProvider : BaseWorkoutWidgetProvider()
