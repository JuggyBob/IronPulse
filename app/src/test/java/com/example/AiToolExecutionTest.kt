package com.example

import com.example.data.ai.AiConversationEngine
import com.example.data.ai.AiDataAccess
import com.example.data.ai.AiErrorKind
import com.example.data.ai.AiException
import com.example.data.ai.AiExerciseSearch
import com.example.data.ai.AiMessage
import com.example.data.ai.AiProvider
import com.example.data.ai.AiProviderConfiguration
import com.example.data.ai.AiProviderId
import com.example.data.ai.AiRequest
import com.example.data.ai.AiResponse
import com.example.data.ai.AiRole
import com.example.data.ai.AiToolCall
import com.example.data.ai.AiToolCatalog
import com.example.data.ai.AiToolDispatcher
import com.example.data.ai.AiToolMode
import com.example.data.ai.AiToolRegistry
import com.example.data.ai.ExerciseListPage
import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import com.example.data.model.RoutineExerciseItem
import com.example.data.model.RoutineParser
import com.example.data.model.SavedRoutine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The canonical tool layer: argument validation, repository-backed reads and the bounded loop.
 *
 * These tests use a fake [AiDataAccess] and a scripted [AiProvider]. They prove IronPulse's side of
 * the contract (what it offers, validates, executes and sends back) without claiming that a live
 * provider accepts it. No test here touches the network.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiToolExecutionTest {

    private val exercises = listOf(
        Exercise(
            id = 1,
            name = "Barbell Bench Press",
            category = "Chest",
            bodyPart = "chest",
            target = "pectorals",
            exerciseType = ExerciseType.STANDARD_WEIGHT,
            equipment = "Barbell",
            instructions = "Lie on a flat bench and press the bar."
        ),
        Exercise(
            id = 2,
            name = "Dumbbell Bench Press",
            category = "Chest",
            bodyPart = "chest",
            target = "pectorals",
            exerciseType = ExerciseType.STANDARD_WEIGHT,
            equipment = "Dumbbells"
        ),
        Exercise(
            id = 3,
            name = "Pull-up",
            category = "Back",
            bodyPart = "back",
            target = "lats",
            exerciseType = ExerciseType.PURE_BODYWEIGHT,
            equipment = "Pull-up Bar"
        ),
        Exercise(
            id = 4,
            name = "Plank",
            category = "Core",
            bodyPart = "waist",
            target = "abs",
            exerciseType = ExerciseType.PURE_BODYWEIGHT,
            equipment = "Bodyweight"
        )
    )

    private val pushRoutine = SavedRoutine(
        id = 10,
        name = "Push Day",
        description = "Chest and triceps",
        daysPerWeek = 2,
        goal = "Strength",
        exercisesJson = RoutineParser.serializeExercises(
            listOf(
                RoutineExerciseItem(
                    exerciseId = 1,
                    exerciseName = "Barbell Bench Press",
                    exerciseType = ExerciseType.STANDARD_WEIGHT,
                    category = "Chest",
                    equipment = "Barbell",
                    targetSets = 4,
                    targetReps = 6,
                    restSeconds = 150,
                    weekday = 1,
                    dayTitle = "Push"
                ),
                RoutineExerciseItem(
                    exerciseId = 3,
                    exerciseName = "Pull-up",
                    exerciseType = ExerciseType.PURE_BODYWEIGHT,
                    category = "Back",
                    equipment = "Pull-up Bar",
                    targetSets = 3,
                    targetReps = 8,
                    restSeconds = 90,
                    weekday = 3
                )
            )
        ),
        createdAt = 2_000
    )

    private val pullRoutine = SavedRoutine(
        id = 11,
        name = "Pull Day",
        description = "",
        daysPerWeek = 1,
        goal = "Hypertrophy",
        exercisesJson = RoutineParser.serializeExercises(
            listOf(
                RoutineExerciseItem(
                    exerciseId = 3,
                    exerciseName = "Pull-up",
                    exerciseType = ExerciseType.PURE_BODYWEIGHT,
                    category = "Back",
                    equipment = "Pull-up Bar",
                    targetSets = 5,
                    targetReps = 5,
                    weekday = 2
                )
            )
        ),
        createdAt = 1_000
    )

    private class FakeDataAccess(
        private val exercises: List<Exercise>,
        private val routines: List<SavedRoutine> = emptyList()
    ) : AiDataAccess {
        override suspend fun searchExercises(query: String, limit: Int): List<Exercise> =
            AiExerciseSearch.rank(exercises, query).take(limit)

        override suspend fun getExerciseById(id: Long): Exercise? =
            exercises.firstOrNull { it.id == id }

        override suspend fun getExerciseByName(name: String): Exercise? =
            exercises.firstOrNull { it.name.equals(name, ignoreCase = true) }

        override suspend fun listExercises(
            category: String?,
            equipment: String?,
            limit: Int,
            offset: Int
        ): ExerciseListPage {
            val filtered = exercises.filter { exercise ->
                (category == null || exercise.category.equals(category, ignoreCase = true)) &&
                    (equipment == null || exercise.equipment.contains(equipment, ignoreCase = true))
            }.sortedBy { it.name.lowercase() }
            return ExerciseListPage(filtered.drop(offset).take(limit), filtered.size)
        }

        override suspend fun allExercises(): List<Exercise> = exercises

        override suspend fun getRoutineById(id: Long): SavedRoutine? =
            routines.firstOrNull { it.id == id }

        override suspend fun listRoutines(): List<SavedRoutine> = routines
    }

    private fun dispatcher(
        dataAccess: AiDataAccess = FakeDataAccess(exercises, listOf(pushRoutine, pullRoutine))
    ) = AiToolDispatcher(AiToolRegistry(dataAccess))

    private fun call(name: String, arguments: String = "{}", id: String = "call_1") =
        AiToolCall(id = id, name = name, argumentsJson = arguments)

    private fun body(result: com.example.data.ai.AiToolResult): JSONObject = JSONObject(result.contentJson)

    // ------------------------------------------------------------ dispatcher

    @Test
    fun `an unknown tool is a structured error instead of an exception`() = runBlocking {
        val result = dispatcher().dispatch(call("drop_database"))
        assertTrue(result.isError)
        assertEquals("unknown_tool", body(result).getString("error"))
        assertTrue(body(result).getString("message").contains("search_exercises"))
    }

    @Test
    fun `malformed JSON arguments are rejected before the handler runs`() = runBlocking {
        val result = dispatcher().dispatch(call(AiToolCatalog.SEARCH_EXERCISES, "{not json"))
        assertTrue(result.isError)
        assertEquals("invalid_arguments", body(result).getString("error"))
    }

    @Test
    fun `a missing required argument is rejected with a readable message`() = runBlocking {
        val result = dispatcher().dispatch(call(AiToolCatalog.SEARCH_EXERCISES, "{}"))
        assertTrue(result.isError)
        assertEquals("invalid_arguments", body(result).getString("error"))
        assertTrue(body(result).getString("message").contains("query"))
    }

    @Test
    fun `numeric strings are accepted but fractional ids are not`() = runBlocking {
        val byString = dispatcher().dispatch(
            call(AiToolCatalog.GET_EXERCISE_DETAILS, """{"exerciseId":"3"}""")
        )
        assertFalse(byString.isError)
        assertEquals("Pull-up", body(byString).getJSONObject("exercise").getString("name"))

        val fractional = dispatcher().dispatch(
            call(AiToolCatalog.GET_EXERCISE_DETAILS, """{"exerciseId":1.5}""")
        )
        assertTrue(fractional.isError)
        assertEquals("invalid_arguments", body(fractional).getString("error"))
    }

    @Test
    fun `search ranks a name match first and respects the limit`() = runBlocking {
        val result = dispatcher().dispatch(
            call(AiToolCatalog.SEARCH_EXERCISES, """{"query":"bench press","limit":1}""")
        )
        assertFalse(result.isError)
        val json = body(result)
        assertEquals(1, json.getInt("count"))
        assertEquals("Barbell Bench Press", json.getJSONArray("exercises").getJSONObject(0).getString("name"))
    }

    @Test
    fun `search requires every typed token so a muscle alone does not return the whole catalog`() = runBlocking {
        val result = dispatcher().dispatch(
            call(AiToolCatalog.SEARCH_EXERCISES, """{"query":"bench pull-up","limit":20}""")
        )
        assertEquals(0, body(result).getInt("count"))
        assertTrue(body(result).getString("note").isNotBlank())
    }

    @Test
    fun `exercise details can be read by id and by exact name`() = runBlocking {
        val byId = dispatcher().dispatch(
            call(AiToolCatalog.GET_EXERCISE_DETAILS, """{"exerciseId":1}""")
        )
        assertEquals("Barbell Bench Press", body(byId).getJSONObject("exercise").getString("name"))
        assertTrue(body(byId).getJSONObject("exercise").getString("instructions").isNotBlank())

        val byName = dispatcher().dispatch(
            call(AiToolCatalog.GET_EXERCISE_DETAILS, """{"name":"plank"}""")
        )
        assertEquals(4L, body(byName).getJSONObject("exercise").getLong("id"))
    }

    @Test
    fun `a missing exercise is a not_found result the model can recover from`() = runBlocking {
        val result = dispatcher().dispatch(
            call(AiToolCatalog.GET_EXERCISE_DETAILS, """{"name":"Definitely Not An Exercise"}""")
        )
        assertTrue(result.isError)
        assertEquals("not_found", body(result).getString("error"))
    }

    @Test
    fun `list exercises filters, paginates and reports hasMore`() = runBlocking {
        val page = dispatcher().dispatch(
            call(AiToolCatalog.LIST_EXERCISES, """{"category":"Chest","limit":1,"offset":0}""")
        )
        val json = body(page)
        assertEquals(2, json.getInt("total"))
        assertEquals(1, json.getInt("count"))
        assertTrue(json.getBoolean("hasMore"))
        assertEquals("Barbell Bench Press", json.getJSONArray("exercises").getJSONObject(0).getString("name"))

        val second = body(
            dispatcher().dispatch(
                call(AiToolCatalog.LIST_EXERCISES, """{"category":"Chest","limit":1,"offset":1}""")
            )
        )
        assertFalse(second.getBoolean("hasMore"))
        assertEquals("Dumbbell Bench Press", second.getJSONArray("exercises").getJSONObject(0).getString("name"))
    }

    @Test
    fun `routine lookup returns the per-entry rest defaults`() = runBlocking {
        val result = dispatcher().dispatch(
            call(AiToolCatalog.GET_ROUTINE, """{"name":"push day"}""")
        )
        assertFalse(result.isError)
        val routine = body(result).getJSONObject("routine")
        assertEquals("Push Day", routine.getString("name"))
        val days = routine.getJSONArray("days")
        assertEquals(2, days.length())

        val monday = days.getJSONObject(0).getJSONArray("exercises").getJSONObject(0)
        assertEquals("Barbell Bench Press", monday.getString("name"))
        assertEquals(150, monday.getInt("restSeconds"))

        val wednesday = days.getJSONObject(1).getJSONArray("exercises").getJSONObject(0)
        assertEquals(90, wednesday.getInt("restSeconds"))
    }

    @Test
    fun `routine lookup without a selector returns the most recent routine`() = runBlocking {
        val result = dispatcher().dispatch(call(AiToolCatalog.GET_ROUTINE, "{}"))
        val json = body(result)
        assertEquals(10L, json.getJSONObject("routine").getLong("id"))
        assertTrue(json.getBoolean("selectedMostRecent"))
    }

    @Test
    fun `routine lookup with no saved routines is a readable error`() = runBlocking {
        val result = dispatcher(FakeDataAccess(exercises)).dispatch(call(AiToolCatalog.GET_ROUTINE, "{}"))
        assertTrue(result.isError)
        assertEquals("no_routines", body(result).getString("error"))
    }

    @Test
    fun `list routines reports counts without dumping the whole plan`() = runBlocking {
        val result = dispatcher().dispatch(call(AiToolCatalog.LIST_ROUTINES, """{"limit":1}"""))
        val json = body(result)
        assertEquals(2, json.getInt("total"))
        assertEquals(1, json.getInt("count"))
        val routine = json.getJSONArray("routines").getJSONObject(0)
        assertEquals("Push Day", routine.getString("name"))
        assertEquals(2, routine.getInt("exerciseCount"))
    }

    @Test
    fun `a tool result over the byte cap is refused rather than flooding the model`() = runBlocking {
        val many = (1..50).map { index ->
            Exercise(
                id = index.toLong(),
                name = "Exercise " + "x".repeat(260) + " $index",
                category = "Chest",
                exerciseType = ExerciseType.STANDARD_WEIGHT,
                equipment = "Barbell"
            )
        }
        val result = dispatcher(FakeDataAccess(many)).dispatch(
            call(AiToolCatalog.LIST_EXERCISES, """{"limit":50}""")
        )
        assertTrue(result.isError)
        assertEquals("result_too_large", body(result).getString("error"))
    }

    @Test
    fun `cancellation from a handler is propagated, not turned into a tool error`() = runBlocking {
        val cancelling = object : AiDataAccess by FakeDataAccess(exercises) {
            override suspend fun searchExercises(query: String, limit: Int): List<Exercise> {
                throw CancellationException("the screen left")
            }
        }
        assertThrows(CancellationException::class.java) {
            runBlocking {
                dispatcher(cancelling).dispatch(
                    call(AiToolCatalog.SEARCH_EXERCISES, """{"query":"bench"}""")
                )
            }
        }
        Unit
    }

    // --------------------------------------------------------------- engine

    private class ScriptedProvider(
        override val id: AiProviderId = AiProviderId.OPENAI,
        private val handler: suspend (AiRequest, Int) -> Result<AiResponse>
    ) : AiProvider {
        val requests = mutableListOf<AiRequest>()
        var callCount = 0

        override fun resolveBaseUrl(config: AiProviderConfiguration): Result<String> =
            Result.success("https://example.test/v1")

        override suspend fun listModels(config: AiProviderConfiguration): Result<List<com.example.data.ai.AiModelDescriptor>> =
            Result.success(emptyList())

        override suspend fun testConnection(config: AiProviderConfiguration): Result<Unit> =
            Result.success(Unit)

        override suspend fun generate(request: AiRequest): Result<AiResponse> {
            requests.add(request)
            callCount++
            return handler(request, callCount)
        }
    }

    private fun configuration(toolMode: AiToolMode = AiToolMode.AUTO) = AiProviderConfiguration(
        provider = AiProviderId.OPENAI,
        apiKey = "test-key",
        model = "test-model",
        toolMode = toolMode
    )

    private fun engine(
        provider: AiProvider,
        maxRounds: Int = 4,
        maxToolCalls: Int = 12,
        requestTimeoutMillis: Long = 5_000,
        maxRetries: Int = 1
    ) = AiConversationEngine(
        dispatcher = dispatcher(),
        providerResolver = { provider },
        maxRounds = maxRounds,
        maxToolCalls = maxToolCalls,
        requestTimeoutMillis = requestTimeoutMillis,
        maxRetries = maxRetries
    )

    private fun nativeCall(name: String, arguments: String, id: String = "call_1") =
        AiResponse(text = "", toolCalls = listOf(call(name, arguments, id)))

    @Test
    fun `a native tool call is executed and its result is sent back before the final answer`() = runBlocking {
        val provider = ScriptedProvider { _, callNumber ->
            if (callNumber == 1) {
                Result.success(nativeCall(AiToolCatalog.SEARCH_EXERCISES, """{"query":"bench"}"""))
            } else {
                Result.success(AiResponse(text = "I found two bench presses."))
            }
        }

        val result = engine(provider).run(
            configuration = configuration(),
            systemInstruction = "coach",
            messages = listOf(AiMessage(AiRole.USER, "What bench press do I have?"))
        )

        assertEquals("I found two bench presses.", result.text)
        assertEquals(2, result.rounds)
        assertEquals(1, result.toolCallsExecuted)
        assertFalse(result.usedCompatibilityFallback)
        assertEquals(2, provider.requests.size)
        assertTrue(provider.requests[0].tools.isNotEmpty())
        assertTrue(provider.requests[1].tools.isNotEmpty())

        val followUp = provider.requests[1].messages
        assertEquals(AiRole.TOOL, followUp.last().role)
        assertEquals("call_1", followUp.last().toolResults.single().callId)
        assertTrue(followUp.last().toolResults.single().contentJson.contains("Barbell Bench Press"))
    }

    @Test
    fun `identical calls in one answer are executed once and answered from the cache`() = runBlocking {
        val provider = ScriptedProvider { _, callNumber ->
            if (callNumber == 1) {
                Result.success(
                    AiResponse(
                        text = "",
                        toolCalls = listOf(
                            call(AiToolCatalog.LIST_ROUTINES, """{"limit":5}""", id = "call_a"),
                            call(AiToolCatalog.LIST_ROUTINES, """{"limit":5}""", id = "call_b")
                        )
                    )
                )
            } else {
                Result.success(AiResponse(text = "Two routines."))
            }
        }

        val result = engine(provider).run(
            configuration = configuration(),
            systemInstruction = "coach",
            messages = listOf(AiMessage(AiRole.USER, "How many routines?"))
        )

        assertEquals(1, result.toolCallsExecuted)
        val results = provider.requests[1].messages.last().toolResults
        assertEquals(listOf("call_a", "call_b"), results.map { it.callId })
        assertTrue(results.all { it.contentJson.contains("\"total\":2") })
    }

    @Test
    fun `compatibility mode reads a text tool envelope and never shows it to the athlete`() = runBlocking {
        val provider = ScriptedProvider { _, callNumber ->
            if (callNumber == 1) {
                Result.success(
                    AiResponse(
                        text = """{"tool_calls":[{"name":"list_routines","arguments":{"limit":5}}]}"""
                    )
                )
            } else {
                Result.success(AiResponse(text = "You have two routines."))
            }
        }

        val result = engine(provider).run(
            configuration = configuration(AiToolMode.COMPATIBILITY),
            systemInstruction = "coach",
            messages = listOf(AiMessage(AiRole.USER, "List my routines"))
        )

        assertEquals("You have two routines.", result.text)
        assertTrue(result.usedCompatibilityFallback)
        assertEquals(1, result.toolCallsExecuted)
        assertTrue(provider.requests[0].tools.isEmpty())
        assertTrue(provider.requests[0].systemInstruction.contains("IRONPULSE TOOL PROTOCOL"))
    }

    @Test
    fun `AUTO falls back to the text protocol when the provider rejects tools`() = runBlocking {
        val provider = ScriptedProvider { _, callNumber ->
            when (callNumber) {
                1 -> Result.failure(
                    AiException(AiErrorKind.INVALID_REQUEST, "tools are not supported by this model")
                )
                2 -> Result.success(
                    AiResponse(
                        text = """{"tool_calls":[{"name":"list_routines","arguments":{"limit":5}}]}"""
                    )
                )
                else -> Result.success(AiResponse(text = "You have two routines."))
            }
        }

        val result = engine(provider).run(
            configuration = configuration(AiToolMode.AUTO),
            systemInstruction = "coach",
            messages = listOf(AiMessage(AiRole.USER, "List my routines"))
        )

        assertEquals("You have two routines.", result.text)
        assertTrue(result.usedCompatibilityFallback)
        assertEquals(3, provider.callCount)
        assertTrue(provider.requests[0].tools.isNotEmpty())
        assertTrue(provider.requests[1].tools.isEmpty())
        assertTrue(provider.requests[1].systemInstruction.contains("IRONPULSE TOOL PROTOCOL"))
    }

    @Test
    fun `NATIVE mode reports a provider that rejects tools instead of silently falling back`() = runBlocking {
        val provider = ScriptedProvider {
                _, _ -> Result.failure(
                AiException(AiErrorKind.INVALID_REQUEST, "tools are not supported by this model")
            )
        }

        val error = assertThrows(AiException::class.java) {
            runBlocking {
                engine(provider).run(
                    configuration = configuration(AiToolMode.NATIVE),
                    systemInstruction = "coach",
                    messages = listOf(AiMessage(AiRole.USER, "hello"))
                )
            }
        }
        assertEquals(AiErrorKind.INVALID_REQUEST, error.kind)
        assertEquals(1, provider.callCount)
    }

    @Test
    fun `the total call budget stops a runaway model and asks once more without tools`() = runBlocking {
        val provider = ScriptedProvider { _, callNumber ->
            when (callNumber) {
                1 -> Result.success(nativeCall(AiToolCatalog.SEARCH_EXERCISES, """{"query":"bench"}"""))
                2 -> Result.success(nativeCall(AiToolCatalog.LIST_ROUTINES, """{"limit":5}"""))
                else -> Result.success(AiResponse(text = "Done with what I have."))
            }
        }

        val result = engine(provider, maxRounds = 2, maxToolCalls = 1).run(
            configuration = configuration(),
            systemInstruction = "coach",
            messages = listOf(AiMessage(AiRole.USER, "keep looking"))
        )

        assertEquals("Done with what I have.", result.text)
        assertEquals(1, result.toolCallsExecuted)
        assertEquals(3, provider.requests.size)
        assertTrue("the forced final request must not offer tools", provider.requests.last().tools.isEmpty())
        assertTrue(
            "the final request must tell the model to answer without tools",
            provider.requests.last().systemInstruction.contains("tool limit")
        )
    }

    @Test
    fun `the round limit forces a tool-free final answer`() = runBlocking {
        val provider = ScriptedProvider { _, callNumber ->
            if (callNumber == 1) {
                Result.success(nativeCall(AiToolCatalog.SEARCH_EXERCISES, """{"query":"bench"}"""))
            } else {
                Result.success(AiResponse(text = "Final answer."))
            }
        }

        val result = engine(provider, maxRounds = 1).run(
            configuration = configuration(),
            systemInstruction = "coach",
            messages = listOf(AiMessage(AiRole.USER, "go"))
        )

        assertEquals("Final answer.", result.text)
        assertTrue(provider.requests.last().tools.isEmpty())
        assertEquals(2, provider.requests.size)
    }

    @Test
    fun `malformed native arguments are returned to the model and the loop continues`() = runBlocking {
        val provider = ScriptedProvider { _, callNumber ->
            if (callNumber == 1) {
                Result.success(nativeCall(AiToolCatalog.SEARCH_EXERCISES, "{not json"))
            } else {
                Result.success(AiResponse(text = "I could not search, sorry."))
            }
        }

        val result = engine(provider).run(
            configuration = configuration(),
            systemInstruction = "coach",
            messages = listOf(AiMessage(AiRole.USER, "search"))
        )

        assertEquals("I could not search, sorry.", result.text)
        val toolResult = provider.requests[1].messages.last().toolResults.single()
        assertTrue(toolResult.isError)
        assertTrue(toolResult.contentJson.contains("invalid_arguments"))
    }

    @Test
    fun `retryable provider errors are retried once before failing`() = runBlocking {
        val provider = ScriptedProvider { _, callNumber ->
            if (callNumber == 1) {
                Result.failure(AiException(AiErrorKind.NETWORK, "offline"))
            } else {
                Result.success(AiResponse(text = "Back online."))
            }
        }

        val result = engine(provider, maxRetries = 1).run(
            configuration = configuration(),
            systemInstruction = "coach",
            messages = listOf(AiMessage(AiRole.USER, "hello"))
        )

        assertEquals("Back online.", result.text)
        assertEquals(2, provider.callCount)
    }

    @Test
    fun `a provider timeout becomes a normalized timeout error`() = runBlocking {
        val provider = ScriptedProvider { _, _ ->
            delay(1_000)
            Result.success(AiResponse(text = "too late"))
        }

        val error = assertThrows(AiException::class.java) {
            runBlocking {
                engine(provider, requestTimeoutMillis = 30, maxRetries = 0).run(
                    configuration = configuration(),
                    systemInstruction = "coach",
                    messages = listOf(AiMessage(AiRole.USER, "hello"))
                )
            }
        }
        assertEquals(AiErrorKind.TIMEOUT, error.kind)
    }

    @Test
    fun `outer cancellation is never turned into a retry or a visible error`() = runBlocking {
        val provider = ScriptedProvider { _, _ ->
            throw CancellationException("the athlete left the screen")
        }

        assertThrows(CancellationException::class.java) {
            runBlocking {
                engine(provider, maxRetries = 3).run(
                    configuration = configuration(),
                    systemInstruction = "coach",
                    messages = listOf(AiMessage(AiRole.USER, "hello"))
                )
            }
        }
        assertEquals(1, provider.callCount)
    }

    @Test
    fun `an empty final answer is a parse error rather than a blank chat bubble`() = runBlocking {
        val provider = ScriptedProvider { _, _ -> Result.success(AiResponse(text = "   ")) }

        val error = assertThrows(AiException::class.java) {
            runBlocking {
                engine(provider).run(
                    configuration = configuration(),
                    systemInstruction = "coach",
                    messages = listOf(AiMessage(AiRole.USER, "hello"))
                )
            }
        }
        assertEquals(AiErrorKind.PARSE, error.kind)
    }

    @Test
    fun `tools are not offered when the caller disables them`() = runBlocking {
        val provider = ScriptedProvider { _, _ -> Result.success(AiResponse(text = "Plain answer.")) }

        val result = engine(provider).run(
            configuration = configuration(),
            systemInstruction = "coach",
            messages = listOf(AiMessage(AiRole.USER, "hello")),
            enableTools = false
        )

        assertEquals("Plain answer.", result.text)
        assertTrue(provider.requests.single().tools.isEmpty())
        assertEquals(0, result.toolCallsExecuted)
    }
}
