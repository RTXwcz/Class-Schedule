package com.kebiao.app.mcp

import kotlinx.serialization.json.*
import java.net.URI

object McpConnectionConfig {
    fun json(endpoint: String, token: String): String {
        require(URI(endpoint).scheme == "http" && endpoint.endsWith("/mcp"))
        require(token.isNotBlank())
        return Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), buildJsonObject {
            putJsonObject("mcpServers") { putJsonObject("class-schedule") {
                put("url", endpoint)
                putJsonObject("headers") { put("Authorization", "Bearer $token") }
            } }
        })
    }
}
