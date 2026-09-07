package com.kebiao.app.domain

import com.kebiao.app.domain.model.Course
import com.kebiao.app.domain.model.ScheduleOverride
import com.kebiao.app.domain.model.WeekRule
import com.kebiao.app.notifications.ReminderPlanner
import com.kebiao.app.ui.AppViewModel
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OptionalParityTest {
    private val monday = LocalDate.of(2026, 9, 7)
    private val odd = Course("odd", "数学", 1, 1, 2, WeekRule.ODD)

    @Test fun disablingParityRestoresWeeklyClassesWithoutLosingStoredRule() {
        val resolver = ScheduleResolver()
        assertTrue(resolver.resolve(monday.plusWeeks(1), monday, listOf(odd), emptyList()).isEmpty())
        val result = resolver.resolve(monday.plusWeeks(1), monday, listOf(odd), emptyList(), false)
        assertEquals(WeekRule.ODD, result.single().course.weekRule)
        assertEquals(1, resolver.resolve(monday, null, listOf(odd), emptyList(), false).size)
    }

    @Test fun explicitWeeksAndDateReplacementsStillApplyWithParityDisabled() {
        val limited = odd.copy(weeks = listOf(2))
        val date = monday.plusWeeks(1).plusDays(5)
        assertEquals(1, ScheduleResolver().resolve(date, monday, listOf(limited), listOf(ScheduleOverride(date, 1)), false).size)
        assertTrue(ScheduleResolver().resolve(monday, monday, listOf(limited), emptyList(), false).isEmpty())
    }

    @Test fun alarmAndWidgetPlanningAgreeWithoutASemesterDate() {
        val now = monday.atTime(7, 0).atZone(ZoneId.of("Asia/Shanghai"))
        assertEquals(3, ReminderPlanner.upcoming(now, null, listOf(odd), emptyList(), parityEnabled = false).size)
        val alarm = ReminderPlanner.plan(now, null, listOf(odd), emptyList(), horizonDays = 0, parityEnabled = false).single()
        assertEquals(now.withHour(7).withMinute(50), alarm.triggerAt)
        assertEquals(alarm.key, ReminderPlanner.currentReminder(alarm.key, alarm.triggerAt.toInstant().toEpochMilli(), alarm.triggerAt,
            null, listOf(odd), emptyList(), parityEnabled = false)?.key)
    }

    @Test fun jsonRoundTripRetainsOptionalParityAndOriginalCourseRule() {
        val vm = AppViewModel()
        vm.addCourse(odd)
        vm.updateSettings { it.copy(parityEnabled = false) }
        val restored = AppViewModel()
        restored.importJson(vm.exportJson())
        assertEquals(false, restored.uiState.value.settings.parityEnabled)
        assertEquals(WeekRule.ODD, restored.uiState.value.courses.single().weekRule)
        assertEquals(1, restored.effectiveCourses(monday).size)
    }
}
