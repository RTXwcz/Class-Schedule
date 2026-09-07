package com.kebiao.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kebiao.app.data.local.AppDatabase
import com.kebiao.app.data.local.CourseEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelStore
import com.kebiao.app.ui.AppViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
    private lateinit var database: AppDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun insertsAndObservesCourses() = runBlocking {
        database.scheduleDao().insertCourses(
            listOf(
                CourseEntity("c1", "高等数学", 1, 1, 2, "ALL", "理科楼", "C203", null, "TEST", 0, 0),
            ),
        )

        val courses = database.scheduleDao().observeCourses().first()

        assertEquals(listOf("c1"), courses.map { it.id })
        assertEquals("理科楼", courses.single().building)
    }

    @Test
    fun imageImportAppendsWithoutReplacingExistingSchedule() = runBlocking {
        val repository = ScheduleRepository(database)
        repository.replaceAll(ScheduleExport(
            courses = listOf(ScheduleCourse("existing", "数学", 1, 1, 2)),
            exams = listOf(ScheduleExam("exam", "数学", "2026-09-10", "09:00")),
            overrides = listOf(ScheduleOverrideRecord("2026-09-12", 1, "补课")),
        ))
        repository.appendCourses(listOf(ScheduleCourse("imported", "物理", 2, 3, 4, source = "OPENAI")))
        val snapshot = repository.snapshot()
        assertEquals(setOf("existing", "imported"), snapshot.courses.map { it.id }.toSet())
        assertEquals("exam", snapshot.exams.single().id)
        assertEquals("2026-09-12", snapshot.overrides.single().date)
    }

    @Test
    fun concurrentRecordEditsKeepOtherRecordsAndCollections() = runBlocking {
        val repository = ScheduleRepository(database)
        repository.upsertExam(ScheduleExam("exam", "数学", "2026-09-10", "09:00"))
        repository.upsertOverride(ScheduleOverrideRecord("2026-09-12", 1))
        coroutineScope {
            repeat(12) { index ->
                launch(Dispatchers.IO) {
                    repository.upsertCourse(ScheduleCourse("c$index", "课程 $index", 1, 1, 2))
                }
            }
        }
        val before = repository.snapshot().courses.first { it.id == "c0" }
        repository.upsertCourse(before.copy(name = "修改名称", createdAtEpochMillis = 0))
        repository.deleteCourse("c1")
        val snapshot = repository.snapshot()
        assertEquals(11, snapshot.courses.size)
        assertEquals("exam", snapshot.exams.single().id)
        assertEquals("2026-09-12", snapshot.overrides.single().date)
        val edited = snapshot.courses.first { it.id == "c0" }
        assertEquals("修改名称", edited.name)
        assertTrue(edited.createdAtEpochMillis > 0)
        assertEquals(before.createdAtEpochMillis, edited.createdAtEpochMillis)
        assertTrue(edited.updatedAtEpochMillis >= before.updatedAtEpochMillis)
        repository.deleteExam("exam")
        repository.deleteOverride("2026-09-12")
        assertEquals(11, repository.snapshot().courses.size)
        assertTrue(repository.snapshot().exams.isEmpty())
        assertTrue(repository.snapshot().overrides.isEmpty())
    }

    @Test
    fun failedAppendRollsBackEntireBatchAndInvalidReplacementPreservesData() = runBlocking {
        val repository = ScheduleRepository(database)
        val original = ScheduleCourse("existing", "数学", 1, 1, 2)
        repository.upsertCourse(original)
        val appendError = runCatching {
            repository.appendCourses(listOf(original.copy(id = "new"), original.copy(name = "重复")))
        }.exceptionOrNull()
        assertNotNull(appendError)
        assertEquals(listOf("existing"), repository.snapshot().courses.map { it.id })
        val replaceError = runCatching {
            repository.replaceAll(ScheduleExport(exams = listOf(ScheduleExam("bad", "数学", "not-a-date"))))
        }.exceptionOrNull()
        assertNotNull(replaceError)
        assertEquals("数学", repository.snapshot().courses.single().name)
    }

    @Test
    fun editingBeforeInitialFlowsLoadDoesNotEraseStoredCourses() = runBlocking {
        val repository = ScheduleRepository(database)
        repository.upsertCourse(ScheduleCourse("existing", "数学", 1, 1, 2))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val store = ViewModelStore()
        try {
            instrumentation.runOnMainSync {
                val viewModel = AppViewModel(repository)
                store.put("test", viewModel)
                viewModel.addExam(ScheduleExam("new-exam", "物理", "2026-09-10"))
            }
            withTimeout(10_000) { repository.observeExams().first { it.any { exam -> exam.id == "new-exam" } } }
            assertEquals("existing", repository.snapshot().courses.single().id)
        } finally {
            instrumentation.runOnMainSync { store.clear() }
        }
    }
}
