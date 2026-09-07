package com.kebiao.app.mcp

import com.kebiao.app.data.JsonScheduleCodec
import com.kebiao.app.data.ScheduleCourse
import com.kebiao.app.data.ScheduleExam
import com.kebiao.app.data.ScheduleExport
import com.kebiao.app.data.ScheduleOverrideRecord
import com.kebiao.app.data.ScheduleRepository
import com.kebiao.app.data.ScheduleRules
import com.kebiao.app.domain.ScheduleResolver
import com.kebiao.app.notifications.PeriodSchedule
import com.kebiao.app.notifications.LessonPeriod
import com.kebiao.app.domain.model.Course
import com.kebiao.app.domain.model.ScheduleOverride
import com.kebiao.app.domain.model.WeekRule
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

interface ScheduleStore {
    suspend fun <T> transaction(block: suspend () -> T): T
    suspend fun snapshot(): ScheduleExport
    suspend fun replaceAll(export: ScheduleExport)
    suspend fun upsertCourse(course: ScheduleCourse)
    suspend fun deleteCourse(id: String)
    suspend fun upsertExam(exam: ScheduleExam)
    suspend fun deleteExam(id: String)
    suspend fun upsertOverride(record: ScheduleOverrideRecord)
    suspend fun deleteOverride(date: String)
}

class RepositoryScheduleStore(private val repository: ScheduleRepository) : ScheduleStore {
    override suspend fun <T> transaction(block: suspend () -> T): T = repository.transaction(block)
    override suspend fun snapshot() = repository.snapshot()
    override suspend fun replaceAll(export: ScheduleExport) = repository.replaceAll(export)
    override suspend fun upsertCourse(course: ScheduleCourse) = repository.upsertCourse(course, source = "MCP")
    override suspend fun deleteCourse(id: String) = repository.deleteCourse(id, source = "MCP")
    override suspend fun upsertExam(exam: ScheduleExam) = repository.upsertExam(exam, source = "MCP")
    override suspend fun deleteExam(id: String) = repository.deleteExam(id, source = "MCP")
    override suspend fun upsertOverride(record: ScheduleOverrideRecord) = repository.upsertOverride(record, source = "MCP")
    override suspend fun deleteOverride(date: String) = repository.deleteOverride(date, source = "MCP")
}

