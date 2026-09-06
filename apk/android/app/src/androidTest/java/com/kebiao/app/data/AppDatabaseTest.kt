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
}
