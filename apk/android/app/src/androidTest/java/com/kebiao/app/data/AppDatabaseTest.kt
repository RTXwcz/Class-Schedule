package com.kebiao.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kebiao.app.data.local.AppDatabase
import com.kebiao.app.data.local.CourseEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
}
