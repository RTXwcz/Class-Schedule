package com.kebiao.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso
import com.kebiao.app.data.ScheduleExam
import com.kebiao.app.domain.model.Course
import com.kebiao.app.ui.calendar.ExamEditorDialog
import com.kebiao.app.ui.timetable.CourseEditorDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class EditorDismissTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unchangedCourseClosesImmediatelyButChangedCourseCanBeKeptOrDiscarded() {
        var dismissed = 0
        compose.setContent {
            MaterialTheme {
                CourseEditorDialog(Course("course", "原有课程", 1, 1, 2),
                    onDismiss = { dismissed++ }, onSave = {}, onDelete = {})
            }
        }
        compose.onNodeWithText("编辑课程").assertIsDisplayed()
        systemBack()
        compose.runOnIdle { assertEquals(1, dismissed) }
        compose.onNodeWithText("放弃修改？").assertDoesNotExist()
        compose.onNodeWithText("上课时段").performClick()
        wheelStep("wheel-星期", "增加星期")
        compose.onNodeWithText("确定时段").performClick()
        systemBack()
        compose.onNodeWithText("放弃修改？").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, dismissed) }
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithText("周二 · 第 1–2 节").assertIsDisplayed()
        systemBack()
        compose.onNodeWithText("放弃修改", substring = false).performClick()
        compose.runOnIdle { assertEquals(2, dismissed) }
    }

    @Test fun returningFromTheTimePickerDoesNotDiscardOrChangeTheParentCourse() {
        var dismissed = 0
        compose.setContent {
            MaterialTheme {
                CourseEditorDialog(Course("course", "原有课程", 1, 1, 2),
                    onDismiss = { dismissed++ }, onSave = {}, onDelete = {})
            }
        }
        compose.onNodeWithText("上课时段").performClick()
        wheelStep("wheel-星期", "增加星期")
        systemBack()
        compose.onNodeWithText("放弃修改？").assertDoesNotExist()
        compose.onNodeWithText("周一 · 第 1–2 节").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, dismissed) }
        systemBack()
        compose.runOnIdle { assertEquals(1, dismissed) }
    }

    @Test fun changedExamBackPreservesFieldsAndExplicitSaveOrCancelNeedsNoExtraConfirmation() {
        var dismissed = 0
        var saved: ScheduleExam? = null
        compose.setContent {
            MaterialTheme {
                ExamEditorDialog(ScheduleExam("exam", "期末考试", "2026-09-10"),
                    onDismiss = { dismissed++ }, onSave = { saved = it }, onDelete = {})
            }
        }
        compose.onNodeWithText("日程", substring = false).performClick()
        systemBack()
        compose.onNodeWithText("放弃修改？").assertIsDisplayed()
        compose.runOnIdle { assertNull(saved); assertEquals(0, dismissed) }
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithText("保存", substring = false).performClick()
        compose.runOnIdle { assertEquals("EVENT", saved!!.type) }
        compose.onNodeWithText("放弃修改？").assertDoesNotExist()
        compose.onNodeWithText("取消", substring = false).performClick()
        compose.runOnIdle { assertEquals(1, dismissed) }
        compose.onNodeWithText("放弃修改？").assertDoesNotExist()
    }

    private fun systemBack() {
        compose.waitForIdle()
        Espresso.closeSoftKeyboard()
        Espresso.pressBack()
        compose.waitForIdle()
    }

    private fun wheelStep(tag: String, label: String) {
        val action = compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == label }
        compose.runOnIdle { action.action() }
        compose.waitForIdle()
    }
}
