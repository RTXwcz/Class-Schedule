package com.kebiao.app.domain.model

import java.time.LocalDate

data class ScheduleOverride(
    val date: LocalDate,
    val replacementWeekday: Int,
    val note: String? = null,
) {
    init {
        require(replacementWeekday in 1..7) { "Replacement weekday must be between 1 and 7" }
    }
}
