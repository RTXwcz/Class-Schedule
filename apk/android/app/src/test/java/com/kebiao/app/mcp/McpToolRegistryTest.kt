package com.kebiao.app.mcp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import com.kebiao.app.data.ScheduleCourse
import com.kebiao.app.data.ScheduleExport

class McpToolRegistryTest {
    @Test
    fun rejectsWritesUntilConfirmedWhenConfirmationIsEnabled() {
        val registry = McpToolRegistry(InMemoryScheduleStore(), InMemoryTokenStore(), writeConfirmation = true)
        val result = registry.handle("schedule.add_course", emptyMap())
        assertEquals("confirmation_required", result.errorCode)
    }

    @Test
    fun listsCoursesWithValidToken() {
        val tokenStore = InMemoryTokenStore()
        val registry = McpToolRegistry(InMemoryScheduleStore(), tokenStore, writeConfirmation = false)
        val result = registry.handle("schedule.list", emptyMap(), tokenStore.currentToken())
        assertTrue(result.success)
    }

    @Test
    fun courseWriteDoesNotReadOrReplaceTheWholeDataset() = runBlocking {
        var saved: ScheduleCourse? = null
        val store = object : ScheduleStore {
            override suspend fun snapshot(): ScheduleExport = error("Course writes must not load a dataset snapshot")
            override suspend fun replaceAll(export: ScheduleExport): Unit = error("Course writes must not replace a dataset")
            override suspend fun upsertCourse(course: ScheduleCourse) { saved = course }
        }
        val tokens = InMemoryTokenStore()
        val registry = McpToolRegistry(store, tokens, false)
        val result = registry.handleWrite("schedule.add_course", mapOf("name" to "数学", "weekday" to "1", "startPeriod" to "1", "endPeriod" to "2"), tokens.currentToken())
        assertTrue(result.success)
        assertEquals("数学", saved?.name)
    }
}
