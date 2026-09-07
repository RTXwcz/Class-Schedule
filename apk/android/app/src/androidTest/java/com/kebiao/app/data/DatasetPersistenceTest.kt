package com.kebiao.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kebiao.app.data.local.AppDatabase
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatasetPersistenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "dataset-persistence-${UUID.randomUUID()}.db"
    private var database: AppDatabase? = null

    @After fun cleanUp() {
        database?.close()
        context.deleteDatabase(databaseName)
    }

    private fun open(): ScheduleRepository {
        val opened = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3).build()
        database = opened
        return ScheduleRepository(opened)
    }

    private fun reopen(): ScheduleRepository {
        database?.close()
        return open()
    }

    @Test fun newDatasetIdentityAndTimestampSurviveReadsAndDiskReopen() = runBlocking {
        val repository = open()
        val metadata = repository.observeMetadata().first()
        assertEquals(metadata.datasetId, UUID.fromString(metadata.datasetId).toString())
        assertEquals("NATIVE", metadata.source)
        assertTrue(metadata.updatedAt.isNotBlank())
        assertEquals(metadata, repository.snapshot())
        assertEquals(metadata, repository.snapshot())
        assertEquals(metadata, reopen().snapshot())
    }

    @Test fun importedMetadataAndCompleteCourseSurviveReopenAndRecordEdits() = runBlocking {
        val imported = ScheduleExport(
            schemaVersion = 1,
            datasetId = "portable-semester-2026",
            updatedAt = "2026-01-02T03:04:05Z",
            source = "WEB_IMPORT",
            courses = listOf(ScheduleCourse("course", "数学", 1, 1, 2,
                teacher = "张老师", weeks = listOf(1, 3, 5), courseNote = "携带教材")),
            exams = listOf(ScheduleExam("exam", "数学期末", "2026-12-20")),
            overrides = listOf(ScheduleOverrideRecord("2026-09-12", 1, "补课")),
            extraFields = mapOf("integration" to Json.parseToJsonElement("""{"owner":"student","tags":["fall",7],"enabled":true,"optional":null}""")),
        )
        open().replaceAll(imported)
        val repository = reopen()
        assertEquals(imported, repository.snapshot())
        val originalCourse = repository.observeCourses().first().single()
        assertEquals(imported.courses.single(), originalCourse.toScheduleCourse())

        repository.upsertCourse(originalCourse.toScheduleCourse().copy(name = "高等数学"))
        var saved = repository.snapshot()
        assertEquals(imported.datasetId, saved.datasetId)
        assertEquals(imported.extraFields, saved.extraFields)
        assertEquals("NATIVE", saved.source)
        assertNotEquals(imported.updatedAt, saved.updatedAt)
        assertTrue(Instant.parse(saved.updatedAt).isAfter(Instant.parse(imported.updatedAt)))
        assertEquals("张老师", saved.courses.single().teacher)
        assertEquals(listOf(1, 3, 5), saved.courses.single().weeks)
        assertEquals("携带教材", saved.courses.single().courseNote)

        repository.appendCourses(listOf(ScheduleCourse("appended", "物理", 2, 3, 4)))
        assertEquals("OPENAI", repository.observeMetadata().first().source)
        repository.upsertExam(imported.exams.single().copy(subject = "数学考试"), source = "MCP")
        assertEquals("MCP", repository.observeMetadata().first().source)
        repository.upsertOverride(imported.overrides.single().copy(note = "更新"))
        assertEquals("NATIVE", repository.observeMetadata().first().source)
        repository.deleteCourse("appended", source = "MCP")
        repository.deleteExam("exam", source = "MCP")
        repository.deleteOverride("2026-09-12", source = "MCP")
        saved = repository.snapshot()
        assertEquals(imported.datasetId, saved.datasetId)
        assertEquals(imported.extraFields, saved.extraFields)
        assertEquals("MCP", saved.source)
        assertEquals(saved.copy(courses = emptyList(), exams = emptyList(), overrides = emptyList()),
            repository.observeMetadata().first())
        assertEquals(saved, reopen().snapshot())
    }

    @Test fun failedMutationRollsBackMetadataWithCourseRecords() = runBlocking {
        val repository = open()
        repository.upsertCourse(ScheduleCourse("existing", "数学", 1, 1, 2))
        val before = repository.snapshot()
        val failed = runCatching {
            repository.transaction {
                repository.upsertCourse(before.courses.single().copy(name = "未提交"), source = "MCP")
                repository.appendCourses(listOf(before.courses.single()))
            }
        }
        assertTrue(failed.isFailure)
        assertEquals(before, repository.snapshot())
        assertEquals(before, reopen().snapshot())
    }

    @Test fun versionOneMigrationPreservesAllRecordsAndAddsDurableMetadata() = runBlocking {
        val file = context.getDatabasePath(databaseName)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { legacy ->
            legacy.execSQL("CREATE TABLE courses (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, weekday INTEGER NOT NULL, startPeriod INTEGER NOT NULL, endPeriod INTEGER NOT NULL, weekRule TEXT NOT NULL, building TEXT, room TEXT, locationNote TEXT, source TEXT NOT NULL, createdAtEpochMillis INTEGER NOT NULL, updatedAtEpochMillis INTEGER NOT NULL)")
            legacy.execSQL("CREATE TABLE exams (id TEXT NOT NULL PRIMARY KEY, subject TEXT NOT NULL, date TEXT NOT NULL, time TEXT, building TEXT, room TEXT, locationNote TEXT, source TEXT NOT NULL, createdAtEpochMillis INTEGER NOT NULL, updatedAtEpochMillis INTEGER NOT NULL)")
            legacy.execSQL("CREATE TABLE schedule_overrides (date TEXT NOT NULL PRIMARY KEY, replacementWeekday INTEGER NOT NULL, note TEXT)")
            legacy.execSQL("INSERT INTO courses VALUES ('legacy-course', '数学', 1, 1, 2, 'ALL', 'A', '101', '东门', 'IMPORT', 10, 20)")
            legacy.execSQL("INSERT INTO exams VALUES ('legacy-exam', '数学期末', '2026-12-20', '09:00', 'B', '201', NULL, 'IMPORT', 30, 40)")
            legacy.execSQL("INSERT INTO schedule_overrides VALUES ('2026-09-12', 1, '补课')")
            legacy.version = 1
        }
        val repository = open()
        val migrated = repository.snapshot()
        assertEquals(ScheduleCourse("legacy-course", "数学", 1, 1, 2, "ALL", "A", "101", "东门", "IMPORT", 10, 20),
            migrated.courses.single())
        assertEquals(ScheduleExam("legacy-exam", "数学期末", "2026-12-20", "09:00", "B", "201", null, "IMPORT", 30, 40),
            migrated.exams.single())
        assertEquals(ScheduleOverrideRecord("2026-09-12", 1, "补课"), migrated.overrides.single())
        assertFalse(migrated.datasetId == "default")
        assertEquals(3, database!!.openHelper.readableDatabase.version)
        assertEquals(migrated, reopen().snapshot())
        repositoryAfterReopenCanStoreNewCourseFields(migrated.courses.single())
    }

    @Test fun versionTwoMigrationPreservesExamsAndEventFieldsSurviveDiskReopen() = runBlocking {
        val file = context.getDatabasePath(databaseName)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { legacy ->
            legacy.execSQL("CREATE TABLE courses (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, weekday INTEGER NOT NULL, startPeriod INTEGER NOT NULL, endPeriod INTEGER NOT NULL, weekRule TEXT NOT NULL, building TEXT, room TEXT, locationNote TEXT, source TEXT NOT NULL, createdAtEpochMillis INTEGER NOT NULL, updatedAtEpochMillis INTEGER NOT NULL, teacher TEXT, weeksJson TEXT NOT NULL DEFAULT '[]', courseNote TEXT)")
            legacy.execSQL("CREATE TABLE exams (id TEXT NOT NULL PRIMARY KEY, subject TEXT NOT NULL, date TEXT NOT NULL, time TEXT, building TEXT, room TEXT, locationNote TEXT, source TEXT NOT NULL, createdAtEpochMillis INTEGER NOT NULL, updatedAtEpochMillis INTEGER NOT NULL)")
            legacy.execSQL("CREATE TABLE schedule_overrides (date TEXT NOT NULL PRIMARY KEY, replacementWeekday INTEGER NOT NULL, note TEXT)")
            legacy.execSQL("CREATE TABLE dataset_metadata (id INTEGER NOT NULL PRIMARY KEY, schemaVersion INTEGER NOT NULL, datasetId TEXT NOT NULL, updatedAt TEXT NOT NULL, source TEXT NOT NULL, extraFieldsJson TEXT NOT NULL)")
            legacy.execSQL("INSERT INTO exams VALUES ('legacy-exam', '数学期末', '2026-12-20', '09:00', 'B', '201', '东门', 'IMPORT', 30, 40)")
            legacy.execSQL("INSERT INTO dataset_metadata VALUES (1, 1, 'old-dataset', '2026-09-07T00:00:00Z', 'IMPORT', '{}')")
            legacy.version = 2
        }
        val repository = open()
        val migrated = repository.snapshot()
        assertEquals("old-dataset", migrated.datasetId)
        val legacyExam = migrated.exams.single()
        assertEquals("EXAM", legacyExam.type)
        assertEquals(null, legacyExam.note)
        assertEquals("东门", legacyExam.locationNote)
        assertEquals(30L, legacyExam.createdAtEpochMillis)
        assertEquals(3, database!!.openHelper.readableDatabase.version)
        repository.upsertExam(ScheduleExam("event", "读书会", "2026-09-12", "15:30", "图书馆", "302", "北门",
            type = "EVENT", note = "带阅读笔记"))
        val afterCreate = reopen().snapshot()
        assertEquals(2, afterCreate.exams.size)
        val event = afterCreate.exams.single { it.id == "event" }
        assertEquals("EVENT", event.type)
        assertEquals("带阅读笔记", event.note)
        assertEquals("北门", event.locationNote)
        assertEquals(afterCreate.exams, JsonScheduleCodec.decode(JsonScheduleCodec.encode(afterCreate)).exams)
        val reopenedRepository = ScheduleRepository(database!!)
        reopenedRepository.upsertExam(event.copy(type = "EXAM", note = "改为测验"), source = "MCP")
        val edited = reopen().snapshot().exams.single { it.id == "event" }
        assertEquals("EXAM", edited.type)
        assertEquals("改为测验", edited.note)
        assertEquals(event.createdAtEpochMillis, edited.createdAtEpochMillis)
        ScheduleRepository(database!!).deleteExam(edited.id)
        assertEquals(listOf(legacyExam), reopen().snapshot().exams)
    }

    private suspend fun repositoryAfterReopenCanStoreNewCourseFields(original: ScheduleCourse) {
        val repository = ScheduleRepository(database!!)
        repository.upsertCourse(original.copy(teacher = "教师", weeks = listOf(2, 4), courseNote = "迁移后备注"))
        val saved = reopen().snapshot().courses.single()
        assertEquals("教师", saved.teacher)
        assertEquals(listOf(2, 4), saved.weeks)
        assertEquals("迁移后备注", saved.courseNote)
    }
}
