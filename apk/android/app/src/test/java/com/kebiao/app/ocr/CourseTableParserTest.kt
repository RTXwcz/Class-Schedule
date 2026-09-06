package com.kebiao.app.ocr

import com.kebiao.app.domain.model.WeekRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CourseTableParserTest {
    private val parser = CourseTableParser()

    @Test
    fun parsesChineseWeekdayPeriodWeekRuleAndLocation() {
        val drafts = parser.parse(
            listOf(
                OcrTextBlock(
                    text = "高等数学\n星期一 第1-2节 单周\n理科楼 C203",
                    confidence = 0.96f,
                    box = OcrSourceBox(10f, 20f, 200f, 100f),
                ),
            ),
        )

        val draft = drafts.single()
        assertEquals("高等数学", draft.name.value)
        assertEquals(1, draft.weekday.value)
        assertEquals(1, draft.startPeriod.value)
        assertEquals(2, draft.endPeriod.value)
        assertEquals(WeekRule.ODD, draft.weekRule.value)
        assertEquals("理科楼", draft.building.value)
        assertEquals("C203", draft.room.value)
        assertEquals(0.96f, draft.name.confidence)
        assertNotNull(draft.name.sourceBox)
    }

    @Test
    fun parsesNumericWeekdayAndDoubleWeekLabels() {
        val draft = parser.parse("数据库\n周3 5至6节 双周\n工科楼 A101").single()

        assertEquals(3, draft.weekday.value)
        assertEquals(5, draft.startPeriod.value)
        assertEquals(6, draft.endPeriod.value)
        assertEquals(WeekRule.EVEN, draft.weekRule.value)
        assertEquals("工科楼", draft.building.value)
        assertEquals("A101", draft.room.value)
    }

    @Test
    fun keepsLowConfidenceFieldsForReviewAndReportsValidation() {
        val draft = parser.parse(
            listOf(OcrTextBlock("英语\n星期八 第0节", 0.32f, OcrSourceBox(0f, 0f, 10f, 10f))),
        ).single()

        assertTrue(draft.hasLowConfidenceFields())
        assertTrue(draft.validationErrors().isNotEmpty())
        assertTrue(draft.validationErrors().any { it.field == "weekday" })
    }
}
