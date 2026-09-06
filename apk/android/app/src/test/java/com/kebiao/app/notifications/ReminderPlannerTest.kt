package com.kebiao.app.notifications

import com.kebiao.app.domain.model.Course
import com.kebiao.app.domain.model.ScheduleOverride
import com.kebiao.app.domain.model.WeekRule
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderPlannerTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val monday = LocalDate.of(2026, 9, 7)

    @Test
    fun plansReminderTenMinutesBeforeCourse() {
        val now = ZonedDateTime.of(2026, 9, 7, 7, 40, 0, 0, zone)
        val result = ReminderPlanner.plan(
            now = now,
            semesterStart = monday,
            courses = listOf(Course("math", "高数", 1, 1, 2)),
            overrides = emptyList(),
            horizonDays = 0,
        )

        assertEquals(1, result.size)
        assertEquals("math@2026-09-07", result.single().key)
        assertEquals(7, result.single().triggerAt.hour)
        assertEquals(50, result.single().triggerAt.minute)
    }

    @Test
    fun skipsExpiredAndDuplicateCourseEntries() {
        val now = ZonedDateTime.of(2026, 9, 7, 8, 5, 0, 0, zone)
        val course = Course("math", "高数", 1, 1, 2)
        val result = ReminderPlanner.plan(
            now = now,
            semesterStart = monday,
            courses = listOf(course, course),
            overrides = emptyList(),
            horizonDays = 0,
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun appliesFullDayOverrideAndWeekParityBeforePlanning() {
        val now = ZonedDateTime.of(2026, 9, 11, 7, 0, 0, 0, zone)
        val result = ReminderPlanner.plan(
            now = now,
            semesterStart = monday,
            courses = listOf(
                Course("monday-odd", "周一单周课", 1, 1, 1, WeekRule.ODD),
                Course("friday-even", "周五双周课", 5, 1, 1, WeekRule.EVEN),
            ),
            overrides = listOf(ScheduleOverride(LocalDate.of(2026, 9, 11), 1)),
            horizonDays = 0,
        )

        assertEquals(listOf("monday-odd"), result.map { it.course.course.id })
    }

    @Test
    fun doesNotPlanReminderWhenLeadTimeHasAlreadyPassed() {
        val now = ZonedDateTime.of(2026, 9, 7, 7, 55, 0, 0, zone)
        val result = ReminderPlanner.plan(
            now = now,
            semesterStart = monday,
            courses = listOf(Course("math", "高数", 1, 1, 1)),
            overrides = emptyList(),
            horizonDays = 0,
        )

        assertTrue(result.isEmpty())
    }
}
