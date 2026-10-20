package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.BodyWeightDao
import com.example.data.dao.ChatDao
import com.example.data.dao.ExerciseDao
import com.example.data.dao.GeminiSummaryDao
import com.example.data.dao.SavedRoutineDao
import com.example.data.dao.UserProfileDao
import com.example.data.dao.WorkoutDao
import com.example.data.model.BodyWeightLog
import com.example.data.model.ChatMessageEntity
import com.example.data.model.ChatSession
import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import com.example.data.model.GeminiSummary
import com.example.data.model.LoggedSet
import com.example.data.model.SavedRoutine
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutSession
import com.example.data.seed.DatasetSeeder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Database(
    entities = [
        Exercise::class,
        WorkoutSession::class,
        LoggedSet::class,
        UserProfile::class,
        SavedRoutine::class,
        BodyWeightLog::class,
        ChatSession::class,
        ChatMessageEntity::class,
        GeminiSummary::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun savedRoutineDao(): SavedRoutineDao
    abstract fun bodyWeightDao(): BodyWeightDao
    abstract fun chatDao(): ChatDao
    abstract fun geminiSummaryDao(): GeminiSummaryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chat_sessions` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `lastUpdatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chat_messages` (
                        `id` TEXT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `sender` TEXT NOT NULL,
                        `text` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Deduplicate any existing exercise rows by lowercased name before creating unique index
                db.execSQL("DELETE FROM exercises WHERE id NOT IN (SELECT MIN(id) FROM exercises GROUP BY LOWER(TRIM(name)))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_exercises_name` ON `exercises` (`name`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Flags exercises created by the user so they can be edited/deleted,
                // while built-in and AI generated exercises stay locked.
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `isUserCreated` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `gemini_summaries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `exerciseName` TEXT NOT NULL,
                        `report` TEXT NOT NULL,
                        `windowDays` INTEGER NOT NULL,
                        `daysLogged` INTEGER NOT NULL,
                        `setCount` INTEGER NOT NULL,
                        `bestOneRm` REAL NOT NULL,
                        `trendPercent` REAL NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gemini_summaries_createdAt` ON `gemini_summaries` (`createdAt`)")
            }
        }

        /**
         * Separates the exercises the AI parser registered on its own from the built-in library,
         * so they can be found with the "ai_generated" search flag and be edited or deleted too.
         *
         * Existing rows keep 0 (= built-in library), which is exactly what they are: every row that
         * existed before this version was either the built-in library or something the user typed.
         * Rows created by an older AI parse are re-flagged by MainViewModel.backfillAiGeneratedExercises.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `isAiGenerated` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Brings the exercise table to the dataset schema: body part, target, secondary muscles,
         * instructions, media path and the dataset record id, plus the isCustom flag that replaces
         * isUserCreated.
         *
         * The table is rebuilt rather than altered because the old isUserCreated column has to go,
         * and SQLite's ADD COLUMN always forces a DEFAULT that Room then rejects as a schema
         * mismatch (it expects no column default, exactly like a freshly created table).
         * The rebuild keeps every row id, so already logged sets are never orphaned, and copies
         * isUserCreated over into isCustom so exercises the user created stay editable.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `exercises_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `bodyPart` TEXT NOT NULL,
                        `target` TEXT NOT NULL,
                        `secondaryMuscles` TEXT NOT NULL,
                        `exerciseType` TEXT NOT NULL,
                        `instructions` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `equipment` TEXT NOT NULL,
                        -- Declared with a default on purpose: the entity annotates this column
                        -- @ColumnInfo(defaultValue = ""), and Room compares column defaults when it
                        -- validates a migrated schema. Without the matching DEFAULT here, every
                        -- upgrade would be rejected as "migration didn't properly handle".
                        `mediaPath` TEXT NOT NULL DEFAULT '',
                        `sourceId` TEXT NOT NULL,
                        `isCustom` INTEGER NOT NULL,
                        `isAiGenerated` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `exercises_new` (
                        `id`, `name`, `category`, `bodyPart`, `target`, `secondaryMuscles`,
                        `exerciseType`, `instructions`, `description`, `equipment`, `mediaPath`,
                        `sourceId`, `isCustom`, `isAiGenerated`
                    )
                    SELECT
                        `id`, `name`, `category`, '', '', '[]',
                        `exerciseType`, `description`, `description`, `equipment`, '', '',
                        `isUserCreated`, `isAiGenerated`
                    FROM `exercises`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `exercises`")
                db.execSQL("ALTER TABLE `exercises_new` RENAME TO `exercises`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_exercises_name` ON `exercises` (`name`)")
            }
        }

        /**
         * Adds the local media slot for exercises the athlete owns: the picked uri, and whether it is
         * a video.
         *
         * The video flag is stored rather than inferred from the uri because a content:// uri from the
         * gallery picker usually has no file extension to infer from, and guessing wrong would try to
         * decode a video as a still image and show nothing.
         *
         * A plain ADD COLUMN works here only because the entity declares the same defaults: Room
         * compares column defaults during validation, so each column has to agree with both the
         * entity and the SQL. Row ids are untouched, which matters because logged sets point at them.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `exercises` ADD COLUMN `customMediaUri` TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE `exercises` ADD COLUMN `customMediaIsVideo` INTEGER NOT NULL DEFAULT 0"
                )
                // Distance for cardio sets. Existing rows default to 0, which reads as "not
                // recorded" rather than a bogus zero distance, so no old set is invalidated.
                db.execSQL(
                    "ALTER TABLE `logged_sets` ADD COLUMN `distanceKm` REAL NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * Records the body weight that applied when each set was logged.
         *
         * Without it a bodyweight set has no weight of its own: the app reads the athlete's current
         * weight, so weighing in today rewrote what last month's pull-ups weighed. The snapshot
         * is what makes the isolated weight tag stable.
         *
         * Existing rows default to 0, which reads as "not recorded" and falls back to the current
         * profile weight. That is the best guess for a set logged before the snapshot existed, and
         * it never invents a number.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `logged_sets` ADD COLUMN `bodyWeightKg` REAL NOT NULL DEFAULT 0"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ironpulse_database"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9
                    )
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .addCallback(DatabasePrepopulationCallback(context))
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

        /**
         * The callback the app installs, exposed so a test can open the database exactly the way the
         * app does. A test that builds its own builder skips the reseed, which is where the upgrade
         * path is actually decided.
         */
        fun prepopulationCallback(context: Context): RoomDatabase.Callback =
            DatabasePrepopulationCallback(context)

    private class DatabasePrepopulationCallback(
        private val context: Context
    ) : RoomDatabase.Callback() {
        private val dbInitMutex = Mutex()
        @Volatile
        private var freshlyCreated = false

        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            freshlyCreated = true
            CoroutineScope(Dispatchers.IO).launch {
                dbInitMutex.withLock {
                    val database = getInstance(context)

                    // 1. Seed the exercise catalog from the bundled dataset asset
                    DatasetSeeder.seedFresh(context, database.exerciseDao())

                    // 2. No mock athlete data.
                    //
                    // There is deliberately no default profile, no sample weigh-ins, no demo sessions
                    // and no starter routines here. This app is a workout logger: an invented athlete
                    // ("Alex Stone") with invented history is indistinguishable from real data once it
                    // reaches the Progress tab, and an athlete who starts with someone else's history
                    // cannot tell their own numbers from it.
                    //
                    // The profile is created by the first run onboarding instead (see OnboardingScreen),
                    // and the routine templates live in PredefinedRoutines, offered as a choice the
                    // athlete makes rather than history that silently appears in their account.
                }
            }
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            CoroutineScope(Dispatchers.IO).launch {
                dbInitMutex.withLock {
                    val database = getInstance(context)
                    val exerciseDao = database.exerciseDao()
                    
                    // 1. Remove duplicate exercises if any exist in the local SQLite table
                    exerciseDao.deduplicateByName()

                    // If database was just created in onCreate in this process, exercises are already fully populated
                    if (!freshlyCreated) {
                        // 2. Upsert the dataset catalog: new exercises are inserted, existing non
                        //    custom ones are refreshed in place (same row id), and custom exercises
                        //    are never touched.
                        DatasetSeeder.seed(context, exerciseDao)
                    }

                    // 3. Always ensure deduplicated again
                    exerciseDao.deduplicateByName()

                    // 4. Keep logged set exercise types in sync with the catalog
                    database.workoutDao().syncLoggedSetExerciseTypes()
                }
            }
        }
    }
}
