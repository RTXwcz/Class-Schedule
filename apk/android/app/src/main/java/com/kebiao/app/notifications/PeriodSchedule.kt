package com.kebiao.app.notifications

import java.time.LocalTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class LessonPeriod(val start: String, val end: String)

/** Validated daily lesson times shared by timetable, reminders and widgets. */
object PeriodSchedule {
    const val MAX_PERIODS = 48
    val defaults: List<LessonPeriod> = listOf(
        LessonPeriod("08:00", "08:50"), LessonPeriod("09:00", "09:50"),
        LessonPeriod("10:10", "11:00"), LessonPeriod("11:10", "12:00"),
        LessonPeriod("13:00", "13:50"), LessonPeriod("14:00", "14:50"),
        LessonPeriod("15:10", "16:00"), LessonPeriod("16:10", "17:00"),
        LessonPeriod("17:10", "18:00"), LessonPeriod("18:40", "19:30"),
        LessonPeriod("19:40", "20:30"), LessonPeriod("20:40", "21:30"),
    )

    fun validate(periods: List<LessonPeriod>): List<LessonPeriod> {
        require(periods.size in 1..MAX_PERIODS) { "每天节数须为 1 到 $MAX_PERIODS" }
        var previousEnd: LocalTime? = null
        periods.forEachIndexed { index, period ->
            val start = parseTime(period.start, "第 ${index + 1} 节开始时间")
            val end = parseTime(period.end, "第 ${index + 1} 节结束时间")
            require(start < end) { "第 ${index + 1} 节结束时间须晚于开始时间，且不能跨天" }
            previousEnd?.let { require(start >= it) { "第 ${index + 1} 节与上一节重叠，时间须按先后排列" } }
            previousEnd = end
        }
        return periods.toList()
    }

    fun encode(periods: List<LessonPeriod>): String = JsonArray(validate(periods).map {
        buildJsonObject { put("start", it.start); put("end", it.end) }
    }).toString()

    fun decode(text: String): List<LessonPeriod> {
        val root = Json.parseToJsonElement(text) as? JsonArray
            ?: throw IllegalArgumentException("作息时间须为 JSON 数组")
        return validate(root.mapIndexed { index, element ->
            val record = element as? JsonObject
                ?: throw IllegalArgumentException("第 ${index + 1} 节格式无效")
            require(record.keys == setOf("start", "end")) { "第 ${index + 1} 节须包含 start 和 end" }
            fun field(key: String): String = (record[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?: throw IllegalArgumentException("第 ${index + 1} 节 $key 须为 HH:mm 字符串")
            LessonPeriod(field("start"), field("end"))
        })
    }

    fun start(period: Int, periods: List<LessonPeriod> = defaults): LocalTime =
        parseTime(periods.getOrNull(period - 1)?.start ?: error("Unknown lesson period: $period"), "开始时间")

    fun end(period: Int, periods: List<LessonPeriod> = defaults): LocalTime =
        parseTime(periods.getOrNull(period - 1)?.end ?: error("Unknown lesson period: $period"), "结束时间")

    private fun parseTime(value: String, field: String): LocalTime {
        require(value.matches(Regex("\\d{2}:\\d{2}"))) { "$field 须使用 HH:mm 格式，例如 08:00" }
        return runCatching { LocalTime.parse(value) }.getOrElse {
            throw IllegalArgumentException("$field 无效，请使用 00:00 到 23:59", it)
        }
    }
}
