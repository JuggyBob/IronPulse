package com.example

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Room 17 -> 18 keeps every existing set as a non-drop row.
 *
 * The migration is called directly on a v17-shaped table so the test isolates the exact step: a
 * full Room open would also run earlier migrations and hide which one assigned the UIDs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DropSetMigrationTest {

    private fun openVersion17(context: Context): SupportSQLiteOpenHelper {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("drop_set_migration.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(17) {
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
                            `distanceKm` REAL NOT NULL,
                            `isCompleted` INTEGER NOT NULL,
                            `timestamp` INTEGER NOT NULL,
                            `bodyWeightKg` REAL NOT NULL,
                            `isWarmup` INTEGER NOT NULL,
                            `restSecondsOverride` INTEGER
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        "INSERT INTO `logged_sets` (`sessionId`,`exerciseId`,`exerciseName`," +
                            "`exerciseType`,`setNumber`,`weight`,`addedWeight`,`reps`," +
                            "`holdDurationSeconds`,`distanceKm`,`isCompleted`,`timestamp`," +
                            "`bodyWeightKg`,`isWarmup`,`restSecondsOverride`) VALUES " +
                            "(1,7,'Bench','STANDARD_WEIGHT',1,40,0,8,0,0,1,100,70,0,NULL)," +
                            "(1,7,'Bench','STANDARD_WEIGHT',2,40,0,6,0,0,1,200,70,1,30)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config)
    }

    @Test
    fun `existing sets become non-drop rows with legacy uids and warmup preserved`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = openVersion17(context)
        try {
            val db = helper.writableDatabase
            AppDatabase.MIGRATION_17_18.migrate(db)

            val columns = mutableListOf<String>()
            db.query("PRAGMA table_info(`logged_sets`)", emptyArray()).use { cursor ->
                while (cursor.moveToNext()) columns.add(cursor.getString(1))
            }
            assertTrue("setUid" in columns)
            assertTrue("parentSetUid" in columns)
            assertTrue("dropIndex" in columns)

            var rowCount = 0
            db.query(
                "SELECT `id`,`setUid`,`parentSetUid`,`dropIndex`,`setNumber`,`isWarmup` " +
                    "FROM `logged_sets` ORDER BY `id`",
                emptyArray()
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    rowCount++
                    assertEquals("legacy-${cursor.getLong(0)}", cursor.getString(1))
                    assertTrue(cursor.isNull(2))
                    assertEquals(0, cursor.getInt(3))
                    if (cursor.getLong(0) == 1L) {
                        assertEquals(1, cursor.getInt(4))
                        assertEquals(0, cursor.getInt(5))
                    } else {
                        assertEquals(2, cursor.getInt(4))
                        assertEquals(1, cursor.getInt(5))
                    }
                }
            }
            assertEquals(2, rowCount)
        } finally {
            helper.close()
        }
    }
}
