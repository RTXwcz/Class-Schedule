package com.kebiao.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import com.kebiao.app.domain.model.Course
import com.kebiao.app.notifications.LessonPeriod
import com.kebiao.app.notifications.PeriodSchedule
import com.kebiao.app.ui.timetable.TimetableScreen
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.time.LocalDate

class TimetableScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun addingEditingAndDeletingCourseRefreshesVisibleDayWithoutNavigation() {
        val viewModel = AppViewModel()
        viewModel.selectDate(LocalDate.of(2026, 9, 7))
        compose.setContent { MaterialTheme { TimetableScreen(viewModel) } }
        val course = Course("math", "Mathematics", 1, 1, 2, building = "Science", room = "A101")
        compose.runOnIdle { viewModel.addCourse(course) }
        // The next-course summary may repeat the same course name, so only the first is required.
        compose.onAllNodesWithText("Mathematics").onFirst().assertIsDisplayed()
        compose.runOnIdle { viewModel.updateCourse(course.copy(name = "Physics", room = "A202")) }
        compose.onAllNodesWithText("Mathematics").assertCountEquals(0)
        compose.onAllNodesWithText("Physics").onFirst().assertIsDisplayed()
        compose.onAllNodesWithText("Science A202").onFirst().assertIsDisplayed()
        compose.runOnIdle { viewModel.deleteCourse(course.id) }
        compose.onAllNodesWithText("Physics").assertCountEquals(0)
    }

    @Test
    fun multiPeriodCourseDoesNotShiftLaterCoursesAgainstOtherDays() {
        val viewModel = AppViewModel()
        viewModel.selectDate(LocalDate.of(2026, 9, 7))
        viewModel.addCourse(Course("span", "Double", 1, 1, 2))
        viewModel.addCourse(Course("later", "Physics", 1, 3, 3))
        viewModel.addCourse(Course("other", "Chemistry", 2, 3, 3))
        compose.setContent { MaterialTheme { TimetableScreen(viewModel) } }
        val firstDay = compose.onNodeWithText("Physics").getUnclippedBoundsInRoot()
        val secondDay = compose.onNodeWithText("Chemistry").getUnclippedBoundsInRoot()
        assertEquals(firstDay.top.value, secondDay.top.value, 1f)
    }

    @Test
    fun simultaneousCoursesAreBothVisible() {
        val viewModel = AppViewModel()
        viewModel.selectDate(LocalDate.of(2026, 9, 7))
        viewModel.addCourse(Course("a", "Physics", 1, 1, 2))
        viewModel.addCourse(Course("b", "Chemistry", 1, 1, 2))
        compose.setContent { MaterialTheme { TimetableScreen(viewModel) } }
        compose.onAllNodesWithText("Physics").onFirst().assertIsDisplayed()
        compose.onAllNodesWithText("Chemistry").onFirst().assertIsDisplayed()
    }

    @Test
    fun threePeriodLessonIsOneContinuousCardAcrossPeriodsThreeToFive() {
        val viewModel = AppViewModel()
        viewModel.selectDate(LocalDate.of(2026, 9, 7))
        viewModel.addCourse(Course("triple", "连续实验", 1, 3, 5))
        viewModel.addCourse(Course("first", "第三节", 2, 3, 3))
        viewModel.addCourse(Course("last", "第五节", 2, 5, 5))
        compose.setContent { MaterialTheme { TimetableScreen(viewModel) } }

        compose.onAllNodesWithTag("course-block-triple").assertCountEquals(1)
        val triple = compose.onNodeWithTag("course-block-triple").getUnclippedBoundsInRoot()
        val first = compose.onNodeWithTag("course-block-first").getUnclippedBoundsInRoot()
        val last = compose.onNodeWithTag("course-block-last").getUnclippedBoundsInRoot()
        assertEquals(first.top.value, triple.top.value, 1f)
        assertEquals(last.bottom.value, triple.bottom.value, 1f)
        assertTrue("Three-period card must span three rows", (triple.bottom - triple.top).value > (first.bottom - first.top).value * 2.8f)
        assertTrue("Three-period card must not cover the next row", (triple.bottom - triple.top).value < (first.bottom - first.top).value * 3.2f)
    }

    @Test
    fun thirteenthCustomPeriodAndItsCourseAreVisibleAfterScrolling() {
        val viewModel = AppViewModel()
        viewModel.selectDate(LocalDate.of(2026, 9, 7))
        viewModel.updateSettings { it.copy(periods = PeriodSchedule.defaults + LessonPeriod("21:40", "22:20")) }
        viewModel.addCourse(Course("late", "晚间研讨", 1, 13, 13))
        compose.setContent { MaterialTheme { TimetableScreen(viewModel) } }

        compose.onNodeWithTag("period-13").performScrollTo().assertIsDisplayed()
        // The next-course strip may repeat the same clock text, so assert the gutter directly.
        compose.onNodeWithTag("period-start-13").assertIsDisplayed()
        compose.onNodeWithTag("period-end-13").assertIsDisplayed()
        compose.onNodeWithTag("course-block-late").assertIsDisplayed()
        compose.onAllNodesWithText("晚间研讨").onFirst().assertIsDisplayed()
    }
}
