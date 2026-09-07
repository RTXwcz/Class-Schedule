package com.kebiao.app.notifications

import com.kebiao.app.domain.model.Course
import com.kebiao.app.ui.AppViewModel
import com.kebiao.app.widget.WidgetSnapshotProvider
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CustomPeriodIntegrationTest {
    @Test fun customTimesDriveSingleReminderForAThreePeriodClass() {
        val periods = listOf(LessonPeriod("08:30", "09:10"), LessonPeriod("09:20", "10:00"), LessonPeriod("10:10", "10:50"))
        val now = LocalDate.of(2026, 9, 7).atTime(7, 0).atZone(ZoneId.of("Asia/Shanghai"))
        val course = Course("lab", "连堂实验", 1, 1, 3)
        val reminder = ReminderPlanner.plan(now, null, listOf(course), emptyList(), horizonDays = 0, periods = periods).single()
        assertEquals("08:20", reminder.triggerAt.toLocalTime().toString())
        assertEquals("08:30", reminder.startsAt.toLocalTime().toString())
        assertEquals(reminder, ReminderPlanner.currentReminder(reminder.key, reminder.triggerAt.toInstant().toEpochMilli(),
            reminder.triggerAt, null, listOf(course), emptyList(), periods = periods))
        val upcoming = ReminderPlanner.upcoming(now, null, listOf(course), emptyList(), periods = periods)
        assertEquals(3, upcoming.size)
        assertTrue(WidgetSnapshotProvider.format(upcoming.map { it.course }, periods).contains("08:30"))
    }

    @Test fun thirteenPeriodsRoundTripAndCannotBeShrunkPastExistingCourse() {
        val periods = PeriodSchedule.defaults + LessonPeriod("21:40", "22:20")
        val vm = AppViewModel()
        vm.updatePeriodSchedule(periods)
        vm.addCourse(Course("late", "研讨", 1, 13, 13))
        val restored = AppViewModel()
        restored.importJson(vm.exportJson())
        assertEquals(periods, restored.uiState.value.settings.periods)
        assertEquals(13, restored.uiState.value.courses.single().startPeriod)
        restored.updatePeriodSchedule(periods.take(12))
        assertEquals(13, restored.uiState.value.settings.periods.size)
        assertTrue(restored.uiState.value.errorMessage.orEmpty().contains("超出"))
    }

    @Test fun invalidImportedTimesCannotReplaceExistingData() {
        val vm = AppViewModel()
        vm.addCourse(Course("keep", "数学", 1, 1, 1))
        vm.importJson("""{"courses":[],"periods":[{"start":"09:00","end":"08:00"}]}""")
        assertEquals("keep", vm.uiState.value.courses.single().id)
        assertEquals(PeriodSchedule.defaults, vm.uiState.value.settings.periods)
    }
}
