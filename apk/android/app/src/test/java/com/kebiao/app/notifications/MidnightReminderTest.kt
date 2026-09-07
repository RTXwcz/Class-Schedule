package com.kebiao.app.notifications

import com.kebiao.app.domain.model.Course
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.*

class MidnightReminderTest {
    @Test fun tomorrowCourseCanBeNotifiedBeforeMidnight() {
        val today = LocalDate.of(2026, 9, 7)
        val now = today.atTime(23, 55).atZone(ZoneId.of("Asia/Shanghai"))
        val periods = listOf(LessonPeriod("00:05", "00:45"))
        val course = Course("night", "夜间实验", 2, 1, 1)
        val plans = ReminderPlanner.plan(now.minusMinutes(1), null, listOf(course), emptyList(), horizonDays = 1, periods = periods)
        val plan = plans.single()
        assertEquals(now, plan.triggerAt)
        assertEquals(today.plusDays(1), plan.course.date)
        assertEquals(plan, ReminderPlanner.currentReminder(plan.key, plan.triggerAt.toInstant().toEpochMilli(), now,
            null, listOf(course), emptyList(), periods = periods))
        assertNull(ReminderPlanner.currentReminder(plan.key, plan.triggerAt.toInstant().toEpochMilli(), now.plusMinutes(11),
            null, listOf(course), emptyList(), periods = periods))
    }
}
