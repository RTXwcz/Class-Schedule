package com.kebiao.app.mcp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
}
