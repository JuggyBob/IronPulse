package com.example.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object RestTimerScheduler {
    const val ACTION_REST_TIMER_EXPIRED = "com.example.fitpulse.ACTION_REST_TIMER_EXPIRED"
    const val EXTRA_TIMER_NAME = "extra_timer_name"
    const val EXTRA_TIMER_END_TIME = "extra_timer_end_time"
    private const val REQUEST_CODE = 9021

    /**
     * Schedules an exact system alarm to trigger when the rest timer duration completes.
     * Works when the app is in the background, screen is off, or device is in Doze/standby.
     *
     * @param endTimeMillis absolute end time of the timer. It travels with the alarm so the
     * receiver can de-duplicate the alert against the in-app countdown loop.
     */
    fun scheduleRestTimer(context: Context, durationSeconds: Int, timerName: String, endTimeMillis: Long) {
        val totalSec = durationSeconds.coerceAtLeast(1)
        val triggerAtMillis = if (endTimeMillis > 0L) endTimeMillis else System.currentTimeMillis() + (totalSec * 1000L)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, RestTimerReceiver::class.java).apply {
            action = ACTION_REST_TIMER_EXPIRED
            putExtra(EXTRA_TIMER_NAME, timerName)
            putExtra(EXTRA_TIMER_END_TIME, endTimeMillis)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    // setAlarmClock does not require SCHEDULE_EXACT_ALARM permission
                    alarmManager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(triggerAtMillis, pendingIntent),
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            // Fallback to setAlarmClock which is exempt from exact alarm permission checks
            try {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAtMillis, pendingIntent),
                    pendingIntent
                )
            } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    /**
     * Cancels any scheduled rest timer alarm.
     */
    fun cancelRestTimer(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, RestTimerReceiver::class.java).apply {
                action = ACTION_REST_TIMER_EXPIRED
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        } catch (_: Exception) {}
    }
}