class McpToolRegistry(
    private val store: ScheduleStore,
    private val approvalQueue: McpApprovalQueue,
    private val writeConfirmation: suspend () -> Boolean,
    private val semesterStart: suspend () -> LocalDate?,
    private val parityEnabled: suspend () -> Boolean = { true },
    private val periods: suspend () -> List<LessonPeriod> = { PeriodSchedule.defaults },
) {
    private val writes = Mutex()
    private val resolver = ScheduleResolver()

    fun createServer(isAuthorized: () -> Boolean = { true }): Server = Server(
        Implementation(name = "kebiao", version = "1.0.0"),
        ServerOptions(capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false))),
    ).apply {
        definitions.forEach { definition ->
            addTool(
                name = definition.name,
                description = definition.description,
                inputSchema = ToolSchema(properties = JsonObject(definition.properties), required = definition.required),
                toolAnnotations = ToolAnnotations(readOnlyHint = !definition.write, destructiveHint = definition.write, openWorldHint = false),
            ) { request -> callTool(definition.name, request.arguments ?: JsonObject(emptyMap()), isAuthorized) }
        }
    }

    suspend fun callTool(name: String, arguments: JsonObject, isAuthorized: () -> Boolean = { true }): CallToolResult {
        val generation = approvalQueue.generation
        if (!isAuthorized()) return failure("unauthorized", "Session authorization has expired")
        val definition = definitions.firstOrNull { it.name == name }
            ?: return failure("unknown_tool", "Unknown tool: $name")
        return try {
            validate(arguments, definition)
            if (definition.write) write(name, arguments, isAuthorized, generation) else read(name, arguments)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (invalid: IllegalArgumentException) {
            failure("invalid_arguments", invalid.message ?: "Invalid arguments")
        } catch (invalid: DateTimeParseException) {
            failure("invalid_arguments", "Invalid ISO date or time")
        } catch (failure: Exception) {
            failure("storage_error", "Unable to access schedule storage")
        }
    }

    private suspend fun read(name: String, args: JsonObject): CallToolResult {
        val snapshot = store.snapshot()
        val document = encode(snapshot)
        return success(when (name) {
            "schedule.list" -> document
            "schedule.list_courses" -> buildJsonObject { put("courses", document.getValue("courses")) }
            "schedule.list_exams" -> buildJsonObject { put("exams", document.getValue("exams")) }
            "schedule.list_overrides" -> buildJsonObject { put("overrides", document.getValue("overrides")) }
            "schedule.for_date" -> {
                val date = date(args.string("date"))
                val rules = if (snapshot.extraFields["rulesVersion"] == JsonPrimitive(1)) ScheduleRules.read(snapshot) else null
                val start = rules?.semesterStartDate?.let(LocalDate::parse) ?: if (rules == null) semesterStart() else null
                val parity = rules?.parityEnabled ?: parityEnabled()
                val times = rules?.periods ?: periods()
                val override = snapshot.overrides.firstOrNull { it.date == date.toString() }
                val effective = resolver.resolve(date, start, snapshot.courses.map { it.domain() }, snapshot.overrides.map {
                    ScheduleOverride(LocalDate.parse(it.date), it.replacementWeekday, it.note)
                }, parityEnabled = parity)
                val byId = snapshot.courses.associateBy { it.id }
                buildJsonObject {
                    put("date", date.toString())
                    put("semesterStart", start?.toString()?.let(::JsonPrimitive) ?: JsonNull)
                    put("parityEnabled", parity)
                    put("periods", Json.parseToJsonElement(PeriodSchedule.encode(times)))
                    put("weekParityKnown", parity && start != null && !date.isBefore(start))
                    put("effectiveWeekday", override?.replacementWeekday ?: date.dayOfWeek.value)
                    put("courses", JsonArray(effective.map { encode(byId.getValue(it.course.id)) }))
                    put("exams", JsonArray(snapshot.exams.filter { it.date == date.toString() }.map(::encode)))
                    put("override", override?.let(::encode) ?: JsonNull)
                }
            }
            else -> error("Unhandled read tool")
        })
    }

    private data class Mutation(val collection: String, val key: String?, val before: JsonElement, val after: JsonElement, val apply: suspend () -> Unit)

    private suspend fun configuredPeriods(snapshot: ScheduleExport): List<LessonPeriod> =
        if (snapshot.extraFields["rulesVersion"] == JsonPrimitive(1)) ScheduleRules.read(snapshot).periods else periods()

    private suspend fun write(name: String, args: JsonObject, isAuthorized: () -> Boolean, generation: Long): CallToolResult {
        val snapshot = store.snapshot()
        val mutation = when (name) {
            "schedule.upsert_course" -> {
                val id = args.optionalString("id") ?: UUID.randomUUID().toString()
                val record = ScheduleCourse(id, args.string("name"), args.integer("weekday"), args.integer("startPeriod"),
                    args.integer("endPeriod"), args.optionalString("weekRule") ?: "ALL", args.optionalString("building"),
                    args.optionalString("room"), args.optionalString("locationNote"), source = "MCP",
                    teacher = args.optionalString("teacher"), courseNote = args.optionalString("courseNote"),
                    weeks = (args["weeks"] as? JsonArray)?.map { (it as JsonPrimitive).intOrNull!! }.orEmpty())
                record.domain()
                require(record.endPeriod <= configuredPeriods(snapshot).size) { "Course exceeds configured daily period count" }
                Mutation("courses", id, snapshot.courses.find { it.id == id }?.let(::encode) ?: JsonNull, encode(record)) {
                    require(record.endPeriod <= configuredPeriods(store.snapshot()).size) { "Daily period count changed; review course periods" }
                    store.upsertCourse(record)
                }
            }
            "schedule.upsert_exam" -> {
                val id = args.optionalString("id") ?: UUID.randomUUID().toString()
                val examDate = date(args.string("date")).toString()
                val time = args.optionalString("time")?.also { require(it.matches(Regex("\\d{2}:\\d{2}"))) { "time must be HH:mm" }; LocalTime.parse(it) }
                val record = ScheduleExam(id, args.string("subject"), examDate, time, args.optionalString("building"),
                    args.optionalString("room"), args.optionalString("locationNote"), source = "MCP",
                    type = args.optionalString("type") ?: "EXAM", note = args.optionalString("note"))
                Mutation("exams", id, snapshot.exams.find { it.id == id }?.let(::encode) ?: JsonNull, encode(record)) { store.upsertExam(record) }
            }
            "schedule.upsert_override" -> {
                val record = ScheduleOverrideRecord(date(args.string("date")).toString(), args.integer("replacementWeekday"), args.optionalString("note"))
                Mutation("overrides", record.date, snapshot.overrides.find { it.date == record.date }?.let(::encode) ?: JsonNull, encode(record)) { store.upsertOverride(record) }
            }
            "schedule.delete_course" -> {
                val id = args.string("id")
                Mutation("courses", id, snapshot.courses.find { it.id == id }?.let(::encode) ?: JsonNull, JsonNull) { store.deleteCourse(id) }
            }
            "schedule.delete_exam" -> {
                val id = args.string("id")
                Mutation("exams", id, snapshot.exams.find { it.id == id }?.let(::encode) ?: JsonNull, JsonNull) { store.deleteExam(id) }
            }
            "schedule.delete_override" -> {
                val date = date(args.string("date")).toString()
                Mutation("overrides", date, snapshot.overrides.find { it.date == date }?.let(::encode) ?: JsonNull, JsonNull) { store.deleteOverride(date) }
            }
            "schedule.clear" -> Mutation("all", null, records(snapshot), records(ScheduleExport())) {
                store.replaceAll(store.snapshot().copy(courses = emptyList(), exams = emptyList(), overrides = emptyList(),
                    source = "MCP", updatedAt = java.time.Instant.now().toString()))
            }
            else -> error("Unhandled write tool")
        }
        if (mutation.after == JsonNull && mutation.before == JsonNull) return failure("not_found", "No matching record exists")
        val preview = buildJsonObject {
            put("collection", mutation.collection)
            mutation.key?.let { put("key", it) }
            put("before", mutation.before)
            put("after", mutation.after)
        }
        if (!isAuthorized()) return failure("unauthorized", "Session authorization has expired")
        if (name == "schedule.clear" || writeConfirmation()) {
            val decision = approvalQueue.request(name, preview, expectedGeneration = generation)
            if (!isAuthorized()) return failure("unauthorized", "Session authorization has expired")
            if (decision != McpApprovalDecision.APPROVED) return failure("approval_${decision.name.lowercase()}", "App approval: ${decision.name.lowercase()}")
        }
        return writes.withLock { store.transaction {
            // Approval is bound to the version shown in the app, including the full clear scope.
            val current = store.snapshot()
            val currentValue = when (mutation.collection) {
                "courses" -> current.courses.find { it.id == mutation.key }?.let(::encode) ?: JsonNull
                "exams" -> current.exams.find { it.id == mutation.key }?.let(::encode) ?: JsonNull
                "overrides" -> current.overrides.find { it.date == mutation.key }?.let(::encode) ?: JsonNull
                else -> records(current)
            }
            if (currentValue != mutation.before) return@transaction failure("schedule_changed", "Schedule changed after this request; submit it again")
            if (!isAuthorized()) return@transaction failure("unauthorized", "Session authorization has expired")
            if (approvalQueue.generation != generation) return@transaction failure("approval_cancelled", "Request was cancelled")
            mutation.apply()
            val saved = store.snapshot()
            val actual = when (mutation.collection) {
                "courses" -> saved.courses.find { it.id == mutation.key }?.let(::encode) ?: JsonNull
                "exams" -> saved.exams.find { it.id == mutation.key }?.let(::encode) ?: JsonNull
                "overrides" -> saved.overrides.find { it.date == mutation.key }?.let(::encode) ?: JsonNull
                else -> records(saved)
            }
            success(buildJsonObject {
                put("tool", name)
                put("before", mutation.before)
                put("after", actual)
            })
        } }
    }

    private fun validate(args: JsonObject, definition: Definition) {
        require(args.keys.all { it in definition.properties }) { "Unknown arguments: ${(args.keys - definition.properties.keys).joinToString()}" }
        definition.required.forEach { require(it in args) { "$it is required" } }
        args.forEach { (key, value) ->
            val property = definition.properties.getValue(key)
            val type = (property.getValue("type") as JsonPrimitive).content
            val primitive = value as? JsonPrimitive
            when (type) {
                "array" -> {
                    require(value is JsonArray && value.size <= 60) { "$key must be an array of at most 60 weeks" }
                    require(value.all { it is JsonPrimitive && !it.isString && it.intOrNull in 1..60 }) { "$key values must be integers from 1 to 60" }
                    require(value.distinct().size == value.size) { "$key cannot contain duplicates" }
                }
                "string" -> {
                    require(primitive != null && primitive.isString && primitive.content.isNotBlank()) { "$key must be a nonblank string" }
                    require(primitive.content.length <= 2000) { "$key exceeds 2000 characters" }
                    val choices = property["enum"] as? JsonArray
                    require(choices == null || value in choices) { "$key is not a supported value" }
                }
                "integer" -> {
                    require(primitive != null && !primitive.isString && primitive.intOrNull != null) { "$key must be an integer" }
                    val number = primitive.intOrNull!!
                    val min = (property.getValue("minimum") as JsonPrimitive).intOrNull!!
                    val max = (property.getValue("maximum") as JsonPrimitive).intOrNull!!
                    require(number in min..max) { "$key must be between $min and $max" }
                }
            }
        }
    }

    private fun date(value: String): LocalDate {
        require(value.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { "date must be YYYY-MM-DD" }
        return LocalDate.parse(value)
    }

    private fun ScheduleCourse.domain() = Course(id, name, weekday, startPeriod, endPeriod, WeekRule.valueOf(weekRule), building, room, locationNote, source, createdAtEpochMillis, updatedAtEpochMillis, teacher, weeks, courseNote)
    private fun JsonObject.string(key: String) = (getValue(key) as JsonPrimitive).content
    private fun JsonObject.optionalString(key: String) = (get(key) as? JsonPrimitive)?.content
    private fun JsonObject.integer(key: String) = (getValue(key) as JsonPrimitive).intOrNull!!
    private fun encode(export: ScheduleExport) = Json.parseToJsonElement(JsonScheduleCodec.encode(export)).jsonObject
    private fun encode(course: ScheduleCourse) = (encode(ScheduleExport(courses = listOf(course))).getValue("courses") as JsonArray).single()
    private fun encode(exam: ScheduleExam) = (encode(ScheduleExport(exams = listOf(exam))).getValue("exams") as JsonArray).single()
    private fun encode(record: ScheduleOverrideRecord) = (encode(ScheduleExport(overrides = listOf(record))).getValue("overrides") as JsonArray).single()
    private fun records(export: ScheduleExport) = JsonObject(encode(export).filterKeys { it in setOf("courses", "exams", "overrides") })
    private fun success(data: JsonObject) = CallToolResult(content = listOf(TextContent(data.toString())), isError = false, structuredContent = data)
    private fun failure(code: String, message: String): CallToolResult {
        val data = buildJsonObject { put("error", code); put("message", message) }
        return CallToolResult(content = listOf(TextContent(data.toString())), isError = true, structuredContent = data)
    }

    private data class Definition(val name: String, val description: String, val properties: Map<String, JsonObject> = emptyMap(), val required: List<String> = emptyList(), val write: Boolean = false)

    companion object {
        private fun text(format: String? = null, choices: List<String>? = null) = buildJsonObject {
            put("type", "string"); put("minLength", 1); put("maxLength", 2000)
            format?.let { put("format", it) }
            choices?.let { put("enum", JsonArray(it.map(::JsonPrimitive))) }
        }
        private fun number(max: Int) = buildJsonObject { put("type", "integer"); put("minimum", 1); put("maximum", max) }
        private val location = mapOf("building" to text(), "room" to text(), "locationNote" to text())
        private val courseDetails = mapOf("teacher" to text(), "courseNote" to text(), "weeks" to buildJsonObject {
            put("type", "array"); put("items", number(60)); put("maxItems", 60); put("uniqueItems", true)
        })
        private val definitions = listOf(
            Definition("schedule.list", "Read the complete course, exam, and date override dataset."),
            Definition("schedule.list_courses", "Read complete course records including IDs, week rules, periods and locations."),
            Definition("schedule.list_exams", "Read all dated items (EXAM and EVENT), including IDs, type, dates, times, locations and note."),
            Definition("schedule.list_overrides", "Read all date overrides and replacement weekdays."),
            Definition("schedule.for_date", "Read effective courses and exams on an ISO date, applying semester odd/even weeks and date overrides. If semester start is unset, odd/even courses cannot be resolved.", mapOf("date" to text("date")), listOf("date")),
            Definition("schedule.upsert_course", "Create or fully replace one course. Periods must fit the daily timetable configured in the app (maximum 48). Omit id to create. Optional fields omitted on replacement are cleared. weeks is a list of semester weeks (empty means all); requires semester start to resolve. May require approval in the app.", location + courseDetails + mapOf("id" to text(), "name" to text(), "weekday" to number(7), "startPeriod" to number(48), "endPeriod" to number(48), "weekRule" to text(choices = listOf("ALL", "ODD", "EVEN"))), listOf("name", "weekday", "startPeriod", "endPeriod"), true),
            Definition("schedule.delete_course", "Delete a course by id. May require approval in the app.", mapOf("id" to text()), listOf("id"), true),
            Definition("schedule.upsert_exam", "Create or fully replace one dated item. type is EXAM (default) or EVENT. Both are stored in exams for compatibility. Omit id to create. time is HH:mm; omit for all-day. note is general text, separate from locationNote. Optional omitted fields are cleared. May require approval in the app.", location + mapOf("id" to text(), "subject" to text(), "date" to text("date"), "time" to text(), "type" to text(choices = listOf("EXAM", "EVENT")), "note" to text()), listOf("subject", "date"), true),
            Definition("schedule.delete_exam", "Delete an exam or event by id. May require approval in the app.", mapOf("id" to text()), listOf("id"), true),
            Definition("schedule.upsert_override", "Set which weekday timetable applies to a date. The date's semester parity still applies. May require approval in the app.", mapOf("date" to text("date"), "replacementWeekday" to number(7), "note" to text()), listOf("date", "replacementWeekday"), true),
            Definition("schedule.delete_override", "Delete an override by ISO date. May require approval in the app.", mapOf("date" to text("date")), listOf("date"), true),
            Definition("schedule.clear", "Delete all courses, exams and date overrides. Always requires explicit approval in the app; no client argument can approve this operation.", write = true),
        )
    }
}
