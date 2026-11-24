package com.example.data.ai

import com.example.data.model.Exercise
import com.example.data.model.RoutineDay
import com.example.data.model.RoutineDropSet
import com.example.data.model.RoutineParser
import com.example.data.model.SavedRoutine
import com.example.data.model.SessionWithSets
import com.example.data.model.DropSetRules
import com.example.data.model.WorkoutDraft
import com.example.data.model.WorkoutDraftCodec
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.FitnessRepository
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One bounded page of the exercise catalog. */
data class ExerciseListPage(
    val items: List<Exercise>,
    val total: Int
)

/** One page of completed workout sessions for a retrieval tool. */
data class AiWorkoutSearchPage(
    val sessions: List<SessionWithSets>,
    val total: Int
)

/** The active weekly routine exactly as the schedule feature sees it. */
data class AiRoutineSchedule(
    val routineId: Long,
    val routineName: String,
    val days: List<RoutineDay>
)

/**
 * The access policy enforced by the dispatcher, not only by the prompt.
 *
 * Personal data and routine proposals share the same explicit Settings permission. When it is
 * false the model receives no tools at all: the exercise catalog may contain the athlete's custom
 * exercises, so it is personal data too.
 */
data class AiAccessPolicy(
    val personalData: Boolean = false,
    val routineModification: Boolean = false
) {
    fun permits(domain: AiToolDomain): Boolean = when (domain) {
        AiToolDomain.CATALOG -> personalData
        AiToolDomain.WORKOUT_DATA -> personalData
        AiToolDomain.ROUTINE_WRITE -> personalData && routineModification
    }

    companion object {
        val disabled = AiAccessPolicy()
        val all = AiAccessPolicy(personalData = true, routineModification = true)
    }
}

enum class AiToolDomain { CATALOG, WORKOUT_DATA, ROUTINE_WRITE }

/**
 * The only data surface a tool handler can reach.
 *
 * It is deliberately small and read-only. A tool never receives a DAO, a database handle, a file,
 * or an HTTP client: adding a tool means adding a method here, which keeps every capability the
 * model can reach reviewable in one place.
 */
interface AiDataAccess {
    suspend fun searchExercises(query: String, limit: Int): List<Exercise>
    suspend fun getExerciseById(id: Long): Exercise?
    suspend fun getExerciseByName(name: String): Exercise?
    suspend fun listExercises(
        category: String?,
        equipment: String?,
        limit: Int,
        offset: Int
    ): ExerciseListPage
    suspend fun allExercises(): List<Exercise>
    suspend fun getRoutineById(id: Long): SavedRoutine?
    suspend fun listRoutines(): List<SavedRoutine>
    suspend fun recentWorkouts(
        fromMillis: Long?,
        toMillis: Long?,
        exerciseName: String?,
        limit: Int,
        offset: Int
    ): AiWorkoutSearchPage
    suspend fun exerciseHistory(
        exerciseName: String,
        fromMillis: Long?,
        toMillis: Long?,
        limit: Int
    ): List<SessionWithSets>
    suspend fun currentRoutineSchedule(): AiRoutineSchedule?

    /**
     * The workout in progress, exactly as the autosave has it, or null when nothing is running.
     * Read-only tools must be able to answer questions about the current session, including drop
     * sets that have not been persisted to history yet.
     */
    suspend fun activeWorkoutDraft(): WorkoutDraft? = null
}

/** Repository-backed implementation used by the app. */
class RepositoryAiDataAccess(
    private val repository: FitnessRepository,
    private val preferences: UserPreferencesRepository? = null,
    private val planRepository: com.example.data.repository.PlanRepository? = null
) : AiDataAccess {

    override suspend fun searchExercises(query: String, limit: Int): List<Exercise> {
        val all = repository.getAllExercisesOnce()
        return AiExerciseSearch.rank(all, query).take(limit)
    }

    override suspend fun getExerciseById(id: Long): Exercise? = repository.getExerciseById(id)

    override suspend fun getExerciseByName(name: String): Exercise? =
        repository.getExerciseByName(name)
            ?: repository.getAllExercisesOnce().firstOrNull {
                AiExerciseName.matches(it.name, name)
            }

    override suspend fun listExercises(
        category: String?,
        equipment: String?,
        limit: Int,
        offset: Int
    ): ExerciseListPage {
        val filtered = repository.getAllExercisesOnce().filter { exercise ->
            (category == null || exercise.category.equals(category, ignoreCase = true)) &&
                (equipment == null || exercise.equipment.contains(equipment, ignoreCase = true))
        }.sortedBy { it.name.lowercase() }
        return ExerciseListPage(
            items = filtered.drop(offset).take(limit),
            total = filtered.size
        )
    }

    override suspend fun allExercises(): List<Exercise> = repository.getAllExercisesOnce()

    override suspend fun getRoutineById(id: Long): SavedRoutine? =
        planRepository?.getRoutine(id) ?: repository.getSavedRoutineById(id)

    override suspend fun listRoutines(): List<SavedRoutine> =
        planRepository?.getAllRoutinesOnce() ?: repository.getAllSavedRoutinesOnce()

    override suspend fun recentWorkouts(
        fromMillis: Long?,
        toMillis: Long?,
        exerciseName: String?,
        limit: Int,
        offset: Int
    ): AiWorkoutSearchPage {
        val query = exerciseName?.takeIf { it.isNotBlank() }
        val matching = repository.getAllSessionsWithSetsOnce()
            .asSequence()
            .filter { it.sets.any { set -> set.isCompleted } }
            .filter { it.session.startTime >= (fromMillis ?: Long.MIN_VALUE) }
            .filter { it.session.startTime <= (toMillis ?: Long.MAX_VALUE) }
            .filter { wrapper ->
                query == null || wrapper.sets.any {
                    it.isCompleted && AiExerciseName.matches(it.exerciseName, query)
                }
            }
            .sortedByDescending { it.session.startTime }
            .toList()
        return AiWorkoutSearchPage(
            sessions = matching.drop(offset).take(limit),
            total = matching.size
        )
    }

    override suspend fun exerciseHistory(
        exerciseName: String,
        fromMillis: Long?,
        toMillis: Long?,
        limit: Int
    ): List<SessionWithSets> {
        return repository.getAllSessionsWithSetsOnce()
            .asSequence()
            .filter { it.session.startTime >= (fromMillis ?: Long.MIN_VALUE) }
            .filter { it.session.startTime <= (toMillis ?: Long.MAX_VALUE) }
            .mapNotNull { wrapper ->
                val matchingSets = wrapper.sets.filter {
                    it.isCompleted && AiExerciseName.matches(it.exerciseName, exerciseName)
                }
                if (matchingSets.isEmpty()) null else wrapper.copy(sets = matchingSets)
            }
            .sortedByDescending { it.session.startTime }
            .take(limit)
            .toList()
    }

    override suspend fun activeWorkoutDraft(): WorkoutDraft? =
        preferences?.activeWorkoutDraftFlow?.first()?.let(WorkoutDraftCodec::decode)

    override suspend fun currentRoutineSchedule(): AiRoutineSchedule? {
        val pref = preferences ?: return null
        val exercises = repository.getAllExercisesOnce()
        val activeScheduleId = pref.activeScheduleIdFlow.first()
        val plans = planRepository
        if (activeScheduleId > 0L && plans != null) {
            val schedule = plans.getSchedule(activeScheduleId) ?: return null
            val routines = plans.getAllRoutinesOnce().associateBy { it.id }
            val slots = plans.getDaysOnce(activeScheduleId).associateBy { it.weekday }
            val days = (1..com.example.data.model.Weekday.COUNT).map { weekday ->
                val routine = slots[weekday]?.routineId?.let(routines::get)
                if (routine == null) {
                    com.example.data.model.RoutineDay(weekday = weekday)
                } else {
                    com.example.data.model.RoutineDay(
                        weekday = weekday,
                        dayTitle = routine.name,
                        exercises = RoutineParser.parseSingleWorkout(routine.exercisesJson, exercises),
                        note = routine.description,
                        routineId = routine.id
                    )
                }
            }
            return AiRoutineSchedule(
                routineId = schedule.id,
                routineName = schedule.name,
                days = days
            )
        }
        // Legacy active routine while the one-time migration has not run yet.
        val activeId = pref.activeRoutineIdFlow.first()
        if (activeId <= 0L) return null
        val routine = repository.getSavedRoutineById(activeId) ?: return null
        val weekStartsOnMonday = pref.appSettingsFlow.first().weekStartsOnMonday
        return AiRoutineSchedule(
            routineId = routine.id,
            routineName = routine.name,
            days = RoutineParser.parseWeek(
                routine.exercisesJson,
                exercises,
                weekStartsOnMonday = weekStartsOnMonday
            )
        )
    }
}

/**
 * Canonical form for comparing an exercise name produced by a model with the names stored locally.
 *
 * Models alternate between "Push-up", "Push Ups" and "pushups" for the same movement. The key below
 * removes case, punctuation, spacing and simple plural differences, and expands a small set of
 * compound spellings. It is deliberately an equality key, not a substring key: "push up" must never
 * be treated as the same exercise as "pike push up" or "push up plus". The stored name is never
 * changed; this only decides whether two names refer to the same movement.
 */
