package com.kebiao.app.ui

import com.kebiao.app.data.ScheduleExam
import com.kebiao.app.domain.model.Course
import com.kebiao.app.domain.model.ScheduleOverride
import com.kebiao.app.domain.model.WeekRule
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppViewModelTest {
    private val monday = LocalDate.of(2026, 9, 7)

    @Test
    fun addingEditingAndDeletingCourseUpdatesState() {
        val viewModel = AppViewModel()
        val first = Course("math", "高等数学", 1, 1, 2, WeekRule.ALL, "理科楼", "C203")
        val edited = first.copy(name = "高等数学（重修）", room = "C204")

        viewModel.addCourse(first)
        assertEquals(listOf(first), viewModel.uiState.value.courses)

        viewModel.updateCourse(edited)
        assertEquals(edited, viewModel.uiState.value.courses.single())

        viewModel.deleteCourse(first.id)
        assertTrue(viewModel.uiState.value.courses.isEmpty())
    }

    @Test
    fun addingAndDeletingExamUpdatesState() {
        val viewModel = AppViewModel()
        val exam = ScheduleExam("exam-1", "数据库", "2026-09-20", "09:00", "教学楼", "101")

        viewModel.addExam(exam)
        assertEquals(listOf(exam), viewModel.uiState.value.exams)

        viewModel.deleteExam(exam.id)
        assertTrue(viewModel.uiState.value.exams.isEmpty())
    }

    @Test
    fun overrideChangesEffectiveWeekdayForSelectedDate() {
        val viewModel = AppViewModel()
        val tuesdayCourse = Course("physics", "大学物理", 2, 3, 4)
        viewModel.addCourse(tuesdayCourse)
        viewModel.addOverride(ScheduleOverride(monday, 2, "周一调休上周二课程"))

        val effective = viewModel.effectiveCourses(monday)
        assertEquals(listOf(tuesdayCourse), effective.map { it.course })
    }
}
