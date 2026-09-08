package com.kebiao.app.imports

import com.kebiao.app.domain.model.WeekRule
import com.kebiao.app.ocr.CourseDraft
import com.kebiao.app.ocr.DraftField
import com.kebiao.app.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ImportApplyStateTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setupDispatcher() = Dispatchers.setMain(dispatcher)
    @After fun resetDispatcher() = Dispatchers.resetMain()

    @Test
    fun applyRejectsMissingDiscardedOrOutdatedReview() {
        val vm = AppViewModel()
        val original = listOf(draft())
        vm.saveImportDrafts(original)
        assertFalse(vm.uiState.value.importBusy)

        vm.editImportDrafts(original)
        val edited = listOf(original.single().copy(name = DraftField("已修正课程", 1f)))
        vm.editImportDrafts(edited)
        vm.saveImportDrafts(original)
        assertFalse(vm.uiState.value.importBusy)
        assertEquals(edited, vm.uiState.value.importDrafts)

        vm.cancelImport()
        vm.saveImportDrafts(edited)
        assertFalse(vm.uiState.value.importBusy)
        assertNull(vm.uiState.value.importDrafts)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun invalidBatchExplainsCourseAndPreservesAllDraftsWithoutPartialApply() {
        val vm = AppViewModel()
        val drafts = listOf(draft(), draft().copy(weekday = DraftField(null, 0f)))
        vm.editImportDrafts(drafts)

        vm.saveImportDrafts(drafts)

        assertFalse(vm.uiState.value.importBusy)
        assertEquals(drafts, vm.uiState.value.importDrafts)
        assertTrue(vm.uiState.value.courses.isEmpty())
        assertTrue(vm.uiState.value.errorMessage.orEmpty().startsWith("第 2 门课程："))
        assertTrue(vm.uiState.value.errorMessage.orEmpty().contains("星期"))
    }

    @Test
    fun currentScheduleLimitIsRecheckedAtApplyTime() {
        val vm = AppViewModel()
        val periodCount = vm.uiState.value.settings.periods.size
        val drafts = listOf(draft().copy(endPeriod = DraftField(periodCount + 1, 1f)))
        vm.editImportDrafts(drafts)

        vm.saveImportDrafts(drafts)

        assertFalse(vm.uiState.value.importBusy)
        assertEquals(drafts, vm.uiState.value.importDrafts)
        assertTrue(vm.uiState.value.errorMessage.orEmpty().contains("当前作息"))
    }

    @Test
    fun unconfirmedBatchStartsOnceAndFailureKeepsEditsForRetry() {
        // No repository deliberately exercises the save-failure path without a fake persistence API.
        val vm = AppViewModel()
        val drafts = listOf(draft())
        vm.editImportDrafts(drafts)

        vm.saveImportDrafts(drafts)
        assertTrue(vm.uiState.value.importBusy)
        vm.saveImportDrafts(drafts)
        vm.cancelImport()
        vm.editImportDrafts(emptyList())
        assertEquals(drafts, vm.uiState.value.importDrafts)

        dispatcher.scheduler.runCurrent()
        assertFalse(vm.uiState.value.importBusy)
        assertEquals(drafts, vm.uiState.value.importDrafts)
        assertEquals("数据库尚未初始化", vm.uiState.value.errorMessage)

        vm.saveImportDrafts(drafts)
        assertTrue(vm.uiState.value.importBusy)
        assertNull(vm.uiState.value.errorMessage)
        dispatcher.scheduler.runCurrent()
        assertEquals(drafts, vm.uiState.value.importDrafts)
    }

    private fun draft() = CourseDraft(
        name = DraftField("待应用课程", .7f), weekday = DraftField(1, .8f),
        startPeriod = DraftField(1, .8f), endPeriod = DraftField(2, .8f),
        weekRule = DraftField(WeekRule.ALL, .8f), building = DraftField(null, 0f),
        room = DraftField(null, 0f), locationNote = DraftField(null, 0f),
    )
}