object AiExerciseName {

    private val compoundUps = Regex("\\b(push|press|pull|sit|chin|muscle)[- ]?ups?\\b")

    fun canonical(raw: String): String {
        var text = raw.trim().lowercase(Locale.ROOT)
        if (text.isBlank()) return ""
        text = text.replace('&', ' ')
        text = text.replace(compoundUps) { match ->
            if (match.groupValues[1] == "press") "push up" else match.groupValues[1] + " up"
        }
        text = text.replace(Regex("[^a-z0-9]+"), " ")
        val words = text.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .map(::singularize)
        return words.joinToString(" ")
    }

    fun compact(raw: String): String = canonical(raw).replace(" ", "")

    /** True when the two names describe the same movement under [canonical]. */
    fun matches(stored: String, query: String): Boolean {
        val storedKey = canonical(stored)
        val queryKey = canonical(query)
        if (storedKey.isBlank() || queryKey.isBlank()) return false
        return storedKey == queryKey || compact(stored) == compact(query)
    }

    private fun singularize(word: String): String = when {
        word.length <= 2 -> word
        word.endsWith("ies") && word.length > 4 -> word.dropLast(3) + "y"
        word.endsWith("sses") -> word.dropLast(2)
        word.endsWith("ches") || word.endsWith("shes") ||
            word.endsWith("xes") || word.endsWith("zes") -> word.dropLast(2)
        word.endsWith("s") && !word.endsWith("ss") &&
            !word.endsWith("us") && !word.endsWith("is") -> word.dropLast(1)
        else -> word
    }
}

/**
 * Deterministic, bounded exercise search.
 *
 * The catalog is around 1,300 rows, so ranking in memory is cheaper than a second query surface and
 * keeps the tool contract identical for every provider. The score is intentionally simple and
 * explainable: an exact name beats a prefix, a prefix beats a substring, and a muscle or equipment
 * match is a weaker hit. Every token the model typed must match somewhere, so "bench press" does not
 * return every chest exercise. Names and query both go through [AiExerciseName], so "push ups"
 * still finds "Push-Up" without matching "Pike Push Up".
 */
object AiExerciseSearch {

    fun rank(exercises: List<Exercise>, rawQuery: String): List<Exercise> {
        val query = AiExerciseName.canonical(rawQuery)
        if (query.isBlank()) return emptyList()
        val compactQuery = query.replace(" ", "")
        val tokens = query.split(Regex("\\s+")).filter { it.isNotBlank() }
        return exercises.mapNotNull { exercise ->
            val name = AiExerciseName.canonical(exercise.name)
            val compactName = name.replace(" ", "")
            val haystack = AiExerciseName.canonical(
                listOf(
                    exercise.name,
                    exercise.category,
                    exercise.bodyPart,
                    exercise.target,
                    exercise.equipment
                ).joinToString(" ")
            )
            val compactHaystack = haystack.replace(" ", "")
            if (tokens.any { !haystack.contains(it) && !compactHaystack.contains(it) }) {
                return@mapNotNull null
            }
            val score = when {
                name == query -> 100
                compactName == compactQuery -> 96
                name.startsWith(query) -> 80
                name.contains(query) -> 60
                compactName.contains(compactQuery) -> 58
                tokens.all { name.contains(it) || compactName.contains(it) } -> 50
                AiExerciseName.canonical(exercise.category) == query -> 40
                AiExerciseName.canonical(exercise.bodyPart) == query -> 38
                AiExerciseName.canonical(exercise.target) == query -> 36
                AiExerciseName.canonical(exercise.equipment) == query -> 34
                else -> 20
            }
            exercise to score
        }.sortedWith(
            compareByDescending<Pair<Exercise, Int>> { it.second }
                .thenBy { it.first.name.lowercase() }
        ).map { it.first }
    }
}

internal class AiToolArgumentException(message: String) : Exception(message)

/** Typed, bounded access to the arguments object a model produced. */
internal object AiToolArgs {

    fun requireText(obj: JSONObject, key: String, maxLength: Int = 200): String {
        val raw = obj.opt(key)
        if (raw == null || raw == JSONObject.NULL) {
            throw AiToolArgumentException("Missing required string argument \"$key\".")
        }
        val value = raw as? String
            ?: throw AiToolArgumentException("Argument \"$key\" must be a string.")
        val trimmed = value.trim()
        if (trimmed.isBlank()) {
            throw AiToolArgumentException("Argument \"$key\" must not be blank.")
        }
        if (trimmed.length > maxLength) {
            throw AiToolArgumentException("Argument \"$key\" is longer than $maxLength characters.")
        }
        return trimmed
    }

    fun optionalText(obj: JSONObject, key: String, maxLength: Int = 200): String? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val value = obj.opt(key) as? String
            ?: throw AiToolArgumentException("Argument \"$key\" must be a string.")
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null
        if (trimmed.length > maxLength) {
            throw AiToolArgumentException("Argument \"$key\" is longer than $maxLength characters.")
        }
        return trimmed
    }

    fun optionalInt(obj: JSONObject, key: String, default: Int, min: Int, max: Int): Int {
        if (!obj.has(key) || obj.isNull(key)) return default
        val value = number(obj, key)
        val whole = value.toInt()
        if (whole.toDouble() != value) {
            throw AiToolArgumentException("Argument \"$key\" must be a whole number.")
        }
        if (whole !in min..max) {
            throw AiToolArgumentException("Argument \"$key\" must be between $min and $max.")
        }
        return whole
    }

    fun optionalLong(obj: JSONObject, key: String): Long? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val value = number(obj, key)
        val whole = value.toLong()
        if (whole.toDouble() != value) {
            throw AiToolArgumentException("Argument \"$key\" must be a whole number.")
        }
        if (whole <= 0L) {
            throw AiToolArgumentException("Argument \"$key\" must be a positive id.")
        }
        return whole
    }

    private fun number(obj: JSONObject, key: String): Double {
        val raw = obj.opt(key)
        val value = when (raw) {
            is Number -> raw.toDouble()
            is String -> raw.toDoubleOrNull()
                ?: throw AiToolArgumentException("Argument \"$key\" must be a number.")
            else -> throw AiToolArgumentException("Argument \"$key\" must be a number.")
        }
        if (!value.isFinite()) {
            throw AiToolArgumentException("Argument \"$key\" must be a finite number.")
        }
        return value
    }
}

/**
 * The canonical tool catalog.
 *
 * Every definition is read-only and every name is stable. A provider adapter only ever sees these
 * definitions; it never gets to declare its own tools, so the same capability is available whether
 * the athlete uses Google, OpenAI, Anthropic, OpenRouter, xAI or a private endpoint.
 */
object AiToolCatalog {

    const val SEARCH_EXERCISES = "search_exercises"
    const val GET_EXERCISE_DETAILS = "get_exercise_details"
    const val LIST_EXERCISES = "list_exercises"
    const val GET_ROUTINE = "get_routine"
    const val LIST_ROUTINES = "list_routines"
    const val SEARCH_RECENT_WORKOUTS = "search_recent_workouts"
    const val GET_CURRENT_ROUTINE = "get_current_routine"
    const val GET_ACTIVE_WORKOUT = "get_active_workout"
    const val SEARCH_EXERCISE_HISTORY = "search_exercise_history"
    const val PROPOSE_ROUTINE_MODIFICATION = "propose_routine_modification"
    const val PROPOSE_NEW_ROUTINE = "propose_new_routine"

    /** Tools exposed for one request under the athlete's explicit permission. */
    fun available(access: AiAccessPolicy): List<AiToolDefinition> =
        definitions.filter { access.permits(it.domain) }

