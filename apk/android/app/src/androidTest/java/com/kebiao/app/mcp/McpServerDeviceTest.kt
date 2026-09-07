package com.kebiao.app.mcp

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kebiao.app.data.ScheduleRepository
import com.kebiao.app.data.local.AppDatabase
import java.io.BufferedInputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class McpServerDeviceTest {
    private lateinit var database: AppDatabase
    private lateinit var server: McpServer
    private lateinit var repository: ScheduleRepository
    private val approvals = McpApprovalQueue()
    private var confirmation = false
    private var token = "device-test-token-with-at-least-32-characters"
    private var port = 0

    @Before fun start() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        repository = ScheduleRepository(database)
        val registry = McpToolRegistry(RepositoryScheduleStore(repository), approvals, { confirmation }, { null })
        port = ServerSocket(0).use { it.localPort }
        server = McpServer(registry, object : McpTokenStore {
            override fun currentToken() = token
            override fun rotate(): String { token += "-rotated"; return token }
        }, port)
        server.start()
    }

    @After fun stop() { approvals.cancelAll(); server.stop(); database.close() }

    @Test fun standardProtocolReturnsRealRecordsAndRejectsInvalidAccess() = runBlocking {
        val initialize = """{"jsonrpc":"2.0","id":"hello-中文","method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"android-test","version":"1"}}}"""
        val initialized = request(initialize)
        assertEquals(initialized.body, 200, initialized.status)
        assertEquals("hello-中文", initialized.json()["id"]!!.jsonPrimitive.content)
        assertEquals("kebiao", initialized.json()["result"]!!.jsonObject["serverInfo"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals(401, request(initialize, bearer = "wrong").status)
        assertEquals(403, request(initialize, host = "attacker.example").status)
        assertEquals(403, request(initialize, origin = "https://attacker.example").status)
        assertEquals(400, request("{invalid").status)

        val listed = request("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""")
        val tools = listed.json()["result"]!!.jsonObject["tools"]!!.jsonArray
        assertTrue(tools.any { it.jsonObject["name"]!!.jsonPrimitive.content == "schedule.for_date" })
        assertEquals(12, tools.size)

        val write = request(call("schedule.upsert_course", """{"id":"mcp-device","name":"高等数学","weekday":1,"startPeriod":1,"endPeriod":2,"building":"理科楼","room":"C203"}"""))
        assertEquals(write.body, 200, write.status)
        assertFalse(write.body, write.body.contains("\"isError\":true"))
        val persisted = repository.snapshot().courses.single()
        assertEquals("高等数学", persisted.name)
        assertEquals("理科楼", persisted.building)
        val read = request(call("schedule.list", "{}"))
        val text = read.json()["result"]!!.jsonObject["content"]!!.jsonArray.first().jsonObject["text"]!!.jsonPrimitive.content
        assertEquals("C203", Json.parseToJsonElement(text).jsonObject["courses"]!!.jsonArray.single().jsonObject["room"]!!.jsonPrimitive.content)

        val previous = token
        token += "-rotated"
        assertEquals(401, request(initialize, bearer = previous).status)
        assertEquals(200, request(initialize).status)
    }

    @Test fun remoteConfirmationCannotBypassAppApproval() = runBlocking {
        confirmation = true
        val args = """{"id":"approved","name":"Physics","weekday":2,"startPeriod":3,"endPeriod":4}"""
        val bypass = request(call("schedule.upsert_course", args.dropLast(1) + ",\"confirmed\":true}"))
        assertTrue(bypass.body, bypass.body.contains("invalid_arguments"))
        assertTrue(repository.snapshot().courses.isEmpty())
        val executor = Executors.newSingleThreadExecutor()
        try {
            val waiting = executor.submit<Response> { request(call("schedule.upsert_course", args)) }
            val pending = withTimeout(5_000) { approvals.pending.first { it.isNotEmpty() }.single() }
            assertTrue(repository.snapshot().courses.isEmpty())
            assertTrue(approvals.resolve(pending.id, true))
            val response = waiting.get(10, TimeUnit.SECONDS)
            assertFalse(response.body, response.body.contains("\"isError\":true"))
            assertEquals("Physics", repository.snapshot().courses.single().name)
        } finally { executor.shutdownNow() }
    }

    private fun call(name: String, arguments: String) =
        """{"jsonrpc":"2.0","id":8,"method":"tools/call","params":{"name":"$name","arguments":$arguments}}"""

    private data class Response(val status: Int, val body: String) {
        fun json() = Json.parseToJsonElement(body).jsonObject
    }

    // Exercise real CIO sockets without changing the production app's cleartext client policy.
    private fun request(body: String, bearer: String = token, host: String = "127.0.0.1:$port", origin: String? = null): Response {
        return Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = 15_000
            val bytes = body.toByteArray(Charsets.UTF_8)
            val headers = buildString {
                append("POST /mcp HTTP/1.1\r\nHost: $host\r\nAuthorization: Bearer $bearer\r\n")
                append("Content-Type: application/json\r\nAccept: application/json, text/event-stream\r\n")
                append("MCP-Protocol-Version: 2025-11-25\r\nConnection: close\r\nContent-Length: ${bytes.size}\r\n")
                origin?.let { append("Origin: $it\r\n") }
                append("\r\n")
            }
            socket.getOutputStream().write(headers.toByteArray(Charsets.US_ASCII) + bytes)
            val input = BufferedInputStream(socket.getInputStream())
            val status = line(input).split(' ')[1].toInt()
            val responseHeaders = buildMap {
                while (true) {
                    val value = line(input)
                    if (value.isEmpty()) break
                    put(value.substringBefore(':').lowercase(), value.substringAfter(':').trim())
                }
            }
            val output = java.io.ByteArrayOutputStream()
            if (responseHeaders["transfer-encoding"] == "chunked") {
                while (true) {
                    val length = line(input).substringBefore(';').toInt(16)
                    if (length == 0) break
                    repeat(length) { output.write(input.read().also { require(it >= 0) }) }
                    line(input)
                }
            } else {
                val length = responseHeaders["content-length"]?.toInt() ?: 0
                repeat(length) { output.write(input.read().also { require(it >= 0) }) }
            }
            Response(status, output.toString("UTF-8"))
        }
    }

    private fun line(input: BufferedInputStream): String {
        val result = StringBuilder()
        while (true) {
            val next = input.read()
            require(next >= 0) { "Unexpected HTTP EOF" }
            if (next == 10) return result.toString().trimEnd('\r')
            result.append(next.toChar())
            require(result.length < 16_384)
        }
    }
}
