package com.kebiao.app.mcp

import com.kebiao.app.data.ScheduleCourse
import com.kebiao.app.data.ScheduleExam
import com.kebiao.app.data.ScheduleExport
import com.kebiao.app.data.ScheduleOverrideRecord
import com.kebiao.app.data.ScheduleRules
import com.kebiao.app.notifications.LessonPeriod
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

@OptIn(ExperimentalCoroutinesApi::class)
class McpToolRegistryTest {
    @Test fun clearPreservesRulesChangedWhileApprovalWasPending() = runTest {
        val store = InMemoryScheduleStore()
        val original = ScheduleRules(null, false, listOf(LessonPeriod("08:00", "08:50")))
        store.value = original.apply(ScheduleExport(courses = listOf(ScheduleCourse("c", "Math", 1, 1, 1))))
        val queue = McpApprovalQueue()
        val tools = registry(store, queue)
        val clear = async { tools.callTool("schedule.clear", args("{}")) }
        runCurrent()
        val updated = original.copy(periods = listOf(LessonPeriod("09:15", "10:00")))
        store.value = updated.apply(store.value)
        queue.resolve(queue.pending.value.single().id, true)
        assertFalse(clear.await().isError!!)
        assertEquals(updated, ScheduleRules.read(store.value))
        val whole = tools.callTool("schedule.list", args("{}")).data()
        val day = tools.callTool("schedule.for_date", args("""{"date":"2026-09-07"}""")).data()
        assertEquals(whole["periods"], day["periods"])
        assertTrue(store.value.courses.isEmpty())
    }
    @Test fun eventsAreCreatedListedByDateEditedAndDeletedWithoutLosingNotes() = runTest {
        val store = InMemoryScheduleStore()
        val registry = registry(store)
        val input = args("""{"id":"event","subject":"读书会","date":"2026-09-07","type":"EVENT","note":"带上阅读笔记","locationNote":"东门","building":"图书馆","room":"302"}""")
        val created = registry.callTool("schedule.upsert_exam", input)
        assertFalse(created.isError!!)
        val saved = created.data().getValue("after").jsonObject
        assertEquals(JsonPrimitive("EVENT"), saved["type"])
        assertEquals(JsonPrimitive("带上阅读笔记"), saved["note"])
        assertEquals(JsonPrimitive("东门"), saved["locationNote"])
        assertEquals(saved, registry.callTool("schedule.list_exams", empty).data().getValue("exams").jsonArray.single())
        assertEquals(saved, registry.callTool("schedule.for_date", args("""{"date":"2026-09-07"}""")).data().getValue("exams").jsonArray.single())
        val edited = registry.callTool("schedule.upsert_exam", JsonObject(input + ("note" to JsonPrimitive("改到线上"))))
        assertEquals(JsonPrimitive("改到线上"), edited.data().getValue("after").jsonObject["note"])
        assertEquals(JsonPrimitive("invalid_arguments"), registry.callTool("schedule.upsert_exam", JsonObject(input + ("type" to JsonPrimitive("COURSE")))).error())
        assertFalse(registry.callTool("schedule.delete_exam", args("""{"id":"event"}""")).isError!!)
        assertTrue(store.value.exams.isEmpty())
    }

    @Test fun disabledParityReturnsOddCoursesAsWeeklyWithoutClaimingKnownParity() = runTest {
        val store = InMemoryScheduleStore()
        store.value = ScheduleExport(courses = listOf(ScheduleCourse("odd", "数学", 1, 1, 2, "ODD")))
        val registry = McpToolRegistry(store, McpApprovalQueue(), { false }, { LocalDate.parse("2026-09-07") }, { false })
        val result = registry.callTool("schedule.for_date", args("""{"date":"2026-09-14"}""")).data()
        assertEquals(JsonPrimitive(false), result["parityEnabled"])
        assertEquals(JsonPrimitive(false), result["weekParityKnown"])
        assertEquals(1, result.getValue("courses").jsonArray.size)
    }

