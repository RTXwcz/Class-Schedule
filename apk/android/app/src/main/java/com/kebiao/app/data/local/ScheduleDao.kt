package com.kebiao.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM courses ORDER BY weekday, startPeriod, endPeriod, id")
    fun observeCourses(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM exams ORDER BY date, time, id")
    fun observeExams(): Flow<List<ExamEntity>>

    @Query("SELECT * FROM schedule_overrides ORDER BY date")
    fun observeOverrides(): Flow<List<ScheduleOverrideEntity>>

    @Query("SELECT * FROM courses ORDER BY weekday, startPeriod, endPeriod, id")
    suspend fun getCourses(): List<CourseEntity>

    @Query("SELECT * FROM exams ORDER BY date, time, id")
    suspend fun getExams(): List<ExamEntity>

    @Query("SELECT * FROM schedule_overrides ORDER BY date")
    suspend fun getOverrides(): List<ScheduleOverrideEntity>

    @Query("SELECT * FROM courses WHERE id = :id")
    suspend fun getCourse(id: String): CourseEntity?

    @Query("SELECT * FROM exams WHERE id = :id")
    suspend fun getExam(id: String): ExamEntity?

    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun deleteCourse(id: String)

    @Query("DELETE FROM exams WHERE id = :id")
    suspend fun deleteExam(id: String)

    @Query("DELETE FROM schedule_overrides WHERE date = :date")
    suspend fun deleteOverride(date: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNewCourses(courses: List<CourseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourses(courses: List<CourseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExams(exams: List<ExamEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOverrides(overrides: List<ScheduleOverrideEntity>)

    @Query("DELETE FROM courses")
    suspend fun deleteAllCourses()

    @Query("DELETE FROM exams")
    suspend fun deleteAllExams()

    @Query("DELETE FROM schedule_overrides")
    suspend fun deleteAllOverrides()
}
