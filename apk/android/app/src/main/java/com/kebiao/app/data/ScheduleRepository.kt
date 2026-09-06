package com.kebiao.app.data

import androidx.room.withTransaction
import com.kebiao.app.data.local.AppDatabase
import com.kebiao.app.data.local.CourseEntity
import com.kebiao.app.data.local.ExamEntity
import com.kebiao.app.data.local.ScheduleOverrideEntity
import com.kebiao.app.domain.model.Course
import com.kebiao.app.domain.model.WeekRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ScheduleRepository(private val database: AppDatabase) {
    private val dao = database.scheduleDao()

    fun observeCourses(): Flow<List<Course>> = dao.observeCourses().map { entities ->
        entities.mapNotNull { entity -> entity.toDomainOrNull() }
    }

    suspend fun replaceAll(export: ScheduleExport) {
        database.withTransaction {
            dao.deleteAllCourses()
            dao.deleteAllExams()
            dao.deleteAllOverrides()
            dao.insertCourses(export.courses.map { it.toEntity(export) })
            dao.insertExams(export.exams.map { it.toEntity(export) })
            dao.insertOverrides(export.overrides.map { it.toEntity() })
        }
    }

    private fun ScheduleCourse.toEntity(export: ScheduleExport) = CourseEntity(
        id = id,
        name = name,
        weekday = weekday,
        startPeriod = startPeriod,
        endPeriod = endPeriod,
        weekRule = weekRule,
        building = building,
        room = room,
        locationNote = locationNote,
        source = source.ifBlank { export.source },
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

    private fun ScheduleExam.toEntity(export: ScheduleExport) = ExamEntity(
        id = id,
        subject = subject,
        date = date,
        time = time,
        building = building,
        room = room,
        locationNote = locationNote,
        source = source.ifBlank { export.source },
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

    private fun ScheduleOverrideRecord.toEntity() = ScheduleOverrideEntity(
        date = date,
        replacementWeekday = replacementWeekday,
        note = note,
    )

    private fun CourseEntity.toDomainOrNull(): Course? = runCatching {
        Course(
            id = id,
            name = name,
            weekday = weekday,
            startPeriod = startPeriod,
            endPeriod = endPeriod,
            weekRule = runCatching { WeekRule.valueOf(weekRule.uppercase()) }.getOrDefault(WeekRule.ALL),
            building = building,
            room = room,
            locationNote = locationNote,
            source = source,
            createdAtEpochMillis = createdAtEpochMillis,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )
    }.getOrNull()
}
