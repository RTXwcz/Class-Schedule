package com.kebiao.app.data

import com.kebiao.app.data.settings.AppSettings
import com.kebiao.app.notifications.LessonPeriod
import com.kebiao.app.notifications.PeriodSchedule
import kotlinx.serialization.json.*
import java.time.LocalDate

/** The interpretation of a dataset is persisted with its records in the same Room transaction. */
data class ScheduleRules(
    val semesterStartDate: String?,
    val parityEnabled: Boolean,
    val periods: List<LessonPeriod>,
) {
    fun validate(): ScheduleRules {
        semesterStartDate?.let { LocalDate.parse(it) }
        PeriodSchedule.validate(periods)
        return this
    }
    fun apply(settings: AppSettings): AppSettings = settings.copy(semesterStartDate = semesterStartDate, parityEnabled = parityEnabled, periods = periods)
    fun apply(export: ScheduleExport): ScheduleExport = export.copy(extraFields = export.extraFields + mapOf(
        "semesterStartDate" to (semesterStartDate?.let(::JsonPrimitive) ?: JsonNull),
        "parityEnabled" to JsonPrimitive(parityEnabled),
        "periods" to Json.parseToJsonElement(PeriodSchedule.encode(periods)),
        "rulesVersion" to JsonPrimitive(1),
    ))
    companion object {
        fun from(settings: AppSettings) = ScheduleRules(settings.semesterStartDate, settings.parityEnabled, settings.periods).validate()
        fun read(export: ScheduleExport, fallback: ScheduleRules = from(AppSettings())): ScheduleRules {
            val extra = export.extraFields
            val semester = if ("semesterStartDate" !in extra) fallback.semesterStartDate else when (val value = extra["semesterStartDate"]) {
                JsonNull -> null
                is JsonPrimitive -> value.takeIf { it.isString }?.content ?: error("学期日期格式无效")
                else -> error("学期日期格式无效")
            }
            val parity = if ("parityEnabled" !in extra) fallback.parityEnabled else
                (extra["parityEnabled"] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull ?: error("单双周开关必须为布尔值")
            val periods = extra["periods"]?.let { PeriodSchedule.decode(it.toString()) } ?: fallback.periods
            return ScheduleRules(semester, parity, periods).validate()
        }
    }
}