    val definitions: List<AiToolDefinition> = listOf(
        AiToolDefinition(
            name = SEARCH_EXERCISES,
            description = "Search the athlete's exercise library by name, muscle group, body part, " +
                "target muscle or equipment. Use this before recommending an exercise so you use a " +
                "real catalog name.",
            inputSchemaJson = """
                {
                  "type": "object",
                  "properties": {
                    "query": {
                      "type": "string",
                      "description": "Words to match, for example 'bench press' or 'hamstring'."
                    },
                    "limit": {
                      "type": "integer",
                      "minimum": 1,
                      "maximum": 20,
                      "default": 8
                    }
                  },
                  "required": ["query"],
                  "additionalProperties": false
                }
            """.trimIndent()
        ),
        AiToolDefinition(
            name = GET_EXERCISE_DETAILS,
            description = "Read the full catalog record of one exercise: category, body part, target " +
                "muscles, equipment and step-by-step instructions. Provide either exerciseId or name.",
            inputSchemaJson = """
                {
                  "type": "object",
                  "properties": {
                    "exerciseId": {
                      "type": "integer",
                      "description": "Stable exercise id returned by search_exercises."
                    },
                    "name": {
                      "type": "string",
                      "description": "Exact exercise name when the id is not known."
                    }
                  },
                  "additionalProperties": false
                }
            """.trimIndent()
        ),
        AiToolDefinition(
            name = LIST_EXERCISES,
            description = "List a page of the exercise library, optionally filtered by category or " +
                "equipment. Use it to see what the athlete actually has available.",
            inputSchemaJson = """
                {
                  "type": "object",
                  "properties": {
                    "category": {
                      "type": "string",
                      "description": "Chest, Back, Legs, Shoulders, Arms, Core or Calisthenics."
                    },
                    "equipment": {
                      "type": "string",
                      "description": "Partial equipment name, for example 'Dumbbell'."
                    },
                    "limit": {
                      "type": "integer",
                      "minimum": 1,
                      "maximum": 50,
                      "default": 20
                    },
                    "offset": {
                      "type": "integer",
                      "minimum": 0,
                      "maximum": 5000,
                      "default": 0
                    }
                  },
                  "additionalProperties": false
                }
            """.trimIndent()
        ),
        AiToolDefinition(
            name = GET_ROUTINE,
            description = "Read one standalone saved Routine (one workout, never multiple days), " +
                "including its ordered exercises, sets, reps, holds and per-exercise rest default. " +
                "Provide routineId or name; with neither, the most recent Routine is returned.",
            inputSchemaJson = """
                {
                  "type": "object",
                  "properties": {
                    "routineId": {
                      "type": "integer",
                      "description": "Stable routine id returned by list_routines."
                    },
                    "name": {
                      "type": "string",
                      "description": "Routine name, case insensitive."
                    }
                  },
                  "additionalProperties": false
                }
            """.trimIndent(),
            domain = AiToolDomain.WORKOUT_DATA
        ),
        AiToolDefinition(
            name = LIST_ROUTINES,
            description = "List the athlete's standalone saved Routines with their exercise count. " +
                "A Routine is one workout; use get_current_routine for the weekly Schedule.",
            inputSchemaJson = """
                {
                  "type": "object",
                  "properties": {
                    "limit": {
                      "type": "integer",
                      "minimum": 1,
                      "maximum": 50,
                      "default": 20
                    }
                  },
                  "additionalProperties": false
                }
            """.trimIndent(),
            domain = AiToolDomain.WORKOUT_DATA
        ),
        AiToolDefinition(
            name = SEARCH_RECENT_WORKOUTS,
            description = "Retrieve completed workout sessions in a time range, optionally filtered " +
                "by exercise name. Returns dates, duration, order and completed set performance, " +
                "including an explicit isWarmup flag, drop-set parent/order fields and warm-up, " +
                "main, drop and working set counts. " +
                "Use it for questions about recent training, volume, frequency or a specific session. " +
                "Warm-up sets count toward volume and set counts, but never as working-set PRs or 1RM records.",
            inputSchemaJson = """
                {
                  "type": "object",
                  "properties": {
                    "daysBack": {
                      "type": "integer",
                      "minimum": 1,
                      "maximum": 365,
                      "default": 30,
                      "description": "Include sessions from the last N days."
                    },
                    "exerciseName": {
                      "type": "string",
                      "description": "Optional exercise name to filter sessions."
                    },
                    "limit": {
                      "type": "integer",
                      "minimum": 1,
                      "maximum": 20,
                      "default": 10
                    },
                    "offset": {
                      "type": "integer",
                      "minimum": 0,
                      "maximum": 200,
                      "default": 0
                    }
                  },
                  "additionalProperties": false
                }
            """.trimIndent(),
            domain = AiToolDomain.WORKOUT_DATA,
            maxResultBytes = 32 * 1024
        ),
        AiToolDefinition(
            name = GET_CURRENT_ROUTINE,
            description = "Read the athlete's active weekly Schedule: seven weekday slots, each " +
                "assigned one standalone Routine or left as a rest day, with every exercise in order. " +
                "Use this instead of guessing the current plan.",
            inputSchemaJson = """
                {
                  "type": "object",
                  "properties": {},
                  "additionalProperties": false
                }
            """.trimIndent(),
            domain = AiToolDomain.WORKOUT_DATA,
            maxResultBytes = 48 * 1024
        ),
        AiToolDefinition(
            name = GET_ACTIVE_WORKOUT,
            description = "Read the workout currently in progress: its exercise order and every set, " +
                "including completion, warm-up status and explicit drop-set parent/order data. " +
                "Returns found=false when no workout is running. Use it for questions about the " +
                "current session; never confuse it with the weekly Schedule.",
            inputSchemaJson = """
                {
                  "type": "object",
                  "properties": {},
                  "additionalProperties": false
                }
            """.trimIndent(),
            domain = AiToolDomain.WORKOUT_DATA,
            maxResultBytes = 32 * 1024
        ),
        AiToolDefinition(
            name = SEARCH_EXERCISE_HISTORY,
            description = "Retrieve completed historical performance for one exact exercise. Returns " +
                "session dates and the completed sets with weight, reps, hold seconds, distance, " +
                "body weight, an explicit isWarmup flag and explicit drop-set parent/order data. " +
                "Use it for progress comparisons; never " +
                "combine different exercise names. Warm-up sets count toward volume but never as " +
                "working-set PRs, best sets or estimated 1RM records.",
            inputSchemaJson = """
                {
                  "type": "object",
                  "properties": {
                    "exerciseName": {
                      "type": "string",
                      "description": "Exact exercise name, for example 'Push-up' or 'Barbell Bench Press'."
                    },
                    "daysBack": {
                      "type": "integer",
                      "minimum": 1,
                      "maximum": 1095,
                      "default": 180
                    },
                    "limit": {
                      "type": "integer",
                      "minimum": 1,
                      "maximum": 30,
                      "default": 20
                    }
                  },
                  "required": ["exerciseName"],
                  "additionalProperties": false
                }
            """.trimIndent(),
            domain = AiToolDomain.WORKOUT_DATA,
            maxResultBytes = 32 * 1024
        ),
        AiToolDefinition(
            name = PROPOSE_ROUTINE_MODIFICATION,
            description = "Propose ONE change to the athlete's current weekly routine: replace one " +
                "exercise on one weekday with an existing catalog exercise, or with a new AI-generated " +
                "exercise. This tool does not modify anything. The app shows the proposal in chat and " +
                "applies it only after explicit approval.",
            inputSchemaJson = """
                {
                  "type": "object",
                  "properties": {
                    "routineId": {
                      "type": "integer",
                      "description": "Routine id from get_current_routine."
                    },
                    "weekday": {
                      "type": "integer",
                      "minimum": 1,
                      "maximum": 7,
                      "description": "1 = Monday ... 7 = Sunday."
                    },
                    "removeExerciseName": {
                      "type": "string",
                      "description": "Existing exercise name to remove from that day."
                    },
                    "replacementExerciseId": {
                      "type": "integer",
                      "description": "Existing catalog exercise id to use."
                    },
                    "replacementExerciseName": {
                      "type": "string",
                      "description": "Name of the existing or generated replacement."
                    },
                    "newExercise": {
                      "type": "object",
                      "description": "Only when no catalog exercise fits. Do not insert it before approval.",
                      "properties": {
                        "name": {"type": "string"},
                        "category": {"type": "string"},
                        "bodyPart": {"type": "string"},
                        "target": {"type": "string"},
                        "secondaryMuscles": {"type": "array", "items": {"type": "string"}},
                        "equipment": {"type": "string"},
                        "exerciseType": {"type": "string"},
                        "instructions": {"type": "string"},
                        "description": {"type": "string"}
                      },
                      "required": ["name", "category", "equipment", "exerciseType", "instructions"],
                      "additionalProperties": false
                    },
                    "reason": {
                      "type": "string",
                      "description": "Short evidence-based reason for the change."
                    }
                  },
                  "required": ["weekday", "removeExerciseName", "replacementExerciseName"],
                  "additionalProperties": false
                }
            """.trimIndent(),
            domain = AiToolDomain.ROUTINE_WRITE,
            maxResultBytes = 24 * 1024
        ),
        AiToolDefinition(
            name = PROPOSE_NEW_ROUTINE,
            description = "Propose a complete NEW weekly plan for explicit approval. A Routine is " +
                "exactly ONE workout; repeated workout days in the proposed week must reuse the same " +
                "Routine. After approval the app creates each unique Routine once and a Schedule with " +
                "seven weekday references; it does not activate the Schedule without an explicit " +
                "request. Use this only when the athlete asks for a new plan and has given enough " +
                "detail (goal, experience, training days, equipment). Prefer catalog exercises.",
            inputSchemaJson = """
                {
                  "type": "object",
                  "properties": {
                    "routineName": {"type": "string", "description": "Short routine name."},
                    "goal": {"type": "string", "description": "Short training goal."},
                    "summary": {"type": "string", "description": "One short sentence describing the plan."},
                    "days": {
                      "type": "array",
                      "minItems": 1,
                      "maxItems": 7,
                      "items": {
                        "type": "object",
                        "properties": {
                          "weekday": {"type": "integer", "minimum": 1, "maximum": 7, "description": "1 = Monday ... 7 = Sunday."},
                          "focus": {"type": "string", "description": "Short day title, e.g. Upper Body Push."},
                          "note": {"type": "string", "description": "One short optional note; omit or leave empty."},
                          "exercises": {
                            "type": "array",
                            "minItems": 1,
                            "maxItems": 12,
                            "items": {
                              "type": "object",
                              "properties": {
                                "name": {"type": "string", "description": "Exact catalog name, or the new exercise name."},
                                "exerciseId": {"type": "integer", "description": "Catalog id from search_exercises when known."},
                                "sets": {"type": "integer", "minimum": 1, "maximum": 10},
                                "reps": {"type": "integer", "minimum": 0, "maximum": 100},
                                "weightKg": {"type": "number", "minimum": 0},
                                "addedWeightKg": {"type": "number", "minimum": 0},
                                "holdSeconds": {"type": "integer", "minimum": 0, "maximum": 600},
                                "distanceKm": {"type": "number", "minimum": 0},
                                "restSeconds": {"type": "integer", "minimum": 0, "maximum": 600},
                                "targetEffortRpe": {
                                  "type": "number",
                                  "minimum": 6,
                                  "maximum": 10,
                                  "description": "Optional target effort as RPE 6-10. Never invent effort; omit when the athlete did not specify it."
                                },
                                "supersetGroupId": {
                                  "type": "string",
                                  "maxLength": 80,
                                  "pattern": "^[A-Za-z0-9_-]+$",
                                  "description": "Optional shared id for exactly two adjacent exercises in this day. Use only when the athlete asked for a superset; every member keeps its own sets and drops."
                                },
                                "prescription": {"type": "string", "description": "Optional, e.g. 4 sets x 8 reps."},
                                "dropSets": {
                                  "type": "array",
                                  "description": "Optional direct children of a main set. Only add them when the training plan calls for drop sets; never flatten them into top-level sets.",
                                  "maxItems": 20,
                                  "items": {
                                    "type": "object",
                                    "properties": {
                                      "mainSet": {"type": "integer", "minimum": 1, "maximum": 10, "description": "1-based main set this child continues."},
                                      "drop": {"type": "integer", "minimum": 1, "maximum": 20, "description": "1-based order within that main set."},
                                      "reps": {"type": "integer", "minimum": 0, "maximum": 100},
                                      "holdSeconds": {"type": "integer", "minimum": 0, "maximum": 600},
                                      "weightKg": {"type": "number", "minimum": 0},
                                      "addedWeightKg": {"type": "number", "minimum": 0},
                                      "distanceKm": {"type": "number", "minimum": 0},
                                      "restSeconds": {"type": "integer", "minimum": 0, "maximum": 600}
                                    },
                                    "required": ["mainSet"],
                                    "additionalProperties": false
                                  }
                                },
                                "newExercise": {
                                  "type": "object",
                                  "description": "Only when no catalog exercise fits. Do not create it before approval.",
                                  "properties": {
                                    "name": {"type": "string"},
                                    "category": {"type": "string"},
                                    "bodyPart": {"type": "string"},
                                    "target": {"type": "string"},
                                    "secondaryMuscles": {"type": "array", "items": {"type": "string"}},
                                    "equipment": {"type": "string"},
                                    "exerciseType": {"type": "string"},
                                    "supportedFields": {
                                      "type": "array",
                                      "description": "Explicit set fields: reps, weight, addedWeight, holdSeconds, timeSeconds, distanceKm. Omit to use the type defaults.",
                                      "items": {"type": "string", "enum": ["reps", "weight", "addedWeight", "holdSeconds", "timeSeconds", "distanceKm"]}
                                    },
                                    "instructions": {"type": "string"},
                                    "description": {"type": "string"}
                                  },
                                  "required": ["name", "category", "equipment", "exerciseType", "instructions"],
                                  "additionalProperties": false
                                }
                              },
                              "required": ["name", "sets", "reps"],
                              "additionalProperties": false
                            }
                          }
                        },
                        "required": ["weekday", "exercises"],
                        "additionalProperties": false
                      }
                    }
                  },
                  "required": ["routineName", "days"],
                  "additionalProperties": false
                }
            """.trimIndent(),
            domain = AiToolDomain.ROUTINE_WRITE,
            maxResultBytes = 32 * 1024
        )
    )

