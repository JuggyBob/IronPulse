/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.AiConfigurationRepository
import com.example.data.ai.AiProviderId
import com.example.data.backup.BackupArchiveReader
import com.example.data.backup.BackupErrorCode
import com.example.data.backup.BackupException
import com.example.data.backup.BackupFormat
import com.example.data.backup.BackupPreferencesStore
import com.example.data.backup.DashboardSectionV1
import com.example.data.backup.DataStoreProfileV1
import com.example.data.backup.PreferencesSectionV1
import com.example.data.backup.UserDataBackupManager
import com.example.data.db.AppDatabase
import com.example.data.model.BodyWeightLog
import com.example.data.model.ChatMessageEntity
import com.example.data.model.ChatSession
import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import com.example.data.model.GeminiSummary
import com.example.data.model.LoggedSet
import com.example.data.model.SavedRoutine
import com.example.data.model.ScheduledWorkoutDay
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutSession
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.preferences.userDataStore
import com.example.data.repository.FitnessRepository
import com.example.data.seed.DatasetSeeder
import com.example.data.seed.LibraryEditTracker
import com.example.util.ProfileMediaStore
import com.example.util.WidgetPhotoStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile
import androidx.datastore.preferences.core.edit

/**
 * The end-to-end guarantee: export from one populated installation, import into a completely
 * different one, and end with only the backup's user data while local credentials and the profile
 * picture survive untouched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UserDataBackupIntegrationTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: FitnessRepository
    private lateinit var preferences: UserPreferencesRepository
    private lateinit var aiConfiguration: AiConfigurationRepository
    private lateinit var manager: UserDataBackupManager

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.userDataStore.edit { it.clear() }
        context.getSharedPreferences("ironpulse_library_edits", Context.MODE_PRIVATE)
            .edit().clear().commit()
        File(context.filesDir, "home_widget_media").deleteRecursively()
        File(context.filesDir, "profile_media").deleteRecursively()
        File(context.filesDir, "exercise_media").deleteRecursively()

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
            geminiSummaryDao = db.geminiSummaryDao()
        )
        preferences = UserPreferencesRepository(context)
        aiConfiguration = AiConfigurationRepository(context)
        manager = UserDataBackupManager(context, db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private data class SourceIds(
        val sessionA: Long,
        val sessionB: Long,
        val customExercise: Long,
        val aiExercise: Long,
        val libraryExercise: Long,
        val routine: Long,
        val chatA: String,
        val chatB: String
    )

    @Test
    fun `round trip replaces destination data and preserves credentials and profile picture`() = runBlocking {
        val ids = populateSource()
        val sourcePhoto = File(
            WidgetPhotoStore.writeEncoded(
                context,
                byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x42, 0x43),
                "jpg"
            )!!
        )
        val sourcePhotoBytes = sourcePhoto.readBytes()
        preferences.setHomeWidgetPhotoPath(sourcePhoto.absolutePath)
        preferences.setHomeWidgetConfig(listOf("today", "streak", "photo"), setOf("week", "recent"))
        preferences.setHomeWidgetWeeklyGoal(5)
        preferences.setThemeMode(com.example.ui.theme.ThemeMode.AMOLED)
        preferences.setAccentColorHex("#9465E8")
        preferences.setWeightUnit("lb")
        preferences.setAutoSaveIntervalSeconds(15)
        preferences.setActiveRoutineId(ids.routine)
        preferences.saveRestTimers(
            listOf(
                com.example.data.model.CustomRestTimer("source-timer", "Source Rest", 120)
            )
        )
        preferences.saveBenchmarkPullUps(18, 12)
        aiConfiguration.setActiveProvider(AiProviderId.OPENAI)
        aiConfiguration.saveModel(AiProviderId.OPENAI, "gpt-source")
        aiConfiguration.saveModel(AiProviderId.GOOGLE, "gemini-source")
        aiConfiguration.saveApiKey(AiProviderId.GOOGLE, "SOURCE_GOOGLE_KEY_SHOULD_NOT_EXPORT")
        aiConfiguration.saveApiKey(AiProviderId.OPENAI, "SOURCE_OPENAI_KEY_SHOULD_NOT_EXPORT")

        val archive = temp.newFile("source.ironpulse")
        val export = manager.exportToFile(archive)

        // The archive itself must not contain the credentials or either excluded media category.
        val rawArchiveText = ZipFile(archive).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toList()
            assertFalse(names.any { it.contains("profile_media") })
            assertFalse(names.any { it.contains("exercise_media") })
            names.joinToString("\n") + "\n" + names.joinToString("\n") { name ->
                zip.getInputStream(zip.getEntry(name)).use { it.readBytes().toString(Charsets.UTF_8) }
            }
        }
        assertFalse(rawArchiveText.contains("SOURCE_GOOGLE_KEY_SHOULD_NOT_EXPORT"))
        assertFalse(rawArchiveText.contains("SOURCE_OPENAI_KEY_SHOULD_NOT_EXPORT"))
        assertFalse(rawArchiveText.contains("SOURCE_ROOM_KEY_SHOULD_NOT_EXPORT"))
        assertFalse(rawArchiveText.contains("customMediaUri"))
        assertFalse(rawArchiveText.contains("source_avatar"))

        val payload = BackupArchiveReader(archive).read()
        assertEquals(6, payload.manifest.sections.getValue(BackupFormat.SECTION_WORKOUTS).recordCount)
        assertEquals(1, payload.files.size)

        // Replace the whole source installation with an unrelated destination state.
        resetToDestinationState()

        val destinationAvatar = File(context.filesDir, "profile_media/avatar.png")
        destinationAvatar.parentFile?.mkdirs()
        destinationAvatar.writeBytes(byteArrayOf(7, 7, 7, 7, 7))
        preferences.setProfileAvatarPath(destinationAvatar.absolutePath)
        aiConfiguration.saveApiKey(AiProviderId.GOOGLE, "DEST_GOOGLE_KEY")
        aiConfiguration.saveApiKey(AiProviderId.OPENAI, "DEST_OPENAI_KEY")
        aiConfiguration.saveApiKey(AiProviderId.CUSTOM_OPENAI, "")
        val destRoomKey = "DEST_ROOM_KEY"
        repository.saveUserProfile(
            UserProfile(
                name = "Destination Athlete",
                heightCm = 170.0,
                currentWeightKg = 70.0,
                targetWeightKg = 72.0,
                age = 25,
                preferredUnit = "kg",
                fitnessLevel = "Beginner",
                customApiKey = destRoomKey
            )
        )
        val destWidget = File(
            WidgetPhotoStore.writeEncoded(context, byteArrayOf(1, 2, 3, 4), "png")!!
        )
        preferences.setHomeWidgetPhotoPath(destWidget.absolutePath)
        preferences.setHomeWidgetConfig(listOf("quote"), setOf("today"))
        preferences.setHomeWidgetWeeklyGoal(2)
        preferences.saveRestTimers(
            listOf(com.example.data.model.CustomRestTimer("destination-timer", "Destination Rest", 30))
        )
        preferences.setWeekStartsOnMonday(true)
        preferences.setFullscreenEnabled(true)
        preferences.setAutoCheckUpdatesOnStart(true)
        preferences.setShareAthleteDataWithAi(false)
        preferences.setLiquidGlassEnabled(false)

        val prepared = manager.inspectFile(archive, "source.ironpulse")
        assertEquals(BuildConfig.VERSION_NAME, prepared.preview.appVersionName)
        manager.applyImport(prepared)

        // Full replacement: the destination's own records are gone, and the backup's are present.
        assertEquals(
            setOf(ids.sessionA, ids.sessionB),
            repository.getAllSessions().first().map { it.id }.toSet()
        )
        assertEquals(4, repository.getAllCompletedSets().first().size)
        assertEquals(listOf(ids.routine), repository.getAllSavedRoutines().first().map { it.id })
        assertEquals(2, repository.getScheduleDays().first().size)
        assertEquals(2, repository.getAllWeightLogs().first().size)
        assertEquals(listOf(ids.chatA, ids.chatB).sorted(), repository.getAllChatSessions().first().map { it.id }.sorted())
        assertEquals(3, repository.getMessagesListForSession(ids.chatA).size + repository.getMessagesListForSession(ids.chatB).size)
        assertEquals(1, repository.getAllGeminiSummaries().first().size)

        val custom = repository.getExerciseById(ids.customExercise)!!
        assertEquals("Custom Cable Row Test", custom.name)
        assertEquals("", custom.customMediaUri)
        assertTrue(custom.isCustom)
        assertFalse(custom.isAiGenerated)
        val ai = repository.getExerciseById(ids.aiExercise)!!
        assertTrue(ai.isCustom)
        assertTrue(ai.isAiGenerated)
        assertEquals("", ai.customMediaUri)
        val editedLibrary = repository.getExerciseById(ids.libraryExercise)!!
        assertEquals("Edited Library Test Press", editedLibrary.name)
        assertEquals("Edited instructions", editedLibrary.instructions)
        assertEquals(setOf(ids.libraryExercise), LibraryEditTracker.editedIds(context))
        assertNull(repository.getExerciseByName("Destination Custom Press"))

        // Credentials survive, including the destination's legacy Room profile key.
        assertEquals("DEST_GOOGLE_KEY", aiConfiguration.configuration(AiProviderId.GOOGLE).apiKey)
        assertEquals("DEST_OPENAI_KEY", aiConfiguration.configuration(AiProviderId.OPENAI).apiKey)
        assertEquals("", aiConfiguration.configuration(AiProviderId.CUSTOM_OPENAI).apiKey)
        assertEquals("DEST_ROOM_KEY", repository.getUserProfile().first()!!.customApiKey)
        assertEquals("Test Athlete", repository.getUserProfile().first()!!.name)

        // The profile picture file and its preference are untouched.
        assertEquals(destinationAvatar.absolutePath, preferences.profileAvatarPathFlow.first())
        assertTrue(destinationAvatar.isFile)
        assertEquals(listOf<Byte>(7, 7, 7, 7, 7), destinationAvatar.readBytes().toList())

        // The Home photo was restored, and the destination's old copy was removed.
        val restoredWidgetPath = preferences.homeWidgetPhotoPathFlow.first()
        assertNotEquals(destWidget.absolutePath, restoredWidgetPath)
        assertTrue(File(restoredWidgetPath).isFile)
        assertEquals(sourcePhotoBytes.toList(), File(restoredWidgetPath).readBytes().toList())
        assertFalse(destWidget.exists())

        // Restored preferences and dashboard.
        assertEquals(com.example.ui.theme.ThemeMode.AMOLED, preferences.themeModeFlow.first())
        assertEquals("#9465E8", preferences.accentColorHexFlow.first())
        assertEquals("lb", preferences.preferredUnitFlow.first())
        assertEquals(15, preferences.autoSaveIntervalSecondsFlow.first())
        assertEquals(ids.routine, preferences.activeRoutineIdFlow.first())
        assertEquals(
            listOf("today", "streak", "photo"),
            preferences.homeWidgetOrderFlow.first()
        )
        assertEquals(setOf("week", "recent"), preferences.homeWidgetHiddenFlow.first())
        assertEquals(5, preferences.homeWidgetWeeklyGoalFlow.first())
        assertFalse(preferences.appSettingsFlow.first().weekStartsOnMonday)
        assertFalse(preferences.appSettingsFlow.first().fullscreenEnabled)
        assertFalse(preferences.appSettingsFlow.first().autoCheckUpdatesOnStart)
        assertTrue(preferences.appSettingsFlow.first().shareAthleteDataWithAi)
        assertTrue(preferences.appSettingsFlow.first().liquidGlassEnabled)
        assertEquals("Source Rest", preferences.savedRestTimersFlow.first().single().name)
        assertEquals(120, preferences.savedRestTimersFlow.first().single().totalSeconds)
        assertEquals(18, preferences.benchmarkPullUpsFlow.first())
        assertEquals(12, preferences.benchmarkPullUpsAddedWeightFlow.first())

        // AI selections came from the backup, keys did not.
        assertEquals(AiProviderId.OPENAI, aiConfiguration.activeProviderFlow.first())
        assertEquals("gpt-source", aiConfiguration.configuration(AiProviderId.OPENAI).model)
        assertEquals("gemini-source", aiConfiguration.configuration(AiProviderId.GOOGLE).model)
    }

    @Test
    fun `an empty backup category clears the destination category`() = runBlocking {
        // Source contains only a profile and no routines, conversations or summaries.
        val profile = UserProfile(name = "Only Profile", customApiKey = "")
        preferences.saveUserProfile(profile)
        repository.saveUserProfile(profile)
        preferences.setOnboardingCompleted()
        val archive = temp.newFile("empty-categories.ironpulse")
        manager.exportToFile(archive)

        // Destination has routines, conversations and summaries that must disappear.
        resetToDestinationState()
        val prepared = manager.inspectFile(archive, "empty-categories.ironpulse")
        manager.applyImport(prepared)

        assertTrue(repository.getAllSavedRoutines().first().isEmpty())
        assertTrue(repository.getAllChatSessions().first().isEmpty())
        assertTrue(repository.getAllGeminiSummaries().first().isEmpty())
        assertTrue(repository.getScheduleDays().first().isEmpty())
        assertEquals("Only Profile", repository.getUserProfile().first()!!.name)
        // A fresh destination with no profile picture must not gain a reference to one.
        assertTrue(preferences.profileAvatarPathFlow.first().isBlank())
        assertFalse(File(context.filesDir, "profile_media/avatar.png").exists())
    }

    @Test
    fun `a failure while applying preferences rolls the database back`() = runBlocking {
        val source = UserProfile(name = "Source Rollback", customApiKey = "")
        preferences.saveUserProfile(source)
        repository.saveUserProfile(source)
        val sourceSession = repository.insertSession(
            WorkoutSession(title = "Source Rollback Workout", startTime = 1_760_000_000_000L, endTime = 1_760_000_100_000L)
        )
        val archive = temp.newFile("rollback-source.ironpulse")
        manager.exportToFile(archive)

        resetToDestinationState()
        val destinationSessions = repository.getAllSessions().first().map { it.id }
        val destinationProfile = repository.getUserProfile().first()!!.name
        val destinationTheme = preferences.themeModeFlow.first()

        val failingPreferences = object : BackupPreferencesStore(context) {
            private var failed = false
            override suspend fun apply(
                profile: DataStoreProfileV1?,
                preferences: PreferencesSectionV1,
                dashboard: DashboardSectionV1,
                widgetPhotoPath: String?
            ) {
                if (!failed) {
                    failed = true
                    throw IOException("simulated preference write failure")
                }
                super.apply(profile, preferences, dashboard, widgetPhotoPath)
            }
        }
        val failingManager = UserDataBackupManager(context, db, failingPreferences)
        val prepared = failingManager.inspectFile(archive, "rollback-source.ironpulse")
        try {
            failingManager.applyImport(prepared)
            org.junit.Assert.fail("expected the import to fail")
        } catch (e: BackupException) {
            assertEquals(BackupErrorCode.IO_ERROR, e.code)
            assertTrue(e.dataPreserved)
        }

        assertEquals(destinationSessions, repository.getAllSessions().first().map { it.id })
        assertTrue(repository.getAllSessions().first().none { it.id == sourceSession })
        assertEquals(destinationProfile, repository.getUserProfile().first()!!.name)
        assertEquals(destinationTheme, preferences.themeModeFlow.first())
    }

    @Test
    fun `a corrupt archive never modifies destination data`() = runBlocking {
        resetToDestinationState()
        val destinationSessions = repository.getAllSessions().first().map { it.id }
        val corrupt = temp.newFile("corrupt.ironpulse")
        corrupt.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0))

        try {
            manager.inspectFile(corrupt, "corrupt.ironpulse")
            org.junit.Assert.fail("expected a corrupt archive failure")
        } catch (e: BackupException) {
            assertTrue(e.code == BackupErrorCode.MALFORMED_ARCHIVE || e.code == BackupErrorCode.NOT_AN_ARCHIVE)
        }

        assertEquals(destinationSessions, repository.getAllSessions().first().map { it.id })
        assertNotNull(repository.getUserProfile().first())
    }

    private suspend fun populateSource(): SourceIds {
        val profile = UserProfile(
            name = "Test Athlete",
            heightCm = 182.0,
            currentWeightKg = 81.0,
            targetWeightKg = 84.0,
            age = 31,
            preferredUnit = "kg",
            fitnessLevel = "Advanced",
            customApiKey = "SOURCE_ROOM_KEY_SHOULD_NOT_EXPORT"
        )
        preferences.saveUserProfile(profile)
        repository.saveUserProfile(profile)
        preferences.setOnboardingCompleted()
        preferences.setProfileAvatarPath("/source/source_avatar.png")
        preferences.setLiquidGlassEnabled(true)
        preferences.setFullscreenEnabled(false)
        preferences.setWeekStartsOnMonday(false)
        preferences.setAutoCheckUpdatesOnStart(false)
        preferences.setShareAthleteDataWithAi(true)

        val libraryId = repository.insertExercise(
            Exercise(
                name = "Edited Library Test Press",
                category = "Chest",
                bodyPart = "Chest",
                target = "Pectorals",
                exerciseType = ExerciseType.STANDARD_WEIGHT,
                equipment = "Barbell",
                instructions = "Edited instructions",
                isCustom = false
            )
        )
        LibraryEditTracker.recordEdit(context, libraryId)

        val customId = repository.insertExercise(
            Exercise(
                name = "Custom Cable Row Test",
                category = "Back",
                bodyPart = "Back",
                target = "Lats",
                exerciseType = ExerciseType.STANDARD_WEIGHT,
                equipment = "Cable",
                isCustom = true,
                customMediaUri = "/source/exercise_media/custom.jpg"
            )
        )
        val aiId = repository.insertExercise(
            Exercise(
                name = "AI Sled Push Test",
                category = "Legs",
                bodyPart = "Legs",
                target = "Quadriceps",
                exerciseType = ExerciseType.CARDIO,
                equipment = "Sled",
                isCustom = true,
                isAiGenerated = true,
                customMediaUri = "/source/exercise_media/ai.gif"
            )
        )

        val sessionA = repository.insertSession(
            WorkoutSession(
                title = "Source Upper",
                startTime = 1_760_000_000_000L,
                endTime = 1_760_003_600_000L,
                notes = "Source notes",
                routineId = 0L
            )
        )
        val sessionB = repository.insertSession(
            WorkoutSession(
                title = "Source Lower",
                startTime = 1_760_100_000_000L,
                endTime = 1_760_103_600_000L
            )
        )
        repository.insertSets(
            listOf(
                LoggedSet(
                    sessionId = sessionA,
                    exerciseId = libraryId,
                    exerciseName = "Edited Library Test Press",
                    exerciseType = ExerciseType.STANDARD_WEIGHT,
                    setNumber = 1,
                    weight = 82.5,
                    reps = 5,
                    bodyWeightKg = 81.0,
                    timestamp = 1_760_000_100_000L
                ),
                LoggedSet(
                    sessionId = sessionA,
                    exerciseId = customId,
                    exerciseName = "Custom Cable Row Test",
                    exerciseType = ExerciseType.STANDARD_WEIGHT,
                    setNumber = 2,
                    weight = 60.0,
                    reps = 8,
                    bodyWeightKg = 81.0,
                    timestamp = 1_760_000_200_000L
                ),
                LoggedSet(
                    sessionId = sessionB,
                    exerciseId = aiId,
                    exerciseName = "AI Sled Push Test",
                    exerciseType = ExerciseType.CARDIO,
                    setNumber = 1,
                    distanceKm = 1.25,
                    bodyWeightKg = 81.0,
                    timestamp = 1_760_100_100_000L
                ),
                LoggedSet(
                    sessionId = sessionB,
                    exerciseId = customId,
                    exerciseName = "Custom Cable Row Test",
                    exerciseType = ExerciseType.STANDARD_WEIGHT,
                    setNumber = 1,
                    weight = 57.5,
                    addedWeight = 2.5,
                    reps = 10,
                    isCompleted = false,
                    bodyWeightKg = 81.0,
                    timestamp = 1_760_100_200_000L
                )
            )
        )

        val routineId = repository.saveRoutine(
            SavedRoutine(
                name = "Source Split",
                description = "Source routine",
                daysPerWeek = 4,
                exercisesJson = """[{"exerciseId":$libraryId,"exerciseName":"Edited Library Test Press","weekday":1,"targetSets":4,"targetReps":6}]""",
                scheduleVersion = 1
            )
        )
        repository.replaceFutureSchedule(
            fromDate = 1_760_000_000_000L,
            rows = listOf(
                ScheduledWorkoutDay(
                    dateStart = 1_760_000_000_000L,
                    dateKey = 20261005L,
                    routineId = routineId,
                    weekday = 1,
                    dayTitle = "Source Day",
                    exerciseCount = 3,
                    capturedAt = 1_760_000_000_000L
                )
            )
        )
        // one past row, kept as history
        db.scheduleDao().insertScheduleDays(
            listOf(
                ScheduledWorkoutDay(
                    dateStart = 1_759_900_000_000L,
                    dateKey = 20261004L,
                    routineId = routineId,
                    weekday = 7,
                    dayTitle = "Past Rest",
                    exerciseCount = 0,
                    capturedAt = 1_759_900_000_000L
                )
            )
        )

        repository.insertWeightLog(BodyWeightLog(weightKg = 81.0, timestamp = 1_760_000_000_000L, note = "Source"))
        repository.insertWeightLog(BodyWeightLog(weightKg = 80.5, timestamp = 1_760_100_000_000L, note = ""))

        repository.insertChatSession(ChatSession(id = "source-chat-1", title = "Source Chat", createdAt = 1_760_000_000_000L, lastUpdatedAt = 1_760_000_000_000L))
        repository.insertChatSession(ChatSession(id = "source-chat-2", title = "Source Chat 2", createdAt = 1_760_100_000_000L, lastUpdatedAt = 1_760_100_000_000L))
        repository.insertChatMessages(
            listOf(
                ChatMessageEntity(id = "source-msg-1", sessionId = "source-chat-1", sender = "USER", text = "Question", timestamp = 1_760_000_100_000L),
                ChatMessageEntity(id = "source-msg-2", sessionId = "source-chat-1", sender = "AI", text = "Answer", timestamp = 1_760_000_200_000L),
                ChatMessageEntity(id = "source-msg-3", sessionId = "source-chat-2", sender = "USER", text = "Second", timestamp = 1_760_100_100_000L)
            )
        )
        repository.insertGeminiSummary(
            GeminiSummary(
                exerciseName = "Edited Library Test Press",
                report = "# Source Summary",
                daysLogged = 4,
                setCount = 12,
                bestOneRm = 102.5,
                trendPercent = 3.75,
                createdAt = 1_760_200_000_000L
            )
        )

        return SourceIds(
            sessionA = sessionA,
            sessionB = sessionB,
            customExercise = customId,
            aiExercise = aiId,
            libraryExercise = libraryId,
            routine = routineId,
            chatA = "source-chat-1",
            chatB = "source-chat-2"
        )
    }

    private suspend fun resetToDestinationState() {
        repository.clearAllUserData()
        db.exerciseDao().deleteAllUserExercises()
        val orphanedLibraryRows = db.exerciseDao().getAllExercisesList()
            .filter { !it.isCustom && !it.isAiGenerated && it.sourceId.isBlank() }
            .map { it.id }
        if (orphanedLibraryRows.isNotEmpty()) db.exerciseDao().deleteByIds(orphanedLibraryRows)
        LibraryEditTracker.clearAll(context)
        DatasetSeeder.seedFresh(context, db.exerciseDao())

        val profile = UserProfile(
            name = "Destination Athlete",
            heightCm = 170.0,
            currentWeightKg = 70.0,
            targetWeightKg = 72.0,
            age = 25,
            preferredUnit = "kg",
            fitnessLevel = "Beginner"
        )
        preferences.saveUserProfile(profile)
        repository.saveUserProfile(profile)
        preferences.setOnboardingCompleted()

        val destCustom = repository.insertExercise(
            Exercise(
                name = "Destination Custom Press",
                category = "Chest",
                bodyPart = "Chest",
                target = "Pectorals",
                exerciseType = ExerciseType.STANDARD_WEIGHT,
                equipment = "Dumbbell",
                isCustom = true
            )
        )
        val destSession = repository.insertSession(
            WorkoutSession(title = "Destination Workout", startTime = 1_770_000_000_000L, endTime = 1_770_003_600_000L)
        )
        repository.insertSet(
            LoggedSet(
                sessionId = destSession,
                exerciseId = destCustom,
                exerciseName = "Destination Custom Press",
                exerciseType = ExerciseType.STANDARD_WEIGHT,
                setNumber = 1,
                weight = 20.0,
                reps = 12
            )
        )
        repository.saveRoutine(
            SavedRoutine(
                id = 0,
                name = "Destination Routine",
                description = "Must disappear",
                exercisesJson = "[]"
            )
        )
        repository.insertWeightLog(BodyWeightLog(weightKg = 70.0, timestamp = 1_770_000_000_000L))
        repository.insertChatSession(ChatSession(id = "destination-chat", title = "Destination Chat"))
        repository.insertChatMessage(
            ChatMessageEntity(
                id = "destination-message",
                sessionId = "destination-chat",
                sender = "USER",
                text = "Must disappear"
            )
        )
        repository.insertGeminiSummary(
            GeminiSummary(
                exerciseName = "Destination Custom Press",
                report = "Must disappear"
            )
        )
        preferences.setThemeMode(com.example.ui.theme.ThemeMode.LIGHT)
        preferences.setWeightUnit("kg")
        preferences.setActiveRoutineId(0L)
    }
}
