package com.kebiao.app.domain

import com.kebiao.app.domain.model.Course
import com.kebiao.app.domain.model.ScheduleOverride
import com.kebiao.app.domain.model.WeekRule
import com.kebiao.app.notifications.ReminderPlanner
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WeekSelectionTest {
    @Test fun parsesRangesAndRejectsMalformedInput() {
        assertEquals(listOf(1, 3, 5, 6, 7, 8), WeekSelection.parse("1,3,5-8"))
        listOf("0", "61", "8-5", "1-", "abc").forEach { assertFailsWith<IllegalArgumentException> { WeekSelection.parse(it) } }
    }

    @Test fun selectedWeeksCombineWithParityAndDateOverride() {
        val start = LocalDate.of(2026, 9, 7)
        val course = Course("weeks", "Math", 1, 1, 2, WeekRule.ODD, weeks = listOf(3, 4))
        val resolver = ScheduleResolver()
        assertTrue(resolver.resolve(start, start, listOf(course), emptyList()).isEmpty())
        val moved = start.plusWeeks(2).plusDays(5)
        assertEquals(listOf(course), resolver.resolve(moved, start, listOf(course), listOf(ScheduleOverride(moved, 1))).map { it.course })
        assertTrue(resolver.resolve(start.plusWeeks(3), start, listOf(course), emptyList()).isEmpty())
        assertTrue(resolver.resolve(moved, null, listOf(course), emptyList()).isEmpty())
    }

    @Test fun widgetFindsLateSemesterCoursesWithoutInventingFurtherOccurrences() {
        val start = LocalDate.of(2026, 9, 7)
        val now = start.atStartOfDay(ZoneId.of("Asia/Shanghai"))
        val course = Course("late", "Math", 1, 1, 2, weeks = listOf(40, 42))
        val upcoming = ReminderPlanner.upcoming(now, start, listOf(course), emptyList())
        assertEquals(listOf(start.plusWeeks(39), start.plusWeeks(41)), upcoming.map { it.course.date })
    }
}