    fun definition(name: String): AiToolDefinition? =
        definitions.firstOrNull { it.name == name }
}

/** Maps the canonical names to handlers. No handler exists outside this registry. */
class AiToolRegistry(private val dataAccess: AiDataAccess) {

    fun definitions(access: AiAccessPolicy = AiAccessPolicy.disabled): List<AiToolDefinition> =
        AiToolCatalog.available(access)

    internal suspend fun execute(
        name: String,
        arguments: JSONObject,
        access: AiAccessPolicy
    ): JSONObject {
        val definition = AiToolCatalog.definition(name)
            ?: return toolError("unknown_tool", "The tool \"$name\" is not available.")
        if (!access.permits(definition.domain)) {
            return toolError(
                "permission_denied",
                "That tool is not available under the current AI data access permission."
            )
        }
        return when (name) {
            AiToolCatalog.SEARCH_EXERCISES -> searchExercises(arguments)
            AiToolCatalog.GET_EXERCISE_DETAILS -> getExerciseDetails(arguments)
            AiToolCatalog.LIST_EXERCISES -> listExercises(arguments)
            AiToolCatalog.GET_ROUTINE -> getRoutine(arguments)
            AiToolCatalog.LIST_ROUTINES -> listRoutines(arguments)
            AiToolCatalog.SEARCH_RECENT_WORKOUTS -> searchRecentWorkouts(arguments)
            AiToolCatalog.GET_CURRENT_ROUTINE -> getCurrentRoutine(arguments)
            AiToolCatalog.GET_ACTIVE_WORKOUT -> getActiveWorkout()
            AiToolCatalog.SEARCH_EXERCISE_HISTORY -> searchExerciseHistory(arguments)
            AiToolCatalog.PROPOSE_ROUTINE_MODIFICATION -> proposeRoutineModification(arguments)
            AiToolCatalog.PROPOSE_NEW_ROUTINE -> proposeNewRoutine(arguments)
            else -> toolError("unknown_tool", "The tool \"$name\" is not available.")
        }
    }

    private suspend fun searchExercises(args: JSONObject): JSONObject {
        val query = AiToolArgs.requireText(args, "query", maxLength = 120)
        val limit = AiToolArgs.optionalInt(args, "limit", default = 8, min = 1, max = 20)
        val results = dataAccess.searchExercises(query, limit)
        return JSONObject().apply {
            put("ok", true)
            put("query", query)
            put("count", results.size)
            put("exercises", JSONArray().apply { results.forEach { put(it.toSummaryJson()) } })
            put("note", if (results.isEmpty()) "No catalog exercise matched. Try fewer words." else "")
        }
    }

