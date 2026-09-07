package com.kebiao.app.mcp

import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject

data class McpPendingApproval(val id: String, val tool: String, val arguments: JsonObject, val requestedAtEpochMillis: Long)

enum class McpApprovalDecision { APPROVED, REJECTED, TIMED_OUT, QUEUE_FULL, CANCELLED }

/** Only the local app holds this queue; no MCP tool exposes resolve(). */
class McpApprovalQueue(private val timeoutMillis: Long = 120_000, private val maxPending: Int = 8) {
    init { require(timeoutMillis > 0); require(maxPending in 1..8) }
    private val lock = Any()
    private val waiting = linkedMapOf<String, CompletableDeferred<McpApprovalDecision>>()
    private val state = MutableStateFlow<List<McpPendingApproval>>(emptyList())
    val pending: StateFlow<List<McpPendingApproval>> = state.asStateFlow()
    private var currentGeneration = 0L
    val generation: Long get() = synchronized(lock) { currentGeneration }

    suspend fun request(tool: String, arguments: JsonObject, expectedGeneration: Long = generation): McpApprovalDecision {
        val entry = McpPendingApproval(UUID.randomUUID().toString(), tool, arguments, System.currentTimeMillis())
        val response = CompletableDeferred<McpApprovalDecision>()
        synchronized(lock) {
            if (expectedGeneration != currentGeneration) return McpApprovalDecision.CANCELLED
            if (waiting.size >= maxPending) return McpApprovalDecision.QUEUE_FULL
            waiting[entry.id] = response
            state.value = state.value + entry
        }
        return try {
            withTimeoutOrNull(timeoutMillis) { response.await() } ?: McpApprovalDecision.TIMED_OUT
        } finally {
            synchronized(lock) {
                waiting.remove(entry.id)
                state.value = state.value.filterNot { it.id == entry.id }
            }
        }
    }

    fun resolve(id: String, approved: Boolean): Boolean = synchronized(lock) {
        val response = waiting.remove(id) ?: return false
        state.value = state.value.filterNot { it.id == id }
        response.complete(if (approved) McpApprovalDecision.APPROVED else McpApprovalDecision.REJECTED)
    }

    fun cancelAll() = synchronized(lock) {
        currentGeneration++
        waiting.values.forEach { it.complete(McpApprovalDecision.CANCELLED) }
        waiting.clear()
        state.value = emptyList()
    }
}
