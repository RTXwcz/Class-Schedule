package com.kebiao.app.domain

import com.kebiao.app.domain.model.Course
import com.kebiao.app.domain.model.EffectiveCourse
import com.kebiao.app.domain.model.ScheduleOverride
import com.kebiao.app.domain.model.WeekRule
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ScheduleResolverTest {
    private val resolver = ScheduleResolver()
    private val monday = LocalDate.of(2026, 9, 7)

    @Test
    fun resolvesCoursesForNaturalWeekdayAndSortsByPeriod() {
        val courses = listOf(
            Course("late", "晚课", 1, 4, 5, WeekRule.ALL),
            Course("early", "早课", 1, 1, 2, WeekRule.ALL),
            Course("other", "周二课", 2, 1, 1, WeekRule.ALL),
        )

        val result = resolver.resolve(monday, monday, courses, emptyList())

        assertEquals(listOf("early", "late"), result.map(EffectiveCourse::course).map(Course::id))
    }

    @Test
    fun resolvesOddAndEvenWeeksFromSemesterStart() {
        val courses = listOf(
            Course("odd", "单周", 1, 1, 1, WeekRule.ODD),
            Course("even", "双周", 1, 2, 2, WeekRule.EVEN),
        )

        assertEquals(listOf("odd"), resolver.resolve(monday, monday, courses, emptyList()).map { it.course.id })
        assertEquals(listOf("even"), resolver.resolve(monday.plusWeeks(1), monday, courses, emptyList()).map { it.course.id })
    }

    @Test
    fun appliesAllWeeklyCoursesWhenSemesterStartIsMissing() {
        val courses = listOf(
            Course("odd", "单周", 1, 1, 1, WeekRule.ODD),
            Course("even", "双周", 1, 2, 2, WeekRule.EVEN),
            Course("all", "每周", 1, 3, 3, WeekRule.ALL),
        )

        assertEquals(listOf("all"), resolver.resolve(monday, null, courses, emptyList()).map { it.course.id })
    }

    @Test
    fun usesReplacementWeekdayForFullDayOverride() {
        val courses = listOf(
            Course("monday", "周一课", 1, 1, 1, WeekRule.ALL),
            Course("friday", "周五课", 5, 1, 1, WeekRule.ALL),
        )

        val result = resolver.resolve(
            LocalDate.of(2026, 9, 11),
            monday,
            courses,
            listOf(ScheduleOverride(LocalDate.of(2026, 9, 11), 1)),
        )

        assertEquals(listOf("monday"), result.map { it.course.id })
    }
}
