package com.kebiao.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.Room
import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CourseEntity::class, ExamEntity::class, ScheduleOverrideEntity::class, DatasetMetadataEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao
    abstract fun datasetMetadataDao(): DatasetMetadataDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE courses ADD COLUMN teacher TEXT")
                db.execSQL("ALTER TABLE courses ADD COLUMN weeksJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE courses ADD COLUMN courseNote TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS dataset_metadata (id INTEGER NOT NULL, schemaVersion INTEGER NOT NULL, datasetId TEXT NOT NULL, updatedAt TEXT NOT NULL, source TEXT NOT NULL, extraFieldsJson TEXT NOT NULL, PRIMARY KEY(id))")
            }
        }
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "schedule.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }
    }
}
