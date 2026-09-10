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
        // The overview fits a whole week; the drag tier keeps three overlapping courses readable.
        compose.onNodeWithTag("grid-zoom").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("course-block-long").assertWidthIsAtLeast(88.dp)
        compose.onNodeWithTag("course-block-b").assertWidthIsAtLeast(88.dp)
        compose.onNodeWithTag("course-block-c").assertWidthIsAtLeast(88.dp)
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
        // A long title on every period keeps the column taller than any test viewport, so the
        // vertical axis is genuinely scrollable instead of depending on the emulator height.
        for (period in 1..13) {
            vm.addCourse(Course(
                if (period == 1) "drag" else "fill-$period",
                "可滑动的完整课程名称用于验证纵向滚动可以正常发生",
                1,
                period,
                period,
            ))
        }
        compose.setContent { ScheduleApp(vm) }
        // Panning needs a week that is wider than the screen, which is the drag tier.
        compose.onNodeWithTag("grid-zoom").performClick()
        compose.waitForIdle()
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

    @Test fun overviewShowsTheWholeWeekAndPresetsWidenIt() {
        val vm = fixture()
        vm.addCourse(Course("compact", "C语言程序设计基础及实验", 1, 1, 2))
        compose.setContent { ScheduleApp(vm) }
        // Default is the overview: seven days, one screen, no dragging required.
        compose.onNodeWithText("全览", substring = false).assertIsDisplayed()
        (1..7).forEach { compose.onNodeWithTag("day-header-$it").assertIsDisplayed() }
        val week = compose.onNodeWithTag("course-block-compact").getUnclippedBoundsInRoot()
        assertTrue("week=${week.right - week.left}", week.right - week.left <= 56.dp)
        compose.onNodeWithTag("course-title-compact", useUnmergedTree = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { get ->
            val result = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            assertTrue(get(result))
            assertFalse("Week zoom must wrap rather than clip", result.single().hasVisualOverflow)
        }
        screenshot("fixed-grid-week-zoom")

        compose.onNodeWithTag("grid-zoom").performClick()
        compose.onNodeWithText("标准", substring = false).assertIsDisplayed()
        val standard = compose.onNodeWithTag("course-block-compact").getUnclippedBoundsInRoot()
        assertTrue("standard=${standard.right - standard.left}", standard.right - standard.left in 86.dp..96.dp)

        compose.onNodeWithTag("grid-zoom").performClick()
        compose.onNodeWithText("宽松", substring = false).assertIsDisplayed()
        val comfortable = compose.onNodeWithTag("course-block-compact").getUnclippedBoundsInRoot()
        assertTrue("comfortable=${comfortable.right - comfortable.left}", comfortable.right - comfortable.left >= 124.dp)

        compose.onNodeWithTag("grid-zoom").performClick()
        compose.onNodeWithText("全览", substring = false).assertIsDisplayed()
        compose.onNodeWithTag("day-header-7").assertIsDisplayed()
        compose.onNodeWithText("录入", substring = false).assertIsDisplayed()
    }

    @Test fun oneDragPansBothAxesAtOnce() {
        val vm = fixture()
        vm.addCourse(Course("diag", "自由平移课程", 1, 1, 1))
        // Long titles on Tuesday make the column taller than the viewport, so both axes have
        // somewhere to travel. The drag tier keeps the week wider than the screen.
        repeat(13) { index ->
            vm.addCourse(Course("fill-${index + 1}", "长标题用于撑满纵向空间以便验证自由平移是否同时作用于两个方向", 2, index + 1, index + 1))
        }
        compose.setContent { ScheduleApp(vm) }
        compose.onNodeWithTag("grid-zoom").performClick()
        compose.waitForIdle()
        val before = compose.onNodeWithTag("course-block-diag").getUnclippedBoundsInRoot()
        val headingBefore = compose.onNodeWithTag("day-header-1").getUnclippedBoundsInRoot()
        val gutterBefore = compose.onNodeWithTag("period-1").getUnclippedBoundsInRoot()

        compose.onNodeWithTag("timetable-grid").performTouchInput {
            swipe(
                start = center + androidx.compose.ui.geometry.Offset(240f, 320f),
                end = center + androidx.compose.ui.geometry.Offset(-160f, -420f),
                durationMillis = 260,
            )
        }
        compose.waitForIdle()

        val after = compose.onNodeWithTag("course-block-diag").getUnclippedBoundsInRoot()
        assertTrue("horizontal must follow the drag: ${before.left} -> ${after.left}", after.left.value < before.left.value - 1f)
        assertTrue("vertical must follow the drag: ${before.top} -> ${after.top}", after.top.value < before.top.value - 1f)
        assertEquals((before.right - before.left).value, (after.right - after.left).value, .5f)
        // The heading and the time gutter still follow only their own axis.
        val headingAfter = compose.onNodeWithTag("day-header-1").getUnclippedBoundsInRoot()
        val gutterAfter = compose.onNodeWithTag("period-1").getUnclippedBoundsInRoot()
        assertTrue("heading must scroll horizontally", headingAfter.left.value < headingBefore.left.value - 1f)
        assertEquals(headingBefore.top.value, headingAfter.top.value, .5f)
        assertTrue("gutter must scroll vertically", gutterAfter.top.value < gutterBefore.top.value - 1f)
        assertEquals(gutterBefore.left.value, gutterAfter.left.value, .5f)
    }

    @Test fun aFastFlickGlidesFurtherThanASlowDragOfTheSameDistance() {
        val vm = fixture()
        vm.addCourse(Course("flick", "自由平移课程", 1, 1, 2))
        compose.setContent { ScheduleApp(vm) }
        compose.onNodeWithTag("grid-zoom").performClick()
        compose.onNodeWithTag("grid-zoom").performClick() // the widest tier leaves room for a long glide
        compose.waitForIdle()
        fun left() = compose.onNodeWithTag("course-block-flick").getUnclippedBoundsInRoot().left.value

        // Both gestures travel the same distance; only the timing differs, so any extra movement
        // in the second one is the fling that follows the finger.
        fun drag(steps: Int, millisPerStep: Long, stepPx: Float) {
            compose.onNodeWithTag("timetable-grid").performTouchInput {
                val origin = center
                down(0, origin)
                repeat(steps) { step ->
                    advanceEventTime(millisPerStep)
                    moveTo(0, origin + androidx.compose.ui.geometry.Offset(stepPx * (step + 1), 0f))
                }
                up(0)
            }
            compose.waitForIdle()
        }

        // The glide runs on its own coroutine, so wait until the position stops changing instead
        // of assuming a single idle pass covers it.
        fun settledLeft(): Float {
            var previous = left()
            repeat(60) {
                Thread.sleep(30)
                compose.waitForIdle()
                val current = left()
                if (kotlin.math.abs(current - previous) < 0.01f) return current
                previous = current
            }
            return previous
        }

        val start = left()
        drag(steps = 10, millisPerStep = 50, stepPx = -22f)
        val slow = start - settledLeft()

        val beforeFlick = left()
        drag(steps = 5, millisPerStep = 12, stepPx = -44f)
        val fast = beforeFlick - settledLeft()

        assertTrue("slow=$slow fast=$fast", fast > slow + 80f)
    }

    @Test fun pinchWidensTheWeekAndPinchingBackRestoresTheOverview() {
        val vm = fixture()
        vm.addCourse(Course("pinch", "C语言程序设计基础及实验", 1, 1, 2))
        compose.setContent { ScheduleApp(vm) }
        val overview = compose.onNodeWithTag("course-block-pinch").getUnclippedBoundsInRoot()

        compose.onNodeWithTag("timetable-grid").performTouchInput {
            val middle = center
            down(0, middle + androidx.compose.ui.geometry.Offset(-30f, 0f))
            down(1, middle + androidx.compose.ui.geometry.Offset(30f, 0f))
            repeat(4) {
                advanceEventTime(16)
                val spread = 30f + 20f * (it + 1)
                moveTo(0, middle + androidx.compose.ui.geometry.Offset(-spread, 0f))
                moveTo(1, middle + androidx.compose.ui.geometry.Offset(spread, 0f))
            }
            up(0)
            up(1)
        }
        compose.waitForIdle()
        val widened = compose.onNodeWithTag("course-block-pinch").getUnclippedBoundsInRoot()
        assertTrue(
            "pinch out must widen the column: ${overview.right - overview.left} -> ${widened.right - widened.left}",
            widened.right - widened.left > (overview.right - overview.left) * 1.5f,
        )
        compose.onNodeWithText("全览", substring = false).assertDoesNotExist()

        compose.onNodeWithTag("timetable-grid").performTouchInput {
            val middle = center
            down(0, middle + androidx.compose.ui.geometry.Offset(-110f, 0f))
            down(1, middle + androidx.compose.ui.geometry.Offset(110f, 0f))
            repeat(4) {
                advanceEventTime(16)
                val spread = 110f - 24f * (it + 1)
                moveTo(0, middle + androidx.compose.ui.geometry.Offset(-spread, 0f))
                moveTo(1, middle + androidx.compose.ui.geometry.Offset(spread, 0f))
            }
            up(0)
            up(1)
        }
        compose.waitForIdle()
        val restored = compose.onNodeWithTag("course-block-pinch").getUnclippedBoundsInRoot()
        assertTrue(
            "pinching back out must return to the fitted week: ${restored.right - restored.left}",
            restored.right - restored.left <= overview.right - overview.left + 1.dp,
        )
        compose.onNodeWithText("全览", substring = false).assertIsDisplayed()
        compose.onNodeWithTag("day-header-7").assertIsDisplayed()
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
