package com.example.data.seed

import android.content.Context
import com.example.data.dao.DatasetExerciseUpdate
import com.example.data.dao.ExerciseDao
import com.example.data.model.Exercise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * One record of the bundled `exercises.json` asset, which is a trimmed export of the
 * hasaneyldrm/exercises-dataset (English instructions only, IronPulse category names).
 */
data class DatasetExercise(
    val id: String,
    val name: String,
    val category: String,
    val bodyPart: String,
    val equipment: String,
    val target: String,
    val secondaryMuscles: List<String>,
    val instructions: String,
    val gifUrl: String,
    val exerciseType: String
) {
    fun toExercise(): Exercise = Exercise(
        name = name,
        category = category,
        bodyPart = bodyPart,
        target = target,
        secondaryMuscles = secondaryMuscles.toJsonArray(),
        exerciseType = exerciseType,
        instructions = instructions,
        equipment = equipment,
        mediaPath = gifUrl,
        sourceId = id
    )

    fun toUpdate(rowId: Long) = DatasetExerciseUpdate(
        id = rowId,
        name = name,
        category = category,
        bodyPart = bodyPart,
        target = target,
        secondaryMuscles = secondaryMuscles.toJsonArray(),
        exerciseType = exerciseType,
        instructions = instructions,
        equipment = equipment,
        mediaPath = gifUrl,
        sourceId = id
    )
}

fun List<String>.toJsonArray(): String {
    val arr = JSONArray()
    forEach { arr.put(it) }
    return arr.toString()
}

/**
 * Reads the bundled dataset asset and upserts it into the `exercises` table.
 *
 * The upsert is deliberately conservative:
 * - An existing non custom exercise with the same name (or dataset id) is updated in place,
 *   so its row id never changes and logged sets keep pointing at it.
 * - A custom exercise (created by the user or the AI) is never touched, even when the dataset
 *   contains an exercise with the same name.
 * - Dataset exercises whose name is taken by a custom exercise are skipped, so the user's
 *   version always wins and no duplicate name is ever created.
 *
 * The whole reseed is applied in a single transaction, so the exercise list never renders a
 * partially seeded catalog.
 */
object DatasetSeeder {

    /**
     * Bump to force a reseed when the bundled asset changes.
     *
     * Version 3 added the CARDIO type: the previous build logged running and the machine based
     * movements as barbell work, so every installed app has rows with the wrong type that only a
     * refresh can correct.
     */
    const val DATASET_VERSION = 3

    private const val PREFS = "ironpulse_dataset"
    private const val KEY_VERSION = "dataset_version"

    /** Reseeds only when the bundled asset version changed. */
    suspend fun seed(context: Context, dao: ExerciseDao) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_VERSION, 0) >= DATASET_VERSION) return@withContext
        apply(context, dao)
        prefs.edit().putInt(KEY_VERSION, DATASET_VERSION).apply()
    }

    /** Reseeds unconditionally, used when the database is created from scratch. */
    suspend fun seedFresh(context: Context, dao: ExerciseDao) = withContext(Dispatchers.IO) {
        apply(context, dao)
    }

    /**
     * Lowercased names of every exercise the dataset ships, used to tell the shipped library apart
     * from rows an older AI parse registered. Cached because the asset is nearly a megabyte.
     */
    suspend fun libraryNames(context: Context): Set<String> = withContext(Dispatchers.IO) {
        cachedNames ?: synchronized(DatasetSeeder) {
            cachedNames ?: parseAsset(context)
                .mapTo(HashSet()) { normalizeName(it.name) }
                .also { cachedNames = it }
        }
    }

    @Volatile
    private var cachedNames: Set<String>? = null

    private suspend fun apply(context: Context, dao: ExerciseDao) {
        val records = parseAsset(context)
        val existing = dao.getAllExercisesList()
        val byName = HashMap<String, Exercise>(existing.size)
        val bySourceId = HashMap<String, Exercise>(existing.size)
        for (row in existing) {
            byName.putIfAbsent(normalizeName(row.name), row)
            if (row.sourceId.isNotEmpty()) bySourceId.putIfAbsent(row.sourceId, row)
        }

        val inserts = ArrayList<Exercise>(records.size)
        val updates = ArrayList<DatasetExerciseUpdate>()
        // Names claimed by an earlier record in this same run. The dataset plus the IronPulse
        // additions can collide once normalised ("Weighted Pull-Up" vs "Weighted Pull-up"), and a
        // collision must drop the later record rather than queue an update against a row id that
        // does not exist yet.
        val claimed = HashSet<String>(records.size * 2)
        // Rows the athlete edited by hand. See LibraryEditTracker: a library row is not frozen, so the
        // reseed must not quietly overwrite work they did to repair a wrong exercise. Only a deliberate
        // revert clears this set.
        val athleteEdited = LibraryEditTracker.editedIds(context)

        for (rec in records) {
            val key = normalizeName(rec.name)
            if (!claimed.add(key)) continue

            val match = byName[key] ?: bySourceId[rec.id]
            when {
                // The user owns this row (or the AI registered it): never overwrite it, and let
                // the custom exercise keep the name so the catalog cannot end up with two of them.
                match != null && match.isCustom -> Unit
                // Edited by hand: leave it exactly as the athlete saved it.
                match != null && athleteEdited.contains(match.id) -> Unit
                match != null -> updates += rec.toUpdate(match.id)
                else -> inserts += rec.toExercise()
            }
        }

        dao.applyDatasetPlan(inserts, updates)
    }

    /** Case and spacing insensitive name key, matching the unique name index in spirit. */
    private fun normalizeName(name: String): String = name.trim().lowercase()

    private fun parseAsset(context: Context): List<DatasetExercise> {
        val raw = context.assets.open("exercises.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val secondary = o.optJSONArray("secondaryMuscles") ?: JSONArray()
            DatasetExercise(
                id = o.getString("id"),
                name = o.getString("name"),
                category = o.getString("category"),
                bodyPart = o.optString("bodyPart", ""),
                equipment = o.optString("equipment", "Bodyweight"),
                target = o.optString("target", ""),
                secondaryMuscles = (0 until secondary.length()).map { secondary.getString(it) },
                instructions = o.optString("instructions", ""),
                gifUrl = o.optString("gifUrl", ""),
                exerciseType = o.optString("exerciseType", "STANDARD_WEIGHT")
            )
        }
    }
}
