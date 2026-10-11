package com.example.data.model

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * How an exercise is logged, which decides which inputs a set shows.
 *
 * CARDIO exists because running and the other machine based movements cannot be expressed by the
 * other three. They have no meaningful "load" and no rep count either: the athlete records a
 * duration and a distance. Showing them a weight and a rep box produced "Bench" style logging for
 * "Run", which is why the dataset's own `cardio` body part needed its own type rather than being
 * forced into Bodyweight.
 */
object ExerciseType {
    const val STANDARD_WEIGHT = "STANDARD_WEIGHT"
    const val ADDED_WEIGHT = "ADDED_WEIGHT"
    const val PURE_BODYWEIGHT = "PURE_BODYWEIGHT"

    /** Distance based work: running, cycling, rowing, elliptical, stair machines. */
    const val CARDIO = "CARDIO"

    val ALL = listOf(STANDARD_WEIGHT, ADDED_WEIGHT, PURE_BODYWEIGHT, CARDIO)

    /** The types that record a distance alongside the time. */
    val DISTANCE_BASED = setOf(CARDIO)

    /**
     * Equipment the dataset marks for its cardio body part. Machine based movement is the reliable
     * signal, and it matches on the raw dataset spelling rather than the display spelling.
     */
    private val CARDIO_EQUIPMENT = setOf(
        "treadmill", "stepmill machine", "stationary bike", "elliptical machine",
        "skierg machine", "upper body ergometer", "rowing", "tire"
    )

    /**
     * Whether this exercise should be logged as distance work.
     *
     * Both signals are checked because neither is complete on its own: the dataset files
     * "Walking High Knees Lunge" under cardio while still describing it as bodyweight calisthenics,
     * and it files some treadmill work under legs.
     */
    fun isCardio(bodyPart: String, equipment: String, target: String): Boolean {
        if (bodyPart.trim().equals("cardio", ignoreCase = true)) return true
        if (equipment.trim().lowercase() in CARDIO_EQUIPMENT) return true
        return target.trim().equals("cardiovascular system", ignoreCase = true)
    }
}

object MuscleCategory {
    const val CHEST = "Chest"
    const val BACK = "Back"
    const val LEGS = "Legs"
    const val SHOULDERS = "Shoulders"
    const val ARMS = "Arms"
    const val CORE = "Core"
    const val CALISTHENICS = "Calisthenics"

    val ALL = listOf(CHEST, BACK, LEGS, SHOULDERS, ARMS, CORE, CALISTHENICS)
}

@Immutable
@Entity(
    tableName = "exercises",
    indices = [Index(value = ["name"], unique = true)]
)
data class Exercise(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    /** Muscle group chip this exercise belongs to (Chest, Back, Legs, ...). */
    val category: String,
    /** Body part targeted, using the dataset vocabulary (chest, upper legs, waist, ...). */
    val bodyPart: String = "",
    /** Primary target muscle (e.g. "pectorals", "abs"). */
    val target: String = "",
    /** Synergist muscles, stored as a JSON array string. */
    val secondaryMuscles: String = "[]",
    val exerciseType: String,
    /** Step by step instructions in English, from the dataset. */
    val instructions: String = "",
    /** Legacy free text kept for exercises created before the dataset catalog existed. */
    val description: String = "",
    val equipment: String = "Bodyweight",
    /**
     * Path of the animation GIF inside the exercises dataset (e.g. "videos/0025-....gif").
     * Resolved to a remote URL by the media pipeline; never bundled in the apk.
     */
    @ColumnInfo(defaultValue = "")
    val mediaPath: String = "",
    /**
     * A local content:// uri for an image or video the athlete attached from their own gallery.
     *
     * Empty unless they picked one, and only ever set on exercises they own (see
     * [ExerciseMedia.isUserOwnedMedia]): the shipped library is locked, so a dataset row can never
     * be given someone else's media. Takes precedence over [mediaPath] wherever media is shown.
     */
    @ColumnInfo(defaultValue = "")
    val customMediaUri: String = "",
    /**
     * Whether [customMediaUri] is a video rather than a still image.
     *
     * Stored instead of inferred: a gallery `content://` uri normally has no file extension, so
     * there is nothing to infer from, and guessing wrong means decoding a video as a still and
     * showing an empty frame.
     */
    @ColumnInfo(defaultValue = "0")
    val customMediaIsVideo: Boolean = false,
    /** The dataset record id ("0025") this exercise was imported from, or "" for legacy rows. */
    val sourceId: String = "",
    /**
     * True for exercises the user or the AI created. Both can be edited or deleted; the built-in
     * library is locked and can never be modified. Replaces the old isUserCreated flag.
     */
    val isCustom: Boolean = false,
    /**
     * True for the exercises the AI parser registered on its own (never typed by the user).
     * They are editable/deletable like the user's own exercises, but the UI always labels them
     * as AI generated so the two are never confused.
     */
    val isAiGenerated: Boolean = false
) {
    /** Synergist muscles as a list, parsed from the stored JSON array. */
    val secondaryMuscleList: List<String>
        get() = parseMuscleList(secondaryMuscles)

    companion object {
        /**
         * Parses the stored muscle list without org.json.
         *
         * org.json is stubbed out in JVM unit tests (it ships as part of the Android runtime), so
         * anything that has to be covered by a plain test cannot use it. It is also only ever handed
         * a short array of muscle names, so a small hand written parser is both simpler and testable.
         */
        fun parseMuscleList(raw: String): List<String> {
            val trimmed = raw.trim()
            if (trimmed.length < 2) return emptyList()
            if (!trimmed.startsWith('[') || !trimmed.endsWith(']')) return emptyList()

            val result = mutableListOf<String>()
            val current = StringBuilder()
            var inQuotes = false
            var escaped = false
            for (index in 1 until trimmed.length - 1) {
                val char = trimmed[index]
                when {
                    escaped -> {
                        // Only \" and \\ reach here; every other escape is passed through as typed.
                        current.append(if (char == '"' || char == '\\') char else char)
                        escaped = false
                    }
                    char == '\\' && inQuotes -> escaped = true
                    char == '"' -> inQuotes = !inQuotes
                    char == ',' && !inQuotes -> {
                        val value = current.toString().trim()
                        if (value.isNotEmpty()) result += value
                        current.setLength(0)
                    }
                    inQuotes -> current.append(char)
                }
            }
            val last = current.toString().trim()
            if (last.isNotEmpty()) result += last
            return result
        }
    }
}

