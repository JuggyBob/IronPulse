package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.BodyWeightLog
import com.example.data.model.ExerciseType
import com.example.data.model.LoggedSet
import com.example.data.model.WorkoutSession
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
            AppDatabase.MIGRATION_8_9,
            AppDatabase.MIGRATION_9_10,
            AppDatabase.MIGRATION_10_11,
            AppDatabase.MIGRATION_11_12
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
            // Migration 10 -> 11 fills the body weight snapshot from the latest weigh-in at or
            // before the workout ended (77.5 kg at t=900, session end t=2000).
            assertEquals(77.5, sets.first().bodyWeightKg, 0.001)
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

    /**
     * The 10 -> 11 step on its own.
     *
     * The v6 test above also runs it, but only as the tail of a long chain; this isolates the
     * backfill so a failure points at the body weight snapshot instead of any earlier migration.
     * The database is built at the current schema, filled the way the previous release would have
     * left it (bodyWeightKg = 0), then stamped back to version 10 so Room takes the real upgrade.
     */
    @Test
    fun `migration 10 to 11 stamps the historical body weight on old sets`() = runBlocking {
        val created = openCurrent()
        created.workoutDao().insertSession(
            WorkoutSession(title = "Old Pull Day", startTime = 1000, endTime = 2000)
        )
        created.workoutDao().insertSet(
            LoggedSet(
                sessionId = 1,
                exerciseId = 1,
                exerciseName = "Pull-up",
                exerciseType = ExerciseType.PURE_BODYWEIGHT,
                setNumber = 1,
                reps = 10,
                timestamp = 1500,
                bodyWeightKg = 0.0
            )
        )
        created.bodyWeightDao().insertWeightLog(
            BodyWeightLog(weightKg = 81.5, timestamp = 900, note = "Morning weigh-in")
        )
        created.close()

        // What the shipped v2.0.1 database looked like: same tables, version 10, no snapshots.
        val file = context.getDatabasePath(DB_NAME)
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
            raw.execSQL("PRAGMA user_version = 10")
        }

        val upgraded = openCurrent()
        try {
            // The 10 -> 11 step is isolated by stamping 10, but every later migration still runs, so
            // the version is read from the registered chain instead of being pinned to 11.
            assertEquals(currentVersion(), versionOf(upgraded))
            val set = upgraded.workoutDao().getAllSets().single()
            assertEquals(81.5, set.bodyWeightKg, 0.001)
            // The set's own load is untouched: only the snapshot is filled.
            assertEquals(0.0, set.weight, 0.001)
            assertEquals(10, set.reps)
        } finally {
            upgraded.close()
        }
    }

    /**
     * The 11 -> 12 step on its own: the hot-query indexes.
     *
     * The database is created at the current schema, the new indexes are dropped and the version is
     * stamped back to 11. Reopening must recreate them, because Room validates the entity's declared
     * indexes at open time and would otherwise throw on the first query.
     */
    @Test
    fun `migration 11 to 12 adds the hot query indexes`() = runBlocking {
        val created = openCurrent()
        created.workoutDao().insertSession(
            WorkoutSession(title = "Indexed Session", startTime = 1000, endTime = 2000)
        )
        created.workoutDao().insertSet(
            LoggedSet(
                sessionId = 1,
                exerciseId = 1,
                exerciseName = "Pull-up",
                exerciseType = ExerciseType.PURE_BODYWEIGHT,
                setNumber = 1,
                reps = 8,
                timestamp = 1500
            )
        )
        created.close()

        val expected = listOf(
            "index_workout_sessions_startTime",
            "index_logged_sets_sessionId",
            "index_logged_sets_exerciseName",
            "index_logged_sets_timestamp",
            "index_saved_routines_createdAt",
            "index_body_weight_logs_timestamp",
            "index_chat_sessions_lastUpdatedAt",
            "index_chat_messages_sessionId",
            "index_chat_messages_timestamp"
        )

        val file = context.getDatabasePath(DB_NAME)
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
            expected.forEach { raw.execSQL("DROP INDEX IF EXISTS `$it`") }
            raw.execSQL("PRAGMA user_version = 11")
        }

        val upgraded = openCurrent()
        try {
            assertEquals(12, versionOf(upgraded))
            val indexes = mutableSetOf<String>()
            upgraded.query("SELECT name FROM sqlite_master WHERE type = 'index'", emptyArray()).use { cursor ->
                while (cursor.moveToNext()) indexes.add(cursor.getString(0))
            }
            expected.forEach { index ->
                assertTrue("migration 11 -> 12 must create $index", index in indexes)
            }

            // The index must cover the column the DAO actually filters on, not merely exist.
            val columns = mutableListOf<String>()
            upgraded.query("PRAGMA index_info(`index_logged_sets_sessionId`)", emptyArray()).use { cursor ->
                while (cursor.moveToNext()) columns.add(cursor.getString(2))
            }
            assertEquals(listOf("sessionId"), columns)

            // The data the athlete already had is still there after the index rebuild.
            assertEquals(1, upgraded.workoutDao().getAllSets().size)
            assertEquals("Indexed Session", upgraded.workoutDao().getAllSessions().first().single().title)
        } finally {
            upgraded.close()
        }
    }
}
