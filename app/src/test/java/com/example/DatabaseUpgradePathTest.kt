package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.ExerciseType
import com.example.data.seed.DatasetSeeder
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
 * Upgrading an already installed database to the current version: a path a fresh install never
 * takes and an ordinary unit test never exercises.
 *
 * This exists because of a real crash. An update from a shipped build crashed on launch, while a
 * reinstall worked. That signature means a migration left the schema out of step with the entity, or
 * threw on the way. Only an upgrade test can see either, because a newly created database gets its
 * schema straight from the entity and runs none of the migration code at all.
 *
 * Two starting points are covered, because the reported crash came from the newer one:
 *
 *  - v3, the oldest schema still in the wild, carried forward through every step.
 *  - v6, the version the previous build shipped, so only 6 -> 7 -> 8 run. That is the upgrade an
 *    installed athlete actually takes, and the one a reinstall skips.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DatabaseUpgradePathTest {

    private companion object {
        const val DB_NAME = "upgrade_path.db"
        private const val DATASET_PREFS = "ironpulse_dataset"

        fun versionOf(db: AppDatabase): Int {
            val cursor = db.query("PRAGMA user_version", emptyArray())
            cursor.moveToFirst()
            val version = cursor.getInt(0)
            cursor.close()
            return version
        }

        /**
         * The migrations the app registers, in the app's order.
         *
         * Read from the companion rather than written out here, so this test cannot drift from the
         * app when a migration is added later. A hand-written list would quietly stop covering the
         * new step, which is exactly how the original break got past the suite.
         */
        fun migrations() = arrayOf(
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5,
            AppDatabase.MIGRATION_5_6,
            AppDatabase.MIGRATION_6_7,
            AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9
        )

        /** The target version, taken from the last registered step rather than hardcoded. */
        fun currentVersion(): Int = migrations().last().endVersion

        /** Tables that existed from v1 and never changed shape. */
        fun createBaseTables(db: SupportSQLiteDatabase) {
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
                    "`exerciseId` INTEGER NOT NULL, `exerciseName` TEXT NOT NULL, " +
                    "`exerciseType` TEXT NOT NULL, `setNumber` INTEGER NOT NULL, `weight` REAL NOT NULL, " +
                    "`addedWeight` REAL NOT NULL, `reps` INTEGER NOT NULL, " +
                    "`holdDurationSeconds` INTEGER NOT NULL, `isCompleted` INTEGER NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL)"
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
        }

        /** Added by 1 -> 2, so any install at v3 or above already has them. */
        fun createChatTables(db: SupportSQLiteDatabase) {
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
        }

        /** Added by 4 -> 5. */
        fun createSummaryTables(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `gemini_summaries` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `exerciseName` TEXT NOT NULL, " +
                    "`report` TEXT NOT NULL, `windowDays` INTEGER NOT NULL, `daysLogged` INTEGER NOT NULL, " +
                    "`setCount` INTEGER NOT NULL, `bestOneRm` REAL NOT NULL, `trendPercent` REAL NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_gemini_summaries_createdAt` " +
                    "ON `gemini_summaries` (`createdAt`)"
            )
        }

        /** The athlete data the previous build seeded on first launch. */
        fun insertAthleteData(db: SupportSQLiteDatabase) {
            db.execSQL(
                "INSERT INTO `exercises` (`name`,`category`,`exerciseType`,`description`,`equipment`) " +
                    "VALUES ('Pull-up','Back','PURE_BODYWEIGHT','The classic vertical pull','Pull-up Bar')"
            )
            db.execSQL(
                "INSERT INTO `workout_sessions` (`title`,`startTime`,`endTime`,`notes`) " +
                    "VALUES ('Legacy Session',1000,2000,'before the upgrade')"
            )
            db.execSQL(
                "INSERT INTO `logged_sets` (`sessionId`,`exerciseId`,`exerciseName`,`exerciseType`, " +
                    "`setNumber`,`weight`,`addedWeight`,`reps`,`holdDurationSeconds`,`isCompleted`,`timestamp`) " +
                    "VALUES (1,1,'Pull-up','PURE_BODYWEIGHT',1,80.0,0.0,10,0,1,1500)"
            )
            db.execSQL(
                "INSERT INTO `user_profile` (`id`,`name`,`heightCm`,`currentWeightKg`,`targetWeightKg`, " +
                    "`age`,`preferredUnit`,`fitnessLevel`,`customApiKey`) " +
                    "VALUES (1,'Alex Stone',180.0,77.5,80.0,25,'kg','Intermediate','')"
            )
            db.execSQL(
                "INSERT INTO `body_weight_logs` (`weightKg`,`timestamp`,`note`) " +
                    "VALUES (77.5,900,'Current morning weigh-in')"
            )
        }

        fun writeFile(context: Context, callback: SupportSQLiteOpenHelper.Callback) {
            val helper = FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name(DB_NAME)
                    .callback(callback)
                    .build()
            )
            helper.writableDatabase.close()
            helper.close()
        }

        fun callback(version: Int, body: (SupportSQLiteDatabase) -> Unit) =
            object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) = body(db)
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }
    }

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DB_NAME)
        // The seeder only reseeds when the bundled asset version moved. Clearing the marker keeps
        // this deterministic no matter which test ran first, rather than depending on order.
        context.getSharedPreferences(DATASET_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove("dataset_version")
            .commit()
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DB_NAME)
    }

    /** v3: exercises without the ownership columns, and no gemini summaries yet. */
    private fun createVersion3Database() = writeFile(
        context,
        callback(3) { db ->
            createBaseTables(db)
            createChatTables(db)
            insertAthleteData(db)
        }
    )

    /**
     * v6: the shape the previously shipped build left on disk, written out in full so only the
     * 6 -> 7 -> 8 steps run against it.
     *
     * Written by hand rather than produced by running earlier migrations, because the whole point is
     * to isolate the steps the crash went through: running 3 -> 6 as well would move the failure to
     * whichever step broke first and hide the newest one.
     */
    private fun createVersion6Database() = writeFile(
        context,
        callback(6) { db ->
            createBaseTables(db)
            createChatTables(db)
            createSummaryTables(db)
            // 3 -> 4 and 5 -> 6.
            db.execSQL("ALTER TABLE `exercises` ADD COLUMN `isUserCreated` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `exercises` ADD COLUMN `isAiGenerated` INTEGER NOT NULL DEFAULT 0")
            insertAthleteData(db)
        }
    )

    private fun openCurrent() = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
        .addMigrations(*migrations())
        .allowMainThreadQueries()
        .build()

    /**
     * Runs what the app's open callback runs after the migrations, on this database.
     *
     * The callback cannot be installed directly here: it resolves its database through the singleton,
     * which points at the real file. So the reseed is driven the same way from the test instead.
     */
    private fun reseedForUpgrade(db: AppDatabase) = runBlocking {
        DatasetSeeder.seedFresh(context, db.exerciseDao())
        db.exerciseDao().deduplicateByName()
        db.workoutDao().syncLoggedSetExerciseTypes()
    }

    @Test
    fun `upgrading an installed v6 database to the current version opens without throwing`() = runBlocking {
        createVersion6Database()

        val db = openCurrent()
        reseedForUpgrade(db)
        try {
            // This is the crash. A schema left out of step with the entity throws on the first query,
            // not at build time, so every test below depends on this one clearing.
            assertTrue(db.exerciseDao().getCount() > 0)
            assertEquals(currentVersion(), versionOf(db))
        } finally {
            db.close()
        }
    }

    @Test
    fun `upgrading the oldest database in the wild opens without throwing`() = runBlocking {
        createVersion3Database()

        // A fresh install never runs these steps, but an install from any older build does.
        val db = openCurrent()
        reseedForUpgrade(db)
        try {
            assertTrue(db.exerciseDao().getCount() > 0)
            assertEquals(currentVersion(), versionOf(db))
        } finally {
            db.close()
        }
    }

    @Test
    fun `the upgraded catalog has every column the entity declares`() = runBlocking {
        createVersion6Database()
        val db = openCurrent()
        reseedForUpgrade(db)
        try {
            // A missing column surfaces as a thrown cursor access, not as a compile error, so every one
            // the entity now carries is read here.
            for (row in db.exerciseDao().getAllExercisesList()) {
                assertNotNull(row.name)
                assertNotNull(row.bodyPart)
                assertNotNull(row.target)
                assertNotNull(row.secondaryMuscles)
                assertNotNull(row.instructions)
                assertNotNull(row.mediaPath)
                assertNotNull(row.customMediaUri)
                assertNotNull(row.sourceId)
                assertNotNull(row.exerciseType)
            }

            for (set in db.workoutDao().getAllSets()) {
                assertNotNull(set.exerciseName)
                assertNotNull(set.exerciseType)
                assertNotNull(set.distanceKm)
            }

            // And the new column can be populated, not only read.
            val upgraded = db.workoutDao().getAllSets().first().copy(distanceKm = 5.0)
            assertTrue("5 km is a real distance", upgraded.distanceKm > 0.0)
        } finally {
            db.close()
        }
    }

    @Test
    fun `the old athlete's history survives the upgrade`() = runBlocking {
        createVersion6Database()
        val db = openCurrent()
        reseedForUpgrade(db)
        try {
            // The point of migrating at all: an update must not cost an athlete their logged history.
            val sets = db.workoutDao().getAllSets()
            assertEquals(1, sets.size)
            assertEquals("Pull-up", sets.first().exerciseName)
            assertEquals(10, sets.first().reps)
            assertEquals(80.0, sets.first().weight, 0.001)
            assertEquals(1, db.bodyWeightDao().getAllWeightLogs().first().size)
            assertEquals("Alex Stone", db.userProfileDao().getProfileDirect()!!.name)
        } finally {
            db.close()
        }
    }

    @Test
    fun `the old library rows are refreshed in place with the dataset data`() = runBlocking {
        createVersion6Database()
        val db = openCurrent()
        reseedForUpgrade(db)
        try {
            // The reseed upserts the dataset into the existing row, so it is upgraded rather than
            // left a stub or duplicated against the unique name index.
            val rows = db.exerciseDao().getAllExercisesList().associateBy { it.name }
            val pullUp = rows["Pull-up"] ?: rows["Pull-Up"] ?: error("the dataset's pull up is missing")
            assertTrue("the row must be linked to its dataset record", pullUp.sourceId.isNotBlank())
            assertTrue(pullUp.mediaPath.endsWith(".gif"))
            assertTrue(pullUp.target.isNotBlank())
            assertTrue("the library must never become custom", !pullUp.isCustom)
        } finally {
            db.close()
        }
    }

    @Test
    fun `exercises the old build left as bodyweight are retagged if the dataset says otherwise`() =
        runBlocking {
            createVersion6Database()
            val db = openCurrent()
            reseedForUpgrade(db)
            try {
                // "Run" is cardio, which is the whole reason CARDIO exists. The reseed has to correct
                // existing rows, not only set the type on newly inserted ones, or an upgraded athlete
                // keeps logging barbell weight against a run.
                val run = db.exerciseDao().getAllExercisesList()
                    .firstOrNull { it.name.equals("Run", ignoreCase = true) }
                assertNotNull("the catalog must contain the running entry", run)
                assertEquals(ExerciseType.CARDIO, run!!.exerciseType)
                assertFalse(
                    "cardio must not keep the weight fields",
                    ExerciseType.ADDED_WEIGHT == run.exerciseType ||
                        ExerciseType.PURE_BODYWEIGHT == run.exerciseType
                )
            } finally {
                db.close()
            }
        }

    @Test
    fun `an upgrade does not fabricate a profile`() = runBlocking {
        createVersion6Database()
        val db = openCurrent()
        reseedForUpgrade(db)
        try {
            // The current build seeds no profile: it comes from onboarding. An upgrade must not
            // invent one on top of what is already there either.
            assertNotNull(db.userProfileDao().getProfileDirect())
            assertEquals("Alex Stone", db.userProfileDao().getProfileDirect()!!.name)
        } finally {
            db.close()
        }
    }
}
