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

    fun observeExams(): Flow<List<ScheduleExam>> = dao.observeExams().map { entities ->
        entities.map { entity ->
            ScheduleExam(
                id = entity.id,
                subject = entity.subject,
                date = entity.date,
                time = entity.time,
                building = entity.building,
                room = entity.room,
                locationNote = entity.locationNote,
                source = entity.source,
                createdAtEpochMillis = entity.createdAtEpochMillis,
                updatedAtEpochMillis = entity.updatedAtEpochMillis,
            )
        }
    }

    fun observeOverrides(): Flow<List<ScheduleOverrideRecord>> = dao.observeOverrides().map { entities ->
        entities.map { entity ->
            ScheduleOverrideRecord(entity.date, entity.replacementWeekday, entity.note)
        }
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

    suspend fun appendCourses(courses: List<ScheduleCourse>) {
        database.withTransaction {
            dao.insertCourses(courses.map { it.toEntity(ScheduleExport(source = "OPENAI")) })
        }
    }

    suspend fun snapshot(): ScheduleExport = ScheduleExport(
        source = "NATIVE",
        courses = dao.getCourses().map { entity ->
            ScheduleCourse(entity.id, entity.name, entity.weekday, entity.startPeriod, entity.endPeriod, entity.weekRule, entity.building, entity.room, entity.locationNote, entity.source, entity.createdAtEpochMillis, entity.updatedAtEpochMillis)
        },
        exams = dao.getExams().map { entity ->
            ScheduleExam(entity.id, entity.subject, entity.date, entity.time, entity.building, entity.room, entity.locationNote, entity.source, entity.createdAtEpochMillis, entity.updatedAtEpochMillis)
        },
        overrides = dao.getOverrides().map { entity -> ScheduleOverrideRecord(entity.date, entity.replacementWeekday, entity.note) },
    )

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
