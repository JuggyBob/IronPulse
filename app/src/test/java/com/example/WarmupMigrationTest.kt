package com.example

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The 16 -> 17 step is the one an upgrading athlete actually runs. It has to leave every old set
 * exactly as it was, and it must never guess that a set was a warm-up from its position or load.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WarmupMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "warmup_migration_test.db"

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun `migration 16 to 17 preserves legacy sets and defaults them to normal`() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(16) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE `logged_sets` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `sessionId` INTEGER NOT NULL,
                                `exerciseId` INTEGER NOT NULL,
                                `exerciseName` TEXT NOT NULL,
                                `exerciseType` TEXT NOT NULL,
                                `setNumber` INTEGER NOT NULL,
                                `weight` REAL NOT NULL,
                                `addedWeight` REAL NOT NULL,
                                `reps` INTEGER NOT NULL,
                                `holdDurationSeconds` INTEGER NOT NULL,
                                `distanceKm` REAL NOT NULL DEFAULT 0,
                                `isCompleted` INTEGER NOT NULL,
                                `timestamp` INTEGER NOT NULL,
                                `bodyWeightKg` REAL NOT NULL DEFAULT 0
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            INSERT INTO `logged_sets`
                            (`id`, `sessionId`, `exerciseId`, `exerciseName`, `exerciseType`,
                             `setNumber`, `weight`, `addedWeight`, `reps`, `holdDurationSeconds`,
                             `distanceKm`, `isCompleted`, `timestamp`, `bodyWeightKg`)
                            VALUES
                            (7, 3, 42, 'Barbell Front Squat', 'STANDARD_WEIGHT',
                             1, 80.0, 0.0, 5, 0, 0.0, 1, 1700000000000, 75.0),
                            (8, 3, 42, 'Barbell Front Squat', 'STANDARD_WEIGHT',
                             2, 80.0, 0.0, 8, 0, 0.0, 1, 1700000060000, 75.0)
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        val db = helper.writableDatabase
        try {
            AppDatabase.MIGRATION_16_17.migrate(db)

            db.query(
                "SELECT id, weight, reps, isWarmup, restSecondsOverride FROM logged_sets ORDER BY id"
            ).use { cursor ->
                assertEquals(2, cursor.count)
                cursor.moveToFirst()
                assertEquals(7L, cursor.getLong(0))
                assertEquals(80.0, cursor.getDouble(1), 0.001)
                assertEquals(5, cursor.getInt(2))
                assertEquals(0, cursor.getInt(3))
                assertNull(cursor.getString(4))
                cursor.moveToNext()
                assertEquals(8L, cursor.getLong(0))
                assertEquals(8, cursor.getInt(2))
                assertEquals(0, cursor.getInt(3))
                assertNull(cursor.getString(4))
            }
        } finally {
            helper.close()
        }
    }
}
