/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * New standalone code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.AiConfigurationRepository
import com.example.data.ai.AiConversationEngine
import com.example.data.ai.AiProvider
import com.example.data.ai.AiProviderConfiguration
import com.example.data.ai.AiProviderId
import com.example.data.ai.AiRequest
import com.example.data.ai.AiResponse
import com.example.data.ai.AiService
import com.example.data.ai.AiToolDispatcher
import com.example.data.ai.AiToolRegistry
import com.example.data.ai.GeminiProtocol
import com.example.data.ai.RepositoryAiDataAccess
import com.example.data.ai.WorkoutSummaryGenerator
import com.example.data.db.AppDatabase
import com.example.data.model.ExerciseType
import com.example.data.model.LoggedSet
import com.example.data.model.WorkoutSession
import com.example.data.repository.FitnessRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * End-to-end summary generation against a scripted provider.
 *
 * No request leaves the process. The test still exercises the real generator, comparison selector,
 * AiService boundary and Room persistence, including the "never overwrite" rule.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkoutSummaryGenerationTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: FitnessRepository
    private lateinit var configuration: AiConfigurationRepository
    private lateinit var provider: ScriptedProvider

    private class ScriptedProvider(
        var response: Result<String> = Result.success("# Summary\n\nSteady session.")
    ) : AiProvider {
        override val id: AiProviderId = AiProviderId.GOOGLE
        val requests = mutableListOf<AiRequest>()

        override fun resolveBaseUrl(config: AiProviderConfiguration): Result<String> =
            Result.success("https://generativelanguage.googleapis.com")

        override suspend fun listModels(
            config: AiProviderConfiguration
        ): Result<List<com.example.data.ai.AiModelDescriptor>> = Result.success(emptyList())

        override suspend fun testConnection(config: AiProviderConfiguration): Result<Unit> =
            Result.success(Unit)

        override suspend fun generate(request: AiRequest): Result<AiResponse> {
            requests += request
            return response.map { AiResponse(text = it) }
        }
    }

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FitnessRepository(
            exerciseDao = db.exerciseDao(),
            workoutDao = db.workoutDao(),
            userProfileDao = db.userProfileDao(),
            savedRoutineDao = db.savedRoutineDao(),
            scheduleDao = db.scheduleDao(),
            bodyWeightDao = db.bodyWeightDao(),
            chatDao = db.chatDao(),
            geminiSummaryDao = db.geminiSummaryDao(),
            workoutAiSummaryDao = db.workoutAiSummaryDao()
        )
        configuration = AiConfigurationRepository(context)
        configuration.setActiveProvider(AiProviderId.GOOGLE)
        configuration.saveModel(AiProviderId.GOOGLE, "test-model")
        configuration.saveApiKey(AiProviderId.GOOGLE, "unit-test-key")
        provider = ScriptedProvider()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun aiService(): AiService {
        val dataAccess = RepositoryAiDataAccess(repository)
        return AiService(
            configurationRepository = configuration,
            dataAccess = dataAccess,
            engine = AiConversationEngine(
                dispatcher = AiToolDispatcher(AiToolRegistry(dataAccess)),
                providerResolver = { provider }
            ),
            providerResolver = { provider }
        )
    }

    private suspend fun insertSession(
        title: String,
        startTime: Long,
        reps: Int,
        routineId: Long = 5L,
        routineWeekday: Int = 1
    ): Long {
        val sessionId = db.workoutDao().insertSession(
            WorkoutSession(
                title = title,
                startTime = startTime,
                endTime = startTime + 3_600_000L,
                routineId = routineId,
                routineWeekday = routineWeekday
            )
        )
        db.workoutDao().insertSets(
            listOf(
                LoggedSet(
                    sessionId = sessionId,
                    exerciseId = 1,
                    exerciseName = "Push-up",
                    exerciseType = ExerciseType.PURE_BODYWEIGHT,
                    setNumber = 1,
                    reps = reps,
                    isCompleted = true
                )
            )
        )
        return sessionId
    }

    @Test
    fun `a generated summary is persisted against the exact workout and never regenerated`() =
        runBlocking {
            val previousId = insertSession("Push Day", 1_000_000L, reps = 8)
            val currentId = insertSession("Push Day", 1_000_000L + 7L * 86_400_000L, reps = 12)
            val generator = WorkoutSummaryGenerator(repository, aiService())

            val first = generator.generate(currentId).getOrThrow()
            assertEquals(currentId, first.workoutId)
            assertEquals(previousId, first.previousWorkoutId)
            assertEquals("google", first.provider)
            assertEquals("test-model", first.model)
            assertEquals("# Summary\n\nSteady session.", first.summaryMarkdown)

            val prompt = provider.requests.single().messages.last().text
            assertTrue(prompt.contains("COMPARISON QUALITY: EXACT"))
            assertTrue(prompt.contains("reps 8"))
            assertTrue(prompt.contains("reps 12"))

            val second = generator.generate(currentId).getOrThrow()
            assertEquals(first.id, second.id)
            assertEquals(1, provider.requests.size)
            assertEquals("# Summary\n\nSteady session.", second.summaryMarkdown)
        }

    @Test
    fun `structured text generation never receives the AI Coach toolset`() = runBlocking {
        val service = aiService()
        service.completeText("Generate a routine as JSON.", provider = AiProviderId.GOOGLE)

        val request = provider.requests.single()
        assertTrue(request.tools.isEmpty())
        assertTrue(request.messages.none { it.role == com.example.data.ai.AiRole.TOOL })
    }

    @Test
    fun `an empty stored model uses the displayed provider default`() = runBlocking {
        configuration.saveModel(AiProviderId.GOOGLE, "")
        val currentId = insertSession("Push Day", 9_000_000L, reps = 12)
        val generator = WorkoutSummaryGenerator(repository, aiService())

        val summary = generator.generate(currentId).getOrThrow()

        assertEquals(GeminiProtocol.DEFAULT_MODEL, summary.model)
        assertEquals(GeminiProtocol.DEFAULT_MODEL, provider.requests.single().model)
    }

    @Test
    fun `no previous comparable workout is disclosed rather than invented`() = runBlocking {
        val currentId = insertSession("Solo Push Day", 5_000_000L, reps = 10)
        val generator = WorkoutSummaryGenerator(repository, aiService())

        val summary = generator.generate(currentId).getOrThrow()

        assertEquals(0L, summary.previousWorkoutId)
        val prompt = provider.requests.single().messages.last().text
        assertTrue(prompt.contains("PREVIOUS COMPARABLE WORKOUT: NONE FOUND"))
        assertNotNull(db.workoutAiSummaryDao().getSummaryForWorkout(currentId))
    }

    @Test
    fun `a provider failure leaves the workout and stores no summary`() = runBlocking {
        val currentId = insertSession("Solo Push Day", 7_000_000L, reps = 10)
        provider.response = Result.failure(IllegalStateException("provider unavailable"))
        val generator = WorkoutSummaryGenerator(repository, aiService())

        val result = generator.generate(currentId)

        assertTrue(result.isFailure)
        assertNotNull(db.workoutDao().getSessionById(currentId))
        assertEquals(null, db.workoutAiSummaryDao().getSummaryForWorkout(currentId))
    }
}
