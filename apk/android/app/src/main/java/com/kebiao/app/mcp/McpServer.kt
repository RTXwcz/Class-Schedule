package com.kebiao.app.mcp

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.ServerSocket

class McpServer(
    private val context: Context,
    private val registry: McpToolRegistry,
    private val port: Int = 8765,
) {
    private var server: ServerSocket? = null
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (server != null) return
        server = ServerSocket(port, 16, InetAddress.getByName("0.0.0.0"))
        job = scope.launch(Dispatchers.IO) {
            while (true) {
                val socket = server?.accept() ?: break
                launch { socket.use { handleConnection(it) } }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        server?.close()
        server = null
    }

    private fun handleConnection(socket: java.net.Socket) {
        val input = socket.getInputStream().bufferedReader()
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = input.readLine() ?: return
            if (line.isEmpty()) break
            line.split(":", limit = 2).takeIf { it.size == 2 }?.let { headers[it[0].lowercase()] = it[1].trim() }
        }
        val length = headers["content-length"]?.toIntOrNull()?.coerceIn(0, 64 * 1024) ?: 0
        val body = CharArray(length)
        input.read(body)
        val response = "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32601,\"message\":\"Use app tool adapter\"}}"
        val bytes = response.toByteArray()
        socket.getOutputStream().bufferedWriter().use { output ->
            output.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n")
            output.write(response)
        }
    }
}
