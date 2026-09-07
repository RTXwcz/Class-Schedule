package com.kebiao.app.mcp

import kotlinx.serialization.json.*
import kotlin.test.*

class McpConnectionConfigTest {
    @Test fun copiedConfigurationUsesExactEndpointAndBearerHeader() {
        val json = Json.parseToJsonElement(McpConnectionConfig.json("http://192.168.1.20:8765/mcp", "test-token")).jsonObject
        val server = json["mcpServers"]!!.jsonObject["class-schedule"]!!.jsonObject
        assertEquals("http://192.168.1.20:8765/mcp", server["url"]!!.jsonPrimitive.content)
        assertEquals("Bearer test-token", server["headers"]!!.jsonObject["Authorization"]!!.jsonPrimitive.content)
    }
}
