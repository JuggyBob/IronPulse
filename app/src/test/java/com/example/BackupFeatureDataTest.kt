/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * New standalone code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.backup.BackupFormat
import com.example.data.backup.BackupSchemaMigrations
import com.example.data.backup.JsonNode
import com.example.data.backup.UserDataBackupManager
import com.example.data.db.AppDatabase
import com.example.data.model.ChatMessageEntity
import com.example.data.model.ChatMessageKind
import com.example.data.model.ChatMessageStatus
import com.example.data.model.ChatSession
import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutAiSummary
import com.example.data.model.WorkoutSession
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.preferences.userDataStore
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The feature data must survive the portable `.ironpulse` archive exactly. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupFeatureDataTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var preferences: UserPreferencesRepository
    private lateinit var manager: UserDataBackupManager

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.userDataStore.edit { it.clear() }
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferences = UserPreferencesRepository(context)
        manager = UserDataBackupManager(context, db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `summaries and independent widget themes round trip exactly`() = runBlocking {
        val sessionId = db.workoutDao().insertSession(
            WorkoutSession(
                title = "Push Day",
                startTime = 1_760_000_000_000L,
                endTime = 1_760_003_600_000L,
                routineId = 5L,
                routineWeekday = 1
            )
        )
        db.workoutAiSummaryDao().insertIfAbsent(
            WorkoutAiSummary(
                workoutId = sessionId,
                summaryMarkdown = "# Push Day\n\n**Original** summary with `code`.",
                provider = "google",
                model = "gemini-test",
                previousWorkoutId = 0,
                createdAt = 1_760_003_700_000L
            )
        )
        db.chatDao().insertSessions(listOf(ChatSession(id = "chat-1", title = "Coach")))
        db.chatDao().insertMessages(
            listOf(
                ChatMessageEntity(
                    id = "message-1",
                    sessionId = "chat-1",
                    sender = "AI",
                    text = "Should I change Monday?",
                    kind = ChatMessageKind.ROUTINE_PROPOSAL,
                    payloadJson = """{"proposalId":"p-1"}""",
                    status = ChatMessageStatus.PENDING
                )
            )
        )
        db.exerciseDao().insert(
            Exercise(
                name = "Backup Custom Curl",
                category = "Arms",
                exerciseType = ExerciseType.STANDARD_WEIGHT,
                equipment = "Dumbbell",
                isCustom = true
            )
        )
        db.userProfileDao().insertOrUpdate(
            UserProfile(
                name = "Backup Athlete",
                heightCm = 181.0,
                currentWeightKg = 80.0
            )
        )
        preferences.saveUserProfile(
            UserProfile(
                name = "Backup Athlete",
                heightCm = 181.0,
                currentWeightKg = 80.0
            )
        )
        preferences.setAiWorkoutDataAccess(true)
        preferences.setPostWorkoutAiSummaries(true)
        preferences.setAndroidWidgetTheme(com.example.data.model.AndroidWidgetKind.STREAK, ThemeMode.AMOLED)
        preferences.setAndroidWidgetTheme(com.example.data.model.AndroidWidgetKind.WEEKLY, ThemeMode.LIGHT)
        preferences.setAndroidWidgetTheme(com.example.data.model.AndroidWidgetKind.PROFILE, ThemeMode.DARK)

        val archive = temp.newFile("features.ironpulse")
        manager.exportToFile(archive)

        // Simulate a fresh installation with unrelated values.
        db.clearAllTables()
        context.userDataStore.edit { it.clear() }
        preferences.setAndroidWidgetTheme(com.example.data.model.AndroidWidgetKind.STREAK, ThemeMode.IRONPULSE)

        val prepared = manager.inspectFile(archive, "features.ironpulse")
        manager.applyImport(prepared)

        val summary = db.workoutAiSummaryDao().getSummaryForWorkout(sessionId)
        assertNotNull(summary)
        assertEquals(
            "# Push Day\n\n**Original** summary with `code`.",
            summary!!.summaryMarkdown
        )
        assertEquals("google", summary.provider)
        assertEquals("gemini-test", summary.model)

        val themes = preferences.androidWidgetThemesFlow.first()
        assertEquals(ThemeMode.AMOLED, themes.streak)
        assertEquals(ThemeMode.LIGHT, themes.weekly)
        assertEquals(ThemeMode.DARK, themes.profile)
        assertTrue(preferences.aiWorkoutDataAccessFlow.first())
        assertTrue(preferences.postWorkoutAiSummariesFlow.first())

        val message = db.chatDao().getMessagesListForSession("chat-1").single()
        assertEquals(ChatMessageKind.ROUTINE_PROPOSAL, message.kind)
        assertEquals(ChatMessageStatus.PENDING, message.status)
        assertEquals("""{"proposalId":"p-1"}""", message.payloadJson)
    }

    @Test
    fun `schema v1 migration supplies safe defaults for the new fields`() {
        val ai = JsonNode.Obj(
            linkedMapOf(
                "chatSessions" to JsonNode.Arr(),
                "chatMessages" to JsonNode.Arr(),
                "geminiSummaries" to JsonNode.Arr()
            )
        )
        val preferences = JsonNode.Obj(linkedMapOf("unit" to JsonNode.Str("kg")))
        val sections = linkedMapOf<String, JsonNode.Obj>(
            BackupFormat.SECTION_AI to ai,
            BackupFormat.SECTION_PREFERENCES to preferences
        )

        BackupSchemaMigrations.migrate(
            sections = sections,
            fromVersion = 1,
            toVersion = 2
        )

        assertEquals("[]", ai["workoutSummaries"]!!.render().trim())
        val widgetThemes = sections.getValue(BackupFormat.SECTION_PREFERENCES)["widgetThemes"]!!
        assertTrue(widgetThemes.render().contains("\"ironpulse\""))
        assertEquals("false", sections.getValue(BackupFormat.SECTION_PREFERENCES)["aiWorkoutDataAccess"]!!.render())
        assertEquals("false", sections.getValue(BackupFormat.SECTION_PREFERENCES)["postWorkoutAiSummaries"]!!.render())
    }
}
