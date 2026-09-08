package com.kebiao.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.kebiao.app.domain.model.WeekRule
import com.kebiao.app.ocr.CourseDraft
import com.kebiao.app.ocr.DraftField
import com.kebiao.app.ui.importexport.ImportExportScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import androidx.room.Room
import com.kebiao.app.data.local.AppDatabase
import com.kebiao.app.data.ScheduleRepository
import kotlinx.coroutines.runBlocking

class ImportReviewEfficiencyTest {
    @get:Rule val compose = createComposeRule()

    @Test fun longImportIsCollapsedAndAppliesWithoutFieldCheckboxes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val repo = ScheduleRepository(db)
        val vm = AppViewModel(repo)
        val batch = (1..30).map { draft("课程 $it") }
        vm.editImportDrafts(batch)
        compose.setContent { ScheduleApp(vm) }
        compose.onNodeWithText("录入", substring = false).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("30 门课程，一次应用").assertIsDisplayed()
        Thread.sleep(400) // Let the window's first draw reach the screenshot surface.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // API 24 emulator display service can crash on UiAutomation screenshot capture.
        // Functional assertions and persistence still run on every supported API.
        if (android.os.Build.VERSION.SDK_INT >= 26) instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(instrumentation.targetContext.getExternalFilesDir(null), "review-efficiency.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        compose.onNodeWithText("课程名称", substring = false).assertDoesNotExist()
        compose.onAllNodes(isToggleable()).assertCountEquals(0)
        compose.onNodeWithText("应用识别结果", substring = false).assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { vm.uiState.value.importDrafts == null }
        compose.runOnIdle {
            vm.saveImportDrafts(batch)
            assertEquals(30, runBlocking { repo.snapshot().courses.size })
            assertNull(vm.uiState.value.importDrafts)
        }
        db.close()
    }

    @Test fun editsStayWithTheirCourseAndRemovalCanBeUndone() {
        val vm = AppViewModel()
        vm.editImportDrafts(listOf(draft("相同课名"), draft("相同课名")))
        compose.setContent { MaterialTheme { ImportExportScreen(vm) } }
        compose.onNodeWithTag("review-card-1").performClick()
        compose.onNodeWithText("课程名称", substring = false).performTextReplacement("第二门已修改")
        compose.onNodeWithTag("review-list").performScrollToNode(hasText("移除此课程"))
        compose.onNodeWithText("移除此课程").performClick()
        compose.runOnIdle { assertEquals("相同课名", vm.uiState.value.importDrafts!!.single().name.value) }
        compose.onNodeWithContentDescription("返回图片导入").performClick()
        compose.onNodeWithText("继续校对").performClick()
        compose.onNodeWithText("撤销移除").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf("相同课名", "第二门已修改"), vm.uiState.value.importDrafts!!.map { it.name.value }) }
    }

    @Test fun applyLocatesInvalidCourseAndBulkWeeksDoesNotInventItsTime() {
        val vm = AppViewModel()
        vm.editImportDrafts((1..20).map { draft("课程 $it") } + draft("缺项课程").copy(weekday = DraftField(null, 0f), weekRule = DraftField(null, 0f)))
        compose.setContent { MaterialTheme { ImportExportScreen(vm) } }
        compose.onNodeWithText("应用识别结果").performClick()
        compose.onNodeWithTag("review-card-20").assertIsDisplayed()
        compose.runOnIdle { assertEquals(21, vm.uiState.value.importDrafts!!.size); assertFalse(vm.uiState.value.importBusy) }
        compose.onNodeWithText("统一周次").performClick()
        compose.onNode(hasText("双周") and hasAnyAncestor(hasTestTag("bulk-weeks-dialog"))).performClick()
        compose.onNodeWithText("应用到 21 门课程").performClick()
        compose.runOnIdle {
            assertTrue(vm.uiState.value.importDrafts!!.all { it.weekRule.value == WeekRule.EVEN })
            assertNull(vm.uiState.value.importDrafts!!.last().weekday.value)
        }
    }

    @Test fun bulkWeekRangeKeepsEachCoursesOriginalParity() {
        val vm = AppViewModel()
        vm.editImportDrafts(listOf(draft("单周课").copy(weekRule = DraftField(WeekRule.ODD, .9f)),
            draft("双周课").copy(weekRule = DraftField(WeekRule.EVEN, .9f))))
        compose.setContent { MaterialTheme { ImportExportScreen(vm) } }
        compose.onNodeWithText("统一周次").performClick()
        compose.onNodeWithText("周次（空为全部，如 1-16）").performTextReplacement("1-16")
        compose.onNodeWithText("应用到 2 门课程").performClick()
        compose.runOnIdle {
            assertEquals(listOf(WeekRule.ODD, WeekRule.EVEN), vm.uiState.value.importDrafts!!.map { it.weekRule.value })
            assertTrue(vm.uiState.value.importDrafts!!.all { it.weeks.value == "1-16" })
        }
    }

    companion object {
        fun draft(name: String) = CourseDraft(DraftField(name, .96f), DraftField(2, .9f), DraftField(3, .9f),
            DraftField(4, .9f), DraftField(WeekRule.ALL, .9f), DraftField("理科楼", .9f), DraftField("A301", .9f),
            DraftField(null, 0f), teacher = DraftField("张老师", .9f))
    }
}
