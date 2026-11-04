/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * New standalone code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.data.ai

import com.example.data.model.Exercise
import com.example.data.model.ExerciseSchema
import com.example.data.model.ExerciseType
import com.example.data.model.GibberishNameDetector
import com.example.data.model.MuscleCategory
import com.example.data.model.RoutineParser
import com.example.data.seed.toJsonArray
import org.json.JSONObject

/**
 * The one parser for an AI-created exercise.
 *
 * The routine generator, the single-exercise creator and the controlled routine-modification
 * proposal all validate model output through this object. Keeping one implementation means the
 * catalog cannot receive a row that one path considers valid and another path rejects.
 */
object AiExerciseFactory {

    /**
     * Reads the single-exercise JSON answer, tolerating prose around the object.
     *
     * Returns null for a refusal, gibberish, prose or a malformed object: the caller must never
     * store a guess as an exercise.
     */
    fun parse(rawResponse: String, fallbackName: String): Exercise? {
        val obj = try {
            JSONObject(RoutineParser.extractJson(rawResponse))
        } catch (_: Exception) {
            return null
        }
        return fromJsonObject(obj, fallbackName)
    }

    /** Parses an already decoded JSON object from a native tool call. */
    fun fromJsonObject(obj: JSONObject, fallbackName: String): Exercise? {
        // The model was told to answer exactly this when the name is not a real exercise.
        if (obj.optString("error").isNotBlank()) return null

        val name = obj.optString("name").trim().ifBlank { fallbackName }
        if (name.isBlank()) return null
        if (RoutineParser.looksLikeProseNotExercise(name)) return null
        if (GibberishNameDetector.isGibberish(name)) return null

        val category = obj.optString("category", "").trim()
        val validCategory = if (MuscleCategory.ALL.contains(category)) category else "Calisthenics"
        val rawType = obj.optString("exerciseType", "").trim().uppercase()
        val validType = when (rawType) {
            ExerciseType.ADDED_WEIGHT -> ExerciseType.ADDED_WEIGHT
            ExerciseType.PURE_BODYWEIGHT -> ExerciseType.PURE_BODYWEIGHT
            else -> ExerciseType.STANDARD_WEIGHT
        }
        val equipment = obj.optString("equipment", "").trim().ifBlank { "Bodyweight" }
        val description = obj.optString("description", "").trim().ifBlank {
            "$name performed with $equipment."
        }

        val rawBodyPart = obj.optString("bodyPart", "").trim()
        val bodyPart = if (ExerciseSchema.isKnownBodyPart(rawBodyPart)) {
            ExerciseSchema.canonicalBodyPart(rawBodyPart)
        } else {
            ExerciseSchema.categoryToBodyPart(validCategory)
        }
        val target = ExerciseSchema.canonicalTarget(
            obj.optString("target", "").trim().ifBlank { fallbackTargetForCategory(validCategory) }
        )
        val secondary = obj.optJSONArray("secondaryMuscles")?.let { arr ->
            (0 until arr.length())
                .mapNotNull { ExerciseSchema.canonicalTarget(arr.optString(it)).takeIf(String::isNotBlank) }
        }.orEmpty()

        return Exercise(
            name = name,
            category = validCategory,
            bodyPart = bodyPart,
            target = target,
            secondaryMuscles = secondary.toJsonArray(),
            exerciseType = validType,
            instructions = obj.optString("instructions", "").trim(),
            equipment = equipment,
            description = description,
            mediaPath = "",
            sourceId = "",
            isCustom = true,
            isAiGenerated = true
        )
    }

    fun fallbackTargetForCategory(category: String): String = when (category) {
        "Chest" -> "Pectorals"
        "Back" -> "Lats"
        "Shoulders" -> "Deltoids"
        "Arms" -> "Biceps"
        "Core" -> "Abdominals"
        "Legs" -> "Quadriceps"
        else -> "Quadriceps"
    }
}
