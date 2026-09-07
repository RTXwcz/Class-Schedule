package com.kebiao.app.mcp

import com.kebiao.app.data.ScheduleCourse
import com.kebiao.app.data.ScheduleExport
import com.kebiao.app.data.ScheduleRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException

interface ScheduleStore {
    suspend fun snapshot(): ScheduleExport
    suspend fun replaceAll(export: ScheduleExport)
    suspend fun upsertCourse(course: ScheduleCourse)
}

class RepositoryScheduleStore(private val repository: ScheduleRepository) : ScheduleStore {
    override suspend fun snapshot(): ScheduleExport = repository.snapshot()
    override suspend fun replaceAll(export: ScheduleExport) = repository.replaceAll(export)
    override suspend fun upsertCourse(course: ScheduleCourse) = repository.upsertCourse(course)
}

data class McpResult(val success: Boolean, val errorCode: String? = null, val data: Map<String, Any?> = emptyMap())

class McpToolRegistry(
    private val store: ScheduleStore,
    private val tokenStore: McpTokenStore,
    private val writeConfirmation: Boolean,
) {
    suspend fun dispatch(tool: String, params: Map<String, String>, token: String?): McpResult {
        if (tool == "schedule.list") {
            if (!tokenStore.isValid(token.orEmpty())) return McpResult(false, "unauthorized")
            val snapshot = store.snapshot()
            return McpResult(true, data = mapOf("courseCount" to snapshot.courses.size, "examCount" to snapshot.exams.size, "overrideCount" to snapshot.overrides.size))
        }
        return handleWrite(tool, params, token.orEmpty())
    }

    fun handle(tool: String, params: Map<String, String>, token: String? = null): McpResult {
        if (writeConfirmation && tool != "schedule.list" && params["confirmed"] != "true") {
            return McpResult(false, "confirmation_required")
        }
        if (!tokenStore.isValid(token.orEmpty())) return McpResult(false, "unauthorized")
        return when (tool) {
            "schedule.list" -> McpResult(true, data = mapOf("message" to "use snapshot endpoint"))
            else -> McpResult(false, "unknown_tool")
        }
    }

    suspend fun handleWrite(tool: String, params: Map<String, String>, token: String): McpResult {
        if (!tokenStore.isValid(token)) return McpResult(false, "unauthorized")
        if (writeConfirmation && params["confirmed"] != "true") return McpResult(false, "confirmation_required")
        return try { when (tool) {
            "schedule.add_course" -> {
                val course = ScheduleCourse(
                    id = params["id"] ?: UUID.randomUUID().toString(),
                    name = params["name"] ?: return McpResult(false, "name_required"),
                    weekday = params["weekday"]?.toIntOrNull() ?: return McpResult(false, "weekday_required"),
                    startPeriod = params["startPeriod"]?.toIntOrNull() ?: return McpResult(false, "start_required"),
                    endPeriod = params["endPeriod"]?.toIntOrNull() ?: return McpResult(false, "end_required"),
                    weekRule = params["weekRule"] ?: "ALL",
                    building = params["building"],
                    room = params["room"],
                    locationNote = params["locationNote"],
                    source = "MCP",
                )
                store.upsertCourse(course)
                McpResult(true)
            }
            "schedule.clear" -> {
                store.replaceAll(ScheduleExport())
                McpResult(true)
            }
            else -> McpResult(false, "unknown_tool")
        } } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (invalid: IllegalArgumentException) {
            McpResult(false, "invalid_arguments")
        } catch (failure: Exception) {
            McpResult(false, "storage_error")
        }
    }
}
