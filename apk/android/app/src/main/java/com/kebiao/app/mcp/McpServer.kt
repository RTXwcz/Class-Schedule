package com.kebiao.app.mcp

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.header
import io.ktor.server.response.respondText
import io.modelcontextprotocol.kotlin.sdk.server.mcpStatelessStreamableHttp
import java.net.InetAddress
import java.net.NetworkInterface

class McpServer(
    private val registry: McpToolRegistry,
    private val tokens: McpTokenStore,
    val port: Int = 8765,
) {
    val hosts = localHosts()
    private val engine = embeddedServer(CIO, host = "0.0.0.0", port = port) {
        intercept(ApplicationCallPipeline.Plugins) {
            // The socket peer is authoritative; forwarded headers are never installed.
            if (!isLocalPeer(context.request.local.remoteAddress)) {
                context.respondText("LAN access only", status = HttpStatusCode.Forbidden)
                finish()
                return@intercept
            }
            val authorization = context.request.header("Authorization").orEmpty()
            if (!authorization.startsWith("Bearer ") || !tokens.isValid(authorization.removePrefix("Bearer "))) {
                context.respondText("Unauthorized", status = HttpStatusCode.Unauthorized)
                finish()
            }
        }
        // SDK bounds POST bodies (4 MiB), validates protocol headers and preserves JSON-RPC IDs.
        mcpStatelessStreamableHttp(
            path = "/mcp", allowedHosts = hosts, allowedOrigins = emptyList(),
        ) {
            val bearer = call.request.header("Authorization").orEmpty().removePrefix("Bearer ")
            registry.createServer { tokens.isValid(bearer) }
        }
    }

    fun start() { engine.start(wait = false) }
    fun stop() { engine.stop(0, 1_000) }

    companion object {
        fun isLocalPeer(address: String): Boolean = runCatching {
            if (!address.matches(Regex("[0-9a-fA-F:.%]+"))) return false
            val peer = InetAddress.getByName(address)
            peer.isLoopbackAddress || peer.isSiteLocalAddress ||
                (peer.address.size == 16 && (peer.address[0].toInt() and 0xfe) == 0xfc)
        }.getOrDefault(false)

        fun localHosts(): List<String> = buildList {
            addAll(listOf("localhost", "127.0.0.1", "[::1]"))
            NetworkInterface.getNetworkInterfaces()?.toList()?.filter { it.isUp }?.forEach { network ->
                network.inetAddresses.toList().filter { it.isSiteLocalAddress }.forEach { address ->
                    address.hostAddress?.substringBefore('%')?.let { host ->
                        add(if (host.contains(':')) "[$host]" else host)
                    }
                }
            }
        }
            .distinct()
    }
}
