package com.kebiao.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import java.time.LocalDate
import com.kebiao.app.domain.model.Course
import com.kebiao.app.data.ScheduleExam
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EntryFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun settingsChangesDailyCountAndEachPeriodTime() {
        val vm = AppViewModel()
        compose.setContent { ScheduleApp(vm) }
        compose.onNodeWithText("设置", substring = false).performClick()
        compose.onNodeWithText("编辑作息").performClick()
        compose.onNodeWithText("每天节数").performTextReplacement("2")
        compose.onNodeWithText("应用节数").performClick()
        compose.onAllNodesWithText("开始 HH:mm")[0].performTextReplacement("07:45")
        compose.onNodeWithText("保存作息").performClick()
        compose.runOnIdle {
            assertEquals(2, vm.uiState.value.settings.periods.size)
            assertEquals("07:45", vm.uiState.value.settings.periods.first().start)
        }
        compose.onNodeWithText("每天 2 节", substring = true).assertIsDisplayed()
        screenshot("custom-periods")
    }

    @Test fun manualCourseAndEventWorkWithoutOcrOrApiSetup() {
        val vm = AppViewModel()
        compose.setContent { ScheduleApp(vm) }
        compose.onNodeWithText("录入", substring = false).performClick()
        compose.onNodeWithText("手动填写").assertIsDisplayed()
        screenshot("entry-options")
        compose.onNodeWithText("添加课程", substring = false).performClick()
        compose.onNodeWithText("课程名称").performTextInput("高等数学")
        compose.onNodeWithText("保存", substring = false).performClick()
        compose.runOnIdle { assertEquals("高等数学", vm.uiState.value.courses.single().name) }
        compose.onNodeWithText("添加日程", substring = false).performClick()
        compose.onNodeWithText("日程名称").performTextInput("社团见面会")
        compose.onNodeWithText("备注（可选）", substring = false).performScrollTo().performTextInput("带学生证")
        compose.onNodeWithText("保存", substring = false).performClick()
        compose.runOnIdle { assertEquals("EVENT", vm.uiState.value.exams.single().type) }
        compose.onNodeWithText("安排", substring = false).performClick()
        compose.onNodeWithText("社团见面会").assertIsDisplayed()
        compose.runOnIdle {
            val date = LocalDate.now().toString()
            vm.addExam(ScheduleExam("sample-exam", "大学英语期中考试", date, "14:00", "教学楼 B", "305"))
        }
        screenshot("dated-items")
        compose.runOnIdle {
            vm.addCourse(Course("physics", "大学物理", 2, 3, 4, building = "理科楼", room = "302"))
            vm.addCourse(Course("english", "大学英语", 3, 1, 2, building = "外语楼", room = "205"))
            vm.addCourse(Course("coding", "程序设计", 4, 5, 6, building = "实验楼", room = "101"))
            vm.addCourse(Course("sports", "体育", 5, 3, 4, building = "体育馆"))
        }
        compose.onNodeWithText("课表", substring = false).performClick()
        compose.onNodeWithText("高等数学").assertIsDisplayed()
        screenshot("whole-week")
        compose.onNodeWithText("展开", substring = false).performClick()
        compose.onNodeWithText("整周", substring = false).assertIsDisplayed()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = File(instrumentation.targetContext.getExternalFilesDir(null), "qa-$name.png")
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
