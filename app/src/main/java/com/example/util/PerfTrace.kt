package com.example.util

import android.util.Log
import com.example.BuildConfig

/**
 * Debug-only wall-clock markers for startup and data-layer work.
 *
 * Every call is a no-op in release builds. The tag is stable so a measurement can be collected
 * with `adb logcat -s IronPulsePerf:I` without adding product behaviour.
 */
object PerfTrace {
    const val TAG = "IronPulsePerf"

    fun start(): Long = System.nanoTime()

    fun mark(label: String, startNs: Long) {
        if (!BuildConfig.DEBUG) return
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
        Log.i(TAG, "$label=${elapsedMs}ms")
    }
}
