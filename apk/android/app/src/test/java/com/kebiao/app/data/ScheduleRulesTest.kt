package com.kebiao.app.data

import com.kebiao.app.data.settings.AppSettings
import com.kebiao.app.notifications.LessonPeriod
import kotlin.test.*

class ScheduleRulesTest {
    @Test fun completeDatasetCarriesItsInterpretationAndPreservesExtensions() {
        val rules = ScheduleRules("2026-09-07", true, listOf(LessonPeriod("09:15", "10:00")))
        val data = rules.apply(ScheduleExport(courses = listOf(ScheduleCourse("one", "Math", 1, 1, 1))))
        val decoded = JsonScheduleCodec.decode(JsonScheduleCodec.encode(data))
        assertEquals(rules, ScheduleRules.read(decoded))
        assertEquals("09:15", ScheduleRules.read(decoded).apply(AppSettings()).periods.single().start)
    }
    @Test fun incorrectRuleTypesAreRejected() {
        assertFails { ScheduleRules.read(JsonScheduleCodec.decode("""{"courses":[],"parityEnabled":"false"}""")) }
        assertFails { ScheduleRules.read(JsonScheduleCodec.decode("""{"courses":[],"semesterStartDate":2026}""")) }
    }
}
