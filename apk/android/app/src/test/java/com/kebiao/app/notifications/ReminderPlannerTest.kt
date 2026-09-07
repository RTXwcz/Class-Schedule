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
import kotlin.test.assertNull
import kotlin.test.assertNotNull

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

    @Test
    fun catchesUpUnsentReminderInsideLeadWindowWithOriginalTrigger() {
        val now = monday.atTime(7, 51).atZone(zone)
        val result = ReminderPlanner.plan(now, monday, listOf(Course("math", "高数", 1, 1, 2)),
            emptyList(), horizonDays = 0, includeDue = true)
        assertEquals(1, result.size)
        assertEquals(monday.atTime(7, 50).atZone(zone), result.single().triggerAt)
        assertNotNull(ReminderPlanner.currentReminder(result.single().key, result.single().triggerAt.toInstant().toEpochMilli(),
            now, monday, listOf(Course("math", "高数", 1, 1, 2)), emptyList()))
    }

    @Test
    fun doesNotCatchUpDeliveredOrStartedCourse() {
        val course = Course("math", "高数", 1, 1, 2)
        assertTrue(ReminderPlanner.plan(monday.atTime(7, 51).atZone(zone), monday, listOf(course),
            emptyList(), horizonDays = 0, includeDue = true, deliveredKeys = setOf("math@$monday")).isEmpty())
        assertTrue(ReminderPlanner.plan(monday.atTime(8, 0).atZone(zone), monday, listOf(course),
            emptyList(), horizonDays = 0, includeDue = true).isEmpty())
        assertTrue(ReminderPlanner.plan(monday.atTime(8, 1).atZone(zone), monday, listOf(course),
            emptyList(), horizonDays = 0, includeDue = true).isEmpty())
    }

    @Test
    fun deliveredTodayDoesNotSuppressNextOccurrence() {
        val result = ReminderPlanner.plan(monday.atTime(7, 51).atZone(zone), monday,
            listOf(Course("math", "高数", 1, 1, 2)), emptyList(), horizonDays = 7,
            includeDue = true, deliveredKeys = setOf("math@$monday"))
        assertEquals(listOf("math@${monday.plusDays(7)}"), result.map { it.key })
    }

    @Test
    fun doesNotDeliverDeletedMovedOrExpiredCourse() {
        val now = monday.atTime(7, 51).atZone(zone)
        val trigger = monday.atTime(7, 50).atZone(zone).toInstant().toEpochMilli()
        val course = Course("math", "高数", 1, 1, 2)
        assertNull(ReminderPlanner.currentReminder("math@$monday", trigger, now, monday, emptyList(), emptyList()))
        assertNull(ReminderPlanner.currentReminder("math@$monday", trigger, now, monday,
            listOf(course.copy(startPeriod = 2)), emptyList()))
        assertNull(ReminderPlanner.currentReminder("math@$monday", trigger, now.withHour(8), monday,
            listOf(course), emptyList()))
        assertNull(ReminderPlanner.currentReminder("math@$monday", trigger, now, monday,
            listOf(course), listOf(ScheduleOverride(monday, 2))))
    }

    @Test
    fun revalidatesLeadTimeAndUsesCurrentCourseDetails() {
        val now = monday.atTime(7, 51).atZone(zone)
        val trigger = monday.atTime(7, 50).atZone(zone).toInstant().toEpochMilli()
        val course = Course("math", "新课程名", 1, 1, 2, building = "新教学楼")
        val current = ReminderPlanner.currentReminder("math@$monday", trigger, now, monday, listOf(course), emptyList())
        assertEquals("新课程名", current?.course?.course?.name)
        assertNull(ReminderPlanner.currentReminder("math@$monday", trigger, now, monday, listOf(course), emptyList(), 5))
        assertNull(ReminderPlanner.currentReminder("math@$monday", trigger, now.minusMinutes(2), monday, listOf(course), emptyList()))
    }

    @Test
    fun zeroMinuteReminderAllowsNormalAlarmDeliveryLatency() {
        val start = monday.atTime(8, 0).atZone(zone)
        val course = Course("math", "高数", 1, 1, 2)
        assertNotNull(ReminderPlanner.currentReminder("math@$monday", start.toInstant().toEpochMilli(), start.plusSeconds(2),
            monday, listOf(course), emptyList(), 0))
        assertNull(ReminderPlanner.currentReminder("math@$monday", start.toInstant().toEpochMilli(), start.plusMinutes(1),
            monday, listOf(course), emptyList(), 0))
    }

    @Test
    fun upcomingIncludesCourseWhoseNotificationLeadTimeHasPassed() {
        val result = ReminderPlanner.upcoming(monday.atTime(7, 55).atZone(zone), monday,
            listOf(Course("math", "高数", 1, 1, 2)), emptyList())
        assertEquals(3, result.size)
        assertEquals(monday, result.first().course.date)
    }

    @Test
    fun upcomingFindsThreeFortnightlyCoursesBeyondReminderHorizon() {
        val course = Course("math", "高数", 1, 1, 2, WeekRule.ODD)
        val result = ReminderPlanner.upcoming(monday.atTime(9, 0).atZone(zone), monday, listOf(course), emptyList())
        assertEquals(listOf(monday.plusDays(14), monday.plusDays(28), monday.plusDays(42)), result.map { it.course.date })
    }

    @Test
    fun upcomingSearchesPastFutureSemesterStartAndLongOverrideSeries() {
        val semester = monday.plusWeeks(20)
        val result = ReminderPlanner.upcoming(monday.atTime(7, 0).atZone(zone), semester,
            listOf(Course("math", "高数", 1, 1, 2, WeekRule.ODD)),
            (0..50).map { ScheduleOverride(semester.plusDays(it.toLong()), 2) })
        assertEquals(3, result.size)
        assertTrue(result.first().course.date.isAfter(semester.plusDays(50)))
    }

    @Test
    fun upcomingDeduplicatesAndAppliesOverridesAndParity() {
        val course = Course("odd", "单周课", 1, 1, 2, WeekRule.ODD)
        val result = ReminderPlanner.upcoming(monday.plusDays(1).atTime(7, 0).atZone(zone), monday,
            listOf(course, course, Course("even", "双周课", 2, 1, 2, WeekRule.EVEN)),
            listOf(ScheduleOverride(monday.plusDays(1), 1)))
        assertEquals("odd", result.first().course.course.id)
        assertEquals(monday.plusDays(1), result.first().course.date)
        assertEquals(3, result.map { it.key }.distinct().size)
    }
}
