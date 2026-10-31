package com.example.data.ai

import com.example.data.model.Exercise
import com.example.data.model.RoutineParser
import com.example.data.model.SavedRoutine
import com.example.data.repository.FitnessRepository
import org.json.JSONArray
import org.json.JSONObject

/** One bounded page of the exercise catalog. */
data class ExerciseListPage(
    val items: List<Exercise>,
    val total: Int
)

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
}

/** Repository-backed implementation used by the app. */
class RepositoryAiDataAccess(private val repository: FitnessRepository) : AiDataAccess {

    override suspend fun searchExercises(query: String, limit: Int): List<Exercise> {
        val all = repository.getAllExercisesOnce()
        return AiExerciseSearch.rank(all, query).take(limit)
    }

    override suspend fun getExerciseById(id: Long): Exercise? = repository.getExerciseById(id)

    override suspend fun getExerciseByName(name: String): Exercise? = repository.getExerciseByName(name)

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

    override suspend fun getRoutineById(id: Long): SavedRoutine? = repository.getSavedRoutineById(id)

    override suspend fun listRoutines(): List<SavedRoutine> = repository.getAllSavedRoutinesOnce()
}

/**
 * Deterministic, bounded exercise search.
 *
 * The catalog is around 1,300 rows, so ranking in memory is cheaper than a second query surface and
 * keeps the tool contract identical for every provider. The score is intentionally simple and
 * explainable: an exact name beats a prefix, a prefix beats a substring, and a muscle or equipment
 * match is a weaker hit. Every token the model typed must match somewhere, so "bench press" does not
 * return every chest exercise.
 */
object AiExerciseSearch {

    fun rank(exercises: List<Exercise>, rawQuery: String): List<Exercise> {
        val query = rawQuery.trim().lowercase()
        if (query.isBlank()) return emptyList()
        val tokens = query.split(Regex("\\s+")).filter { it.isNotBlank() }
        return exercises.mapNotNull { exercise ->
            val name = exercise.name.lowercase()
            val haystack = listOf(
                exercise.name,
                exercise.category,
                exercise.bodyPart,
                exercise.target,
                exercise.equipment
            ).joinToString(" ").lowercase()
            if (tokens.any { !haystack.contains(it) }) return@mapNotNull null
            val score = when {
                name == query -> 100
                name.startsWith(query) -> 80
                name.contains(query) -> 60
                tokens.all { name.contains(it) } -> 50
                exercise.category.equals(query, ignoreCase = true) -> 40
                exercise.bodyPart.equals(query, ignoreCase = true) -> 38
                exercise.target.equals(query, ignoreCase = true) -> 36
                exercise.equipment.equals(query, ignoreCase = true) -> 34
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
            description = "Read one saved routine and its training days, including sets, reps, holds " +
                "and the per-exercise rest default. Provide routineId or name; with neither, the most " +
                "recent routine is returned.",
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
            """.trimIndent()
        ),
        AiToolDefinition(
            name = LIST_ROUTINES,
            description = "List the athlete's saved routines with their goal, weekly frequency and " +
                "exercise count. Use it before answering questions about their plan.",
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
            """.trimIndent()
        )
    )

    fun definition(name: String): AiToolDefinition? =
        definitions.firstOrNull { it.name == name }
}

/** Maps the canonical names to handlers. No handler exists outside this registry. */
class AiToolRegistry(private val dataAccess: AiDataAccess) {

    fun definitions(): List<AiToolDefinition> = AiToolCatalog.definitions

    internal suspend fun execute(name: String, arguments: JSONObject): JSONObject = when (name) {
        AiToolCatalog.SEARCH_EXERCISES -> searchExercises(arguments)
        AiToolCatalog.GET_EXERCISE_DETAILS -> getExerciseDetails(arguments)
        AiToolCatalog.LIST_EXERCISES -> listExercises(arguments)
        AiToolCatalog.GET_ROUTINE -> getRoutine(arguments)
        AiToolCatalog.LIST_ROUTINES -> listRoutines(arguments)
        else -> toolError("unknown_tool", "The tool \"$name\" is not available.")
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
        val days = RoutineParser.parseDays(routine.exercisesJson, dataAccess.allExercises())
        return JSONObject().apply {
            put("ok", true)
            put("routine", routine.toRoutineJson(days))
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

    fun definitions(): List<AiToolDefinition> = registry.definitions()

    suspend fun dispatch(call: AiToolCall): AiToolResult {
        val definition = AiToolCatalog.definition(call.name)
            ?: return errorResult(
                call,
                "unknown_tool",
                "The tool \"${call.name}\" does not exist. Available tools: " +
                    AiToolCatalog.definitions.joinToString { it.name } + "."
            )

        val arguments = try {
            parseArguments(call.argumentsJson)
        } catch (e: AiToolArgumentException) {
            return errorResult(call, "invalid_arguments", e.message.orEmpty())
        }

        val result = try {
            registry.execute(call.name, arguments)
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

    private fun errorResult(call: AiToolCall, code: String, message: String): AiToolResult =
        AiToolResult(
            callId = call.id,
            name = call.name,
            contentJson = JSONObject().apply {
                put("ok", false)
                put("error", code)
                put("message", message)
            }.toString(),
            isError = true
        )
}

private fun Exercise.toSummaryJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("category", category)
    put("bodyPart", bodyPart)
    put("target", target)
    put("equipment", equipment)
    put("exerciseType", exerciseType)
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
    put("instructions", instructions.take(4000))
    put("instructionsTruncated", instructions.length > 4000)
    put("description", description.take(1000))
    put("isCustom", isCustom)
}

private fun SavedRoutine.toRoutineJson(days: List<com.example.data.model.RoutineDay>): JSONObject =
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
                                                put("name", item.exerciseName)
                                                put("equipment", item.equipment)
                                                put("sets", item.targetSets)
                                                put("reps", item.targetReps)
                                                put("weightKg", item.targetWeight)
                                                put("addedWeightKg", item.targetAddedWeightKg)
                                                put("holdSeconds", item.targetHoldSeconds)
                                                put("restSeconds", item.restSeconds)
                                                put("prescription", item.prescription)
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

private fun parseStringArray(raw: String): JSONArray {
    if (raw.isBlank()) return JSONArray()
    return runCatching { JSONArray(raw) }.getOrElse {
        JSONArray().put(raw)
    }
}
