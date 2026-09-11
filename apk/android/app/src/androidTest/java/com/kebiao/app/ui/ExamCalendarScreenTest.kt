package com.kebiao.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.kebiao.app.data.ScheduleExam
import com.kebiao.app.ui.calendar.ExamCalendarScreen
import com.kebiao.app.ui.calendar.ExamEditorDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ExamCalendarScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun calendarFiltersExamsAndEventsSeparately() {
        val viewModel = AppViewModel()
        val date = java.time.LocalDate.now().toString()
        viewModel.addExam(ScheduleExam("exam", "期末数学", date))
        viewModel.addExam(ScheduleExam("event", "读书会", date, type = "EVENT", note = "带阅读笔记"))
        compose.setContent { MaterialTheme { ExamCalendarScreen(viewModel) } }
        compose.onNodeWithText("期末数学").assertIsDisplayed()
        compose.onNodeWithText("读书会").assertIsDisplayed()
        compose.onNodeWithText("日程", substring = false).performClick()
        compose.onNodeWithText("期末数学").assertDoesNotExist()
        compose.onNodeWithText("读书会").assertIsDisplayed()
        compose.onNodeWithText("考试", substring = false).performClick()
        compose.onNodeWithText("读书会").assertDoesNotExist()
        compose.onNodeWithText("期末数学").assertIsDisplayed()
        compose.onNodeWithText("全部").performClick()
        compose.onNodeWithText("带阅读笔记").assertIsDisplayed()
    }

    @Test fun manualEventEditorSavesGeneralNoteSeparatelyAndValidatesDates() {
        var saved: ScheduleExam? = null
        compose.setContent {
            MaterialTheme {
                ExamEditorDialog(onDismiss = {}, onSave = { saved = it }, onDelete = {}, initialType = "EVENT")
            }
        }
        compose.onNodeWithText("添加日程").assertIsDisplayed()
        compose.onNodeWithText("日程名称").performTextInput("读书会")
        compose.onNodeWithContentDescription("日期").performClick()
        compose.onNodeWithText("确定日期").performClick()
        compose.onNodeWithText("地点备注（可选）").performScrollTo().performTextInput("东门")
        compose.onNodeWithText("备注（可选）", substring = false).performScrollTo().performTextInput("带阅读笔记")
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle {
            assertEquals("EVENT", saved!!.type)
            assertEquals("读书会", saved!!.subject)
            assertEquals(java.time.LocalDate.now().toString(), saved!!.date)
            assertEquals(null, saved!!.time)
            assertEquals("东门", saved!!.locationNote)
            assertEquals("带阅读笔记", saved!!.note)
        }
    }
}

