package com.kebiao.app.domain

import com.kebiao.app.domain.model.Course
import com.kebiao.app.domain.model.EffectiveCourse
import com.kebiao.app.domain.model.ScheduleOverride
import com.kebiao.app.domain.model.WeekRule
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Resolves all date-dependent course rules for UI, reminders, widgets, and MCP. */
class ScheduleResolver {
    fun resolve(
        date: LocalDate,
        semesterStart: LocalDate?,
        courses: List<Course>,
        overrides: List<ScheduleOverride>,
    ): List<EffectiveCourse> {
        val naturalWeekday = date.dayOfWeek.value
        val effectiveWeekday = overrides
            .firstOrNull { it.date == date }
            ?.replacementWeekday
            ?: naturalWeekday

        val weekParity = semesterStart
            ?.takeUnless { date.isBefore(it) }
            ?.let { start ->
                val weekIndex = ChronoUnit.WEEKS.between(start, date)
                if (Math.floorMod(weekIndex, 2L) == 0L) WeekRule.ODD else WeekRule.EVEN
            }

        val sorted = courses
            .asSequence()
            .filter { it.weekday == effectiveWeekday }
            .filter { course ->
                when (course.weekRule) {
                    WeekRule.ALL -> true
                    WeekRule.ODD, WeekRule.EVEN -> weekParity == course.weekRule
                }
            }
            .sortedWith(compareBy<Course> { it.startPeriod }.thenBy { it.endPeriod }.thenBy { it.name })
            .toList()

        return sorted.mapIndexed { index, course ->
            val conflictsWithPrevious = index > 0 && sorted[index - 1].endPeriod >= course.startPeriod
            EffectiveCourse(
                course = course,
                date = date,
                effectiveWeekday = effectiveWeekday,
                conflictGroup = if (conflictsWithPrevious) index else null,
            )
        }
    }
}
