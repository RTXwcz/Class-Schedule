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
import java.time.LocalDate
import java.time.LocalTime

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
        export.courses.forEach(::validateCourse)
        export.exams.forEach(::validateExam)
        export.overrides.forEach(::validateOverride)
        require(export.courses.map { it.id }.distinct().size == export.courses.size) { "课程 ID 重复" }
        require(export.exams.map { it.id }.distinct().size == export.exams.size) { "考试 ID 重复" }
        require(export.overrides.map { it.date }.distinct().size == export.overrides.size) { "调休日期重复" }
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
            courses.forEach(::validateCourse)
            dao.insertNewCourses(courses.map { it.toEntity(ScheduleExport(source = "OPENAI")) })
        }
    }

    suspend fun upsertCourse(course: ScheduleCourse) {
        validateCourse(course)
        database.withTransaction {
            val now = System.currentTimeMillis()
            val previous = dao.getCourse(course.id)
            val saved = course.copy(
                createdAtEpochMillis = previous?.createdAtEpochMillis?.takeIf { it > 0 }
                    ?: course.createdAtEpochMillis.takeIf { it > 0 } ?: now,
                updatedAtEpochMillis = now,
            )
            dao.insertCourses(listOf(saved.toEntity(ScheduleExport(source = "NATIVE"))))
        }
    }

    suspend fun deleteCourse(id: String) = dao.deleteCourse(id)

    suspend fun upsertExam(exam: ScheduleExam) {
        validateExam(exam)
        database.withTransaction {
            val now = System.currentTimeMillis()
            val previous = dao.getExam(exam.id)
            val saved = exam.copy(
                createdAtEpochMillis = previous?.createdAtEpochMillis?.takeIf { it > 0 }
                    ?: exam.createdAtEpochMillis.takeIf { it > 0 } ?: now,
                updatedAtEpochMillis = now,
            )
            dao.insertExams(listOf(saved.toEntity(ScheduleExport(source = "NATIVE"))))
        }
    }

    suspend fun deleteExam(id: String) = dao.deleteExam(id)

    suspend fun upsertOverride(record: ScheduleOverrideRecord) {
        validateOverride(record)
        dao.insertOverrides(listOf(record.toEntity()))
    }

    suspend fun deleteOverride(date: String) = dao.deleteOverride(date)

    suspend fun snapshot(): ScheduleExport = database.withTransaction { ScheduleExport(
        source = "NATIVE",
        courses = dao.getCourses().map { entity ->
            ScheduleCourse(entity.id, entity.name, entity.weekday, entity.startPeriod, entity.endPeriod, entity.weekRule, entity.building, entity.room, entity.locationNote, entity.source, entity.createdAtEpochMillis, entity.updatedAtEpochMillis)
        },
        exams = dao.getExams().map { entity ->
            ScheduleExam(entity.id, entity.subject, entity.date, entity.time, entity.building, entity.room, entity.locationNote, entity.source, entity.createdAtEpochMillis, entity.updatedAtEpochMillis)
        },
        overrides = dao.getOverrides().map { entity -> ScheduleOverrideRecord(entity.date, entity.replacementWeekday, entity.note) },
    ) }

    private fun validateCourse(course: ScheduleCourse) {
        Course(course.id, course.name, course.weekday, course.startPeriod, course.endPeriod, WeekRule.valueOf(course.weekRule))
    }

    private fun validateExam(exam: ScheduleExam) {
        require(exam.id.isNotBlank() && exam.subject.isNotBlank()) { "考试名称不能为空" }
        LocalDate.parse(exam.date)
        exam.time?.takeIf { it.isNotBlank() }?.let(LocalTime::parse)
    }

    private fun validateOverride(record: ScheduleOverrideRecord) {
        LocalDate.parse(record.date)
        require(record.replacementWeekday in 1..7) { "目标星期必须为 1 到 7" }
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

fun Course.toScheduleCourse() = ScheduleCourse(
    id, name, weekday, startPeriod, endPeriod, weekRule.name, building, room, locationNote,
    source, createdAtEpochMillis, updatedAtEpochMillis,
)
