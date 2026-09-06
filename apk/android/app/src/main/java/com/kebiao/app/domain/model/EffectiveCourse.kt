package com.kebiao.app.domain.model

import java.time.LocalDate

data class EffectiveCourse(
    val course: Course,
    val date: LocalDate,
    val effectiveWeekday: Int,
    val conflictGroup: Int? = null,
) {
    val hasConflict: Boolean
        get() = conflictGroup != null
}
