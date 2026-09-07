package com.kebiao.app.mcp

import com.kebiao.app.data.ScheduleExport
import com.kebiao.app.data.ScheduleCourse
import com.kebiao.app.data.ScheduleExam
import com.kebiao.app.data.ScheduleOverrideRecord
import java.util.UUID

class InMemoryTokenStore : McpTokenStore {
    private var token = UUID.randomUUID().toString().replace("-", "")
    override fun currentToken(): String = token
    override fun rotate(): String { token = UUID.randomUUID().toString().replace("-", ""); return token }
}

class InMemoryScheduleStore : ScheduleStore {
    override suspend fun <T> transaction(block: suspend () -> T): T = block()
    var value = ScheduleExport()
    var replacements = 0
    override suspend fun snapshot(): ScheduleExport = value
    override suspend fun replaceAll(export: ScheduleExport) { replacements++; value = export }
    override suspend fun upsertCourse(course: ScheduleCourse) {
        val saved = course.copy(createdAtEpochMillis = value.courses.find { it.id == course.id }?.createdAtEpochMillis ?: 100L, updatedAtEpochMillis = 200L)
        value = value.copy(courses = value.courses.filterNot { it.id == course.id } + saved)
    }
    override suspend fun deleteCourse(id: String) { value = value.copy(courses = value.courses.filterNot { it.id == id }) }
    override suspend fun upsertExam(exam: ScheduleExam) { value = value.copy(exams = value.exams.filterNot { it.id == exam.id } + exam) }
    override suspend fun deleteExam(id: String) { value = value.copy(exams = value.exams.filterNot { it.id == id }) }
    override suspend fun upsertOverride(record: ScheduleOverrideRecord) { value = value.copy(overrides = value.overrides.filterNot { it.date == record.date } + record) }
    override suspend fun deleteOverride(date: String) { value = value.copy(overrides = value.overrides.filterNot { it.date == date }) }
}