    private suspend fun getExerciseDetails(args: JSONObject): JSONObject {
        val id = AiToolArgs.optionalLong(args, "exerciseId")
        val name = AiToolArgs.optionalText(args, "name", maxLength = 160)
        if (id == null && name == null) {
            throw AiToolArgumentException("Provide exerciseId or name.")
        }
        val exercise = if (id != null) {
            dataAccess.getExerciseById(id)
        } else {
            dataAccess.getExerciseByName(name!!)
        } ?: return toolError(
            "not_found",
            "No exercise matched ${if (id != null) "id $id" else "\"$name\""}."
        )
        return JSONObject().apply {
            put("ok", true)
            put("exercise", exercise.toDetailJson())
        }
    }

    private suspend fun listExercises(args: JSONObject): JSONObject {
        val category = AiToolArgs.optionalText(args, "category", maxLength = 60)
        val equipment = AiToolArgs.optionalText(args, "equipment", maxLength = 80)
        val limit = AiToolArgs.optionalInt(args, "limit", default = 20, min = 1, max = 50)
        val offset = AiToolArgs.optionalInt(args, "offset", default = 0, min = 0, max = 5000)
        val page = dataAccess.listExercises(category, equipment, limit, offset)
        return JSONObject().apply {
            put("ok", true)
            put("total", page.total)
            put("offset", offset)
            put("count", page.items.size)
            put("hasMore", offset + page.items.size < page.total)
            put("exercises", JSONArray().apply { page.items.forEach { put(it.toSummaryJson()) } })
        }
    }

    private suspend fun getRoutine(args: JSONObject): JSONObject {
        val id = AiToolArgs.optionalLong(args, "routineId")
        val name = AiToolArgs.optionalText(args, "name", maxLength = 160)
        val routines = dataAccess.listRoutines()
        if (routines.isEmpty()) {
            return toolError("no_routines", "The athlete has no saved routines yet.")
        }
        val routine = when {
            id != null -> routines.firstOrNull { it.id == id }
            name != null -> routines.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: routines.firstOrNull { it.name.contains(name, ignoreCase = true) }
            else -> routines.firstOrNull()
        } ?: return toolError(
            "not_found",
            "No routine matched ${if (id != null) "id $id" else "\"$name\""}."
        )
        val catalog = dataAccess.allExercises()
        val days = RoutineParser.parseDays(routine.exercisesJson, catalog)
        return JSONObject().apply {
            put("ok", true)
            put("routine", routine.toRoutineJson(days, catalog))
            put("selectedMostRecent", id == null && name == null)
        }
    }

    private suspend fun listRoutines(args: JSONObject): JSONObject {
        val limit = AiToolArgs.optionalInt(args, "limit", default = 20, min = 1, max = 50)
        val all = dataAccess.listRoutines()
        val exercises = dataAccess.allExercises()
        val page = all.take(limit)
        return JSONObject().apply {
            put("ok", true)
            put("total", all.size)
            put("count", page.size)
            put(
                "routines",
                JSONArray().apply {
                    page.forEach { routine ->
                        val days = RoutineParser.parseDays(routine.exercisesJson, exercises)
                        put(
                            JSONObject().apply {
                                put("id", routine.id)
                                put("name", routine.name)
                                put("goal", routine.goal)
                                put("daysPerWeek", routine.daysPerWeek)
                                put("createdAt", routine.createdAt)
                                put("exerciseCount", days.sumOf { it.exercises.size })
                            }
                        )
                    }
                }
            )
        }
    }

    private suspend fun searchRecentWorkouts(args: JSONObject): JSONObject {
        val daysBack = AiToolArgs.optionalInt(args, "daysBack", default = 30, min = 1, max = 365)
        val exerciseName = AiToolArgs.optionalText(args, "exerciseName", maxLength = 160)
        val limit = AiToolArgs.optionalInt(args, "limit", default = 10, min = 1, max = 20)
        val offset = AiToolArgs.optionalInt(args, "offset", default = 0, min = 0, max = 200)
        val to = System.currentTimeMillis()
        val from = to - daysBack * DAY_MILLIS
        val page = dataAccess.recentWorkouts(from, to, exerciseName, limit, offset)
        return JSONObject().apply {
            put("ok", true)
            put("fromDate", isoDate(from))
            put("toDate", isoDate(to))
            put("total", page.total)
            put("offset", offset)
            put("hasMore", offset + page.sessions.size < page.total)
            put("sessions", JSONArray().apply { page.sessions.forEach { put(it.toWorkoutJson()) } })
        }
    }

    private suspend fun getCurrentRoutine(args: JSONObject): JSONObject {
        val schedule = dataAccess.currentRoutineSchedule()
            ?: return toolError(
                "no_active_routine",
                "The athlete has no active Schedule. No weekly plan is configured."
            )
        if (schedule.days.isEmpty()) {
            return toolError(
                "no_active_routine",
                "The active Schedule has no configured weekday slots."
            )
        }
        val catalog = dataAccess.allExercises()
        val byId = catalog.associateBy { it.id }
        val byName = catalog.associateBy { it.name.trim().lowercase() }
        return JSONObject().apply {
            put("ok", true)
            put("scheduleId", schedule.routineId)
            put("scheduleName", schedule.routineName)
            put("routineId", schedule.routineId)
            put("routineName", schedule.routineName)
            put("days", JSONArray().apply {
                schedule.days.forEach { day ->
                    put(JSONObject().apply {
                        put("weekday", day.weekday)
                        put("day", day.dayName)
                        put("title", day.dayTitle)
                        put("routineId", day.routineId)
                        put("restDay", day.isRestDay)
                        put("note", day.note)
                        put("exercises", JSONArray().apply {
                            day.exercises.forEach { item ->
                                put(JSONObject().apply {
                                    put("exerciseId", item.exerciseId)
                                    put("name", item.exerciseName)
                                    put("exerciseType", item.exerciseType)
                                    put("category", item.category)
                                    put("equipment", item.equipment)
                                    put("sets", item.targetSets)
                                    put("reps", item.targetReps)
                                    put("weightKg", item.targetWeight)
                                    put("addedWeightKg", item.targetAddedWeightKg)
                                    put("holdSeconds", item.targetHoldSeconds)
                                    put("distanceKm", item.targetDistanceKm)
                                    put("restSeconds", item.restSeconds)
put("targetEffortRpe", item.targetEffortRpe ?: JSONObject.NULL)
put("supersetGroupId", item.supersetGroupId ?: JSONObject.NULL)
                                    put("prescription", item.prescription)
                                    val catalogExercise = byId[item.exerciseId]
                                        ?: byName[item.exerciseName.trim().lowercase()]
                                    if (catalogExercise != null) {
                                        put("supportedFields", JSONArray().apply {
                                            catalogExercise.exerciseFields.forEach { put(it.id) }
                                        })
                                    }
                                    put("dropSets", item.dropSets.toToolDropSetArray())
                                    put(
                                        "instructions",
                                        catalogExercise?.instructions.orEmpty().take(500)
                                    )
                                    put(
                                        "instructionsTruncated",
                                        (catalogExercise?.instructions?.length ?: 0) > 500
                                    )
                                })
                            }
                        })
                    })
                }
            })
        }
    }

    /**
     * The live session as structured JSON. The set fields mirror the history tool so the model can
     * use one mental model for completed and in-progress work, and drop children keep their parent
     * UID instead of being flattened.
     */
    private suspend fun getActiveWorkout(): JSONObject {
        val draft = dataAccess.activeWorkoutDraft()
            ?: return JSONObject().apply {
                put("ok", true)
                put("found", false)
                put("message", "No workout is currently in progress.")
            }
        val routineItemsById = draft.routineItems.associateBy { it.exerciseId }
        val setsByExercise = draft.sets.groupBy { it.exerciseId }
        val catalogById = dataAccess.allExercises().associateBy { it.id }
        val orderedIds = LinkedHashSet<Long>().apply {
            addAll(draft.exerciseIds)
            addAll(draft.sets.map { it.exerciseId })
        }
        return JSONObject().apply {
            put("ok", true)
            put("found", true)
            put("kind", draft.kind)
            put("title", draft.title)
            put("startMillis", draft.startTime)
            put("routineId", draft.routineId)
            put("dayTitle", draft.dayTitle)
            put("sessionOnlyExerciseIds", JSONArray().apply {
                draft.sessionOnlyExerciseIds.sorted().forEach { put(it) }
            })
            put("exercises", JSONArray().apply {
                orderedIds.forEachIndexed { index, exerciseId ->
                    val exerciseSets = setsByExercise[exerciseId].orEmpty()
                    val item = routineItemsById[exerciseId]
                    put(JSONObject().apply {
                        put("order", index + 1)
                        put("exerciseId", exerciseId)
                        put("name", item?.exerciseName ?: exerciseSets.firstOrNull()?.exerciseName.orEmpty())
                        put("exerciseType", item?.exerciseType ?: exerciseSets.firstOrNull()?.exerciseType.orEmpty())
                        catalogById[exerciseId]?.let { catalogExercise ->
                            put("supportedFields", JSONArray().apply {
                                catalogExercise.exerciseFields.forEach { put(it.id) }
                            })
                        }
                        put("sessionOnly", exerciseId in draft.sessionOnlyExerciseIds)
                        put("mainSetCount", exerciseSets.count { !it.isDropSet })
                        put("dropSetCount", exerciseSets.count { it.isDropSet })
                        put("completedSetCount", exerciseSets.count { it.isCompleted })
                    })
                }
            })
            put("sets", JSONArray().apply {
                draft.sets.sortedWith(
                    compareBy({ it.setNumber }, { it.isDropSet }, { it.dropIndex }, { it.timestamp })
                ).forEach { set ->
                    put(JSONObject().apply {
                        put("setUid", set.setUid)
                        put("exerciseId", set.exerciseId)
                        put("exerciseName", set.exerciseName)
                        put("setNumber", set.setNumber)
                        put("isDropSet", set.isDropSet)
                        put("parentSetUid", set.parentSetUid ?: JSONObject.NULL)
                        put("dropIndex", set.dropIndex)
                        put("weightKg", set.weight)
                        put("addedWeightKg", set.addedWeight)
                        put("reps", set.reps)
                        put("holdSeconds", set.holdDurationSeconds)
                        put("distanceKm", set.distanceKm)
                        put("isWarmup", set.isWarmup)
                        put("isCompleted", set.isCompleted)
                        put("restSecondsOverride", set.restSecondsOverride ?: JSONObject.NULL)
                        put("effortRpe", set.effortRpe ?: JSONObject.NULL)
                        put("supersetGroupId", set.supersetGroupId ?: JSONObject.NULL)
                    })
                }
            })
        }
    }

    private suspend fun searchExerciseHistory(args: JSONObject): JSONObject {
        val exerciseName = AiToolArgs.requireText(args, "exerciseName", maxLength = 160)
        val daysBack = AiToolArgs.optionalInt(args, "daysBack", default = 180, min = 1, max = 1095)
        val limit = AiToolArgs.optionalInt(args, "limit", default = 20, min = 1, max = 30)
        val to = System.currentTimeMillis()
        val from = to - daysBack * DAY_MILLIS
        val sessions = dataAccess.exerciseHistory(exerciseName, from, to, limit)
        val catalog = dataAccess.allExercises()
        val catalogMatch = catalog.firstOrNull { AiExerciseName.matches(it.name, exerciseName) }
        return JSONObject().apply {
            put("ok", true)
            put("exerciseName", exerciseName)
            put("found", sessions.isNotEmpty())
            put("exerciseInCatalog", catalogMatch != null)
            catalogMatch?.let { exercise ->
                put("catalogName", exercise.name)
                put("catalogExerciseId", exercise.id)
                put("catalogExerciseType", exercise.exerciseType)
                put("supportedFields", JSONArray().apply {
                    exercise.exerciseFields.forEach { put(it.id) }
                })
            }
            put("fromDate", isoDate(from))
            put("toDate", isoDate(to))
            put("count", sessions.size)
            if (sessions.isEmpty()) {
                put(
                    "note",
                    if (catalogMatch != null) {
                        "The exercise exists in the catalog but has no completed sets in this period."
                    } else {
                        "No catalog exercise matches that name and there is no workout history for it."
                    }
                )
                if (catalogMatch == null) {
                    val closest = AiExerciseSearch.rank(catalog, exerciseName).take(5)
                    if (closest.isNotEmpty()) {
                        put(
                            "closestMatches",
                            JSONArray().apply { closest.forEach { put(it.toSummaryJson()) } }
                        )
                        put(
                            "closestMatchesNote",
                            "These are suggestions only. They are not the exact exercise the caller named."
                        )
                    }
                }
            }
            put("sessions", JSONArray().apply { sessions.forEach { put(it.toWorkoutJson()) } })
        }
    }

    private suspend fun proposeNewRoutine(args: JSONObject): JSONObject {
        val routineName = AiToolArgs.requireText(args, "routineName", maxLength = 120)
        val goal = AiToolArgs.optionalText(args, "goal", maxLength = 160).orEmpty()
        val summary = AiToolArgs.optionalText(args, "summary", maxLength = 400).orEmpty()
        val daysArray = args.optJSONArray("days")
            ?: throw AiToolArgumentException("Missing required array argument \"days\".")
        if (daysArray.length() == 0) {
            throw AiToolArgumentException("Argument \"days\" must contain at least one training day.")
        }

        val byWeekday = linkedMapOf<Int, ProposedRoutineDay>()
        for (dayIndex in 0 until daysArray.length()) {
            val dayObj = daysArray.optJSONObject(dayIndex)
                ?: throw AiToolArgumentException("Each entry of \"days\" must be an object.")
            val weekday = dayObj.optInt("weekday", 0)
            if (weekday !in 1..7) {
                throw AiToolArgumentException("Each day needs \"weekday\" between 1 (Monday) and 7 (Sunday).")
            }
            val focus = dayObj.optString("focus", "").trim()
            val note = dayObj.optString("note", "").trim()
            val exercisesArray = dayObj.optJSONArray("exercises")
                ?: throw AiToolArgumentException("Day $weekday is missing its \"exercises\" array.")
            if (exercisesArray.length() == 0) {
                throw AiToolArgumentException("Day $weekday must contain at least one exercise.")
            }
            val parsedExercises = mutableListOf<ProposedRoutineExercise>()
            for (exerciseIndex in 0 until exercisesArray.length()) {
                val exObj = exercisesArray.optJSONObject(exerciseIndex)
                    ?: throw AiToolArgumentException("Each exercise must be an object.")
                val rawName = exObj.optString("name", "").trim()
                if (rawName.isBlank()) {
                    throw AiToolArgumentException("Every exercise needs a non-empty \"name\".")
                }
                val suppliedId = exObj.optLong("exerciseId", 0L).takeIf { it > 0L }
                val catalog = if (suppliedId != null) {
                    dataAccess.getExerciseById(suppliedId)
                } else {
                    dataAccess.getExerciseByName(rawName)
                }
                val generated = exObj.optJSONObject("newExercise")?.let { newObj ->
                    AiExerciseFactory.fromJsonObject(
                        newObj,
                        fallbackName = newObj.optString("name", rawName)
                    ) ?: throw AiToolArgumentException(
                        "The newExercise \"$rawName\" is not a valid exercise record."
                    )
                }
                if (generated != null && generated.instructions.isBlank()) {
                    throw AiToolArgumentException(
                        "The newExercise \"$rawName\" needs non-empty \"instructions\"."
                    )
                }
                if (catalog == null && generated == null) {
                    throw AiToolArgumentException(
                        "No catalog exercise matches \"$rawName\". Call search_exercises and use a " +
                            "real name, or include newExercise metadata for a genuinely new movement."
                    )
                }
                val sets = exObj.optInt("sets", 3).coerceIn(1, 10)
                var reps = exObj.optInt("reps", 8).coerceIn(0, 100)
                var hold = exObj.optInt("holdSeconds", 0).coerceIn(0, 600)
                if (reps <= 0 && hold <= 0) reps = 8
                val equipment = catalog?.equipment
                    ?: generated?.equipment.orEmpty().ifBlank { "Bodyweight" }
                // The exercise's explicit configuration is authoritative. The model may be using
                // an older schema that always emits weight and reps; never store those values for
                // an exercise that does not display them.
                val fields = (catalog ?: generated)?.exerciseFields.orEmpty()
                fun supports(field: com.example.data.model.ExerciseField): Boolean =
                    fields.isEmpty() || field in fields

                var weightKg = exObj.optDouble("weightKg", 0.0).coerceAtLeast(0.0)
                var addedWeightKg = exObj.optDouble("addedWeightKg", 0.0).coerceAtLeast(0.0)
                var distanceKm = exObj.optDouble("distanceKm", 0.0).coerceAtLeast(0.0)
                if (!supports(com.example.data.model.ExerciseField.REPS)) reps = 0
                if (
                    !supports(com.example.data.model.ExerciseField.HOLD_SECONDS) &&
                    !supports(com.example.data.model.ExerciseField.TIME_SECONDS)
                ) {
                    hold = 0
                }
                if (!supports(com.example.data.model.ExerciseField.WEIGHT)) weightKg = 0.0
                if (!supports(com.example.data.model.ExerciseField.ADDED_WEIGHT)) addedWeightKg = 0.0
                if (!supports(com.example.data.model.ExerciseField.DISTANCE_KM)) distanceKm = 0.0

                val parsedPrescription = RoutineParser.shortPrescription(
                    exObj.optString("prescription", ""),
                    equipment
                ).ifBlank {
                    when {
                        hold > 0 && reps > 0 -> "$sets sets x $reps reps / ${hold}s"
                        hold > 0 -> "$sets sets x ${hold}s"
                        distanceKm > 0.0 -> "$sets sets x ${distanceKm} km"
                        else -> "$sets sets x $reps reps"
                    }
                }
                val proposed = ProposedRoutineExercise(
                    name = catalog?.name ?: generated!!.name,
                    exerciseId = catalog?.id ?: 0L,
                    category = catalog?.category ?: generated?.category.orEmpty(),
                    equipment = equipment,
                    exerciseType = catalog?.exerciseType
                        ?: generated?.exerciseType
                        ?: com.example.data.model.ExerciseType.PURE_BODYWEIGHT,
                    generatedExercise = generated?.let(ProposedExercise::fromExercise),
                    targetSets = sets,
                    targetReps = reps,
                    targetWeightKg = weightKg,
                    targetAddedWeightKg = addedWeightKg,
                    targetHoldSeconds = hold,
                    targetDistanceKm = distanceKm,
                    restSeconds = exObj.optInt("restSeconds", 90).coerceIn(0, 600),
                    prescription = parsedPrescription,
                    targetEffortRpe = com.example.data.model.EffortScale.normalize(
                        exObj.optDouble("targetEffortRpe", Double.NaN)
                    ),
                    supersetGroupId = exObj.optString("supersetGroupId")
                        .trim()
                        .takeIf { it.isNotBlank() && it.length <= 80 },
                    dropSets = parseToolDropSets(exObj, sets, fields)
                )
                parsedExercises.add(
                    if (hold > 0 || distanceKm > 0.0) {
                        proposed.copy(
                            prescription = RoutineParser.buildPrescription(proposed.toRoutineItem(1, "", ""))
                        )
                    } else {
                        proposed
                    }
                )
            }
            val existing = byWeekday[weekday]
            byWeekday[weekday] = if (existing == null) {
                ProposedRoutineDay(weekday, focus, note, parsedExercises)
            } else {
                existing.copy(
                    focus = existing.focus.ifBlank { focus },
                    note = existing.note.ifBlank { note },
                    exercises = existing.exercises + parsedExercises
                )
            }
        }

        val proposal = NewRoutineProposal(
            routineName = routineName,
            goal = goal,
            summary = summary,
            days = byWeekday.values.sortedBy { it.weekday }
        )
        if (proposal.trainingDays == 0 || proposal.exerciseCount == 0) {
            throw AiToolArgumentException("The routine must contain at least one exercise.")
        }
        // Show the athlete only superset groups that the approval service can actually keep:
        // exactly two adjacent exercises on the same day.
        val normalizedProposal = proposal.copy(
            days = proposal.days.map { day ->
                val kept = com.example.data.model.SupersetRules.normalize(
                    day.exercises.map { it.toRoutineItem(day.weekday, day.focus, day.note) }
                ).mapNotNull { it.supersetGroupId }.toSet()
                day.copy(
                    exercises = day.exercises.map { exercise ->
                        if (exercise.supersetGroupId != null &&
                            exercise.supersetGroupId !in kept
                        ) {
                            exercise.copy(supersetGroupId = null)
                        } else {
                            exercise
                        }
                    }
                )
            }
        )
        return JSONObject().apply {
            put("ok", true)
            put("proposal", normalizedProposal.toJson())
            put(
                "message",
                "The app will show this plan for approval. Nothing has been created or saved yet."
            )
        }
    }

    private suspend fun proposeRoutineModification(args: JSONObject): JSONObject {
        val weekday = AiToolArgs.optionalInt(args, "weekday", default = 0, min = 1, max = 7)
        val removeName = AiToolArgs.requireText(args, "removeExerciseName", maxLength = 160)
        val requestedRoutineId = AiToolArgs.optionalLong(args, "routineId")
        val activeSchedule = dataAccess.currentRoutineSchedule()
            ?: return toolError("no_active_routine", "There is no active weekly routine to change.")
        val days = activeSchedule.days.filter { !it.isRestDay }
        val day = days.firstOrNull { it.weekday == weekday }
            ?: return toolError("invalid_day", "The routine has no day for weekday $weekday.")
        // A Schedule is a week of separate standalone Routines. Resolve the Routine that owns the
        // requested weekday; using the Schedule id here would edit the wrong row in a multi-day plan.
        // Legacy single-Routine weeks have no per-day id, so their active id is the fallback.
        val dayRoutineId = day.routineId.takeIf { it > 0L } ?: activeSchedule.routineId
        if (requestedRoutineId != null &&
            requestedRoutineId != activeSchedule.routineId &&
            requestedRoutineId != dayRoutineId
        ) {
            return toolError(
                "not_active_routine",
                "Only the Routine in the athlete's active Schedule can be changed."
            )
        }
        val routine = dataAccess.getRoutineById(dayRoutineId)
            ?: return toolError("not_found", "The routine no longer exists.")
        val removed = day.exercises.firstOrNull {
            it.exerciseName.trim().equals(removeName, ignoreCase = true)
        } ?: return toolError(
            "exercise_not_in_day",
            "\"$removeName\" is not in ${day.dayName}. Read the current routine again before proposing."
        )

        val replacementId = AiToolArgs.optionalLong(args, "replacementExerciseId")
        val replacementName = AiToolArgs.optionalText(args, "replacementExerciseName", maxLength = 160)
        val newExerciseObj = args.optJSONObject("newExercise")
        val newExercise = newExerciseObj?.let(ProposedExercise::fromJson)
        if (newExerciseObj != null && newExercise == null) {
            return toolError("invalid_exercise", "The new exercise record is incomplete or invalid.")
        }

        val existing = when {
            replacementId != null -> dataAccess.getExerciseById(replacementId)
                ?: return toolError("not_found", "No catalog exercise has id $replacementId.")
            !replacementName.isNullOrBlank() -> dataAccess.getExerciseByName(replacementName)
            else -> null
        }
        if (existing != null && newExercise != null) {
            return toolError(
                "ambiguous_replacement",
                "A proposal can use an existing exercise or create one, not both."
            )
        }
        val finalName = existing?.name ?: newExercise?.name ?: replacementName
        if (finalName.isNullOrBlank()) {
            return toolError(
                "missing_replacement",
                "Provide replacementExerciseId, a matching replacementExerciseName, or newExercise."
            )
        }
        if (newExercise != null) {
            val collision = dataAccess.getExerciseByName(newExercise.name)
            if (collision != null) {
                return toolError(
                    "exercise_exists",
                    "That exercise already exists with id ${collision.id}. Use replacementExerciseId."
                )
            }
        }
        if (existing == null && newExercise == null && replacementName != null) {
            return toolError(
                "exercise_not_found",
                "No catalog exercise matched \"$replacementName\". Search first, or provide newExercise."
            )
        }
        if (removed.exerciseName.equals(finalName, ignoreCase = true)) {
            return toolError("same_exercise", "The replacement is the same exercise that would be removed.")
        }

        val reason = AiToolArgs.optionalText(args, "reason", maxLength = 600)
            ?: "Requested by the athlete."
        val proposal = RoutineModificationProposal(
            routineId = routine.id,
            routineName = routine.name,
            routineSignature = RoutineModificationProposal.signatureOf(routine),
            weekday = weekday,
            dayTitle = day.dayTitle,
            removeExerciseName = removed.exerciseName,
            removeExerciseId = removed.exerciseId,
            replacementExerciseId = existing?.id,
            replacementExerciseName = finalName,
            generatedExercise = newExercise,
            reason = reason,
            summary = "Replace ${removed.exerciseName} with $finalName on ${day.dayName}" +
                if (day.dayTitle.isNotBlank()) " (${day.dayTitle})" else ""
        )
        return JSONObject().apply {
            put("ok", true)
            put("proposal", proposal.toJson())
            put(
                "note",
                "Nothing has been changed. The app will ask the athlete to approve this exact proposal."
            )
        }
    }

    private fun toolError(code: String, message: String): JSONObject = JSONObject().apply {
        put("ok", false)
        put("error", code)
        put("message", message)
    }
}

