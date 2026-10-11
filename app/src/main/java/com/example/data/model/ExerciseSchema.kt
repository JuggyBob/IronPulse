package com.example.data.model

/**
 * The closed vocabularies of the exercise catalog.
 *
 * The dataset has its own vocabulary for body parts and muscle groups, which is what the catalog and
 * the AI parser speak now. Keeping the lists here rather than inline in the prompts and the UI is
 * what makes them verifiable: [ExerciseSchemaTest] asserts that the prompts, the body map and the
 * stored data all agree on these exact strings.
 */
object ExerciseSchema {

    /**
     * The ten body parts hasaneyldrm/exercises-dataset uses.
     *
     * Note "waist" is the dataset's word for what the app calls Core, and "cardio" has no muscle
     * group of its own (it lands in Calisthenics). Both are deliberate: renaming them here would
     * stop the body map from matching the catalog.
     */
    val DATASET_BODY_PARTS = setOf(
        "chest",
        "back",
        "shoulders",
        "upper arms",
        "lower arms",
        "waist",
        "upper legs",
        "lower legs",
        "cardio",
        "neck"
    )

    /** The IronPulse muscle group chips, in the order the filter row shows them. */
    val MUSCLE_GROUP_CHIPS = MuscleCategory.ALL

    /**
     * Fallback body part for an AI created exercise whose `bodyPart` was missing or invented.
     *
     * The AI is asked to emit a dataset body part, but it does not always comply, and a wrong body
     * part would put the exercise on the wrong muscle on the body map. Deriving one from the
     * category guarantees the two always agree.
     */
    fun categoryToBodyPart(category: String): String = when (category) {
        MuscleCategory.CHEST -> "chest"
        MuscleCategory.BACK -> "back"
        MuscleCategory.SHOULDERS -> "shoulders"
        MuscleCategory.ARMS -> "upper arms"
        MuscleCategory.CORE -> "waist"
        MuscleCategory.LEGS -> "upper legs"
        MuscleCategory.CALISTHENICS -> "cardio"
        else -> "chest"
    }
}