    private val empty = JsonObject(emptyMap())
    private fun args(value: String) = Json.parseToJsonElement(value).jsonObject
    private fun registry(store: ScheduleStore, queue: McpApprovalQueue = McpApprovalQueue(), confirmation: suspend () -> Boolean = { false }, start: LocalDate? = LocalDate.parse("2026-09-07")) =
        McpToolRegistry(store, queue, confirmation, { start })
    private fun CallToolResult.data(): JsonObject {
        assertEquals(structuredContent, Json.parseToJsonElement((content.single() as TextContent).text).jsonObject)
        return structuredContent!!
    }
    private fun CallToolResult.error() = data().getValue("error")

    @Test fun sdkRegistersCompleteSchemasWithoutClientApprovalArgument() {
        val server = registry(InMemoryScheduleStore()).createServer()
        assertEquals(12, server.tools.size)
        val tool = server.tools.getValue("schedule.upsert_course").tool
        assertEquals(listOf("name", "weekday", "startPeriod", "endPeriod"), tool.inputSchema.required)
        assertFalse(tool.inputSchema.properties!!.containsKey("confirmed"))
        assertTrue(server.tools.getValue("schedule.list").tool.annotations!!.readOnlyHint == true)
    }

    @Test fun completeCourseExamAndOverrideCrudReturnsSavedRecords() = runTest {
        val store = InMemoryScheduleStore()
        val registry = registry(store)
        val course = args("""{"id":"c","name":"Math","weekday":1,"startPeriod":1,"endPeriod":2,"weekRule":"ODD","building":"A","room":"101","locationNote":"East"}""")
        val created = registry.callTool("schedule.upsert_course", course)
        assertFalse(created.isError!!)
        assertEquals(JsonPrimitive(200L), created.data().getValue("after").jsonObject["updatedAtEpochMillis"])
        val updated = registry.callTool("schedule.upsert_course", JsonObject(course + ("name" to JsonPrimitive("Physics"))))
        assertEquals(JsonPrimitive("Math"), updated.data().getValue("before").jsonObject["name"])
        assertEquals("Physics", store.value.courses.single().name)
        assertEquals(100L, store.value.courses.single().createdAtEpochMillis)

        assertFalse(registry.callTool("schedule.upsert_exam", args("""{"id":"e","subject":"Final","date":"2026-09-14","time":"09:30","building":"B","room":"202"}""")).isError!!)
        assertFalse(registry.callTool("schedule.upsert_exam", args("""{"id":"e","subject":"Updated final","date":"2026-09-15"}""")).isError!!)
        assertEquals("Updated final", store.value.exams.single().subject)
        assertEquals(null, store.value.exams.single().time)
        assertFalse(registry.callTool("schedule.upsert_override", args("""{"date":"2026-09-14","replacementWeekday":2,"note":"Holiday"}""")).isError!!)
        assertFalse(registry.callTool("schedule.upsert_override", args("""{"date":"2026-09-14","replacementWeekday":3}""")).isError!!)
        assertEquals(3, store.value.overrides.single().replacementWeekday)

        val listed = registry.callTool("schedule.list", empty).data()
        assertEquals("Physics", (listed.getValue("courses").jsonArray.single().jsonObject.getValue("name") as JsonPrimitive).content)
        listOf("courses", "exams", "overrides").forEach { collection ->
            assertEquals(listed[collection], registry.callTool("schedule.list_$collection", empty).data()[collection])
        }
        listOf("course" to """{"id":"c"}""", "exam" to """{"id":"e"}""", "override" to """{"date":"2026-09-14"}""").forEach { (kind, parameters) ->
            val deleted = registry.callTool("schedule.delete_$kind", args(parameters))
            assertFalse(deleted.isError!!)
            assertEquals(JsonNull, deleted.data()["after"])
            assertEquals(JsonPrimitive("not_found"), registry.callTool("schedule.delete_$kind", args(parameters)).error())
        }
        assertTrue(store.value.courses.isEmpty() && store.value.exams.isEmpty() && store.value.overrides.isEmpty())
        assertEquals(0, store.replacements)
    }

