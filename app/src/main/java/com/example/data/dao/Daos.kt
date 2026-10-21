package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Update
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

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name ASC")
    fun getAllExercises(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE category = :category ORDER BY name ASC")
    fun getExercisesByCategory(category: String): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE name LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchExercises(query: String): Flow<List<Exercise>>

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun getCount(): Int

    /** Exercises the user typed themselves (custom, but not registered by the AI parser). */
    @Query("SELECT * FROM exercises WHERE isCustom = 1 AND isAiGenerated = 0 ORDER BY name ASC")
    fun getUserCreatedExercises(): Flow<List<Exercise>>

    /** Exercises the AI parser registered on its own (the "ai_generated" search flag). */
    @Query("SELECT * FROM exercises WHERE isAiGenerated = 1 ORDER BY name ASC")
    fun getAiGeneratedExercises(): Flow<List<Exercise>>

    /** Every exercise the user is allowed to edit or delete (their own + the AI generated ones). */
    @Query("SELECT * FROM exercises WHERE id = :id AND (isCustom = 1 OR isAiGenerated = 1) LIMIT 1")
    suspend fun getEditableExerciseById(id: Long): Exercise?

    /** Kept for the user owned exercises only, see [getEditableExerciseById]. */
    @Query("SELECT * FROM exercises WHERE id = :id AND isCustom = 1 AND isAiGenerated = 0 LIMIT 1")
    suspend fun getUserCreatedExerciseById(id: Long): Exercise?

    @Query("SELECT name FROM exercises")
    suspend fun getAllExerciseNames(): List<String>

    /** Every exercise row, used by the dataset seeder to decide what to insert or update. */
    @Query("SELECT * FROM exercises")
    suspend fun getAllExercisesList(): List<Exercise>

    /** Finds an exercise by its dataset record id, or null when it was never imported. */
    @Query("SELECT * FROM exercises WHERE sourceId = :sourceId LIMIT 1")
    suspend fun getExerciseBySourceId(sourceId: String): Exercise?

    /** Finds an exercise by name (case insensitive), or null. */
    @Query("SELECT * FROM exercises WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) LIMIT 1")
    suspend fun getExerciseByName(name: String): Exercise?

    @Query("DELETE FROM exercises WHERE id NOT IN (SELECT MIN(id) FROM exercises GROUP BY LOWER(TRIM(name)))")
    suspend fun deduplicateByName()

    @Query("DELETE FROM exercises WHERE name IN (:names)")
    suspend fun deleteByNames(names: List<String>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(exercises: List<Exercise>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(exercise: Exercise): Long

    @Query("UPDATE exercises SET exerciseType = :exerciseType WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name))")
    suspend fun updateExerciseType(name: String, exerciseType: String)

    /** Every distinct exercise name currently stored, used by the AI parser prompts. */
    @Query("SELECT name FROM exercises ORDER BY name ASC")
    suspend fun getCatalogNames(): List<String>

    /** The library names the athlete already has history for, used to bias the AI parser. */
    @Query("SELECT DISTINCT exerciseName FROM logged_sets ORDER BY exerciseName ASC")
    suspend fun getLoggedExerciseNames(): List<String>

    /**
     * Updates an exercise the user owns (created themselves) or that the AI parser registered.
     * The guard makes it impossible to ever modify a built-in IronPulse library exercise,
     * no matter what the UI sends.
     * @return number of updated rows (0 when the exercise belongs to the built-in library).
     */
    @Query(
        """
        UPDATE exercises
        SET name = :name, category = :category, exerciseType = :exerciseType,
            equipment = :equipment, description = :description,
            instructions = :instructions, bodyPart = :bodyPart, target = :target,
            secondaryMuscles = :secondaryMuscles
        WHERE id = :id AND (isCustom = 1 OR isAiGenerated = 1)
        """
    )
    suspend fun updateEditableExercise(
        id: Long,
        name: String,
        category: String,
        exerciseType: String,
        equipment: String,
        description: String,
        instructions: String,
        bodyPart: String,
        target: String,
        secondaryMuscles: String
    ): Int

    /**
     * Full update of a catalog row, with no ownership guard.
     *
     * This exists because the shipped library is not always right, and an athlete who can see a
     * mistake on the detail screen had no way to fix it. Every field the editor offers is written
     * here, including the ones the guarded query above leaves alone, so a mislabelled exercise can be
     * corrected completely rather than partially.
     *
     * The guard is deliberately absent and the confirmation it requires lives in the UI: a guarded
     * query would make the library permanently immutable, which is the bug. The row id is never
     * touched, so logged sets keep pointing at the same exercise.
     *
     * @return number of updated rows (1 on success, 0 if the row vanished concurrently).
     */
    @Query(
        """
        UPDATE exercises
        SET name = :name, category = :category, exerciseType = :exerciseType,
            equipment = :equipment, description = :description,
            instructions = :instructions, bodyPart = :bodyPart, target = :target,
            secondaryMuscles = :secondaryMuscles
        WHERE id = :id
        """
    )
    suspend fun updateLibraryExercise(
        id: Long,
        name: String,
        category: String,
        exerciseType: String,
        equipment: String,
        description: String,
        instructions: String,
        bodyPart: String,
        target: String,
        secondaryMuscles: String
    ): Int

    /**
     * Sets or clears the photo / video the athlete attached to one of their own exercises.
     *
     * Guarded on ownership, like every other edit. The dataset rows are locked, so this can never
     * repoint a shipped exercise at local media (which the next catalog reseed would overwrite
     * anyway).
     *
     * @return number of updated rows (0 for a library exercise).
     */
    @Query(
        """
        UPDATE exercises
        SET customMediaUri = :uri, customMediaIsVideo = :isVideo
        WHERE id = :id AND (isCustom = 1 OR isAiGenerated = 1)
        """
    )
    suspend fun updateEditableMedia(id: Long, uri: String, isVideo: Boolean): Int

    /** Deletes an exercise the user owns or the AI registered (guarded, see updateEditableExercise). */
    @Query("DELETE FROM exercises WHERE id = :id AND (isCustom = 1 OR isAiGenerated = 1)")
    suspend fun deleteEditableExercise(id: Long): Int

    /**
     * Deletes every exercise the athlete created by hand.
     *
     * Part of "Revert to original database". The library rows are restored in place by the seeder, so
     * the row ids survive; these rows are the athlete's own and have no dataset record to restore.
     *
     * Logged sets are untouched by design: history is stored against exercise names, not ids, so a
     * deleted exercise's history stays readable rather than vanishing with the row.
     */
    @Query("DELETE FROM exercises WHERE isCustom = 1 AND isAiGenerated = 0")
    suspend fun deleteUserCreatedExercises(): Int

    /** Deletes every exercise the AI parser registered. Same reasoning as [deleteUserCreatedExercises]. */
    @Query("DELETE FROM exercises WHERE isCustom = 1 AND isAiGenerated = 1")
    suspend fun deleteAiCreatedExercises(): Int

    /** How many library rows exist, used to report what a revert restored. */
    @Query("SELECT COUNT(*) FROM exercises WHERE isCustom = 0 AND isAiGenerated = 0")
    suspend fun libraryExerciseCount(): Int

    /** How many rows the athlete created by hand, for the same report. */
    @Query("SELECT COUNT(*) FROM exercises WHERE isCustom = 1 AND isAiGenerated = 0")
    suspend fun userCreatedExerciseCount(): Int

    /** How many rows the AI parser registered, for the same report. */
    @Query("SELECT COUNT(*) FROM exercises WHERE isCustom = 1 AND isAiGenerated = 1")
    suspend fun aiCreatedExerciseCount(): Int

    /**
     * Flags an exercise as AI generated. Used to mark the exercises that older app versions
     * already stored without a flag, so the "ai_generated" search also finds them.
     * @return number of updated rows.
     */
    @Query(
        """
        UPDATE exercises
        SET isAiGenerated = 1
        WHERE id = :id AND isCustom = 0 AND isAiGenerated = 0
        """
    )
    suspend fun markAsAiGenerated(id: Long): Int

    /**
     * Refreshes the static catalog fields of a non custom exercise during a dataset reseed.
     * The row id is never touched, so logged sets keep pointing at the same exercise.
     * @return number of updated rows (0 when the exercise is custom and must be left alone).
     */
    @Query(
        """
        UPDATE exercises
        SET name = :name, category = :category, bodyPart = :bodyPart, target = :target,
            secondaryMuscles = :secondaryMuscles, exerciseType = :exerciseType,
            instructions = :instructions, equipment = :equipment, mediaPath = :mediaPath,
            sourceId = :sourceId
        WHERE id = :id AND isCustom = 0
        """
    )
    suspend fun updateDatasetExercise(
        id: Long,
        name: String,
        category: String,
        bodyPart: String,
        target: String,
        secondaryMuscles: String,
        exerciseType: String,
        instructions: String,
        equipment: String,
        mediaPath: String,
        sourceId: String
    ): Int

    /**
     * Applies a whole dataset reseed in one transaction.
     *
     * Atomicity matters: the catalog is observed live by the exercise list, so inserting row by
     * row would publish a half filled catalog several times over and the UI would show a partial
     * library that then silently grows. One transaction means observers only ever see the old
     * catalog or the complete new one.
     */
    @androidx.room.Transaction
    suspend fun applyDatasetPlan(inserts: List<Exercise>, updates: List<DatasetExerciseUpdate>) {
        if (inserts.isNotEmpty()) insertAll(inserts)
        for (u in updates) {
            updateDatasetExercise(
                id = u.id,
                name = u.name,
                category = u.category,
                bodyPart = u.bodyPart,
                target = u.target,
                secondaryMuscles = u.secondaryMuscles,
                exerciseType = u.exerciseType,
                instructions = u.instructions,
                equipment = u.equipment,
                mediaPath = u.mediaPath,
                sourceId = u.sourceId
            )
        }
    }
}

/** One row of [ExerciseDao.applyDatasetPlan], carrying the existing id it updates in place. */
data class DatasetExerciseUpdate(
    val id: Long,
    val name: String,
    val category: String,
    val bodyPart: String,
    val target: String,
    val secondaryMuscles: String,
    val exerciseType: String,
    val instructions: String,
    val equipment: String,
    val mediaPath: String,
    val sourceId: String
)

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workout_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<WorkoutSession>>

    /** Every logged set, used to recompute the athlete's benchmark personal records. */
    @Query("SELECT * FROM logged_sets")
    suspend fun getAllSets(): List<LoggedSet>

    /**
     * Every session with its sets.
     *
     * The activity heat map sums the training time and the lifted volume per day from this, so it
     * never needs a second query per session.
     */
    @androidx.room.Transaction
    @Query("SELECT * FROM workout_sessions ORDER BY startTime DESC")
    fun getAllSessionsWithSets(): Flow<List<SessionWithSets>>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): WorkoutSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: WorkoutSession): Long

    @Delete
    suspend fun deleteSession(session: WorkoutSession)

    /**
     * Deletes a workout session together with every logged set that belongs to it.
     * Without this, orphaned sets would keep showing up in the Progress page analytics.
     */
    @androidx.room.Transaction
    suspend fun deleteSessionWithSets(session: WorkoutSession) {
        deleteSetsBySessionId(session.id)
        deleteSession(session)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSets(sets: List<LoggedSet>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSet(set: LoggedSet): Long

    @Update
    suspend fun updateSet(set: LoggedSet)

    @Delete
    suspend fun deleteSet(set: LoggedSet)

    @Delete
    suspend fun deleteSets(sets: List<LoggedSet>)

    @Query("DELETE FROM logged_sets WHERE id = :id")
    suspend fun deleteSetById(id: Long)

    @Query("DELETE FROM logged_sets WHERE sessionId = :sessionId")
    suspend fun deleteSetsBySessionId(sessionId: Long)

    @Query("DELETE FROM logged_sets")
    suspend fun deleteAllSets()

    @Query("DELETE FROM workout_sessions")
    suspend fun deleteAllSessions()

    @Query("SELECT * FROM logged_sets WHERE sessionId = :sessionId ORDER BY id ASC")
    fun getSetsForSession(sessionId: Long): Flow<List<LoggedSet>>

    @Query("SELECT * FROM logged_sets WHERE exerciseName = :exerciseName ORDER BY timestamp ASC")
    fun getSetsForExercise(exerciseName: String): Flow<List<LoggedSet>>

    @Query("SELECT * FROM logged_sets WHERE exerciseName = :exerciseName ORDER BY timestamp ASC, id ASC")
    suspend fun getSetsForExerciseList(exerciseName: String): List<LoggedSet>

    @Query("SELECT * FROM logged_sets ORDER BY timestamp DESC")
    fun getAllCompletedSets(): Flow<List<LoggedSet>>

    @Query("SELECT DISTINCT exerciseName FROM logged_sets ORDER BY exerciseName ASC")
    fun getDistinctLoggedExerciseNames(): Flow<List<String>>

    @Query("SELECT DISTINCT addedWeight FROM logged_sets WHERE exerciseName = :exerciseName ORDER BY addedWeight ASC")
    fun getHistoricalAddedWeights(exerciseName: String): Flow<List<Double>>

    @Query("UPDATE logged_sets SET exerciseType = (SELECT exerciseType FROM exercises WHERE exercises.name = logged_sets.exerciseName) WHERE EXISTS (SELECT 1 FROM exercises WHERE exercises.name = logged_sets.exerciseName)")
    suspend fun syncLoggedSetExerciseTypes()
}

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    fun getProfile(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    suspend fun getProfileDirect(): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(profile: UserProfile)

    @Query("DELETE FROM user_profile")
    suspend fun deleteAllProfiles()
}

@Dao
interface SavedRoutineDao {
    @Query("SELECT * FROM saved_routines ORDER BY createdAt DESC")
    fun getAllRoutines(): Flow<List<SavedRoutine>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(routine: SavedRoutine): Long

    @Delete
    suspend fun deleteRoutine(routine: SavedRoutine)

    @Query("DELETE FROM saved_routines")
    suspend fun deleteAllRoutines()

    /**
     * The routines whose days are still the old numbered training days.
     *
     * Empty once the migration has run, which is what makes it cheap to ask on every open.
     */
    @Query("SELECT * FROM saved_routines WHERE scheduleVersion < 1")
    suspend fun getRoutinesNeedingScheduleMigration(): List<SavedRoutine>

    /**
     * Writes the migrated days of a routine back.
     *
     * The name, the description and the goal are not touched: the migration only binds the days.
     */
    @Query(
        "UPDATE saved_routines SET exercisesJson = :exercisesJson, daysPerWeek = :daysPerWeek, " +
            "scheduleVersion = :scheduleVersion WHERE id = :id"
    )
    suspend fun updateRoutineSchedule(
        id: Long,
        exercisesJson: String,
        daysPerWeek: Int,
        scheduleVersion: Int
    )
}

@Dao
interface BodyWeightDao {
    @Query("SELECT * FROM body_weight_logs ORDER BY timestamp ASC")
    fun getAllWeightLogs(): Flow<List<BodyWeightLog>>

    @Query("SELECT COUNT(*) FROM body_weight_logs")
    suspend fun weightLogCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeightLog(log: BodyWeightLog): Long

    @Delete
    suspend fun deleteWeightLog(log: BodyWeightLog)

    @Query("DELETE FROM body_weight_logs WHERE id = :id")
    suspend fun deleteWeightLogById(id: Long)

    @Query("DELETE FROM body_weight_logs")
    suspend fun deleteAllWeightLogs()
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_sessions ORDER BY lastUpdatedAt DESC")
    fun getAllSessions(): Flow<List<ChatSession>>

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): ChatSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSession)

    @Update
    suspend fun updateSession(session: ChatSession)

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesListForSession(sessionId: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String)

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAllMessages()

    @Query("DELETE FROM chat_sessions")
    suspend fun deleteAllSessions()
}

@Dao
interface GeminiSummaryDao {
    @Query("SELECT * FROM gemini_summaries ORDER BY createdAt DESC")
    fun getAllSummaries(): Flow<List<GeminiSummary>>

    @Query("SELECT * FROM gemini_summaries WHERE id = :id LIMIT 1")
    suspend fun getSummaryById(id: Long): GeminiSummary?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummary(summary: GeminiSummary): Long

    @Delete
    suspend fun deleteSummary(summary: GeminiSummary)

    @Query("DELETE FROM gemini_summaries")
    suspend fun deleteAllSummaries()
}

