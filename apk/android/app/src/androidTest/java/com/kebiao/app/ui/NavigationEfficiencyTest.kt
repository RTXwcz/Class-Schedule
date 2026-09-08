package com.kebiao.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kebiao.app.data.ScheduleExam
import com.kebiao.app.ui.calendar.ExamCalendarScreen
import com.kebiao.app.ui.calendar.ExamEditorDialog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class NavigationEfficiencyTest {
    @get:Rule val compose = createComposeRule()

    @Test fun switchingTabsKeepsImportModeFilterAndSettingsSection() {
        val vm = AppViewModel()
        compose.setContent { ScheduleApp(vm) }
        compose.onNodeWithText("录入", substring = false).performClick()
        compose.onNodeWithText("图片识别", substring = false).performClick()
        compose.onNodeWithText("设置识别方式").performScrollTo().performClick()
        compose.onNodeWithText("本地图片识别").assertIsDisplayed()
        compose.onNodeWithText("录入", substring = false).performClick()
        compose.onNodeWithText("图片识别", substring = false).assertIsSelected()
        compose.onNodeWithText("安排", substring = false).performClick()
        compose.onNodeWithText("日程", substring = false).performClick()
        compose.onNodeWithText("课表", substring = false).performClick()
        compose.onNodeWithText("安排", substring = false).performClick()
        compose.onNodeWithText("日程", substring = false).assertIsSelected()
        compose.onNodeWithText("设置", substring = false).performClick()
        compose.onNodeWithText("AI 连接").performClick()
        compose.onNodeWithText("课表", substring = false).performClick()
        compose.onNodeWithText("设置", substring = false).performClick()
        compose.onNodeWithText("AI 连接").assertIsSelected()
    }

    @Test fun upcomingItemsAppearBeforeCollapsedHistory() {
        val vm = AppViewModel()
        repeat(30) { vm.addExam(ScheduleExam("past-$it", "历史安排 $it", LocalDate.now().minusDays(it + 1L).toString())) }
        vm.addExam(ScheduleExam("future", "明天要做的事", LocalDate.now().plusDays(1).toString(), type = "EVENT"))
        compose.setContent { MaterialTheme { ExamCalendarScreen(vm) } }
        compose.onNodeWithText("明天要做的事").assertIsDisplayed()
        compose.onNodeWithText("历史安排 0", substring = false).assertDoesNotExist()
        compose.onNodeWithText("查看过去安排 · 30").performClick()
        compose.onNodeWithText("历史安排 0", substring = false).assertExists()
    }

    @Test fun allDayRoundTripRetainsPreviouslySelectedTime() {
        var saved: ScheduleExam? = null
        val initial = ScheduleExam("time", "下午活动", LocalDate.now().toString(), time = "14:35", type = "EVENT")
        compose.setContent { MaterialTheme { ExamEditorDialog(initial, {}, { saved = it }, {}) } }
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithContentDescription("开始时间").assertDoesNotExist()
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText("14:35").assertIsDisplayed()
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle { assertEquals("14:35", saved!!.time) }
    }
}
