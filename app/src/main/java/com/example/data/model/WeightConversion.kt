package com.example.data.model

import java.util.Locale

/**
 * Weight units.
 *
 * Everything is **stored in kilograms**, always. Only the display layer converts, which is what
 * makes it safe to switch the unit at any time: no history, no set, no body weight and no personal
 * record is ever rewritten.
 */
enum class WeightUnit(val suffix: String) {
    KG("kg"),
    LB("lb");

    /** True when the athlete wants to see (and type) pounds. */
    val isPounds: Boolean get() = this == LB

    companion object {
        /** Pounds in one kilogram, the conversion used everywhere in the app. */
        const val POUNDS_PER_KILOGRAM = 2.20462262

        /** Anything that is not "lb" is kilograms, so a blank or broken value can never divide by zero. */
        fun fromLabel(label: String?): WeightUnit =
            if (label != null && label.trim().equals("lb", ignoreCase = true)) LB else KG
    }
}

/** Converts between the stored kilograms and the unit the athlete chose. */
object WeightConversion {

    /** Kilograms to the display unit. */
    fun fromKg(kg: Double, unit: WeightUnit): Double =
        if (unit.isPounds) kg * WeightUnit.POUNDS_PER_KILOGRAM else kg

    /** The display unit back to the kilograms that get stored. */
    fun toKg(value: Double, unit: WeightUnit): Double =
        if (unit.isPounds) value / WeightUnit.POUNDS_PER_KILOGRAM else value

    /**
     * Formats a stored weight for display, e.g. "225 lb" or "102.1 kg".
     *
     * @param decimals fixed number of decimals. One decimal suits both units: a round weight in
     * pounds reads better without the trailing ".0".
     */
    fun format(
        kg: Double,
        unit: WeightUnit,
        decimals: Int = 1,
        withUnit: Boolean = true
    ): String {
        val value = fromKg(kg, unit)
        val text = String.format(Locale.US, "%.${decimals}f", value)
        // Trim a trailing ".0" so a round weight reads as "225" and not "225.0"
        val trimmed = if (text.contains('.') && text.substringAfter('.').all { it == '0' }) {
            text.substringBefore('.')
        } else {
            text
        }
        return if (withUnit) "$trimmed ${unit.suffix}" else trimmed
    }

    /** Short label for a chart axis or a column header. */
    fun axisLabel(unit: WeightUnit): String = unit.suffix

    /**
     * Parses what the athlete typed into the stored kilograms.
     *
     * Accepts plain numbers ("225", "225.5", "102,5") and also a typed unit ("225 lb", "102 kg").
     * A value with an explicit unit wins over the current preference, so pasting a weight from a
     * program written in pounds is never silently read as kilograms.
     *
     * @return null when the text is not a usable positive weight.
     */
    fun parseToKg(input: String, defaultUnit: WeightUnit): Double? {
        var raw = input.trim().lowercase().replace(',', '.')
        if (raw.isEmpty()) return null

        // A trailing unit name wins over the preference
        var typedUnit: WeightUnit? = null
        for ((suffix, unit) in UNIT_SUFFIXES) {
            if (raw.endsWith(suffix)) {
                typedUnit = unit
                raw = raw.dropLast(suffix.length).trim()
                break
            }
        }

        val value = raw.toDoubleOrNull() ?: return null
        if (value <= 0.0) return null

        return toKg(value, typedUnit ?: defaultUnit)
    }

    /** Longest first, so "lbs" is never mistaken for "lb" plus a stray "s". */
    private val UNIT_SUFFIXES: List<Pair<String, WeightUnit>> = listOf(
        "pounds" to WeightUnit.LB,
        "pound" to WeightUnit.LB,
        "lbs" to WeightUnit.LB,
        "lb" to WeightUnit.LB,
        "kilograms" to WeightUnit.KG,
        "kilogram" to WeightUnit.KG,
        "kgs" to WeightUnit.KG,
        "kg" to WeightUnit.KG
    )
}