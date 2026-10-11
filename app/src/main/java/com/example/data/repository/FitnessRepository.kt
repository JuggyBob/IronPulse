package com.example.data.repository

import com.example.data.dao.BodyWeightDao
import com.example.data.dao.ChatDao
import com.example.data.dao.ExerciseDao
import com.example.data.dao.GeminiSummaryDao
import com.example.data.dao.SavedRoutineDao
import com.example.data.dao.UserProfileDao
import kotlinx.coroutines.flow.combine
import com.example.data.dao.WorkoutDao
import com.example.data.model.BodyWeightLog
import com.example.data.model.ChatMessageEntity
import com.example.data.model.ChatSession
import com.example.data.model.Exercise
import com.example.data.model.GeminiSummary
import com.example.data.model.LoggedSet
import com.example.data.model.SavedRoutine
import com.example.data.model.SessionWithSets
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutSession
import kotlinx.coroutines.flow.Flow

class FitnessRepository(
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val userProfileDao: UserProfileDao,
    private val savedRoutineDao: SavedRoutineDao,
    private val bodyWeightDao: BodyWeightDao,
    private val chatDao: ChatDao,
    private val geminiSummaryDao: GeminiSummaryDao
) {
    // Exercises
    fun getAllExercises(): Flow<List<Exercise>> = exerciseDao.getAllExercises()
    fun getExercisesByCategory(category: String): Flow<List<Exercise>> = exerciseDao.getExercisesByCategory(category)
    fun searchExercises(query: String): Flow<List<Exercise>> = exerciseDao.searchExercises(query)
    fun getUserCreatedExercises(): Flow<List<Exercise>> = exerciseDao.getUserCreatedExercises()
    fun getAiGeneratedExercises(): Flow<List<Exercise>> = exerciseDao.getAiGeneratedExercises()
    suspend fun getEditableExerciseById(id: Long): Exercise? = exerciseDao.getEditableExerciseById(id)
    suspend fun getUserCreatedExerciseById(id: Long): Exercise? = exerciseDao.getUserCreatedExerciseById(id)

    /**
     * The exercise DAO itself, for the dataset seeder to drive directly.
     *
     * Exposed because "revert to the original database" is exactly the seeder's own upsert applied on
     * demand. Going through this repository would mean reimplementing the insert/update plan here,
     * and the copy would drift from the one that runs on every app open.
     */
    fun exerciseDaoForReseed(): ExerciseDao = exerciseDao
    suspend fun insertExercise(exercise: Exercise): Long = exerciseDao.insert(exercise)

    /**
     * Creates a custom exercise owned by the user.
     * @return the new row id, or -1 when a different exercise already uses that exact name.
     */
    suspend fun createUserExercise(exercise: Exercise): Long =
        exerciseDao.insert(exercise.copy(isCustom = true, isAiGenerated = false))

    /**
     * Registers an exercise the AI invented. It is flagged as AI generated (the user did not type
     * it) but, like the user's own exercises, it is custom and can be edited or deleted later on.
     */
    suspend fun createAiGeneratedExercise(exercise: Exercise): Long =
        exerciseDao.insert(exercise.copy(isCustom = true, isAiGenerated = true))

    /**
     * @return true when the exercise belongs to the user (created by them or by the AI) and got
     * updated. Built-in library exercises are always rejected.
     */
    suspend fun updateUserExercise(exercise: Exercise): Boolean =
        exerciseDao.updateEditableExercise(
            id = exercise.id,
            name = exercise.name.trim(),
            category = exercise.category,
            exerciseType = exercise.exerciseType,
            equipment = exercise.equipment,
            description = exercise.description,
            instructions = exercise.instructions,
            bodyPart = exercise.bodyPart,
            target = exercise.target,
            secondaryMuscles = exercise.secondaryMuscles
        ) > 0

    /**
     * Writes every editable field of a library exercise.
     *
     * The unguarded counterpart to [updateUserExercise], used only once the athlete has confirmed the
     * warning. This is the escape hatch for the cases where the shipped catalog is simply wrong: an
     * exercise typed as bodyweight when it is loaded, an exercise with the wrong muscle group, or an
     * exercise whose `exerciseType` makes the app ask for barbell weight on a run.
     *
     * Logged sets are deliberately not touched here. A set already recorded keeps the type it was
     * recorded under, so correcting an exercise never rewrites what the athlete logged.
     */
    suspend fun updateLibraryExercise(exercise: Exercise): Boolean =
        exerciseDao.updateLibraryExercise(
            id = exercise.id,
            name = exercise.name.trim(),
            category = exercise.category,
            exerciseType = exercise.exerciseType,
            equipment = exercise.equipment,
            description = exercise.description,
            instructions = exercise.instructions,
            bodyPart = exercise.bodyPart,
            target = exercise.target,
            secondaryMuscles = exercise.secondaryMuscles
        ) > 0

    /** Restores the shipped library rows from the bundled dataset. Deletes the rows the athlete kept. */
    suspend fun revertCatalogToDataset(
        keepUserCreated: Boolean,
        keepAiCreated: Boolean
    ): RevertReport {
        val removedUser = if (keepUserCreated) 0 else exerciseDao.deleteUserCreatedExercises()
        val removedAi = if (keepAiCreated) 0 else exerciseDao.deleteAiCreatedExercises()
        val library = exerciseDao.libraryExerciseCount()
        val keptUser = exerciseDao.userCreatedExerciseCount()
        val keptAi = exerciseDao.aiCreatedExerciseCount()
        return RevertReport(
            libraryRestored = library,
            removedUserCreated = removedUser,
            removedAiCreated = removedAi,
            keptUserCreated = keptUser,
            keptAiCreated = keptAi
        )
    }

    /** What a "Revert to original database" actually did, so the confirmation can say so. */
    data class RevertReport(
        val libraryRestored: Int,
        val removedUserCreated: Int,
        val removedAiCreated: Int,
        val keptUserCreated: Int,
        val keptAiCreated: Int
    )

    /**
     * Points an exercise at a photo or video the athlete picked from their gallery.
     *
     * Guarded on ownership for the same reason as every other edit: the shipped library is locked, so
     * a dataset row can never be given local media (which would also be overwritten by the next
     * catalog reseed).
     *
     * @return true when the exercise belonged to the athlete and was updated.
     */
    suspend fun attachExerciseMedia(id: Long, uri: String, isVideo: Boolean): Boolean =
        exerciseDao.updateEditableMedia(id, uri, isVideo) > 0

    /** Clears the media the athlete attached. Same ownership guard. */
    suspend fun clearExerciseMedia(id: Long): Boolean =
        exerciseDao.updateEditableMedia(id, "", false) > 0

    /**
     * @return true when the exercise belongs to the user (created by them or by the AI) and got
     * deleted. Built-in library exercises are always rejected.
     */
    suspend fun deleteUserExercise(id: Long): Boolean = exerciseDao.deleteEditableExercise(id) > 0

    /** Flags an already stored exercise as AI generated (see ExerciseDao.markAsAiGenerated). */
    suspend fun markExerciseAsAiGenerated(id: Long): Boolean = exerciseDao.markAsAiGenerated(id) > 0


    // Workout Sessions
    fun getAllSessions(): Flow<List<WorkoutSession>> = workoutDao.getAllSessions()

    /** A session together with its sets, used by the activity heat map. */
    fun getAllSessionsWithSets(): Flow<List<SessionWithSets>> = workoutDao.getAllSessionsWithSets()
    suspend fun insertSession(session: WorkoutSession): Long = workoutDao.insertSession(session)
    suspend fun deleteSession(session: WorkoutSession) = workoutDao.deleteSessionWithSets(session)
    suspend fun getSessionById(id: Long): WorkoutSession? = workoutDao.getSessionById(id)

    // Logged Sets
    fun getSetsForSession(sessionId: Long): Flow<List<LoggedSet>> = workoutDao.getSetsForSession(sessionId)
    fun getSetsForExercise(exerciseName: String): Flow<List<LoggedSet>> = workoutDao.getSetsForExercise(exerciseName)
    suspend fun getSetsForExerciseList(exerciseName: String): List<LoggedSet> = workoutDao.getSetsForExerciseList(exerciseName)

    /** Every set ever logged, used to recompute the athlete's benchmark personal records. */
    suspend fun getAllLoggedSets(): List<LoggedSet> = workoutDao.getAllSets()
    fun getAllCompletedSets(): Flow<List<LoggedSet>> = workoutDao.getAllCompletedSets()
    fun getDistinctLoggedExerciseNames(): Flow<List<String>> = workoutDao.getDistinctLoggedExerciseNames()
    fun getHistoricalAddedWeights(exerciseName: String): Flow<List<Double>> = workoutDao.getHistoricalAddedWeights(exerciseName)

    suspend fun insertSet(set: LoggedSet): Long = workoutDao.insertSet(set)
    suspend fun insertSets(sets: List<LoggedSet>) = workoutDao.insertSets(sets)
    suspend fun updateSet(set: LoggedSet) = workoutDao.updateSet(set)
    suspend fun deleteSet(set: LoggedSet) = workoutDao.deleteSet(set)
    suspend fun deleteSets(sets: List<LoggedSet>) = workoutDao.deleteSets(sets)
    suspend fun deleteSetById(id: Long) = workoutDao.deleteSetById(id)
    suspend fun deleteSetsBySessionId(sessionId: Long) = workoutDao.deleteSetsBySessionId(sessionId)

    // User Profile
    fun getUserProfile(): Flow<UserProfile?> = userProfileDao.getProfile()

    /**
     * True once the athlete has any logged data: sessions, weigh-ins or routines.
     *
     * Used to tell a fresh install from an install that predates the first run onboarding. That
     * distinction cannot be read from preferences, because a key that defaults to false looks exactly
     * the same for both. Confusing them shows the landing pages to an existing athlete and then
     * overwrites the profile they already filled in.
     */
    fun hasAnyLoggedData(): Flow<Boolean> = combine(
        workoutDao.getAllSessions(),
        bodyWeightDao.getAllWeightLogs(),
        savedRoutineDao.getAllRoutines()
    ) { sessions, weightLogs, routines ->
        sessions.isNotEmpty() || weightLogs.isNotEmpty() || routines.isNotEmpty()
    }

    /** How many weigh-ins exist, used to avoid logging a second first weigh-in. */
    suspend fun weightLogCount(): Int = bodyWeightDao.weightLogCount()
    suspend fun saveUserProfile(profile: UserProfile) = userProfileDao.insertOrUpdate(profile)

    // Saved Routines
    fun getAllSavedRoutines(): Flow<List<SavedRoutine>> = savedRoutineDao.getAllRoutines()
    suspend fun saveRoutine(routine: SavedRoutine): Long = savedRoutineDao.insertRoutine(routine)
    suspend fun deleteRoutine(routine: SavedRoutine) = savedRoutineDao.deleteRoutine(routine)

    // Body Weight
    fun getAllWeightLogs(): Flow<List<BodyWeightLog>> = bodyWeightDao.getAllWeightLogs()
    suspend fun insertWeightLog(log: BodyWeightLog): Long = bodyWeightDao.insertWeightLog(log)
    suspend fun deleteWeightLog(log: BodyWeightLog) = bodyWeightDao.deleteWeightLog(log)
    suspend fun deleteWeightLogById(id: Long) = bodyWeightDao.deleteWeightLogById(id)

    /**
     * Wipes every piece of user generated data (workout sessions, logged sets, saved routines,
     * weigh-ins, AI coach chat history, Gemini monthly summaries and the profile row).
     * The exercise library is intentionally left untouched so that exercises generated by the
     * AI parser (and the built-in library) survive the wipe.
     */
    suspend fun clearAllUserData() {
        workoutDao.deleteAllSets()
        workoutDao.deleteAllSessions()
        savedRoutineDao.deleteAllRoutines()
        bodyWeightDao.deleteAllWeightLogs()
        chatDao.deleteAllMessages()
        chatDao.deleteAllSessions()
        geminiSummaryDao.deleteAllSummaries()
        userProfileDao.deleteAllProfiles()
    }

    // Gemini monthly summaries (Progress tab history)
    fun getAllGeminiSummaries(): Flow<List<GeminiSummary>> = geminiSummaryDao.getAllSummaries()
    suspend fun getGeminiSummaryById(id: Long): GeminiSummary? = geminiSummaryDao.getSummaryById(id)
    suspend fun insertGeminiSummary(summary: GeminiSummary): Long = geminiSummaryDao.insertSummary(summary)
    suspend fun deleteGeminiSummary(summary: GeminiSummary) = geminiSummaryDao.deleteSummary(summary)

    // AI Coach Chat Sessions & Messages
    fun getAllChatSessions(): Flow<List<ChatSession>> = chatDao.getAllSessions()
    suspend fun getChatSessionById(id: String): ChatSession? = chatDao.getSessionById(id)
    suspend fun insertChatSession(session: ChatSession) = chatDao.insertSession(session)
    suspend fun updateChatSession(session: ChatSession) = chatDao.updateSession(session)
    suspend fun deleteChatSession(sessionId: String) {
        chatDao.deleteMessagesForSession(sessionId)
        chatDao.deleteSession(sessionId)
    }
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>> = chatDao.getMessagesForSession(sessionId)
    suspend fun getMessagesListForSession(sessionId: String): List<ChatMessageEntity> = chatDao.getMessagesListForSession(sessionId)
    suspend fun insertChatMessage(message: ChatMessageEntity) = chatDao.insertMessage(message)
    suspend fun insertChatMessages(messages: List<ChatMessageEntity>) = chatDao.insertMessages(messages)
}
