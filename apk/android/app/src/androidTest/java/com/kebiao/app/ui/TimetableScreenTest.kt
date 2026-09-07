package com.kebiao.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.kebiao.app.domain.model.Course
import com.kebiao.app.ui.timetable.TimetableScreen
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
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
}
