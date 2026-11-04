/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * New standalone code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.data.model

/**
 * The single BMI calculation used by the Profile tab and the launcher widget.
 *
 * Both surfaces must agree exactly, so the formula lives here rather than beside either drawing
 * layer. A missing or nonsensical height returns 0, which callers render as unavailable rather
 * than as a misleading number.
 */
object ProfileMetrics {
    fun bmi(weightKg: Double, heightCm: Double): Double {
        if (weightKg <= 0.0 || heightCm <= 0.0) return 0.0
        val heightM = heightCm / 100.0
        val value = weightKg / (heightM * heightM)
        return if (value.isFinite()) value else 0.0
    }

    fun bmiText(weightKg: Double, heightCm: Double): String {
        val value = bmi(weightKg, heightCm)
        return if (value > 0.0) String.format(java.util.Locale.US, "%.1f", value) else "—"
    }
}
