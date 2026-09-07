package com.kebiao.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
        compose.onNodeWithText("Mathematics").assertIsDisplayed()
        compose.runOnIdle { viewModel.updateCourse(course.copy(name = "Physics", room = "A202")) }
        compose.onNodeWithText("Mathematics").assertDoesNotExist()
        compose.onNodeWithText("Physics").assertIsDisplayed()
        compose.onNodeWithText("Science A202").assertIsDisplayed()
        compose.runOnIdle { viewModel.deleteCourse(course.id) }
        compose.onNodeWithText("Physics").assertDoesNotExist()
    }

    @Test
    fun multiPeriodCourseDoesNotShiftLaterCoursesAgainstOtherDays() {
        val viewModel = AppViewModel()
        viewModel.selectDate(LocalDate.of(2026, 9, 7))
        viewModel.addCourse(Course("span", "Double", 1, 1, 2))
        viewModel.addCourse(Course("later", "Physics", 1, 3, 3))
        viewModel.addCourse(Course("other", "Chemistry", 2, 3, 3))
        compose.setContent { MaterialTheme { TimetableScreen(viewModel) } }
        val firstDay = compose.onNodeWithText("Physics").fetchSemanticsNode().boundsInRoot
        val secondDay = compose.onNodeWithText("Chemistry").fetchSemanticsNode().boundsInRoot
        assertEquals(firstDay.top, secondDay.top, 1f)
    }

    @Test
    fun simultaneousCoursesAreBothVisible() {
        val viewModel = AppViewModel()
        viewModel.selectDate(LocalDate.of(2026, 9, 7))
        viewModel.addCourse(Course("a", "Physics", 1, 1, 2))
        viewModel.addCourse(Course("b", "Chemistry", 1, 1, 2))
        compose.setContent { MaterialTheme { TimetableScreen(viewModel) } }
        compose.onNodeWithText("Physics").assertIsDisplayed()
        compose.onNodeWithText("Chemistry").assertIsDisplayed()
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
        val triple = compose.onNodeWithTag("course-block-triple").fetchSemanticsNode().boundsInRoot
        val first = compose.onNodeWithTag("course-block-first").fetchSemanticsNode().boundsInRoot
        val last = compose.onNodeWithTag("course-block-last").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, triple.top, 1f)
        assertEquals(last.bottom, triple.bottom, 1f)
        assertTrue("Three-period card must span three rows", triple.height > first.height * 2.8f)
        assertTrue("Three-period card must not cover the next row", triple.height < first.height * 3.2f)
    }

    @Test
    fun thirteenthCustomPeriodAndItsCourseAreVisibleAfterScrolling() {
        val viewModel = AppViewModel()
        viewModel.selectDate(LocalDate.of(2026, 9, 7))
        viewModel.updateSettings { it.copy(periods = PeriodSchedule.defaults + LessonPeriod("21:40", "22:20")) }
        viewModel.addCourse(Course("late", "晚间研讨", 1, 13, 13))
        compose.setContent { MaterialTheme { TimetableScreen(viewModel) } }

        compose.onNodeWithTag("period-13").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("21:40").assertIsDisplayed()
        compose.onNodeWithText("22:20").assertIsDisplayed()
        compose.onNodeWithTag("course-block-late").assertIsDisplayed()
        compose.onNodeWithText("晚间研讨").assertIsDisplayed()
    }
}
