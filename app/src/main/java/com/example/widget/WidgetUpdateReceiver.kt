/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * New standalone code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles the midnight refresh, boot, timezone changes and package replacement.
 *
 * There is no polling loop: one alarm is scheduled for the next local midnight and the receiver
 * schedules the following one after it has updated the widgets.
 */
class WidgetUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WorkoutWidgetUpdater.updateAll(appContext)
                WidgetMidnightScheduler.schedule(appContext)
            } finally {
                pending.finish()
            }
        }
    }
}
