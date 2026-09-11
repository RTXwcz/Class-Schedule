package com.kebiao.app.mcp

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McpRateLimitTest {
    @Test fun limitsBurstsPerPeerAndRecoversAfterTheWindow() {
        McpServer.resetRateLimiter()
        val start = 1_000_000L
        repeat(60) { step -> assertTrue(McpServer.allowRequest("10.0.0.5", start + step)) }
        // The 61st call inside the window is refused…
        assertFalse(McpServer.allowRequest("10.0.0.5", start + 60))
        // …another peer is unaffected…
        assertTrue(McpServer.allowRequest("10.0.0.6", start + 61))
        // …and the window slides.
        assertTrue(McpServer.allowRequest("10.0.0.5", start + 10_001))
    }
}
