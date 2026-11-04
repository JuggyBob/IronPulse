/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * New standalone code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import com.example.data.ai.AiAccessPolicy
import com.example.data.ai.AiConversationEngine
import com.example.data.ai.AiDataAccess
import com.example.data.ai.AiExerciseSearch
import com.example.data.ai.AiMessage
import com.example.data.ai.AiProvider
import com.example.data.ai.AiProviderConfiguration
import com.example.data.ai.AiProviderId
import com.example.data.ai.AiRequest
import com.example.data.ai.AiResponse
import com.example.data.ai.AiRole
import com.example.data.ai.AiRoutineSchedule
import com.example.data.ai.AiToolCall
import com.example.data.ai.AiToolCatalog
import com.example.data.ai.AiToolDispatcher
import com.example.data.ai.AiToolMode
import com.example.data.ai.AiToolRegistry
import com.example.data.ai.AiWorkoutSearchPage
import com.example.data.ai.ExerciseListPage
import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import com.example.data.model.LoggedSet
import com.example.data.model.RoutineExerciseItem
import com.example.data.model.RoutineParser
import com.example.data.model.SavedRoutine
import com.example.data.model.SessionWithSets
import com.example.data.model.WorkoutSession
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The database-aware coach surface.
 *
 * These tests prove the security boundary and the retrieval contract without a network: a disabled
 * permission exposes no tools and rejects direct dispatch, while an enabled permission returns only
 * bounded, real records and a proposal never mutates anything.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiWorkoutQueriesTest {

    private val pushUp = Exercise(
        id = 1,
        name = "Push-up",
        category = "Chest",
        exerciseType = ExerciseType.PURE_BODYWEIGHT,
        equipment = "Bodyweight"
    )
    private val pullUp = Exercise(
        id = 2,
        name = "Pull-up",
        category = "Back",
        exerciseType = ExerciseType.PURE_BODYWEIGHT,
        equipment = "Pull-up Bar"
    )
    private val dip = Exercise(
        id = 3,
        name = "Dip",
        category = "Chest",
        exerciseType = ExerciseType.PURE_BODYWEIGHT,
        equipment = "Parallel Bars"
    )
    private val allExercises = listOf(pushUp, pullUp, dip)

    private val now = System.currentTimeMillis()
    private val recentSession = SessionWithSets(
        session = WorkoutSession(
            id = 10,
            title = "Push Day",
            startTime = now - 2L * 86_400_000L,
            endTime = now - 2L * 86_400_000L + 3_600_000L,
            routineId = 20,
            routineWeekday = 1
        ),
        sets = listOf(
            LoggedSet(
                id = 1,
                sessionId = 10,
                exerciseId = 1,
                exerciseName = "Push-up",
                exerciseType = ExerciseType.PURE_BODYWEIGHT,
                setNumber = 1,
                reps = 12,
                isCompleted = true
            ),
            LoggedSet(
                id = 2,
                sessionId = 10,
                exerciseId = 1,
                exerciseName = "Push-up",
                exerciseType = ExerciseType.PURE_BODYWEIGHT,
                setNumber = 2,
                reps = 10,
                isCompleted = true
            ),
            LoggedSet(
                id = 3,
                sessionId = 10,
                exerciseId = 1,
                exerciseName = "Push-up (Wide)",
                exerciseType = ExerciseType.PURE_BODYWEIGHT,
                setNumber = 1,
                reps = 30,
                isCompleted = true
            )
        )
    )

    private val routine = SavedRoutine(
        id = 20,
        name = "Weekly Plan",
        description = "",
        goal = "Strength",
        exercisesJson = RoutineParser.serializeExercises(
            listOf(
                RoutineExerciseItem(
                    exerciseId = 1,
                    exerciseName = "Push-up",
                    exerciseType = ExerciseType.PURE_BODYWEIGHT,
                    category = "Chest",
                    equipment = "Bodyweight",
                    targetSets = 3,
                    targetReps = 12,
                    weekday = 1,
                    dayTitle = "Push Day"
                ),
                RoutineExerciseItem(
                    exerciseId = 2,
                    exerciseName = "Pull-up",
                    exerciseType = ExerciseType.PURE_BODYWEIGHT,
                    category = "Back",
                    equipment = "Pull-up Bar",
                    targetSets = 4,
                    targetReps = 6,
                    weekday = 3,
                    dayTitle = "Pull Day"
                )
            )
        )
    )

    private inner class FakeDataAccess : AiDataAccess {
        override suspend fun searchExercises(query: String, limit: Int): List<Exercise> =
            AiExerciseSearch.rank(allExercises, query).take(limit)

        override suspend fun getExerciseById(id: Long): Exercise? =
            allExercises.firstOrNull { it.id == id }

        override suspend fun getExerciseByName(name: String): Exercise? =
            allExercises.firstOrNull { it.name.equals(name, ignoreCase = true) }

        override suspend fun listExercises(
            category: String?,
            equipment: String?,
            limit: Int,
            offset: Int
        ): ExerciseListPage {
            val filtered = allExercises.filter { exercise ->
                (category == null || exercise.category.equals(category, ignoreCase = true)) &&
                    (equipment == null || exercise.equipment.contains(equipment, ignoreCase = true))
            }.sortedBy { it.name }
            return ExerciseListPage(filtered.drop(offset).take(limit), filtered.size)
        }

        override suspend fun allExercises(): List<Exercise> = allExercises

        override suspend fun getRoutineById(id: Long): SavedRoutine? =
            routine.takeIf { it.id == id }

        override suspend fun listRoutines(): List<SavedRoutine> = listOf(routine)

        override suspend fun recentWorkouts(
            fromMillis: Long?,
            toMillis: Long?,
            exerciseName: String?,
            limit: Int,
            offset: Int
        ): AiWorkoutSearchPage {
            val query = exerciseName?.trim()?.lowercase()
            val matching = listOf(recentSession)
                .filter { it.session.startTime >= (fromMillis ?: Long.MIN_VALUE) }
                .filter { it.session.startTime <= (toMillis ?: Long.MAX_VALUE) }
                .filter { wrapper ->
                    query.isNullOrBlank() || wrapper.sets.any {
                        it.isCompleted && it.exerciseName.lowercase().contains(query)
                    }
                }
                .sortedByDescending { it.session.startTime }
            return AiWorkoutSearchPage(matching.drop(offset).take(limit), matching.size)
        }

        override suspend fun exerciseHistory(
            exerciseName: String,
            fromMillis: Long?,
            toMillis: Long?,
            limit: Int
        ): List<SessionWithSets> {
            val query = exerciseName.trim().lowercase()
            return listOf(recentSession).mapNotNull { wrapper ->
                val matching = wrapper.sets.filter {
                    it.isCompleted && it.exerciseName.trim().lowercase() == query
                }
                if (matching.isEmpty()) null else wrapper.copy(sets = matching)
            }.take(limit)
        }

        override suspend fun currentRoutineSchedule(): AiRoutineSchedule = AiRoutineSchedule(
            routineId = routine.id,
            routineName = routine.name,
            days = RoutineParser.parseWeek(routine.exercisesJson, allExercises)
        )
    }

    private fun dispatcher() = AiToolDispatcher(AiToolRegistry(FakeDataAccess()))

    private fun call(name: String, arguments: String = "{}") =
        AiToolCall(id = "call_1", name = name, argumentsJson = arguments)

    private fun body(result: com.example.data.ai.AiToolResult): JSONObject =
        JSONObject(result.contentJson)

    @Test
    fun `disabled permission exposes no tools and rejects a direct retrieval`() = runBlocking {
        val dispatcher = dispatcher()
        assertTrue(dispatcher.definitions(AiAccessPolicy.disabled).isEmpty())

        val result = dispatcher.dispatch(
            call(AiToolCatalog.SEARCH_RECENT_WORKOUTS, """{"daysBack":30}"""),
            AiAccessPolicy.disabled
        )
        assertTrue(result.isError)
        assertEquals("permission_denied", body(result).getString("error"))
    }

    @Test
    fun `enabled permission exposes the four retrieval tools and the scoped proposal`() {
        val names = dispatcher().definitions(AiAccessPolicy.all).map { it.name }.toSet()
        assertTrue(names.contains(AiToolCatalog.SEARCH_RECENT_WORKOUTS))
        assertTrue(names.contains(AiToolCatalog.GET_CURRENT_ROUTINE))
        assertTrue(names.contains(AiToolCatalog.SEARCH_EXERCISE_HISTORY))
        assertTrue(names.contains(AiToolCatalog.SEARCH_EXERCISES))
        assertTrue(names.contains(AiToolCatalog.PROPOSE_ROUTINE_MODIFICATION))
    }

    @Test
    fun `recent workouts return completed performance with dates and units`() = runBlocking {
        val result = dispatcher().dispatch(
            call(AiToolCatalog.SEARCH_RECENT_WORKOUTS, """{"daysBack":365,"limit":5}"""),
            AiAccessPolicy.all
        )
        assertFalse(result.isError)
        val json = body(result)
        assertEquals(1, json.getInt("total"))
        val session = json.getJSONArray("sessions").getJSONObject(0)
        assertEquals(10L, session.getLong("sessionId"))
        assertTrue(session.getString("date").matches(Regex("""\d{4}-\d{2}-\d{2}""")))
        val sets = session.getJSONArray("sets")
        assertEquals(3, sets.length())
        assertEquals(12, sets.getJSONObject(0).getInt("reps"))
        assertEquals("Push-up", sets.getJSONObject(0).getString("exerciseName"))
    }

    @Test
    fun `exercise history matches the exact name and never merges a variant`() = runBlocking {
        val result = dispatcher().dispatch(
            call(
                AiToolCatalog.SEARCH_EXERCISE_HISTORY,
                """{"exerciseName":"Push-up","daysBack":365}"""
            ),
            AiAccessPolicy.all
        )
        assertFalse(result.isError)
        val json = body(result)
        assertEquals(1, json.getInt("count"))
        val sets = json.getJSONArray("sessions").getJSONObject(0).getJSONArray("sets")
        assertEquals(2, sets.length())
        assertEquals("Push-up", sets.getJSONObject(0).getString("exerciseName"))
        assertEquals("Push-up", sets.getJSONObject(1).getString("exerciseName"))
    }

    @Test
    fun `current routine includes rest days and exercise order`() = runBlocking {
        val result = dispatcher().dispatch(
            call(AiToolCatalog.GET_CURRENT_ROUTINE),
            AiAccessPolicy.all
        )
        assertFalse(result.isError)
        val days = body(result).getJSONArray("days")
        assertEquals(7, days.length())
        val monday = days.getJSONObject(0)
        assertEquals("Monday", monday.getString("day"))
        assertEquals("Push Day", monday.getString("title"))
        assertFalse(monday.getBoolean("restDay"))
        assertEquals("Push-up", monday.getJSONArray("exercises").getJSONObject(0).getString("name"))
        assertTrue(days.getJSONObject(1).getBoolean("restDay"))
    }

    @Test
    fun `a proposal is returned for approval without applying it`() = runBlocking {
        val result = dispatcher().dispatch(
            call(
                AiToolCatalog.PROPOSE_ROUTINE_MODIFICATION,
                """{
                  "weekday":1,
                  "removeExerciseName":"Push-up",
                  "replacementExerciseName":"Dip",
                  "reason":"Shoulder-friendly replacement"
                }"""
            ),
            AiAccessPolicy.all
        )
        assertFalse(result.isError)
        val proposal = body(result).getJSONObject("proposal")
        assertEquals(20L, proposal.getLong("routineId"))
        assertEquals("Push-up", proposal.getString("removeExerciseName"))
        assertEquals("Dip", proposal.getString("replacementExerciseName"))
        assertEquals(3L, proposal.getLong("replacementExerciseId"))
        assertEquals(1, proposal.getInt("weekday"))
        assertEquals(routine, FakeDataAccess().getRoutineById(20))
    }

    @Test
    fun `a write proposal requires the separate routine-write permission`() = runBlocking {
        val result = dispatcher().dispatch(
            call(
                AiToolCatalog.PROPOSE_ROUTINE_MODIFICATION,
                """{
                  "weekday":1,
                  "removeExerciseName":"Push-up",
                  "replacementExerciseName":"Dip"
                }"""
            ),
            AiAccessPolicy(personalData = true, routineModification = false)
        )
        assertTrue(result.isError)
        assertEquals("permission_denied", body(result).getString("error"))
    }

    @Test
    fun `revoking permission during a tool loop blocks the pending retrieval`() = runBlocking {
        var access = AiAccessPolicy.all
        val requests = mutableListOf<AiRequest>()
        val provider = object : AiProvider {
            override val id: AiProviderId = AiProviderId.GOOGLE
            private var call = 0

            override fun resolveBaseUrl(config: AiProviderConfiguration): Result<String> =
                Result.success("https://generativelanguage.googleapis.com")

            override suspend fun listModels(
                config: AiProviderConfiguration
            ): Result<List<com.example.data.ai.AiModelDescriptor>> = Result.success(emptyList())

            override suspend fun testConnection(
                config: AiProviderConfiguration
            ): Result<Unit> = Result.success(Unit)

            override suspend fun generate(request: AiRequest): Result<AiResponse> {
                requests += request
                call++
                return if (call == 1) {
                    access = AiAccessPolicy.disabled
                    Result.success(
                        AiResponse(
                            text = "",
                            toolCalls = listOf(
                                AiToolCall(
                                    id = "permission-test",
                                    name = AiToolCatalog.SEARCH_RECENT_WORKOUTS,
                                    argumentsJson = """{"daysBack":30}"""
                                )
                            )
                        )
                    )
                } else {
                    Result.success(AiResponse(text = "I cannot access that data now."))
                }
            }
        }
        val engine = AiConversationEngine(
            dispatcher = dispatcher(),
            providerResolver = { provider }
        )
        val result = engine.run(
            configuration = com.example.data.ai.AiProviderConfiguration(
                provider = AiProviderId.GOOGLE,
                apiKey = "test-key",
                model = "test-model",
                toolMode = AiToolMode.NATIVE
            ),
            systemInstruction = "coach",
            messages = listOf(AiMessage(AiRole.USER, "Show my recent workouts")),
            accessPolicy = AiAccessPolicy.all,
            accessPolicyProvider = { access }
        )

        assertEquals("I cannot access that data now.", result.text)
        assertEquals(1, result.toolCallsExecuted)
        assertTrue(requests[1].tools.isEmpty())
    }

    @Test
    fun `invalid arguments are a structured error and never reach a handler`() = runBlocking {
        val result = dispatcher().dispatch(
            call(AiToolCatalog.SEARCH_RECENT_WORKOUTS, """{"daysBack":9999}"""),
            AiAccessPolicy.all
        )
        assertTrue(result.isError)
        assertEquals("invalid_arguments", body(result).getString("error"))
    }
}