/**
 * Validates and executes one call.
 *
 * Validation happens before the handler runs, and every failure is turned into a JSON result the
 * model can read. A thrown exception would end the conversation; a structured error lets the model
 * correct a bad argument or choose another tool. Cancellation is always rethrown.
 */
class AiToolDispatcher(private val registry: AiToolRegistry) {

    fun definitions(access: AiAccessPolicy = AiAccessPolicy.disabled): List<AiToolDefinition> =
        registry.definitions(access)

    suspend fun dispatch(
        call: AiToolCall,
        access: AiAccessPolicy = AiAccessPolicy.disabled
    ): AiToolResult {
        val definition = AiToolCatalog.definition(call.name)
        if (definition == null) {
            return errorResult(
                call,
                "unknown_tool",
                "The tool \"${call.name}\" does not exist. Available tools: " +
                    registry.definitions(access).joinToString { it.name } + "."
            )
        }
        if (!access.permits(definition.domain)) {
            return errorResult(
                call,
                "permission_denied",
                "That tool is not available under the current AI data access permission. " +
                    "Available tools: " + registry.definitions(access).joinToString { it.name } + "."
            )
        }

        val arguments = try {
            parseArguments(call.argumentsJson)
        } catch (e: AiToolArgumentException) {
            return errorResult(call, "invalid_arguments", e.message.orEmpty())
        }

        val result = try {
            registry.execute(call.name, arguments, access)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: AiToolArgumentException) {
            return errorResult(call, "invalid_arguments", e.message.orEmpty())
        } catch (e: Exception) {
            return errorResult(
                call,
                "tool_failed",
                "The tool could not complete: ${e.message?.take(180) ?: "unknown error"}"
            )
        }

        val serialized = result.toString()
        if (serialized.toByteArray(Charsets.UTF_8).size > definition.maxResultBytes) {
            return errorResult(
                call,
                "result_too_large",
                "The tool result was too large. Narrow the request with a smaller limit or a more " +
                    "specific query."
            )
        }
        return AiToolResult(
            callId = call.id,
            name = call.name,
            contentJson = serialized,
            isError = !result.optBoolean("ok", false)
        )
    }

