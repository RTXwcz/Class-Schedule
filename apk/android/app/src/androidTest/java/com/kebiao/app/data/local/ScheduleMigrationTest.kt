package com.kebiao.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration 3 -> 4 shipped in the 1.6.x series, so it is the hop an existing install performs.
 * (Versions 1 and 2 have no exported schema JSON left in the repository, so those older hops can
 * only be exercised on a device that still runs the original build.)
 */
@RunWith(AndroidJUnit4::class)
class ScheduleMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test fun addsTheRulesInitializedFlagWithoutLosingData() {
        helper.createDatabase(DB_NAME, 3).apply {
            execSQL(
                "INSERT INTO courses (id, name, weekday, startPeriod, endPeriod, weekRule, building, room, locationNote, " +
                    "source, createdAtEpochMillis, updatedAtEpochMillis, teacher, weeksJson, courseNote) " +
                    "VALUES ('c1','英语',2,3,4,'ALL','文科楼','101',NULL,'MANUAL',0,0,'张老师','[1,2]',NULL)",
            )
            execSQL("INSERT INTO dataset_metadata (id, schemaVersion, datasetId, updatedAt, source, extraFieldsJson) VALUES (1,1,'default','2026-01-01','NATIVE','{}')")
            close()
        }

        helper.runMigrationsAndValidate(DB_NAME, 4, true, AppDatabase.MIGRATION_3_4).use { database ->
            database.query("SELECT name, teacher, weeksJson FROM courses").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("英语", cursor.getString(0))
                assertEquals("张老师", cursor.getString(1))
                assertEquals("[1,2]", cursor.getString(2))
                assertEquals(1, cursor.count)
            }
            database.query("SELECT rulesInitialized FROM dataset_metadata").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test fun emptyDatabaseStillMigrates() {
        helper.createDatabase(DB_NAME, 3).close()
        helper.runMigrationsAndValidate(DB_NAME, 4, true, AppDatabase.MIGRATION_3_4).use { database ->
            database.query("SELECT COUNT(*) FROM courses").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    private companion object { const val DB_NAME = "schedule-migration-test.db" }
}