    @Test fun strictTypesRequiredFieldsRangesDatesAndUnknownArgumentsAreRejected() = runTest {
        val store = InMemoryScheduleStore()
        val registry = registry(store)
        val good = args("""{"name":"Math","weekday":1,"startPeriod":1,"endPeriod":2}""")
        val invalidCourses = listOf(
            empty, JsonObject(good - "name"),
            JsonObject(good + ("weekday" to JsonPrimitive("1"))),
            JsonObject(good + ("weekday" to JsonPrimitive(1.5))),
            JsonObject(good + ("weekday" to JsonPrimitive(true))),
            JsonObject(good + ("weekday" to JsonPrimitive(8))),
            JsonObject(good + ("startPeriod" to JsonPrimitive(3))),
            JsonObject(good + ("endPeriod" to JsonPrimitive(13))),
            JsonObject(good + ("name" to JsonPrimitive(10))),
            JsonObject(good + ("room" to JsonNull)),
            JsonObject(good + ("weekRule" to JsonPrimitive("unknown"))),
            JsonObject(good + ("confirmed" to JsonPrimitive(true))),
        )
        invalidCourses.forEach { assertEquals(JsonPrimitive("invalid_arguments"), registry.callTool("schedule.upsert_course", it).error()) }
        listOf("2026-02-30", "09/07/2026", "2026-9-7").forEach {
            assertEquals(JsonPrimitive("invalid_arguments"), registry.callTool("schedule.for_date", args("""{"date":"$it"}""")).error())
        }
        assertEquals(JsonPrimitive("invalid_arguments"), registry.callTool("schedule.upsert_exam", args("""{"subject":"Final","date":"2026-09-07","time":"25:00"}""")).error())
        assertEquals(JsonPrimitive("invalid_arguments"), registry.callTool("schedule.upsert_override", args("""{"date":"2026-09-07","replacementWeekday":0}""")).error())
        assertEquals(JsonPrimitive("invalid_arguments"), registry.callTool("schedule.clear", args("""{"confirmed":true}""")).error())
        assertEquals(JsonPrimitive("unknown_tool"), registry.callTool("approval.resolve", args("""{"approved":true}""")).error())
        assertTrue(store.value.courses.isEmpty())
        assertEquals(0, store.replacements)
    }

    @Test fun localApprovalControlsWritesAndReadsCurrentModeForEveryRequest() = runTest {
        val store = InMemoryScheduleStore()
        val queue = McpApprovalQueue()
        var confirmation = true
        val registry = registry(store, queue, { confirmation })
        val parameters = args("""{"name":"Math","weekday":1,"startPeriod":1,"endPeriod":2}""")
        val rejected = async { registry.callTool("schedule.upsert_course", parameters) }
        runCurrent()
        assertTrue(store.value.courses.isEmpty())
        assertEquals(JsonNull, queue.pending.value.single().arguments["before"])
        assertTrue(queue.resolve(queue.pending.value.single().id, false))
        assertEquals(JsonPrimitive("approval_rejected"), rejected.await().error())
        val approved = async { registry.callTool("schedule.upsert_course", parameters) }
        runCurrent()
        val preview = queue.pending.value.single()
        val proposedId = preview.arguments.getValue("after").jsonObject.getValue("id")
        queue.resolve(preview.id, true)
        assertEquals(proposedId, approved.await().data().getValue("after").jsonObject["id"])
        confirmation = false
        assertFalse(registry.callTool("schedule.upsert_course", parameters).isError!!)
        assertTrue(queue.pending.value.isEmpty())
        val clear = async { registry.callTool("schedule.clear", empty) }
        runCurrent()
        assertEquals(2, store.value.courses.size)
        assertEquals(2, queue.pending.value.single().arguments.getValue("before").jsonObject.getValue("courses").jsonArray.size)
        queue.resolve(queue.pending.value.single().id, true)
        assertFalse(clear.await().isError!!)
        assertTrue(store.value.courses.isEmpty())
        assertEquals(1, store.replacements)
    }

    @Test fun rejectsApprovalWhoseTargetChangedAndSurfacesCancellationAndTimeout() = runTest {
        val store = InMemoryScheduleStore()
        store.value = ScheduleExport(courses = listOf(ScheduleCourse("c", "Before", 1, 1, 2)))
        val queue = McpApprovalQueue(timeoutMillis = 100)
        val registry = registry(store, queue, { true })
        val pending = async { registry.callTool("schedule.delete_course", args("""{"id":"c"}""")) }
        runCurrent()
        store.value = store.value.copy(courses = listOf(store.value.courses.single().copy(name = "Changed")))
        queue.resolve(queue.pending.value.single().id, true)
        assertEquals(JsonPrimitive("schedule_changed"), pending.await().error())
        assertEquals("Changed", store.value.courses.single().name)
        val cancelled = async { registry.callTool("schedule.clear", empty) }
        runCurrent()
        queue.cancelAll()
        assertEquals(JsonPrimitive("approval_cancelled"), cancelled.await().error())
        assertEquals(JsonPrimitive("approval_timed_out"), registry.callTool("schedule.clear", empty).error())
        assertEquals(0, store.replacements)
    }