    private fun parseArguments(raw: String): JSONObject {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return JSONObject()
        return try {
            JSONObject(trimmed)
        } catch (_: Exception) {
            throw AiToolArgumentException("The tool arguments were not a valid JSON object.")
        }
    }

    private fun errorResult(call: AiToolCall, code: String, message: String): AiToolResult {
        val definition = AiToolCatalog.definition(call.name)
        val json = JSONObject().apply {
            put("ok", false)
            put("error", code)
            put("message", message)
            if (definition != null) {
                put("tool", definition.name)
                put(
                    "correctArgumentsSchema",
                    runCatching { JSONObject(definition.inputSchemaJson) }
                        .getOrElse { JSONObject().put("type", "object") }
                )
                put(
                    "howToCorrect",
                    "Call ${definition.name} again with an arguments object that matches " +
                        "correctArgumentsSchema, or answer without tools."
                )
            }
        }
        return AiToolResult(
            callId = call.id,
            name = call.name,
            contentJson = json.toString(),
            isError = true
        )
    }
}

private fun Exercise.toSummaryJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("category", category)
    put("bodyPart", bodyPart)
    put("target", target)
    put("equipment", equipment)
    put("exerciseType", exerciseType)
    put("supportedFields", JSONArray().apply { exerciseFields.forEach { put(it.id) } })
}

