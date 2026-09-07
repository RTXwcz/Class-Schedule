package com.kebiao.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

data class ScheduleCourse(
    val id: String,
    val name: String,
    val weekday: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weekRule: String = "ALL",
    val building: String? = null,
    val room: String? = null,
    val locationNote: String? = null,
    val source: String = "IMPORT",
    val createdAtEpochMillis: Long = 0L,
    val updatedAtEpochMillis: Long = 0L,
)

data class ScheduleExam(
    val id: String,
    val subject: String,
    val date: String,
    val time: String? = null,
    val building: String? = null,
    val room: String? = null,
    val locationNote: String? = null,
    val source: String = "IMPORT",
    val createdAtEpochMillis: Long = 0L,
    val updatedAtEpochMillis: Long = 0L,
)

data class ScheduleOverrideRecord(
    val date: String,
    val replacementWeekday: Int,
    val note: String? = null,
)

data class ScheduleExport(
    val schemaVersion: Int = 1,
    val datasetId: String = "default",
    val updatedAt: String = "",
    val source: String = "IMPORT",
    val courses: List<ScheduleCourse> = emptyList(),
    val exams: List<ScheduleExam> = emptyList(),
    val overrides: List<ScheduleOverrideRecord> = emptyList(),
    val extraFields: Map<String, JsonElement> = emptyMap(),
)

/** Shared JSON contract for the Web/PWA and native app. */
object JsonScheduleCodec {
    private val parser = Json { ignoreUnknownKeys = true }
    private val knownTopLevel = setOf("schemaVersion", "datasetId", "updatedAt", "source", "courses", "exams", "overrides")

    fun decode(json: String): ScheduleExport {
        val document = try {
            parser.parseToJsonElement(json)
        } catch (error: Exception) {
            throw IllegalArgumentException("Schedule JSON must be an object or legacy array", error)
        }
        val legacyArray = document as? JsonArray
        if (legacyArray != null) return decodeLegacyArray(legacyArray)
        val root = document as? JsonObject
            ?: throw IllegalArgumentException("Schedule JSON must be an object or legacy array")
        require(listOf("courses", "exams", "overrides").any { it in root }) { "JSON 中缺少课程、考试或调休数据" }
        val courses = root["courses"].asArray().mapIndexed { index, item -> requireNotNull(decodeCourse(item)) { "第 ${index + 1} 条课程无效，未导入任何数据" } }
        val exams = root["exams"].asArray().mapIndexed { index, item -> requireNotNull(decodeExam(item)) { "第 ${index + 1} 条考试无效，未导入任何数据" } }
        val overrides = root["overrides"].asArray().mapIndexed { index, item -> requireNotNull(decodeOverride(item)) { "第 ${index + 1} 条调休无效，未导入任何数据" } }
        return ScheduleExport(
            schemaVersion = root.int("schemaVersion") ?: 1,
            datasetId = root.string("datasetId")?.takeIf(String::isNotBlank) ?: UUID.randomUUID().toString(),
            updatedAt = root.string("updatedAt")?.takeIf(String::isNotBlank) ?: Instant.now().toString(),
            source = root.string("source")?.takeIf(String::isNotBlank) ?: "IMPORT",
            courses = courses,
            exams = exams,
            overrides = overrides,
            extraFields = root.filterKeys { it !in knownTopLevel },
        )
    }

    private fun decodeLegacyArray(array: JsonArray): ScheduleExport {
        val courses = mutableListOf<ScheduleCourse>()
        val exams = mutableListOf<ScheduleExam>()
        array.forEachIndexed { index, item ->
            val course = decodeCourse(item)
            val exam = if (course == null) decodeExam(item) else null
            when {
                course != null -> courses.add(course)
                exam != null -> exams.add(exam)
                else -> error("第 ${index + 1} 条数据无效，未导入任何数据")
            }
        }
        return ScheduleExport(
            datasetId = UUID.randomUUID().toString(),
            updatedAt = Instant.now().toString(),
            exams = exams,
            courses = courses,
            source = "WEB_IMPORT",
        )
    }

    fun encode(export: ScheduleExport): String {
        val root = buildJsonObject {
            export.extraFields.forEach { (key, value) -> put(key, value) }
            put("schemaVersion", export.schemaVersion)
            put("datasetId", export.datasetId)
            put("updatedAt", export.updatedAt.ifBlank { Instant.now().toString() })
            put("source", export.source)
            put("courses", buildJsonArray { export.courses.forEach { add(encodeCourse(it)) } })
            put("exams", buildJsonArray { export.exams.forEach { add(encodeExam(it)) } })
            put("overrides", buildJsonArray { export.overrides.forEach { add(encodeOverride(it)) } })
        }
        return parser.encodeToString(JsonObject.serializer(), root)
    }

