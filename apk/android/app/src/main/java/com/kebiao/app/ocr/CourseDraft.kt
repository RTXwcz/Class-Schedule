package com.kebiao.app.ocr

import com.kebiao.app.domain.model.WeekRule

data class OcrSourceBox(val left: Float, val top: Float, val right: Float, val bottom: Float)
data class OcrTextBlock(val text: String, val confidence: Float, val box: OcrSourceBox)
data class DraftField<T>(val value: T, val confidence: Float, val sourceBox: OcrSourceBox? = null, val confirmed: Boolean = false) {
    fun confirm(): DraftField<T> = copy(confirmed = true)
}
data class CourseDraft(
    val name: DraftField<String>,
    val weekday: DraftField<Int?>,
    val startPeriod: DraftField<Int?>,
    val endPeriod: DraftField<Int?>,
    val weekRule: DraftField<WeekRule?>,
    val building: DraftField<String?>,
    val room: DraftField<String?>,
    val locationNote: DraftField<String?>,
) {
    fun confirmAll() = copy(name = name.confirm(), weekday = weekday.confirm(), startPeriod = startPeriod.confirm(), endPeriod = endPeriod.confirm(), weekRule = weekRule.confirm(), building = building.confirm(), room = room.confirm(), locationNote = locationNote.confirm())
    fun hasLowConfidenceFields() = listOf(name, weekday, startPeriod, endPeriod, weekRule, building, room).any { it.confidence < 0.8f }
    fun validationErrors(): List<ValidationIssue> = buildList {
        if (name.value.isBlank()) add(ValidationIssue("name", "课程名称不能为空"))
        if (weekday.value !in 1..7) add(ValidationIssue("weekday", "星期必须为1到7"))
        if (weekRule.value == null) add(ValidationIssue("weekRule", "请选择每周、单周或双周"))
        if (startPeriod.value !in 1..12) add(ValidationIssue("startPeriod", "开始节次无效"))
        if (endPeriod.value !in 1..12) add(ValidationIssue("endPeriod", "结束节次无效"))
        if (startPeriod.value != null && endPeriod.value != null && startPeriod.value!! > endPeriod.value!!) add(ValidationIssue("endPeriod", "结束节次不能早于开始节次"))
    }
}
data class ValidationIssue(val field: String, val message: String)
