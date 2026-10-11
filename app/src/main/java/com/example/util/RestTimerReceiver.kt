package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class RestTimerReceiver : BroadcastReceiver() {
    companion object {
        const val CHANNEL_ID = "fitpulse_rest_timer_channel"
        const val NOTIFICATION_ID = 4001
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val timerName = intent?.getStringExtra(RestTimerScheduler.EXTRA_TIMER_NAME)?.ifBlank { "Rest Timer" } ?: "Rest Timer"
        val endTime = intent?.getLongExtra(RestTimerScheduler.EXTRA_TIMER_END_TIME, 0L) ?: 0L
        // Identifies this exact timer so the alarm broadcast and the in-app countdown loop
        // never play the alert twice for the same rest period.
        val timerKey = if (endTime > 0L) endTime.toString() else "alarm_${System.currentTimeMillis()}"

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "fitpulse:rest_timer_wakelock"
        )

        try {
            // Keep the CPU awake for the whole alert (~3s sound) even if the app was closed
            // or the device was dozing.
            wakeLock?.acquire(
                (RestTimerAlertCoordinator.ALERT_DURATION_MS + 2_000L).coerceAtMost(10_000L)
            )

            // 1. Phone vibration + alert sound ("beep beep beep" ... pause ... "beeeeeep")
            RestTimerAlertCoordinator.playFinishedAlert(context, timerKey)

            // 2. Notify in-memory event manager for active ViewModels/screens
            RestTimerEventManager.notifyFinished()

            // 3. Post system notification
            showFinishedNotification(context, timerName)
        } catch (_: Exception) {
        } finally {
            if (wakeLock?.isHeld == true) {
                try {
                    wakeLock.release()
                } catch (_: Exception) {}
            }
        }
    }

    private fun showFinishedNotification(context: Context, timerName: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Workout Rest Timer",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts when your rest timer finishes"
                    // The vibration is played by the app itself with a controlled pattern,
                    // so the channel must not add a second, competing buzz.
                    enableVibration(false)
                    enableLights(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingOpenIntent = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Rest Finished! 📳")
                .setContentText("$timerName is complete. Ready for your next set!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingOpenIntent)
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {}
    }
}
