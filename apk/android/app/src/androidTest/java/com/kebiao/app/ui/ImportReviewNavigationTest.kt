package com.kebiao.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso
import com.kebiao.app.domain.model.Course
import com.kebiao.app.domain.model.WeekRule
import com.kebiao.app.ocr.CourseDraft
import com.kebiao.app.ocr.DraftField
import com.kebiao.app.ui.importexport.ImportExportScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ImportReviewNavigationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun toolbarAndSystemBackPreserveEditsWhileDiscardRequiresConfirmation() {
        val vm = AppViewModel()
        vm.addCourse(Course("existing", "原有课程", 1, 1, 1))
        vm.editImportDrafts(listOf(CourseDraft(DraftField("待校对课程", .7f), DraftField(1, .8f), DraftField(1, .8f),
            DraftField(2, .8f), DraftField(WeekRule.ALL, .8f), DraftField("教学楼", .8f), DraftField("301", .8f), DraftField(null, 0f))))
        compose.setContent { MaterialTheme { ImportExportScreen(vm) } }
        compose.runOnIdle {
            vm.recognizeImage(android.net.Uri.parse("content://test/new-image"), local = true)
            assertEquals("待校对课程", vm.uiState.value.importDrafts!!.single().name.value)
            assertFalse(vm.uiState.value.importBusy)
        }
        compose.onNodeWithContentDescription("返回图片导入").assertIsDisplayed()
        compose.onNodeWithText("放弃", substring = false).assertIsDisplayed()
        compose.onNodeWithText("课程名称", substring = false).performTextReplacement("已修正名称")
        compose.onNodeWithContentDescription("返回图片导入").performClick()
        compose.onNodeWithText("选择课表图片 · 本地 OCR").assertIsNotEnabled()
        compose.onNodeWithText("选择课表图片 · OpenAI").assertIsNotEnabled()
        compose.onNodeWithText("继续校对").assertIsDisplayed().performClick()
        compose.onNodeWithText("已修正名称", substring = false).assertIsDisplayed()
        Espresso.pressBack()
        compose.onNodeWithText("继续校对").assertIsDisplayed().performClick()
        compose.onNodeWithText("放弃", substring = false).performClick()
        compose.onNodeWithText("继续保留").performClick()
        compose.onNodeWithText("已修正名称", substring = false).assertIsDisplayed()
        compose.onNodeWithText("放弃", substring = false).performClick()
        compose.onNodeWithText("确认放弃").performClick()
        compose.runOnIdle {
            assertNull(vm.uiState.value.importDrafts)
            assertEquals("原有课程", vm.uiState.value.courses.single().name)
        }
        compose.onNodeWithText("校对识别结果").assertDoesNotExist()
    }
}
