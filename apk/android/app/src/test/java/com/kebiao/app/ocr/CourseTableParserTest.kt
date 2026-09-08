package com.kebiao.app.ocr

import com.kebiao.app.domain.model.WeekRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    @Test
    fun groupsSevenColumnTimetableAndDoesNotCreateHeaderOrDetailCourses() {
        val blocks = headers() + listOf(
            block("第1-2节", 10f, 100f, 80f),
            block("第3-4节", 10f, 260f, 80f),
            block("第5-6节", 10f, 420f, 80f),
            block("高等数学", 110f, 90f),
            block("张老师", 110f, 120f),
            block("1-16周 单周", 110f, 150f),
            block("理科楼 C203", 110f, 180f),
            block("大学英语", 310f, 250f),
            block("李老师", 310f, 280f),
            block("工科楼 A101", 310f, 310f),
        )
        val drafts = parser.parse(blocks.shuffled(kotlin.random.Random(7)))
        assertEquals(2, drafts.size)
        val maths = drafts.single { it.name.value == "高等数学" }
        assertEquals(1, maths.weekday.value)
        assertEquals(1, maths.startPeriod.value)
        assertEquals(2, maths.endPeriod.value)
        assertEquals(WeekRule.ODD, maths.weekRule.value)
        assertEquals("理科楼", maths.building.value)
        assertEquals("C203", maths.room.value)
        assertEquals("张老师", maths.teacher.value)
        assertEquals("1-16", maths.weeks.value)
        assertTrue(maths.courseNote.value!!.contains("张老师"))
        assertTrue(maths.courseNote.value!!.contains("1-16周"))
        val english = drafts.single { it.name.value == "大学英语" }
        assertEquals(3, english.weekday.value)
        assertEquals(3, english.startPeriod.value)
        assertEquals(4, english.endPeriod.value)
        assertNull(english.weekRule.value)
    }

    @Test
    fun numberedRowsUseTentativeSpanAndSeparateLaterCourseInSameColumn() {
        val rows = (1..12).map { n -> block("第${n}节", 10f, 80f + n * 80, 80f) }
        val drafts = parser.parse(headers() + rows + listOf(
            block("数据库", 210f, 145f),
            block("教师：王老师", 210f, 190f),
            block("实验楼 B302", 210f, 230f),
            block("操作系统", 210f, 310f),
            block("教学楼 C202", 210f, 350f),
        ))
        assertEquals(2, drafts.size)
        val database = drafts.single { it.name.value == "数据库" }
        assertEquals(2, database.weekday.value)
        assertEquals(1, database.startPeriod.value)
        assertEquals(2, database.endPeriod.value)
        assertTrue(database.endPeriod.confidence < 0.8f)
        assertEquals("实验楼", database.building.value)
        val os = drafts.single { it.name.value == "操作系统" }
        assertEquals(3, os.startPeriod.value)
        assertNull(os.endPeriod.value)
    }

    @Test
    fun missingPeriodAxisKeepsUnknownTimingAndPreservesRawDetails() {
        val draft = parser.parse(headers() + listOf(
            block("线性代数", 110f, 180f),
            block("张老师", 110f, 210f),
            block("3-8周、10-16周 双周", 110f, 240f),
            block("教学楼A 301", 110f, 270f),
        )).single()
        assertEquals(1, draft.weekday.value)
        assertNull(draft.startPeriod.value)
        assertNull(draft.endPeriod.value)
        assertEquals(0f, draft.startPeriod.confidence)
        assertEquals(WeekRule.EVEN, draft.weekRule.value)
        assertEquals("3-8,10-16", draft.weeks.value)
        assertTrue(draft.courseNote.value!!.contains("10-16周"))
        assertEquals("301", draft.room.value)
    }

    @Test
    fun outOfAxisPositionDoesNotInventPeriodOrWeekday() {
        val draft = parser.parse(headers() + listOf(
            block("第1-2节", 10f, 120f, 80f),
            block("第3-4节", 10f, 280f, 80f),
            block("临时课程", 850f, 850f),
            block("教师：陈老师", 850f, 880f),
        )).single()
        assertNull(draft.weekday.value)
        assertNull(draft.startPeriod.value)
        assertNull(draft.endPeriod.value)
        assertEquals(0f, draft.weekday.confidence)
    }

    @Test
    fun explicitPeriodLineInCellOverridesGeometricEstimate() {
        val draft = parser.parse(headers() + listOf(
            block("第1-2节", 10f, 120f, 80f),
            block("第3-4节", 10f, 280f, 80f),
            block("物理实验", 410f, 100f),
            block("第5-6节", 410f, 140f),
            block("每周", 410f, 175f),
        )).single()
        assertEquals(4, draft.weekday.value)
        assertEquals(5, draft.startPeriod.value)
        assertEquals(6, draft.endPeriod.value)
        assertEquals(WeekRule.ALL, draft.weekRule.value)
    }

    @Test
    fun missingWeekRuleInVerticalTextIsNotSilentlyEveryWeek() {
        val draft = parser.parse("高等数学\n星期一 第1-2节\n理科楼 C203").single()
        assertNull(draft.weekRule.value)
        assertEquals(0f, draft.weekRule.confidence)
    }

    @Test
    fun separatesTeacherAndExplicitNonconsecutiveWeeksFromPreservedDetails() {
        val draft = parser.parse("离散数学\n星期二 第3-4节\n任课教师：张三\n周次：1,3,5-8\n理科楼 C203").single()
        assertEquals("张三", draft.teacher.value)
        assertEquals("1,3,5-8", draft.weeks.value)
        assertEquals(WeekRule.ALL, draft.weekRule.value)
        assertTrue(draft.courseNote.value!!.contains("周次：1,3,5-8"))
        assertNull(draft.locationNote.value)
    }

    private fun headers(): List<OcrTextBlock> = listOf("一", "二", "三", "四", "五", "六", "日")
        .mapIndexed { i, day -> block("星期$day", 110f + i * 100, 20f) }

    @Test fun unequalColumnsJoinWrappedNamesWithoutJoiningIndependentCourses() {
        val titles = listOf("一", "二", "三", "四", "五", "六", "日")
            .mapIndexed { i, day -> block("星期$day", listOf(140f, 310f, 480f, 600f, 695f, 755f, 805f)[i] - 20, 10f, 40f) }
        val drafts = parser.parse(titles + (1..6).map { block("$it", 70f, 30f + it * 24, 10f) } + listOf(
            block("软件设计基础及", 95f, 100f, 90f), block("实验", 125f, 119f, 30f),
            block("游泳（初级）", 270f, 52f), block("羽毛球（初级）", 268f, 72f, 84f),
            block("大学生文化素", 439f, 150f, 82f), block("养", 472f, 169f, 16f),
            block("认识海", 674f, 105f, 42f), block("洋", 687f, 124f, 16f),
        ))
        assertEquals(5, drafts.size)
        assertEquals(1, drafts.single { it.name.value == "软件设计基础及实验" }.weekday.value)
        assertEquals(3, drafts.single { it.name.value == "大学生文化素养" }.weekday.value)
        assertEquals(5, drafts.single { it.name.value == "认识海洋" }.weekday.value)
        assertEquals(2, drafts.count { it.weekday.value == 2 })
    }

    @Test fun closedCellsDetermineFullPeriodSpanAndDoNotMergeSeparateCells() {
        val rows = (1..6).map { block("$it", 10f, 60f + it * 30, 20f) }
        val firstCell = OcrSourceBox(100f, 85f, 200f, 175f)
        val secondCell = OcrSourceBox(100f, 175f, 200f, 205f)
        val drafts = parser.parse(headers() + rows + listOf(
            block("软件设计及", 110f, 115f).copy(cellBox = firstCell),
            block("实验", 136f, 137f, 28f).copy(cellBox = firstCell),
            block("实践", 136f, 180f, 28f).copy(cellBox = secondCell),
        ))
        assertEquals(2, drafts.size)
        assertEquals("软件设计及实验", drafts[0].name.value)
        assertEquals(1, drafts[0].startPeriod.value)
        assertEquals(3, drafts[0].endPeriod.value)
        assertEquals("实践", drafts[1].name.value)
        assertEquals(4, drafts[1].startPeriod.value)
        assertEquals(4, drafts[1].endPeriod.value)
    }

    @Test fun missingWeekdayHeaderDoesNotAssignItsCoursesToAdjacentDay() {
        val draft = parser.parse(headers().filterNot { it.text == "星期二" } + listOf(block("未知星期课程", 210f, 100f))).single()
        assertNull(draft.weekday.value)
    }

    @Test fun shortIndependentCourseInSameCellIsNotTreatedAsWrappedSuffix() {
        val cell = OcrSourceBox(100f, 80f, 200f, 180f)
        val drafts = parser.parse(headers() + listOf(block("高等数学", 110f, 100f).copy(cellBox = cell),
            block("英语", 130f, 120f, 40f).copy(cellBox = cell)))
        assertEquals(listOf("高等数学", "英语"), drafts.map { it.name.value })
    }

    @Test fun nestedTitleParenthesesArePreserved() {
        assertEquals("《论语（选读）》", parser.parse("《论语（选读）》").single().name.value)
        assertEquals("《文献》", parser.parse("《文献>").single().name.value)
    }

    private fun block(text: String, left: Float, top: Float, width: Float = 80f) =
        OcrTextBlock(text, 0.96f, OcrSourceBox(left, top, left + width, top + 20f))
}
