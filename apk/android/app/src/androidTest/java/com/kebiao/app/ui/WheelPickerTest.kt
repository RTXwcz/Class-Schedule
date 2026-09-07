package com.kebiao.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kebiao.app.ui.components.DateWheelDialog
import com.kebiao.app.ui.components.TimeWheelDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class WheelPickerTest {
    @get:Rule val compose = createComposeRule()
    @Test fun monthSwipeClampsDayToLeapFebruaryThenYearChangeClampsAgain() {
        var selected: LocalDate? = null
        compose.setContent { MaterialTheme { DateWheelDialog(LocalDate.of(2024, 1, 31), onDismiss = {}, onConfirm = { selected = it }) } }
        compose.onNodeWithTag("wheel-月").performTouchInput { swipe(center, center - Offset(0f, height / 5f), 600) }
        compose.waitForIdle()
        compose.onNodeWithText("2024年2月29日", substring = true).assertIsDisplayed()
        wheelStep("wheel-年", "增加年")
        compose.onNodeWithText("2025年2月28日", substring = true).assertIsDisplayed()
        compose.onNodeWithText("确定日期").performClick()
        compose.runOnIdle { assertEquals(LocalDate.of(2025, 2, 28), selected) }
    }
    @Test fun timeWheelCommitsExactMinuteWithoutKeyboardInput() {
        var selected: LocalTime? = null
        compose.setContent { MaterialTheme { TimeWheelDialog(LocalTime.of(7, 59), onDismiss = {}, onConfirm = { selected = it }) } }
        compose.onNodeWithTag("wheel-时").performTouchInput { swipe(center, center - Offset(0f, height / 5f), 600) }
        wheelStep("wheel-分", "减少分")
        compose.onNodeWithText("确定时间").performClick()
        compose.runOnIdle { assertEquals(LocalTime.of(8, 58), selected) }
    }
    private fun wheelStep(tag: String, label: String) {
        val action = compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == label }
        compose.runOnIdle { action.action() }
        compose.waitForIdle()
    }

}