private fun Exercise.toDetailJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("category", category)
    put("bodyPart", bodyPart)
    put("target", target)
    put("secondaryMuscles", parseStringArray(secondaryMuscles))
    put("equipment", equipment)
    put("exerciseType", exerciseType)
    put("supportedFields", JSONArray().apply { exerciseFields.forEach { put(it.id) } })
    put("instructions", instructions.take(4000))
    put("instructionsTruncated", instructions.length > 4000)
    put("description", description.take(1000))
    put("isCustom", isCustom)
}

private fun SavedRoutine.toRoutineJson(
    days: List<com.example.data.model.RoutineDay>,
    catalog: List<Exercise>
): JSONObject =
    JSONObject().apply {
        put("id", id)
        put("name", name)
        put("description", description)
        put("goal", goal)
        put("daysPerWeek", daysPerWeek)
        put("createdAt", createdAt)
        put(
            "days",
            JSONArray().apply {
                days.forEach { day ->
                    put(
                        JSONObject().apply {
                            put("weekday", day.weekday)
                            put("day", day.dayName)
                            put("title", day.dayTitle)
                            put("note", day.note)
                            put(
                                "exercises",
                                JSONArray().apply {
                                    day.exercises.forEach { item ->
                                        put(
                                            JSONObject().apply {
                                                put("exerciseId", item.exerciseId)
                                                put("name", item.exerciseName)
                                                put("exerciseType", item.exerciseType)
                                                val catalogExercise = catalog.firstOrNull {
                                                    it.id == item.exerciseId ||
                                                        it.name.equals(item.exerciseName, ignoreCase = true)
                                                }
                                                if (catalogExercise != null) {
                                                    put("supportedFields", JSONArray().apply {
                                                        catalogExercise.exerciseFields.forEach { put(it.id) }
                                                    })
                                                }
                                                put("equipment", item.equipment)
                                                put("sets", item.targetSets)
                                                put("reps", item.targetReps)
                                                put("weightKg", item.targetWeight)
                                                put("addedWeightKg", item.targetAddedWeightKg)
                                                put("holdSeconds", item.targetHoldSeconds)
                                                put("distanceKm", item.targetDistanceKm)
                                                put("restSeconds", item.restSeconds)
                                                put("targetEffortRpe", item.targetEffortRpe ?: JSONObject.NULL)
                                                put("supersetGroupId", item.supersetGroupId ?: JSONObject.NULL)
                                                put("prescription", item.prescription)
                                                put("dropSets", item.dropSets.toToolDropSetArray())
                                            }
                                        )
                                    }
                                }
                            )
                        }
                    )
                }
            }
        )
    }

private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

private fun isoDate(epochMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(epochMillis))

private const val MAX_SETS_IN_TOOL_RESULT = 120

private fun SessionWithSets.toWorkoutJson(): JSONObject = JSONObject().apply {
    put("sessionId", session.id)
    put("title", session.title)
    put("date", isoDate(session.startTime))
    // A human-readable copy makes small models far less likely to shift a date by a day when
    // summarising several sessions. Both fields come from the same local timestamp.
    put(
        "dateLabel",
        SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(Date(session.startTime))
    )
    put("startMillis", session.startTime)
    put("endMillis", session.endTime)
    put("durationMinutes", ((session.endTime - session.startTime).coerceAtLeast(0L) / 60_000L))
    put("notes", session.notes.take(500))
    put("routineId", session.routineId)
    put("routineWeekday", session.routineWeekday)
    put("plannedDate", session.plannedDate)
    val completed = sets.filter { it.isCompleted }
        .sortedWith(compareBy({ it.setNumber }, { it.timestamp }))
    put("completedSetCount", completed.size)
    put("warmupSetCount", completed.count { it.isWarmup })
    put("dropSetCount", completed.count { it.isDropSet })
    put("mainSetCount", completed.count { !it.isDropSet })
    put("workingSetCount", completed.count { !it.isWarmup && !it.isDropSet })
    put("setsTruncated", completed.size > MAX_SETS_IN_TOOL_RESULT)
    put(
        "sets",
        JSONArray().apply {
            completed.take(MAX_SETS_IN_TOOL_RESULT).forEach { set ->
                put(
                    JSONObject().apply {
                        put("setNumber", set.setNumber)
                        put("setUid", set.setUid)
                        put("isDropSet", set.isDropSet)
                        put("parentSetUid", set.parentSetUid ?: JSONObject.NULL)
                        put("dropIndex", set.dropIndex)
                        put("exerciseId", set.exerciseId)
                        put("exerciseName", set.exerciseName)
                        put("exerciseType", set.exerciseType)
                        put("weightKg", set.weight)
                        put("addedWeightKg", set.addedWeight)
                        put("reps", set.reps)
                        put("holdSeconds", set.holdDurationSeconds)
                        put(
                            "cardioMinutes",
                            if (set.exerciseType == com.example.data.model.ExerciseType.CARDIO) {
                                set.holdDurationSeconds / 60.0
                            } else {
                                0.0
                            }
                        )
                        put("distanceKm", set.distanceKm)
                        put("bodyWeightKg", set.bodyWeightKg)
                        put("isWarmup", set.isWarmup)
                        put("restSecondsOverride", set.restSecondsOverride ?: JSONObject.NULL)
                        put("effortRpe", set.effortRpe ?: JSONObject.NULL)
                        put("supersetGroupId", set.supersetGroupId ?: JSONObject.NULL)
                    }
                )
            }
        }
    )
}

/** Reads optional AI drop-set children, clamped to the exercise's real main sets. */
private fun parseToolDropSets(
    obj: JSONObject,
    mainSetCount: Int,
    fields: List<com.example.data.model.ExerciseField>
): List<RoutineDropSet> {
    val array = obj.optJSONArray("dropSets") ?: return emptyList()
    val result = mutableListOf<RoutineDropSet>()
    fun supports(field: com.example.data.model.ExerciseField): Boolean =
        fields.isEmpty() || field in fields
    for (i in 0 until array.length()) {
        val drop = array.optJSONObject(i) ?: continue
        result += RoutineDropSet(
            mainSetIndex = drop.optInt("mainSet", drop.optInt("mainSetIndex", 1))
                .coerceIn(1, mainSetCount.coerceAtLeast(1)),
            dropIndex = drop.optInt("drop", drop.optInt("dropIndex", i + 1)).coerceAtLeast(1),
            targetReps = if (supports(com.example.data.model.ExerciseField.REPS)) {
                drop.optInt("reps", 0).coerceIn(0, 100)
            } else {
                0
            },
            targetHoldSeconds = if (
                supports(com.example.data.model.ExerciseField.HOLD_SECONDS) ||
                supports(com.example.data.model.ExerciseField.TIME_SECONDS)
            ) {
                drop.optInt("holdSeconds", 0).coerceIn(0, 600)
            } else {
                0
            },
            targetDistanceKm = if (supports(com.example.data.model.ExerciseField.DISTANCE_KM)) {
                drop.optDouble("distanceKm", 0.0).coerceAtLeast(0.0)
            } else {
                0.0
            },
            targetWeight = if (supports(com.example.data.model.ExerciseField.WEIGHT)) {
                drop.optDouble("weightKg", drop.optDouble("weight", 0.0)).coerceAtLeast(0.0)
            } else {
                0.0
            },
            targetAddedWeightKg = if (
                supports(com.example.data.model.ExerciseField.ADDED_WEIGHT)
            ) {
                drop.optDouble(
                    "addedWeightKg",
                    drop.optDouble("addedWeight", 0.0)
                ).coerceAtLeast(0.0)
            } else {
                0.0
            },
            restSeconds = drop.optInt("restSeconds", 0).coerceIn(0, 600)
        )
    }
    return result
        .groupBy { it.mainSetIndex }
        .flatMap { (_, rows) ->
            rows.sortedBy { it.dropIndex }.mapIndexed { index, drop -> drop.copy(dropIndex = index + 1) }
        }
}

/** Structured drop-set output for the read-only routine tools. */
private fun List<RoutineDropSet>.toToolDropSetArray(): JSONArray = JSONArray().apply {
    this@toToolDropSetArray
        .sortedWith(compareBy({ it.mainSetIndex }, { it.dropIndex }))
        .forEach { drop ->
            put(JSONObject().apply {
                put("mainSet", drop.mainSetIndex)
                put("drop", drop.dropIndex)
                put("reps", drop.targetReps)
                put("holdSeconds", drop.targetHoldSeconds)
                put("distanceKm", drop.targetDistanceKm)
                put("weightKg", drop.targetWeight)
                put("addedWeightKg", drop.targetAddedWeightKg)
                put("restSeconds", drop.restSeconds)
            })
        }
}

private fun parseStringArray(raw: String): JSONArray {
    if (raw.isBlank()) return JSONArray()
    return runCatching { JSONArray(raw) }.getOrElse {
        JSONArray().put(raw)
    }
}
