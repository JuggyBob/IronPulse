package com.example.data.seed

import android.content.Context

/**
 * Remembers which shipped library exercises the athlete has edited.
 *
 * The dataset reseed runs on app open and rewrites every non custom row from the bundled asset. That
 * is correct for a fresh install, and it is also how an athlete quietly loses work: edit a library
 * exercise to fix its fields, ship a release that bumps the dataset, and the edit is gone with no
 * indication it ever existed.
 *
 * So a library row the athlete has edited is remembered and excluded from the reseed. The recorded ids
 * are cleared only by "Revert to original database", which is the one action that is explicitly about
 * putting the shipped values back.
 *
 * Stored as preferences rather than a column on `exercises` because it has to be readable from the
 * database open callback, before Room has finished opening. A new column would mean a migration for
 * state that is not part of the exercise itself.
 */
object LibraryEditTracker {

    private const val PREFS = "ironpulse_library_edits"
    private const val KEY_EDITED_IDS = "edited_exercise_ids"

    /** The ids of library rows the athlete has edited. */
    fun editedIds(context: Context): Set<Long> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_EDITED_IDS, "").orEmpty()
        if (raw.isBlank()) return emptySet()
        return raw.split(',')
            .mapNotNull { it.trim().toLongOrNull() }
            .toSet()
    }

    /** Records that a library row was edited, so the reseed leaves it alone. */
    fun recordEdit(context: Context, exerciseId: Long) {
        if (exerciseId <= 0L) return
        val current = editedIds(context)
        if (exerciseId in current) return
        val updated = (current + exerciseId).joinToString(",")
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_EDITED_IDS, updated)
            .apply()
    }

    /**
     * Forgets every edit, called when the athlete asks for the original database back.
     *
     * Only after this does the reseed own those rows again, so a revert really does restore the shipped
     * values rather than being undone by the next database open.
     */
    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_EDITED_IDS)
            .apply()
    }
}
