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
        // Unknown span: the first period is certain, the end stays low confidence.
        assertEquals(3, os.endPeriod.value)
        assertTrue(os.endPeriod.confidence < 0.8f)
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


    @Test fun desktopExportWithParityColumnsAndRepeatedPeriodsParsesCleanly() {
        // Mirrors a 教务导出截图: a lost "一" in 星期一, 单/双 sub-column headings, clock labels in the
        // time column, and one session printed once per period with teacher, place and a date stamp.
        val headers = listOf(
            "星期" to 300f, "星期二" to 640f, "星期三" to 960f, "星期四" to 1280f,
            "星期五" to 1600f, "星期六" to 1920f, "星期日" to 2240f,
        ).map { (text, left) -> OcrTextBlock(text, 0.99f, OcrSourceBox(left, 0f, left + 80f, 28f)) }
        val axis = (1..13).map { period ->
            OcrTextBlock("$period", 1f, OcrSourceBox(60f, 180f + period * 100f, 90f, 200f + period * 100f))
        }
        val clock = listOf("08:00", "08:45", "09:00").mapIndexed { index, text ->
            OcrTextBlock(text, 1f, OcrSourceBox(110f, 160f + index * 100f, 190f, 180f + index * 100f))
        }
        val parity = listOf(
            OcrTextBlock("单", 1f, OcrSourceBox(520f, 55f, 550f, 85f)),
            OcrTextBlock("双", 1f, OcrSourceBox(740f, 55f, 770f, 85f)),
        )
        val first = OcrSourceBox(513f, 665f, 835f, 855f)
        val second = OcrSourceBox(513f, 855f, 835f, 1059f)
        fun cell(text: String, left: Float, top: Float, width: Float, box: OcrSourceBox) =
            OcrTextBlock(text, 0.96f, OcrSourceBox(left, top, left + width, top + 22f), box)
        val lesson = listOf(
            cell("计算机操作系统原理", 520f, 683f, 280f, first),
            cell("与实践", 610f, 712f, 70f, first),
            cell("秋冬(第1-8周(2节/周))", 520f, 737f, 200f, first),
            cell("张三", 560f, 764f, 60f, first),
            cell("教学楼 2-101", 560f, 790f, 130f, first),
            cell("2027年01月10日(14:00-16:00)", 520f, 818f, 250f, first),
            cell("计算机操作系统原理", 520f, 882f, 280f, second),
            cell("与实践", 610f, 908f, 70f, second),
            cell("秋冬(第1-8周(2节/周))", 520f, 933f, 200f, second),
            cell("张三", 560f, 961f, 60f, second),
            cell("教学楼 2-101", 560f, 987f, 130f, second),
            cell("2027年01月10日(14:00-16:00)", 520f, 1015f, 250f, second),
        )
        val drafts = CourseTableParser().parse((headers + axis + clock + parity + lesson).shuffled(kotlin.random.Random(11)))
        val draft = drafts.single()
        assertEquals("计算机操作系统原理与实践", draft.name.value)
        assertEquals(2, draft.weekday.value)
        // The two repeated copies of the session span these rows in the fixture geometry.
        assertEquals(5, draft.startPeriod.value)
        assertEquals(7, draft.endPeriod.value)
        assertEquals(WeekRule.EVEN, draft.weekRule.value)
        assertEquals("张三", draft.teacher.value)
        assertEquals("教学楼", draft.building.value)
        assertEquals("2-101", draft.room.value)
        assertEquals("1-8", draft.weeks.value)
    }

    @Test fun keepsParenthesesInPlaceNames() {
        val field = parser.parse("身体素质课\n星期五 第3-4节\n紫金港田径场（东）").single()
        assertEquals("紫金港田径场（东）", field.building.value)
        assertNull(field.room.value)

        val split = parser.parse("体育\n星期五 第1-2节\n体育馆（东）A301").single()
        assertEquals("体育馆（东）", split.building.value)
        assertEquals("A301", split.room.value)
    }

    @Test fun readsTheDailyPeriodScheduleFromTheTimeAxis() {
        val headers = listOf("星期一" to 300f, "星期二" to 640f, "星期三" to 960f)
            .map { (text, left) -> OcrTextBlock(text, 0.99f, OcrSourceBox(left, 0f, left + 80f, 28f)) }
        fun clock(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)
        val blocks = mutableListOf<OcrTextBlock>()
        var minutes = 8 * 60
        repeat(13) { index ->
            val top = 180f + index * 100f
            blocks += OcrTextBlock("${index + 1}", 1f, OcrSourceBox(60f, top, 90f, top + 20f))
            blocks += OcrTextBlock(clock(minutes), 1f, OcrSourceBox(110f, top, 190f, top + 20f))
            blocks += OcrTextBlock(clock(minutes + 45), 1f, OcrSourceBox(110f, top + 30f, 190f, top + 50f))
            minutes += 50
        }
        val periods = CourseTableParser().parsePeriods((headers + blocks).shuffled(kotlin.random.Random(5)))
        assertEquals(13, periods.size)
        assertEquals("08:00", periods.first().start)
        assertEquals("08:45", periods.first().end)
        assertEquals("18:00", periods.last().start)
        assertEquals("18:45", periods.last().end)

        // A broken axis must not renumber anything.
        assertTrue(CourseTableParser().parsePeriods(headers + blocks.filterNot { it.text == "09:35" }).isEmpty())
    }

    @Test fun wrappedTitleKeepsTeacherAndPlaceApart() {
        // Mirrors a real screenshot: the title wraps, the teacher's surname is 楼 (which also means
        // "building"), and the place carries a parenthetical note.
        val cell = OcrSourceBox(513f, 665f, 835f, 855f)
        fun line(text: String, top: Float) = OcrTextBlock(text, 0.96f, OcrSourceBox(520f, top, 820f, top + 22f), cell)
        val blocks = listOf(
            line("习近平新时代中国特色社会主", 683f),
            line("义思想概论", 712f),
            line("秋冬第1-8周1节/周", 737f),
            line("楼俊超/李梦宇", 764f),
            line("紫金港东2-101", 790f),
            line("2027年01月10日(14:00-16:00)", 818f),
        )
        val axes = (1..13).map { period ->
            OcrTextBlock("$period", 1f, OcrSourceBox(60f, 180f + period * 100f, 90f, 200f + period * 100f))
        }
        val headers = listOf("星期一" to 300f, "星期二" to 640f, "星期三" to 960f)
            .map { (text, left) -> OcrTextBlock(text, 0.99f, OcrSourceBox(left, 0f, left + 80f, 28f)) }
        val draft = parser.parse(headers + axes + blocks).single()
        assertEquals("习近平新时代中国特色社会主义思想概论", draft.name.value)
        assertEquals("楼俊超/李梦宇", draft.teacher.value)
        assertEquals("紫金港东", draft.building.value)
        assertEquals("2-101", draft.room.value)
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










