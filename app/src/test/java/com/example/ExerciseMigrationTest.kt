package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.ExerciseDao
import com.example.data.db.AppDatabase
import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import com.example.data.model.MuscleCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The custom exercise feature added the `isUserCreated` column to the exercises table
 * (database version 3 -> 4) and the AI generated feature added `isAiGenerated` (5 -> 6).
 * Existing installs must upgrade without losing their library.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExerciseMigrationTest {

    private var database: AppDatabase? = null

    @After
    fun tearDown() {
        database?.close()
    }

    @Test
    fun `migration from version 3 keeps the existing exercises and marks none as user created`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_test_ironpulse.db"
        context.deleteDatabase(dbName)

        // 1. Create a version 3 database (the schema shipped before custom exercises existed)
        val legacyCallback = object : SupportSQLiteOpenHelper.Callback(3) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `exercises` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`category` TEXT NOT NULL, " +
                        "`exerciseType` TEXT NOT NULL, " +
                        "`description` TEXT NOT NULL, " +
                        "`equipment` TEXT NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_exercises_name` ON `exercises` (`name`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `workout_sessions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                        "`startTime` INTEGER NOT NULL, `endTime` INTEGER NOT NULL, `notes` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `logged_sets` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, " +
                        "`exerciseId` INTEGER NOT NULL, `exerciseName` TEXT NOT NULL, `exerciseType` TEXT NOT NULL, " +
                        "`setNumber` INTEGER NOT NULL, `weight` REAL NOT NULL, `addedWeight` REAL NOT NULL, " +
                        "`reps` INTEGER NOT NULL, `holdDurationSeconds` INTEGER NOT NULL, " +
                        "`isCompleted` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `user_profile` (" +
                        "`id` INTEGER PRIMARY KEY NOT NULL, `name` TEXT NOT NULL, `heightCm` REAL NOT NULL, " +
                        "`currentWeightKg` REAL NOT NULL, `targetWeightKg` REAL NOT NULL, `age` INTEGER NOT NULL, " +
                        "`preferredUnit` TEXT NOT NULL, `fitnessLevel` TEXT NOT NULL, `customApiKey` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `saved_routines` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`description` TEXT NOT NULL, `daysPerWeek` INTEGER NOT NULL, `goal` TEXT NOT NULL, " +
                        "`exercisesJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `body_weight_logs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `weightKg` REAL NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL, `note` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chat_sessions` (" +
                        "`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `lastUpdatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chat_messages` (" +
                        "`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `sender` TEXT NOT NULL, " +
                        "`text` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )

                db.execSQL("INSERT INTO `exercises` (`name`,`category`,`exerciseType`,`description`,`equipment`) VALUES ('Barbell Bench Press','Chest','STANDARD_WEIGHT','Built-in','Barbell')")
                db.execSQL("INSERT INTO `workout_sessions` (`title`,`startTime`,`endTime`,`notes`) VALUES ('Legacy Session',1,2,'')")
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val legacyHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(legacyCallback)
                .build()
        )
        legacyHelper.writableDatabase.use { legacyDb ->
            assertEquals(3, legacyDb.version)
        }
        legacyHelper.close()

        // 2. Open it with the current database: the 3 -> 4 -> 5 -> 6 migrations must run automatically
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
    AppDatabase.MIGRATION_9_10
            )
            .allowMainThreadQueries()
            .build()
        database = migrated

        val exerciseDao: ExerciseDao = migrated.exerciseDao()
        val exercises = runBlocking { exerciseDao.getAllExercises().first() }

        // The pre-existing row survived and is treated as part of the built-in library
        assertEquals(1, exercises.size)
        assertEquals("Barbell Bench Press", exercises.first().name)
        assertEquals(false, exercises.first().isCustom)
        assertEquals(false, exercises.first().isAiGenerated)
        assertTrue(runBlocking { exerciseDao.getUserCreatedExercises().first() }.isEmpty())
        assertTrue(runBlocking { exerciseDao.getAiGeneratedExercises().first() }.isEmpty())
        // The previously logged history is untouched by the migration
        assertEquals(1, runBlocking { migrated.workoutDao().getAllSessions().first() }.size)
        // The summaries table added by the 4 -> 5 migration exists and is empty
        assertTrue(runBlocking { migrated.geminiSummaryDao().getAllSummaries().first() }.isEmpty())
        runBlocking {
            val summaryId = migrated.geminiSummaryDao().insertSummary(
                com.example.data.model.GeminiSummary(
                    exerciseName = "Barbell Bench Press",
                    report = "# Report"
                )
            )
            assertTrue(summaryId > 0)
            assertEquals(1, migrated.geminiSummaryDao().getAllSummaries().first().size)
        }

        // The new columns are usable: a user created and an AI generated exercise can be added
        runBlocking {
            val userId = exerciseDao.insert(
                Exercise(
                    name = "Z-Bar Curl",
                    category = MuscleCategory.ARMS,
                    exerciseType = ExerciseType.STANDARD_WEIGHT,
                    equipment = "EZ-Bar",
                    isCustom = true
                )
            )
            assertTrue(userId > 0)
            val stored = exerciseDao.getUserCreatedExerciseById(userId)
            assertEquals("Z-Bar Curl", stored?.name)
            assertEquals(true, stored?.isCustom)
            assertEquals(false, stored?.isAiGenerated)

            val aiId = exerciseDao.insert(
                Exercise(
                    name = "Pelican Curl",
                    category = MuscleCategory.ARMS,
                    exerciseType = ExerciseType.PURE_BODYWEIGHT,
                    equipment = "Rings",
                    isAiGenerated = true
                )
            )
            assertTrue(aiId > 0)
            val aiStored = exerciseDao.getEditableExerciseById(aiId)
            assertEquals("Pelican Curl", aiStored?.name)
            assertEquals(true, aiStored?.isAiGenerated)
            assertEquals(false, aiStored?.isCustom)

            // The pre-existing row keeps both flags off, so it stays locked
            assertEquals(1, exerciseDao.updateEditableExercise(userId, "Angled Bar Curl", MuscleCategory.ARMS, ExerciseType.STANDARD_WEIGHT, "EZ-Bar", "Edited", "", "", "", "[]"))
            assertEquals("Angled Bar Curl", exerciseDao.getEditableExerciseById(userId)?.name)
            assertEquals(0, exerciseDao.updateEditableExercise(1L, "Hacked", MuscleCategory.CHEST, ExerciseType.STANDARD_WEIGHT, "Barbell", "", "", "", "", "[]"))
            assertEquals(0, exerciseDao.deleteEditableExercise(1L))
        }

        context.deleteDatabase(dbName)
    }
}
