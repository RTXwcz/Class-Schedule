package com.kebiao.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
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

    @Test fun anInactiveCourseIsAlwaysReachableAndCanBeDeletedWithConfirmation() {
        val vm = AppViewModel()
        vm.addCourse(Course("hidden", "限周课程", 1, 1, 2, weeks = listOf(1, 2)))
        compose.setContent { ScheduleApp(vm) }
        compose.onNodeWithText("限周课程").assertDoesNotExist()
        compose.onNodeWithText("全部课程 · 1").performClick()
        compose.onNodeWithText("限周课程").assertIsDisplayed().performClick()
        compose.onNodeWithText("删除", substring = false).performClick()
        compose.onNodeWithText("确认删除").performClick()
        compose.runOnIdle { assertEquals(0, vm.uiState.value.courses.size) }
    }

    @Test fun mcpGuideExplainsSetupAndDisablesConfigUntilServerIsReady() {
        compose.setContent { ScheduleApp(AppViewModel()) }
        compose.onNodeWithText("设置", substring = false).performClick()
        compose.onNodeWithText("AI 连接").performClick()
        compose.onNodeWithText("1  连接同一个可信 Wi-Fi").assertIsDisplayed()
        compose.onNodeWithText("复制完整 JSON 配置").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("连接不上？查看排查方法").performScrollTo().performClick()
        compose.onNodeWithText("401 通常表示", substring = true).performScrollTo().assertIsDisplayed()
        screenshot("mcp-guide")
    }

    @Test fun settingsChangesDailyCountAndEachPeriodTime() {
        val vm = AppViewModel()
        compose.setContent { ScheduleApp(vm) }
        compose.onNodeWithText("设置", substring = false).performClick()
        compose.onNodeWithText("编辑作息").performClick()
        compose.onNodeWithText("调整节数").performClick()
        repeat(10) { wheelStep("wheel-节数", "减少节数") }
        compose.onNodeWithText("应用节数").performClick()
        compose.onNodeWithContentDescription("第1节开始").performClick()
        wheelStep("wheel-时", "减少时")
        wheelStep("wheel-分", "增加分")
        compose.onNodeWithText("确定时间").performClick()
        compose.onNodeWithText("保存作息").performClick()
        compose.runOnIdle {
            assertEquals(2, vm.uiState.value.settings.periods.size)
            assertEquals("07:01", vm.uiState.value.settings.periods.first().start)
        }
        compose.onNodeWithText("2 节课", substring = true).assertIsDisplayed()
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
    private fun wheelStep(tag: String, label: String) {
        val action = compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == label }
        compose.runOnIdle { action.action() }
        compose.waitForIdle()
    }

}
