package com.kebiao.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.kebiao.app.ocr.DraftField
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Exercise the real application shell so status/navigation/keyboard insets are included. */
class ReviewLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun purpleReviewKeepsEditingAndApplyReachableInBothBrightnessModes() {
        val vm = AppViewModel()
        vm.updateSettings { it.copy(colorPalette = "purple", theme = "light") }
        vm.editImportDrafts(listOf(
            ImportReviewEfficiencyTest.draft("C语言程序设计基础及实验").copy(weeks = DraftField("1-16", .96f)),
            ImportReviewEfficiencyTest.draft("高等数学"), ImportReviewEfficiencyTest.draft("大学英语")))
        compose.setContent { ScheduleApp(vm) }
        compose.onNodeWithText("录入", substring = false).performClick()
        compose.onNodeWithText("应用识别结果").assertIsDisplayed()
        screenshot("review-purple-light")
        compose.onNodeWithTag("review-list").performScrollToNode(hasTestTag("review-card-0"))
        compose.onNodeWithTag("review-card-0").performClick()
        compose.onNodeWithText("课程名称", substring = false).performScrollTo().assertIsDisplayed().performTextReplacement("C语言程序设计基础及实验")
        compose.onNodeWithText("应用识别结果").assertIsDisplayed()
        Espresso.closeSoftKeyboard()
        screenshot("review-purple-expanded")
        compose.onNodeWithTag("review-list").performScrollToIndex(0)
        compose.onNodeWithTag("review-list").performScrollToNode(hasTestTag("review-card-0"))
        compose.onNodeWithTag("review-card-0").performClick()
        compose.runOnIdle { vm.updateSettings { it.copy(theme = "dark") } }
        compose.onNodeWithText("应用识别结果").assertIsDisplayed()
        screenshot("review-purple-dark")
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        Thread.sleep(400)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
