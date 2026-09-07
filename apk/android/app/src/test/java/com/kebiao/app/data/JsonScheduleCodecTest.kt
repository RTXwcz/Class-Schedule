package com.kebiao.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFails

class JsonScheduleCodecTest {
    @Test
    fun decodesLegacyWebFieldsAndSplitsLocation() {
        val json = """
            {
              "schemaVersion": 1,
              "datasetId": "web-1",
              "updatedAt": "2026-09-06T12:00:00Z",
              "source": "WEB",
              "courses": [{"id":"c1","name":"高等数学","day":1,"start":1,"end":2,"week":"odd","location":"理科楼 C203"}],
              "exams": [{"id":"e1","subject":"高数","date":"2026-10-01","time":"09:00","location":"理科楼 C203"}],
              "unknown": {"kept": true}
            }
        """

        val export = JsonScheduleCodec.decode(json)

        assertEquals(1, export.schemaVersion)
        assertEquals("web-1", export.datasetId)
        assertEquals("2026-09-06T12:00:00Z", export.updatedAt)
        assertEquals("WEB", export.source)
        assertEquals(1, export.courses.size)
        assertEquals(1, export.courses.single().weekday)
        assertEquals(1, export.courses.single().startPeriod)
        assertEquals(2, export.courses.single().endPeriod)
        assertEquals("ODD", export.courses.single().weekRule)
        assertEquals("理科楼", export.courses.single().building)
        assertEquals("C203", export.courses.single().room)
        assertEquals("高数", export.exams.single().subject)
        assertTrue(export.extraFields.containsKey("unknown"))
    }

    @Test
    fun encodesSharedSchemaAndRoundTripsMetadata() {
        val input = ScheduleExport(
            schemaVersion = 2,
            datasetId = "dataset-42",
            updatedAt = "2026-09-06T12:00:00Z",
            source = "MANUAL",
            courses = listOf(
                ScheduleCourse(
                    id = "c1", name = "英语", weekday = 2, startPeriod = 3,
                    endPeriod = 4, weekRule = "ALL", building = "文科楼", room = "101",
                ),
            ),
        )

        val json = JsonScheduleCodec.encode(input)
        assertTrue(json.contains("\"weekday\""))
        assertTrue(json.contains("\"startPeriod\""))
        assertTrue(!json.contains("\"day\""))

        val output = JsonScheduleCodec.decode(json)
        assertEquals(input.schemaVersion, output.schemaVersion)
        assertEquals(input.datasetId, output.datasetId)
        assertEquals(input.updatedAt, output.updatedAt)
        assertEquals(input.courses, output.courses)
    }

    @Test
    fun rejectsMalformedRecordsInsteadOfSilentlyDiscardingThem() {
        val json = """
            {"courses":[
              {"id":"ok","name":"物理","weekday":3,"startPeriod":1,"endPeriod":2},
              {"id":"bad","name":"","weekday":9,"startPeriod":0,"endPeriod":2},
              {"id":"bad-json"}
            ],"exams":[{"id":"ok-exam","subject":"物理","date":"2026-10-01"},{"subject":"缺 id"}]}
        """

        assertFails { JsonScheduleCodec.decode(json) }
    }

    @Test
    fun rejectsUnknownDocumentsAndInvalidSectionTypes() {
        listOf("{}", "{\"courses\":null}", "{\"courses\":{}}", "[{\"unrelated\":true}]").forEach { json ->
            assertFails { JsonScheduleCodec.decode(json) }
        }
    }

    @Test
    fun acceptsLegacyWebCourseArrayExport() {
        val export = JsonScheduleCodec.decode(
            """[{"id":"c1","name":"数据库","day":2,"start":3,"end":4,"week":"all","location":"工科楼 A101"}]""",
        )

        assertEquals("WEB_IMPORT", export.source)
        assertEquals(listOf("c1"), export.courses.map { it.id })
        assertEquals("工科楼", export.courses.single().building)
        assertEquals("A101", export.courses.single().room)
    }
}
