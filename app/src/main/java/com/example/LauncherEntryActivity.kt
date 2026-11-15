/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * The tiny launcher bridge behind every icon alias.
 *
 * Starting MainActivity directly from an alias leaves the task rooted at that alias. Android then
 * removes the task when the alias is disabled to change icons. This bridge starts MainActivity in
 * its own task with a different affinity, removes the bridge task, and lets the normal app task keep
 * running while launcher aliases are switched underneath it.
 */
class LauncherEntryActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launch = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        startActivity(launch)
        finishAndRemoveTask()
    }
}
