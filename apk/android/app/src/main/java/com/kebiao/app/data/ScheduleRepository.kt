package com.kebiao.app.data

import androidx.room.withTransaction
import com.kebiao.app.data.local.AppDatabase
import com.kebiao.app.data.local.CourseEntity
import com.kebiao.app.data.local.ExamEntity
import com.kebiao.app.data.local.DatasetMetadataEntity
import com.kebiao.app.data.local.ScheduleOverrideEntity
import com.kebiao.app.data.settings.AppSettings
import com.kebiao.app.domain.model.Course
import com.kebiao.app.domain.model.WeekRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

class ScheduleRepository(private val database: AppDatabase) {
    private val dao = database.scheduleDao()
    private val metadataDao = database.datasetMetadataDao()

    suspend fun <T> transaction(block: suspend () -> T): T = database.withTransaction(block)

    fun observeSnapshot(): Flow<ScheduleExport> = database.invalidationTracker
        .createFlow("courses", "exams", "schedule_overrides", "dataset_metadata").map { snapshot() }

    suspend fun ensureRules(legacy: AppSettings) = database.withTransaction {
        val entity = ensureMetadata()
        val current = entity.toExport()
        // This migration flag is local, so importing newer JSON into an older app cannot suppress migration.
        if (!entity.rulesInitialized) metadataDao.upsert(ScheduleRules.from(legacy).apply(current).toMetadata())
    }

    suspend fun snapshotWithSettings(legacy: AppSettings): ScheduleExport = database.withTransaction {
        ensureRules(legacy)
        snapshot()
    }

    suspend fun updateRules(rules: ScheduleRules) = database.withTransaction {
        rules.validate()
        require(dao.getCourses().all { it.endPeriod <= rules.periods.size }) { "已有课程超出新节数，请先调整课程" }
        val current = ensureMetadata().toExport()
        metadataDao.upsert(rules.apply(current).copy(updatedAt = Instant.now().toString(), source = "NATIVE").toMetadata())
    }

    private suspend fun validateConfiguredPeriods(courses: List<ScheduleCourse>) {
        val current = ensureMetadata().toExport()
        if (current.extraFields["rulesVersion"] == JsonPrimitive(1)) {
            val count = ScheduleRules.read(current).periods.size
            require(courses.all { it.endPeriod <= count }) { "课程超出当前作息节数" }
        }
    }

    fun observeMetadata(): Flow<ScheduleExport> = metadataDao.observe()
        .onStart { database.withTransaction { ensureMetadata() } }
        .filterNotNull()
        .map { it.toExport() }

    fun observeCourses(): Flow<List<Course>> = dao.observeCourses().map { entities ->
        entities.mapNotNull { entity -> entity.toDomainOrNull() }
    }

