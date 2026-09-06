package com.kebiao.app.mcp

import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class McpAuthTest {
    @Test
    fun tokenIsStableUntilRotated() {
        val store = InMemoryTokenStore()
        val first = store.currentToken()
        assertTrue(first.length >= 32)
        assertTrue(store.isValid(first))
        val second = store.rotate()
        assertNotEquals(first, second)
        assertTrue(!store.isValid(first))
        assertTrue(store.isValid(second))
    }
}