    private fun decodeCourse(element: JsonElement): ScheduleCourse? {
        val obj = element as? JsonObject ?: return null
        val id = obj.string("id")?.takeIf(String::isNotBlank) ?: return null
        val name = obj.string("name")?.trim()?.takeIf(String::isNotBlank) ?: return null
        val weekday = obj.int("weekday") ?: obj.int("day") ?: return null
        val start = obj.int("startPeriod") ?: obj.int("start") ?: return null
        val end = obj.int("endPeriod") ?: obj.int("end") ?: return null
        if (weekday !in 1..7 || start !in 1..12 || end !in 1..12 || start > end) return null
        val location = obj.string("location")?.trim()?.takeIf(String::isNotBlank)
        val modernBuilding = obj.string("building")?.trim()?.takeIf(String::isNotBlank)
        val modernRoom = obj.string("room")?.trim()?.takeIf(String::isNotBlank)
        val split = splitLocation(location)
        return ScheduleCourse(
            id = id,
            name = name,
            weekday = weekday,
            startPeriod = start,
            endPeriod = end,
            weekRule = normalizeWeekRule(obj.string("weekRule") ?: obj.string("week")),
            building = modernBuilding ?: split.first,
            room = modernRoom ?: split.second,
            locationNote = obj.string("locationNote") ?: if (split.first == null && split.second == null) location else null,
            source = obj.string("source")?.ifBlank { "IMPORT" } ?: "IMPORT",
            createdAtEpochMillis = obj.long("createdAtEpochMillis") ?: 0L,
            updatedAtEpochMillis = obj.long("updatedAtEpochMillis") ?: 0L,
        )
    }

    private fun decodeExam(element: JsonElement): ScheduleExam? {
        val obj = element as? JsonObject ?: return null
        val id = obj.string("id")?.takeIf(String::isNotBlank) ?: return null
        val subject = (obj.string("subject") ?: obj.string("name"))?.trim()?.takeIf(String::isNotBlank) ?: return null
        val date = obj.string("date")?.trim()?.takeIf(String::isNotBlank) ?: return null
        val location = obj.string("location")?.trim()?.takeIf(String::isNotBlank)
        val split = splitLocation(location)
        return ScheduleExam(
            id = id,
            subject = subject,
            date = date,
            time = obj.string("time"),
            building = obj.string("building")?.takeIf(String::isNotBlank) ?: split.first,
            room = obj.string("room")?.takeIf(String::isNotBlank) ?: split.second,
            locationNote = obj.string("locationNote") ?: if (split.first == null && split.second == null) location else null,
            source = obj.string("source")?.ifBlank { "IMPORT" } ?: "IMPORT",
            createdAtEpochMillis = obj.long("createdAtEpochMillis") ?: 0L,
            updatedAtEpochMillis = obj.long("updatedAtEpochMillis") ?: 0L,
        )
    }

    private fun decodeOverride(element: JsonElement): ScheduleOverrideRecord? {
        val obj = element as? JsonObject ?: return null
        val date = obj.string("date")?.takeIf(String::isNotBlank) ?: return null
        val weekday = obj.int("replacementWeekday") ?: obj.int("day") ?: return null
        return weekday.takeIf { it in 1..7 }?.let { ScheduleOverrideRecord(date, it, obj.string("note")) }
    }

    private fun encodeCourse(course: ScheduleCourse) = buildJsonObject {
        put("id", course.id)
        put("name", course.name)
        put("weekday", course.weekday)
        put("startPeriod", course.startPeriod)
        put("endPeriod", course.endPeriod)
        put("weekRule", normalizeWeekRule(course.weekRule))
        course.building?.let { put("building", it) }
        course.room?.let { put("room", it) }
        course.locationNote?.let { put("locationNote", it) }
        put("source", course.source)
        put("createdAtEpochMillis", course.createdAtEpochMillis)
        put("updatedAtEpochMillis", course.updatedAtEpochMillis)
    }

    private fun encodeExam(exam: ScheduleExam) = buildJsonObject {
        put("id", exam.id)
        put("subject", exam.subject)
        put("date", exam.date)
        exam.time?.let { put("time", it) }
        exam.building?.let { put("building", it) }
        exam.room?.let { put("room", it) }
        exam.locationNote?.let { put("locationNote", it) }
        put("source", exam.source)
        put("createdAtEpochMillis", exam.createdAtEpochMillis)
        put("updatedAtEpochMillis", exam.updatedAtEpochMillis)
    }

    private fun encodeOverride(override: ScheduleOverrideRecord) = buildJsonObject {
        put("date", override.date)
        put("replacementWeekday", override.replacementWeekday)
        override.note?.let { put("note", it) }
    }

    private fun normalizeWeekRule(value: String?): String = when (value?.trim()?.lowercase()) {
        "odd", "single", "单", "单周" -> "ODD"
        "even", "double", "双", "双周" -> "EVEN"
        null, "", "all", "每周", "全部", "全周" -> "ALL"
        else -> error("无法识别的周次：$value")
    }

    private fun splitLocation(location: String?): Pair<String?, String?> {
        if (location.isNullOrBlank()) return null to null
        val pieces = location.trim().split(Regex("\\s+"))
        if (pieces.size < 2) return null to null
        val room = pieces.last()
        return if (room.matches(Regex("[A-Za-z]?[A-Za-z0-9-]*\\d[A-Za-z0-9-]*"))) {
            pieces.dropLast(1).joinToString(" ").ifBlank { null } to room
        } else null to null
    }

    private fun JsonElement?.asArray(): JsonArray = when (this) {
        null -> JsonArray(emptyList())
        is JsonArray -> this
        else -> error("课程、考试和调休必须为 JSON 数组")
    }
    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
}