    fun observeExams(): Flow<List<ScheduleExam>> = dao.observeExams().map { entities ->
        entities.map { entity ->
            ScheduleExam(
                id = entity.id,
                subject = entity.subject,
                type = entity.type,
                note = entity.note,
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
        if (export.extraFields["rulesVersion"] == JsonPrimitive(1)) {
            val rules = ScheduleRules.read(export)
            require(export.courses.all { it.endPeriod <= rules.periods.size }) { "课程超出导入作息节数" }
        }
        database.withTransaction {
            dao.deleteAllCourses()
            dao.deleteAllExams()
            dao.deleteAllOverrides()
            dao.insertCourses(export.courses.map { it.toEntity(export) })
            dao.insertExams(export.exams.map { it.toEntity(export) })
            dao.insertOverrides(export.overrides.map { it.toEntity() })
            metadataDao.upsert(export.toMetadata())
        }
    }

    suspend fun appendCourses(courses: List<ScheduleCourse>, source: String = "OPENAI") {
        database.withTransaction {
            courses.forEach(::validateCourse)
            validateConfiguredPeriods(courses)
            dao.insertNewCourses(courses.map { it.toEntity(ScheduleExport(source = source)) })
            touchMetadata(source)
        }
    }

    suspend fun upsertCourse(course: ScheduleCourse, source: String = "NATIVE") {
        validateCourse(course)
        database.withTransaction {
            val now = System.currentTimeMillis()
            validateConfiguredPeriods(listOf(course))
            val previous = dao.getCourse(course.id)
            val saved = course.copy(
                createdAtEpochMillis = previous?.createdAtEpochMillis?.takeIf { it > 0 }
                    ?: course.createdAtEpochMillis.takeIf { it > 0 } ?: now,
                updatedAtEpochMillis = now,
            )
            dao.insertCourses(listOf(saved.toEntity(ScheduleExport(source = source))))
            touchMetadata(source)
        }
    }

    suspend fun deleteCourse(id: String, source: String = "NATIVE") = database.withTransaction {
        dao.deleteCourse(id)
        touchMetadata(source)
    }

    suspend fun upsertExam(exam: ScheduleExam, source: String = "NATIVE") {
        validateExam(exam)
        database.withTransaction {
            val now = System.currentTimeMillis()
            val previous = dao.getExam(exam.id)
            val saved = exam.copy(
                createdAtEpochMillis = previous?.createdAtEpochMillis?.takeIf { it > 0 }
                    ?: exam.createdAtEpochMillis.takeIf { it > 0 } ?: now,
                updatedAtEpochMillis = now,
            )
            dao.insertExams(listOf(saved.toEntity(ScheduleExport(source = source))))
            touchMetadata(source)
        }
    }

    suspend fun deleteExam(id: String, source: String = "NATIVE") = database.withTransaction {
        dao.deleteExam(id)
        touchMetadata(source)
    }

    suspend fun upsertOverride(record: ScheduleOverrideRecord, source: String = "NATIVE") {
        validateOverride(record)
        database.withTransaction {
            dao.insertOverrides(listOf(record.toEntity()))
            touchMetadata(source)
        }
    }

    suspend fun deleteOverride(date: String, source: String = "NATIVE") = database.withTransaction {
        dao.deleteOverride(date)
        touchMetadata(source)
    }

    suspend fun snapshot(): ScheduleExport = database.withTransaction { ensureMetadata().toExport().copy(
        courses = dao.getCourses().map { entity ->
            ScheduleCourse(entity.id, entity.name, entity.weekday, entity.startPeriod, entity.endPeriod, entity.weekRule, entity.building, entity.room, entity.locationNote, entity.source, entity.createdAtEpochMillis, entity.updatedAtEpochMillis,
                entity.teacher, decodeWeeks(entity.weeksJson), entity.courseNote)
        },
        exams = dao.getExams().map { entity ->
            ScheduleExam(entity.id, entity.subject, entity.date, entity.time, entity.building, entity.room, entity.locationNote, entity.source, entity.createdAtEpochMillis, entity.updatedAtEpochMillis, entity.type, entity.note)
        },
        overrides = dao.getOverrides().map { entity -> ScheduleOverrideRecord(entity.date, entity.replacementWeekday, entity.note) },
    ) }

    // Every caller holds a Room transaction, so first access creates one durable identity.
    private suspend fun ensureMetadata(): DatasetMetadataEntity = metadataDao.get()
        ?: ScheduleExport(datasetId = UUID.randomUUID().toString(), updatedAt = Instant.now().toString(), source = "NATIVE")
            .toMetadata().also { metadataDao.upsert(it) }

    private suspend fun touchMetadata(source: String) {
        val previous = ensureMetadata()
        metadataDao.upsert(previous.copy(updatedAt = Instant.now().toString(), source = source))
    }

    private fun ScheduleExport.toMetadata() = DatasetMetadataEntity(
        schemaVersion = schemaVersion,
        datasetId = datasetId.ifBlank { UUID.randomUUID().toString() },
        updatedAt = updatedAt.ifBlank { Instant.now().toString() },
        source = source,
        extraFieldsJson = JsonObject(extraFields).toString(),
        rulesInitialized = extraFields["rulesVersion"] == JsonPrimitive(1),
    )

    private fun DatasetMetadataEntity.toExport() = ScheduleExport(
        schemaVersion = schemaVersion,
        datasetId = datasetId,
        updatedAt = updatedAt,
        source = source,
        extraFields = Json.parseToJsonElement(extraFieldsJson).jsonObject,
    )

    private fun decodeWeeks(value: String) = Json.parseToJsonElement(value).jsonArray.map { it.jsonPrimitive.int }

    private fun validateCourse(course: ScheduleCourse) {
        Course(course.id, course.name, course.weekday, course.startPeriod, course.endPeriod, WeekRule.valueOf(course.weekRule),
            teacher = course.teacher, weeks = course.weeks, courseNote = course.courseNote)
    }

    private fun validateExam(exam: ScheduleExam) {
        require(exam.id.isNotBlank() && exam.subject.isNotBlank()) { "考试或日程名称不能为空" }
        require(exam.type in setOf("EXAM", "EVENT")) { "类型必须为考试或日程" }
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
        teacher = teacher,
        weeksJson = JsonArray(weeks.map(::JsonPrimitive)).toString(),
        courseNote = courseNote,
    )

    private fun ScheduleExam.toEntity(export: ScheduleExport) = ExamEntity(
        id = id,
        type = type,
        note = note,
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
            teacher = teacher,
            weeks = decodeWeeks(weeksJson),
            courseNote = courseNote,
        )
    }.getOrNull()
}

fun Course.toScheduleCourse() = ScheduleCourse(
    id, name, weekday, startPeriod, endPeriod, weekRule.name, building, room, locationNote,
    source, createdAtEpochMillis, updatedAtEpochMillis,
    teacher, weeks, courseNote,
)
