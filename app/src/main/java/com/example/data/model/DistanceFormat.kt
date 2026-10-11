package com.example.data.model

import java.util.Locale

/**
 * Formatting for the distance a cardio set covers.
 *
 * Distances are stored in kilometres, the same way every weight is stored in kilograms: one canonical
 * unit in the database, converted only for display. Keeping that rule here means the Progress page,
 * the workout history and the logging input all format a distance identically instead of each
 * rounding it its own way.
 */
object DistanceFormat {

    /** Unit for display, swapped for miles when the athlete works in pounds. */
    fun suffixFor(weightUnit: WeightUnit): String = if (weightUnit == WeightUnit.LB) "mi" else "km"

    /**
     * Converts a stored kilometre value into the athlete's unit.
     *
     * 1 mile is 1.609344 km, not 1.6: at 5 km the difference is about 0.11 mi, which is visible on
     * every single run the athlete logs.
     */
    fun fromKm(km: Double, weightUnit: WeightUnit): Double =
        if (weightUnit == WeightUnit.LB) km / 1.609344 else km

    /**
     * Formats a stored kilometre value for display, without a unit suffix.
     *
     * Two decimals below 10, one above: "5.00" reads as a measurement, "21.3" reads as a run.
     */
    fun format(km: Double, weightUnit: WeightUnit): String {
        val value = fromKm(km, weightUnit)
        if (value == 0.0) return ""
        val pattern = if (value < 10.0) "%.2f" else "%.1f"
        return String.format(Locale.US, pattern, value)
    }

    /** Parses what the athlete typed back into kilometres. A blank or unparseable field is 0. */
    fun toKm(input: String, weightUnit: WeightUnit): Double {
        val parsed = input.trim().replace(',', '.').toDoubleOrNull() ?: return 0.0
        if (parsed <= 0.0) return 0.0
        return if (weightUnit == WeightUnit.LB) parsed * 1.609344 else parsed
    }
}

/** Convenience for the logging input, which shows the raw number back to the athlete. */
internal fun formatDistance(km: Double): String = String.format(Locale.US, "%.2f", km)
