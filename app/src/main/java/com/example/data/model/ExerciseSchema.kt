package com.example.data.model

/**
 * The closed vocabularies of the exercise catalog.
 *
 * The dataset has its own vocabulary for body parts and muscle groups, which is what the catalog and
 * the AI parser speak now. Keeping the lists here rather than inline in the prompts and the UI is
 * what makes them verifiable: [ExerciseSchemaTest] asserts that the prompts, the body map and the
 * stored data all agree on these exact strings.
 *
 * The words the dataset stores are lower case (`"upper arms"`, `"pectorals"`). That is fine as a
 * machine key but wrong as a label: the exercise page showed "pectorals" and the editor offered a
 * list of Title Case names that the stored value did not match. The stored value and the label are
 * therefore separated here. Rows are seeded and displayed through [canonicalBodyPart] and
 * [canonicalTarget], which give every value its one display spelling, so a row can no longer be
 * outside the dropdown it is edited with.
 */
object ExerciseSchema {

    /**
     * The ten body parts hasaneyldrm/exercises-dataset uses, as its lower case keys.
     *
     * Note "waist" is the dataset's word for what the app calls Core, and "cardio" has no muscle
     * group of its own (it lands in Calisthenics). Both are deliberate: renaming them here would
     * stop the body map from matching the catalog.
     */
    private val BODY_PART_BY_KEY: Map<String, String> = linkedMapOf(
        "chest" to "Chest",
        "back" to "Back",
        "shoulders" to "Shoulders",
        "upper arms" to "Upper Arms",
        "lower arms" to "Lower Arms",
        "waist" to "Waist",
        "upper legs" to "Upper Legs",
        "lower legs" to "Lower Legs",
        "cardio" to "Cardio",
        "neck" to "Neck"
    )

    val DATASET_BODY_PARTS: Set<String> = BODY_PART_BY_KEY.keys

    /** The body part labels, in the order the editor dropdown shows them. */
    val BODY_PART_OPTIONS: List<String> = BODY_PART_BY_KEY.values.toList()

    /**
     * The dataset's target muscle vocabulary, spelled the way people write it.
     *
     * This is the list the exercise editor offers. It is also the set every seeded target is
     * resolved into by [canonicalTarget], which is what keeps "abs" and "Abdominals" from being two
     * different muscles in the same library.
     */
    val TARGET_MUSCLES: List<String> = listOf(
        "Pectorals", "Lats", "Upper Back", "Lower Back", "Trapezius", "Deltoids",
        "Rotator Cuff", "Biceps", "Triceps", "Forearms", "Abdominals", "Obliques",
        "Serratus Anterior", "Hip Flexors", "Adductors", "Abductors", "Glutes",
        "Hamstrings", "Quadriceps", "Calves", "Soleus", "Tibialis", "Ankles",
        "Neck", "Cardiovascular System", "Full Body"
    )

    /** The IronPulse muscle group chips, in the order the filter row shows them. */
    val MUSCLE_GROUP_CHIPS = MuscleCategory.ALL

    /**
     * Dataset targets that are a synonym of one of [TARGET_MUSCLES] rather than a spelling of it.
     *
     * The asset and the hand written IronPulse additions both use the short gym words ("abs",
     * "delts", "quads", "Back", "Core") while the editor uses the anatomy names. Resolving them
     * here means the seeder writes a value the dropdown can show, instead of a value it cannot.
     */
    private val TARGET_ALIASES: Map<String, String> = mapOf(
        "abs" to "Abdominals",
        "abdominals" to "Abdominals",
        "core" to "Abdominals",
        "waist" to "Abdominals",
        "pectorals" to "Pectorals",
        "pecs" to "Pectorals",
        "chest" to "Pectorals",
        "lats" to "Lats",
        "latissimus dorsi" to "Lats",
        "upper back" to "Upper Back",
        "back" to "Lats",
        "lower back" to "Lower Back",
        "spine" to "Lower Back",
        "spinal erectors" to "Lower Back",
        "traps" to "Trapezius",
        "trapezius" to "Trapezius",
        "delts" to "Deltoids",
        "deltoids" to "Deltoids",
        "shoulders" to "Deltoids",
        "rotator cuff" to "Rotator Cuff",
        "biceps" to "Biceps",
        "triceps" to "Triceps",
        "forearms" to "Forearms",
        "obliques" to "Obliques",
        "serratus anterior" to "Serratus Anterior",
        "hip flexors" to "Hip Flexors",
        "adductors" to "Adductors",
        "abductors" to "Abductors",
        "glutes" to "Glutes",
        "hamstrings" to "Hamstrings",
        "quads" to "Quadriceps",
        "quadriceps" to "Quadriceps",
        "legs" to "Quadriceps",
        "calves" to "Calves",
        "soleus" to "Soleus",
        "tibialis" to "Tibialis",
        "ankles" to "Ankles",
        "neck" to "Neck",
        "levator scapulae" to "Neck",
        "cardiovascular system" to "Cardiovascular System",
        "full body" to "Full Body"
    )

    /** Whether [raw] is one of the dataset's body part keys, ignoring case and surrounding space. */
    fun isKnownBodyPart(raw: String): Boolean = raw.trim().lowercase() in DATASET_BODY_PARTS

    /**
     * The display spelling of a body part.
     *
     * A dataset row is stored in the same Title Case form the editor offers, so the two agree. An
     * unrecognised value is still title cased rather than left as a stray lower case word; it just
     * is not one of the ten known parts.
     */
    fun canonicalBodyPart(raw: String): String {
        val clean = raw.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) return ""
        BODY_PART_BY_KEY[clean.lowercase()]?.let { return it }
        return titleCase(clean)
    }

    /**
     * The display spelling of a target muscle.
     *
     * Known dataset synonyms are folded into [TARGET_MUSCLES]; anything already in that vocabulary
     * keeps its spelling, and an unknown value is title cased so at least the label is not lower
     * case. Seeding passes every target through this, so the stored library only contains values
     * the editor's dropdown can show.
     */
    fun canonicalTarget(raw: String): String {
        val clean = raw.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) return ""
        TARGET_ALIASES[clean.lowercase()]?.let { return it }
        TARGET_MUSCLES.firstOrNull { it.equals(clean, ignoreCase = true) }?.let { return it }
        return titleCase(clean)
    }

    /** Whether [raw] canonicalises to one of [TARGET_MUSCLES]. */
    fun isKnownTarget(raw: String): Boolean {
        val canonical = canonicalTarget(raw)
        return TARGET_MUSCLES.any { it.equals(canonical, ignoreCase = true) }
    }

    /**
     * Fallback body part for an AI created exercise whose `bodyPart` was missing or invented.
     *
     * The AI is asked to emit a dataset body part, but it does not always comply, and a wrong body
     * part would put the exercise on the wrong muscle on the body map. Deriving one from the
     * category guarantees the two always agree.
     */
    fun categoryToBodyPart(category: String): String = when (category) {
        MuscleCategory.CHEST -> "Chest"
        MuscleCategory.BACK -> "Back"
        MuscleCategory.SHOULDERS -> "Shoulders"
        MuscleCategory.ARMS -> "Upper Arms"
        MuscleCategory.CORE -> "Waist"
        MuscleCategory.LEGS -> "Upper Legs"
        MuscleCategory.CALISTHENICS -> "Cardio"
        else -> "Chest"
    }

    private fun titleCase(value: String): String =
        value.split(' ').joinToString(" ") { word ->
            word.replaceFirstChar { char -> char.uppercaseChar() }
        }
}
