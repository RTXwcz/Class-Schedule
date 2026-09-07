package com.kebiao.app.mcp

import com.kebiao.app.data.ScheduleExport
import com.kebiao.app.data.ScheduleCourse
import java.util.UUID

class InMemoryTokenStore : McpTokenStore {
    private var token = UUID.randomUUID().toString().replace("-", "")
    override fun currentToken(): String = token
    override fun rotate(): String { token = UUID.randomUUID().toString().replace("-", ""); return token }
}

class InMemoryScheduleStore : ScheduleStore {
    var value = ScheduleExport()
    override suspend fun snapshot(): ScheduleExport = value
    override suspend fun replaceAll(export: ScheduleExport) { value = export }
    override suspend fun upsertCourse(course: ScheduleCourse) { value = value.copy(courses = value.courses.filterNot { it.id == course.id } + course) }
}
