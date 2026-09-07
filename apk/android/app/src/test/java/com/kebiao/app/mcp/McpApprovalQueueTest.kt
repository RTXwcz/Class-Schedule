package com.kebiao.app.mcp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject

@OptIn(ExperimentalCoroutinesApi::class)
class McpApprovalQueueTest {
    private val args = JsonObject(emptyMap())

    @Test fun localDecisionCanOnlyResolveOnce() = runTest {
        val queue = McpApprovalQueue()
        val result = async { queue.request("write", args) }
        runCurrent()
        val id = queue.pending.value.single().id
        assertFalse(queue.resolve("unknown", true))
        assertTrue(queue.resolve(id, true))
        assertFalse(queue.resolve(id, true))
        assertEquals(McpApprovalDecision.APPROVED, result.await())
        assertTrue(queue.pending.value.isEmpty())
    }

    @Test fun defaultTimeoutIsTwoMinutesAndRemovesRequest() = runTest {
        val queue = McpApprovalQueue()
        val result = async { queue.request("write", args) }
        runCurrent()
        val id = queue.pending.value.single().id
        advanceTimeBy(119_999)
        assertFalse(result.isCompleted)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(McpApprovalDecision.TIMED_OUT, result.await())
        assertTrue(queue.pending.value.isEmpty())
        assertFalse(queue.resolve(id, true))
    }

    @Test fun atMostEightPendingAndCancelAllResolvesEveryWaiter() = runTest {
        val queue = McpApprovalQueue()
        val waiting = (1..8).map { async { queue.request("write", args) } }
        runCurrent()
        assertEquals(8, queue.pending.value.size)
        assertEquals(McpApprovalDecision.QUEUE_FULL, queue.request("ninth", args))
        queue.cancelAll()
        waiting.forEach { assertEquals(McpApprovalDecision.CANCELLED, it.await()) }
        assertTrue(queue.pending.value.isEmpty())
        val next = async { queue.request("next", args) }
        runCurrent()
        queue.resolve(queue.pending.value.single().id, false)
        assertEquals(McpApprovalDecision.REJECTED, next.await())
    }

    @Test fun cancelledClientCoroutineRemovesPendingRequest() = runTest {
        val queue = McpApprovalQueue()
        val request = async { queue.request("write", args) }
        runCurrent()
        request.cancel()
        runCurrent()
        assertTrue(queue.pending.value.isEmpty())
        assertTrue(request.isCancelled)
    }

    @Test fun cancellationInvalidatesRequestsNotYetEnqueued() = runTest {
        val queue = McpApprovalQueue()
        val previous = queue.generation
        queue.cancelAll()
        assertEquals(previous + 1, queue.generation)
        assertEquals(McpApprovalDecision.CANCELLED, queue.request("old", args, expectedGeneration = previous))
        assertTrue(queue.pending.value.isEmpty())
        val current = async { queue.request("current", args) }
        runCurrent()
        queue.resolve(queue.pending.value.single().id, true)
        assertEquals(McpApprovalDecision.APPROVED, current.await())
    }
}
