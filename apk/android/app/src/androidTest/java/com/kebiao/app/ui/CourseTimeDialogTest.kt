package com.kebiao.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso
import com.kebiao.app.domain.model.Course
import com.kebiao.app.ui.components.CourseTimeDialog
import com.kebiao.app.ui.timetable.TimetableScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class CourseTimeDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cancelAndSystemBackNeverCommitTemporarySelections() {
        var confirmed: Triple<Int, Int, Int>? = null
        var dismissCount = 0
        compose.setContent {
            MaterialTheme {
                CourseTimeDialog(2, 3, 4, 12, onDismiss = { dismissCount++ },
                    onConfirm = { day, start, end -> confirmed = Triple(day, start, end) })
            }
        }
        wheelStep("wheel-星期", "增加星期")
        wheelStep("wheel-起始节", "增加起始节")
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertNull(confirmed); assertEquals(1, dismissCount) }
        Espresso.pressBack()
        compose.runOnIdle { assertNull(confirmed); assertEquals(2, dismissCount) }
    }

    @Test fun missingOcrValuesRemainUncommittedUntilExplicitConfirmation() {
        var confirmed: Triple<Int, Int, Int>? = null
        compose.setContent {
            MaterialTheme {
                CourseTimeDialog(null, null, null, 12, onDismiss = {},
                    onConfirm = { day, start, end -> confirmed = Triple(day, start, end) })
            }
        }
        compose.runOnIdle { assertNull(confirmed) }
        wheelStep("wheel-星期", "增加星期")
        wheelStep("wheel-起始节", "增加起始节")
        wheelStep("wheel-结束节", "增加结束节")
        compose.runOnIdle { assertNull(confirmed) }
        compose.onNodeWithText("确定时段").performClick()
        compose.runOnIdle { assertEquals(Triple(2, 2, 3), confirmed) }
    }

    @Test fun confirmationWaitsUntilTheWheelStopsMoving() {
        compose.setContent {
            MaterialTheme { CourseTimeDialog(2, 3, 4, 12, onDismiss = {}, onConfirm = { _, _, _ -> }) }
        }
        compose.onNodeWithTag("wheel-星期").performTouchInput {
            down(center)
            advanceEventTime(60)
            moveTo(center - Offset(0f, height / 3f))
        }
        compose.onNodeWithText("确定时段").assertIsNotEnabled()
        compose.onNodeWithTag("wheel-星期").performTouchInput { up() }
        compose.waitForIdle()
        compose.onNodeWithText("确定时段").assertIsEnabled()
    }

    @Test fun editingFromAllCoursesReturnsToTheSameListPositionAfterSaveAndCancel() {
        val vm = AppViewModel()
        repeat(30) { index -> vm.addCourse(Course("list-$index", "列表课程${index.toString().padStart(2, '0')}", index / 5 + 1, index % 5 + 1, index % 5 + 1)) }
        compose.setContent { MaterialTheme { TimetableScreen(vm) } }
        compose.onNodeWithText("全部课程 · 30").performClick()
        compose.onNodeWithTag("all-courses-list").performScrollToNode(hasText("列表课程25"))
        val listCourse = hasText("列表课程25") and hasAnyAncestor(hasTestTag("all-courses-list"))
        compose.onNode(listCourse).performClick()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("全部课程", substring = false).assertIsDisplayed()
        compose.onNode(listCourse).assertIsDisplayed().performClick()
        compose.onNodeWithText("保存", substring = false).performClick()
        compose.onNodeWithText("全部课程", substring = false).assertIsDisplayed()
        compose.onNode(listCourse).assertIsDisplayed()
    }

    private fun wheelStep(tag: String, label: String) {
        val action = compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == label }
        compose.runOnIdle { action.action() }
        compose.waitForIdle()
    }
}