@Immutable
@Entity(tableName = "workout_sessions")
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long = System.currentTimeMillis(),
    val notes: String = ""
)

/**
 * A session together with its sets, resolved in a single transaction.
 *
 * The activity heat map needs the training time and the lifted volume of every day, so it reads the
 * sets of every session as well instead of querying them one by one.
 */
data class SessionWithSets(
    @Embedded val session: WorkoutSession,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val sets: List<LoggedSet>
)

@Immutable
@Entity(tableName = "logged_sets")
data class LoggedSet(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val exerciseType: String,
    val setNumber: Int,
    val weight: Double = 0.0,
    val addedWeight: Double = 0.0,
    val reps: Int = 0,
    val holdDurationSeconds: Int = 0,
    /**
     * Distance covered in kilometres, for [ExerciseType.CARDIO] sets only.
     *
     * Always stored in km, like every other weight in the app, and converted for display the same way.
     * 0 means "not recorded", which is the right reading for a set that is not distance based.
     */
    @ColumnInfo(defaultValue = "0")
    val distanceKm: Double = 0.0,
    val isCompleted: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
) {
    /** Whether this set records a distance rather than a load. */
    val isDistanceBased: Boolean get() = ExerciseType.DISTANCE_BASED.contains(exerciseType)
}

@Immutable
@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey
    val id: Int = 1,
    val name: String = "Athlete",
    val heightCm: Double = 178.0,
    val currentWeightKg: Double = 75.0,
    val targetWeightKg: Double = 78.0,
    val age: Int = 26,
    val preferredUnit: String = "kg",
    val fitnessLevel: String = "Intermediate",
    val customApiKey: String = ""
)

@Immutable
@Entity(tableName = "saved_routines")
data class SavedRoutine(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String,
    val daysPerWeek: Int = 3,
    val goal: String = "Hypertrophy & Strength",
    val exercisesJson: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Immutable
@Entity(tableName = "body_weight_logs")
data class BodyWeightLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val weightKg: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = ""
)

@Immutable
@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String = "New Chat",
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdatedAt: Long = System.currentTimeMillis()
)

@Immutable
@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val sessionId: String,
    val sender: String, // "USER" or "AI"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * A Gemini monthly strength summary generated from the Progress tab.
 * Kept so the athlete can re-read and delete past reports.
 */
@Immutable
@Entity(
    tableName = "gemini_summaries",
    indices = [Index(value = ["createdAt"])]
)
data class GeminiSummary(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val exerciseName: String,
    val report: String,
    val windowDays: Int = 30,
    val daysLogged: Int = 0,
    val setCount: Int = 0,
    val bestOneRm: Double = 0.0,
    val trendPercent: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)

