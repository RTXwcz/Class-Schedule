package com.kebiao.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.kebiao.app.domain.model.Course
import com.kebiao.app.notifications.LessonPeriod
import com.kebiao.app.notifications.PeriodSchedule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import java.time.LocalDate

class FixedTimetableTest {
    @get:Rule val compose = createComposeRule()

    @Before fun requestedOrientation() {
        val orientation = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("gridRotation")?.toIntOrNull()
        if (orientation != null) {
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.setRotation(orientation)
            Thread.sleep(350)
        }
    }

    @Test fun overlappingCoursesKeepReadableWidthAndFullTitles() {
        val vm = fixture()
        vm.addCourse(Course("long", "新时代中国特色社会主义理论与实践研究专题课程", 1, 1, 1, building = "综合实验教学楼", room = "A301"))
        vm.addCourse(Course("b", "C语言程序设计基础及实验", 1, 1, 2))
        vm.addCourse(Course("c", "大学生思想文化素养", 1, 1, 3))
        compose.setContent { ScheduleApp(vm) }
        screenshot("fixed-grid-readable")
        compose.onNodeWithTag("course-block-long").assertWidthIsAtLeast(180.dp)
        compose.onNodeWithTag("course-block-b").assertWidthIsAtLeast(180.dp)
        compose.onNodeWithTag("course-block-c").assertWidthIsAtLeast(180.dp)
        compose.onNodeWithTag("course-title-long", useUnmergedTree = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { get ->
            val result = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            assertTrue(get(result))
            assertFalse("Full title must not be truncated", result.single().hasVisualOverflow)
        }
        compose.onNodeWithText("展开", substring = false).assertDoesNotExist()
    }

    @Test fun bothAxesScrollWhileHeadersAndTimeGutterStayAligned() {
        val vm = fixture()
        vm.addCourse(Course("mon", "周一课程", 1, 1, 1))
        vm.addCourse(Course("late", "周日第十三节课程", 7, 13, 13))
        compose.setContent { ScheduleApp(vm) }
        val headerTop = compose.onNodeWithTag("day-header-7").getUnclippedBoundsInRoot().top
        val gutterLeft = compose.onNodeWithTag("period-13").getUnclippedBoundsInRoot().left
        compose.onNodeWithTag("period-13").performScrollTo()
        compose.onNodeWithTag("course-block-late").performScrollTo()
        screenshot("fixed-grid-last-course")
        compose.onNodeWithTag("course-block-late").assertIsDisplayed()
        compose.onNodeWithTag("period-13").assertIsDisplayed()
        compose.onNodeWithTag("day-header-7").assertIsDisplayed()
        val header = compose.onNodeWithTag("day-header-7").getUnclippedBoundsInRoot()
        val course = compose.onNodeWithTag("course-block-late").getUnclippedBoundsInRoot()
        val period = compose.onNodeWithTag("period-13").getUnclippedBoundsInRoot()
        assertEquals(headerTop.value, header.top.value, 1f)
        assertEquals(gutterLeft.value, period.left.value, 1f)
        // The card keeps a small inset from its column and row edges; the day header and the
        // time gutter still line up with the column and row the card belongs to.
        assertEquals(header.left.value + 2f, course.left.value, 1f)
        assertEquals(period.top.value + 2f, course.top.value, 1f)
        compose.onNodeWithTag("course-block-late").performClick()
        compose.onNodeWithText("编辑课程").assertIsDisplayed()
    }

    private fun fixture() = AppViewModel().apply {
        selectDate(LocalDate.of(2026, 9, 7))
        updateSettings { it.copy(theme = "dark", colorPalette = "purple", periods = PeriodSchedule.defaults + LessonPeriod("21:40", "22:30")) }
    }

    @Test fun fingerSwipesMoveEachAxisWithoutChangingCourseWidth() {
        val vm = fixture()
        vm.addCourse(Course("drag", "可滑动的完整课程名称", 1, 1, 1))
        compose.setContent { ScheduleApp(vm) }
        val initial = compose.onNodeWithTag("course-block-drag").getUnclippedBoundsInRoot()
        val heading = compose.onNodeWithTag("day-header-1").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("timetable-horizontal").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        val shifted = compose.onNodeWithTag("course-block-drag").getUnclippedBoundsInRoot()
        assertTrue(shifted.left < initial.left)
        assertEquals((initial.right - initial.left).value, (shifted.right - shifted.left).value, .5f)
        assertEquals((shifted.left - initial.left).value,
            (compose.onNodeWithTag("day-header-1").getUnclippedBoundsInRoot().left - heading.left).value, 1f)
        val gutter = compose.onNodeWithTag("period-1").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("timetable-vertical").performTouchInput { swipeUp() }
        compose.waitForIdle()
        val moved = compose.onNodeWithTag("period-1").getUnclippedBoundsInRoot()
        assertTrue(moved.top < gutter.top)
        assertEquals(gutter.left.value, moved.left.value, .5f)
        assertEquals(heading.top.value, compose.onNodeWithTag("day-header-1").getUnclippedBoundsInRoot().top.value, .5f)
    }

    @Test fun compactZoomUsesNarrowerColumnsAndWrapsTitles() {
        val vm = fixture()
        vm.addCourse(Course("compact", "C语言程序设计基础及实验", 1, 1, 2))
        compose.setContent { ScheduleApp(vm) }
        compose.onNodeWithText("紧凑", substring = false).assertIsDisplayed().performClick()
        val compactBounds = compose.onNodeWithTag("course-block-compact").getUnclippedBoundsInRoot()
        assertTrue(compactBounds.right - compactBounds.left <= 150.dp)
        compose.onNodeWithTag("course-title-compact", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("录入", substring = false).assertIsDisplayed()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        Thread.sleep(350)
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        if (android.os.Build.VERSION.SDK_INT < 26) return
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
