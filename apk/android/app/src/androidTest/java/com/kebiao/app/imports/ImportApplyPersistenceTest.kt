package com.kebiao.app.imports

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.kebiao.app.data.ScheduleRepository
import com.kebiao.app.data.local.AppDatabase
import com.kebiao.app.data.settings.AppSettingsStore
import com.kebiao.app.domain.model.WeekRule
import com.kebiao.app.ocr.CourseDraft
import com.kebiao.app.ocr.DraftField
import com.kebiao.app.ui.AppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A screenshot usually mixes readable courses with one that does not fit the current schedule yet.
 * The readable ones must reach the database while the other stays in the review list with a reason.
 */
class ImportApplyPersistenceTest {
    @Test fun appliesReadableCoursesAndKeepsTheRestForReview() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repository = ScheduleRepository(database)
            val viewModel = AppViewModel(repository = repository, settingsStore = AppSettingsStore(context))
            withTimeout(5_000) { while (viewModel.uiState.value.settings.periods.isEmpty()) delay(20) }
            val periodCount = viewModel.uiState.value.settings.periods.size
            val readable = draft("可导入课程", periodCount - 1, periodCount - 1)
            val beyond = draft("超出作息课程", periodCount, periodCount + 1)
            val drafts = listOf(readable, beyond)
            viewModel.editImportDrafts(drafts)
            viewModel.saveImportDrafts(drafts)
            withTimeout(10_000) { while (viewModel.uiState.value.importBusy) delay(20) }

            val applied = repository.snapshot().courses
            assertEquals(1, applied.size)
            assertEquals("可导入课程", applied.single().name)
            assertEquals(listOf(beyond.reviewId), viewModel.uiState.value.importDrafts?.map { it.reviewId })
            assertTrue(viewModel.uiState.value.errorMessage.orEmpty().contains("当前作息"))

            // Adopting the schedule read from the image lets the remaining course apply as well.
            viewModel.updateSettings { it.copy(periods = it.periods + com.kebiao.app.notifications.LessonPeriod("21:40", "22:30")) }
            withTimeout(5_000) { while (viewModel.uiState.value.settings.periods.size == periodCount) delay(20) }
            viewModel.editImportDrafts(listOf(beyond))
            viewModel.saveImportDrafts(listOf(beyond))
            withTimeout(10_000) { while (viewModel.uiState.value.importBusy) delay(20) }
            assertEquals(2, repository.snapshot().courses.size)
            assertNull(viewModel.uiState.value.importDrafts)
        } finally {
            database.close()
        }
    }

    private fun draft(name: String, start: Int, end: Int) = CourseDraft(
        name = DraftField(name, 0.9f),
        weekday = DraftField(1, 0.9f),
        startPeriod = DraftField(start, 0.9f),
        endPeriod = DraftField(end, 0.9f),
        weekRule = DraftField(WeekRule.ALL, 0.9f),
        building = DraftField(null, 0f),
        room = DraftField(null, 0f),
        locationNote = DraftField(null, 0f),
    )
}
