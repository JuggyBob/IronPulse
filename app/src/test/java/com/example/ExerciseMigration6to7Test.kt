package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import com.example.data.model.MuscleCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Upgrading a real v6 database to v7, which is what every existing install does on first launch.
 *
 * The one thing that must not break is history: logged sets reference exercises by id and repeat the
 * name, so the migration has to keep every row id intact while replacing the table's shape. If it
 * does not, the Progress page silently loses the athlete's training history.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExerciseMigration6to7Test {

    private lateinit var context: Context
    private var database: AppDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("migration6to7.db")
    }

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase("migration6to7.db")
    }

    /** Builds the exact v6 schema the previous app version shipped, with a little history in it. */
    private fun createVersion6Database() {
        val callback = object : SupportSQLiteOpenHelper.Callback(6) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `exercises` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`category` TEXT NOT NULL, `exerciseType` TEXT NOT NULL, " +
                        "`description` TEXT NOT NULL, `equipment` TEXT NOT NULL, " +
                        "`isUserCreated` INTEGER NOT NULL, `isAiGenerated` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_exercises_name` ON `exercises` (`name`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `logged_sets` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, " +
                        "`exerciseId` INTEGER NOT NULL, `exerciseName` TEXT NOT NULL, " +
                        "`exerciseType` TEXT NOT NULL, `setNumber` INTEGER NOT NULL, `weight` REAL NOT NULL, " +
                        "`addedWeight` REAL NOT NULL, `reps` INTEGER NOT NULL, " +
                        "`holdDurationSeconds` INTEGER NOT NULL, `isCompleted` INTEGER NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `workout_sessions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                        "`startTime` INTEGER NOT NULL, `endTime` INTEGER NOT NULL, `notes` TEXT NOT NULL)"
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
                        "`id` TEXT NOT NULL, `title` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "`lastUpdatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chat_messages` (" +
                        "`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `sender` TEXT NOT NULL, " +
                        "`text` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `gemini_summaries` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `exerciseName` TEXT NOT NULL, " +
                        "`report` TEXT NOT NULL, `windowDays` INTEGER NOT NULL, `daysLogged` INTEGER NOT NULL, " +
                        "`setCount` INTEGER NOT NULL, `bestOneRm` REAL NOT NULL, `trendPercent` REAL NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                // Part of the v6 schema (added by the 4 -> 5 migration), so it has to be present
                // here: this test only registers MIGRATION_6_7 and therefore never recreates it.
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_gemini_summaries_createdAt` " +
                        "ON `gemini_summaries` (`createdAt`)"
                )

                // A library exercise, one the user made, and one an older AI parse registered,
                // which is exactly the mix the ownership flags have to survive.
                db.execSQL(
                    "INSERT INTO `exercises` (`id`,`name`,`category`,`exerciseType`,`description`,`equipment`,`isUserCreated`,`isAiGenerated`) " +
                        "VALUES (1,'Barbell Bench Press','Chest','STANDARD_WEIGHT','Built-in','Barbell',0,0)"
                )
                db.execSQL(
                    "INSERT INTO `exercises` (`id`,`name`,`category`,`exerciseType`,`description`,`equipment`,`isUserCreated`,`isAiGenerated`) " +
                        "VALUES (2,'Z-Bar Curl','Arms','STANDARD_WEIGHT','Mine','EZ-Bar',1,0)"
                )
                db.execSQL(
                    "INSERT INTO `exercises` (`id`,`name`,`category`,`exerciseType`,`description`,`equipment`,`isUserCreated`,`isAiGenerated`) " +
                        "VALUES (3,'Legacy AI Move','Arms','PURE_BODYWEIGHT','From an older parse','Rings',0,1)"
                )
                db.execSQL(
                    "INSERT INTO `workout_sessions` (`id`,`title`,`startTime`,`endTime`,`notes`) " +
                        "VALUES (1,'Legacy Session',1000,2000,'before the upgrade')"
                )
                db.execSQL(
                    "INSERT INTO `logged_sets` (`sessionId`,`exerciseId`,`exerciseName`,`exerciseType`,`setNumber`,`weight`,`addedWeight`,`reps`,`holdDurationSeconds`,`isCompleted`,`timestamp`) " +
                        "VALUES (1,1,'Barbell Bench Press','STANDARD_WEIGHT',1,80.0,0.0,8,0,1,1500)"
                )
                db.execSQL(
                    "INSERT INTO `logged_sets` (`sessionId`,`exerciseId`,`exerciseName`,`exerciseType`,`setNumber`,`weight`,`addedWeight`,`reps`,`holdDurationSeconds`,`isCompleted`,`timestamp`) " +
                        "VALUES (1,2,'Z-Bar Curl','STANDARD_WEIGHT',1,25.0,0.0,10,0,1,1500)"
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name("migration6to7.db")
                .callback(callback)
                .build()
        )
        helper.writableDatabase.use { assertEquals(6, it.version) }
        helper.close()
    }

    /**
     * Opens the database at the current version, chaining the migrations exactly as the app does.
     *
     * Both steps are registered rather than just 6 to 7: a v6 install has to reach the current
     * version, and this test is the only place that whole path is exercised.
     */
    private fun openMigrated(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "migration6to7.db")
            .addMigrations(AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9)
            .allowMainThreadQueries()
            .build()
            .also { database = it }

    @Test
    fun `every exercise keeps its row id so logged history stays attached`() = runBlocking {
        createVersion6Database()
        val migrated = openMigrated()

        val all = migrated.exerciseDao().getAllExercisesList()
        assertEquals(3, all.size)
        assertEquals(
            listOf(1L, 2L, 3L),
            all.map { it.id }.sortedBy { it }
        )
        // The set logged against exercise 1 must still resolve to the bench press row
        val bench = all.first { it.id == 1L }
        assertEquals("Barbell Bench Press", bench.name)
    }

    @Test
    fun `logged history survives the upgrade intact`() = runBlocking {
        createVersion6Database()
        val migrated = openMigrated()

        val sets = migrated.workoutDao().getAllSets()
        assertEquals("no logged set may be lost", 2, sets.size)
        val benchSet = sets.first { it.exerciseName == "Barbell Bench Press" }
        assertEquals(1L, benchSet.exerciseId)
        assertEquals(80.0, benchSet.weight, 0.0001)
        assertEquals(8, benchSet.reps)
        assertEquals(ExerciseType.STANDARD_WEIGHT, benchSet.exerciseType)

        assertEquals(1, migrated.workoutDao().getAllSessions().first().size)
    }

    @Test
    fun `the isUserCreated flag becomes isCustom`() = runBlocking {
        createVersion6Database()
        val migrated = openMigrated()

        val byId = migrated.exerciseDao().getAllExercisesList().associateBy { it.id }
        assertFalse("the library stays locked", byId.getValue(1L).isCustom)
        assertTrue("the user's own exercise stays editable", byId.getValue(2L).isCustom)
        assertFalse("an AI registered exercise was never user created", byId.getValue(3L).isCustom)
        assertTrue("and it stays AI flagged", byId.getValue(3L).isAiGenerated)
    }

    @Test
    fun `the old description is kept so pre-existing exercises do not lose their text`() = runBlocking {
        createVersion6Database()
        val migrated = openMigrated()

        val byName = migrated.exerciseDao().getAllExercisesList().associateBy { it.name }
        assertEquals("Built-in", byName.getValue("Barbell Bench Press").description)
        assertEquals("Mine", byName.getValue("Z-Bar Curl").description)
    }

    @Test
    fun `upgraded rows start with empty dataset fields ready to be filled by the reseed`() = runBlocking {
        createVersion6Database()
        val migrated = openMigrated()

        for (row in migrated.exerciseDao().getAllExercisesList()) {
            assertEquals("", row.bodyPart)
            assertEquals("", row.target)
            assertEquals(emptyList<String>(), row.secondaryMuscleList)
            assertEquals("", row.mediaPath)
            assertEquals("", row.sourceId)
            // The 7 to 8 step adds the local media slot, empty for every pre-existing exercise.
            assertEquals("", row.customMediaUri)
            assertFalse(row.customMediaIsVideo)
            assertNotNull(row.exerciseType)
        }
    }

    @Test
    fun `logged sets start with no distance so old history is not invalidated`() = runBlocking {
        createVersion6Database()
        val migrated = openMigrated()

        val sets = migrated.workoutDao().getAllSets()
        assertEquals(2, sets.size)
        for (set in sets) {
            // 0 means "not recorded", which is the right reading for a pre-cardio set.
            assertEquals(0.0, set.distanceKm, 0.0)
            assertFalse(set.isDistanceBased)
        }
    }

    @Test
    fun `the upgraded catalog can still accept new custom exercises`() = runBlocking {
        createVersion6Database()
        val migrated = openMigrated()

        val id = migrated.exerciseDao().insert(
            Exercise(
                name = "Pelican Curl",
                category = MuscleCategory.ARMS,
                bodyPart = "upper arms",
                exerciseType = ExerciseType.PURE_BODYWEIGHT,
                equipment = "Rings",
                isCustom = true,
                isAiGenerated = true
            )
        )
        assertTrue(id > 0)
        val stored = migrated.exerciseDao().getEditableExerciseById(id)
        assertNotNull(stored)
        assertEquals("upper arms", stored!!.bodyPart)
    }
}