    @Test fun expiredSessionCannotWriteEvenAfterLocalApproval() = runTest {
        val store = InMemoryScheduleStore()
        val queue = McpApprovalQueue()
        val registry = registry(store, queue, { true })
        var authorized = true
        val request = async {
            registry.callTool("schedule.upsert_course", args("""{"name":"Math","weekday":1,"startPeriod":1,"endPeriod":2}"""), { authorized })
        }
        runCurrent()
        assertTrue(queue.resolve(queue.pending.value.single().id, true))
        authorized = false
        assertEquals(JsonPrimitive("unauthorized"), request.await().error())
        assertTrue(store.value.courses.isEmpty())
    }

    @Test fun cancelledGenerationCannotEnqueueAnOlderCall() = runTest {
        val store = InMemoryScheduleStore()
        val queue = McpApprovalQueue()
        val delayedStore = object : ScheduleStore by store {
            override suspend fun snapshot(): ScheduleExport {
                queue.cancelAll()
                return store.snapshot()
            }
        }
        val registry = registry(delayedStore, queue, { true })
        val result = registry.callTool("schedule.upsert_course", args("""{"name":"Math","weekday":1,"startPeriod":1,"endPeriod":2}"""))
        assertEquals(JsonPrimitive("approval_cancelled"), result.error())
        assertTrue(queue.pending.value.isEmpty())
        assertTrue(store.value.courses.isEmpty())
    }

    @Test fun authorizationIsRecheckedInsideWriteTransaction() = runTest {
        val store = InMemoryScheduleStore()
        var authorized = true
        val delayedStore = object : ScheduleStore by store {
            override suspend fun <T> transaction(block: suspend () -> T): T {
                authorized = false
                return block()
            }
        }
        val registry = registry(delayedStore)
        val result = registry.callTool("schedule.upsert_course", args("""{"name":"Math","weekday":1,"startPeriod":1,"endPeriod":2}"""), { authorized })
        assertEquals(JsonPrimitive("unauthorized"), result.error())
        assertTrue(store.value.courses.isEmpty())
    }

    @Test fun dateQueryUsesSharedOddEvenResolverAndOverrides() = runTest {
        val store = InMemoryScheduleStore()
        store.value = ScheduleExport(
            courses = listOf(ScheduleCourse("odd", "Odd Monday", 1, 1, 2, "ODD"), ScheduleCourse("even", "Even Tuesday", 2, 3, 4, "EVEN"), ScheduleCourse("all", "Every Tuesday", 2, 5, 6)),
            exams = listOf(ScheduleExam("e", "Final", "2026-09-14", "09:00")),
            overrides = listOf(ScheduleOverrideRecord("2026-09-14", 2, "Makeup")),
        )
        val registry = registry(store)
        val odd = registry.callTool("schedule.for_date", args("""{"date":"2026-09-07"}""")).data()
        assertEquals(JsonPrimitive("odd"), odd.getValue("courses").jsonArray.single().jsonObject["id"])
        val even = registry.callTool("schedule.for_date", args("""{"date":"2026-09-14"}""")).data()
        assertEquals(listOf(JsonPrimitive("even"), JsonPrimitive("all")), even.getValue("courses").jsonArray.map { it.jsonObject["id"] })
        assertEquals(JsonPrimitive(2), even["effectiveWeekday"])
        assertEquals(JsonPrimitive("Final"), even.getValue("exams").jsonArray.single().jsonObject["subject"])
        assertEquals(JsonPrimitive("Makeup"), even.getValue("override").jsonObject["note"])
        val unknown = registry(store, start = null).callTool("schedule.for_date", args("""{"date":"2026-09-14"}""")).data()
        assertEquals(JsonPrimitive(false), unknown["weekParityKnown"])
        assertEquals(JsonPrimitive("all"), unknown.getValue("courses").jsonArray.single().jsonObject["id"])
    }
}
