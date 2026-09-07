package com.kebiao.app.domain.model

data class Course(
    val id: String,
    val name: String,
    val weekday: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weekRule: WeekRule = WeekRule.ALL,
    val building: String? = null,
    val room: String? = null,
    val locationNote: String? = null,
    val source: String = "MANUAL",
    val createdAtEpochMillis: Long = 0L,
    val updatedAtEpochMillis: Long = 0L,
    val teacher: String? = null,
    val weeks: List<Int> = emptyList(),
    val courseNote: String? = null,
) {
    init {
        require(id.isNotBlank()) { "Course id must not be blank" }
        require(name.isNotBlank()) { "Course name must not be blank" }
        require(weekday in 1..7) { "Course weekday must be between 1 and 7" }
        require(startPeriod in 1..48) { "Course start period must be between 1 and 48" }
        require(endPeriod in 1..48) { "Course end period must be between 1 and 48" }
        require(startPeriod <= endPeriod) { "Course start period must not be after end period" }
        require(weeks.all { it in 1..60 } && weeks.distinct().size == weeks.size) { "Course weeks must be unique values from 1 to 60" }
    }
}
