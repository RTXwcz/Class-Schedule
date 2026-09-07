package com.kebiao.app.notifications

import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class PeriodScheduleTest {
    @Test fun defaultsKeepExistingTwelvePeriods() {
        assertEquals(12, PeriodSchedule.defaults.size)
        assertEquals(PeriodSchedule.defaults, PeriodSchedule.validate(PeriodSchedule.defaults))
        assertEquals(LocalTime.of(8, 0), PeriodSchedule.start(1))
        assertEquals(LocalTime.of(21, 30), PeriodSchedule.end(12))
    }

    @Test fun customCountAndChangedTimesRoundTripAndDriveStartEndLookup() {
        val custom = listOf(LessonPeriod("07:30", "08:10"), LessonPeriod("08:20", "09:00"), LessonPeriod("13:15", "14:00"))
        assertEquals(custom, PeriodSchedule.decode(PeriodSchedule.encode(custom)))
        assertEquals(LocalTime.of(7, 30), PeriodSchedule.start(1, custom))
        assertEquals(LocalTime.of(14, 0), PeriodSchedule.end(3, custom))
        assertFails { PeriodSchedule.start(4, custom) }
        assertFails { PeriodSchedule.end(0, custom) }
    }

    @Test fun invalidCountsTimesOverlapsAndCrossMidnightAreRejectedWithoutCorrection() {
        val invalid = listOf(
            emptyList(), List(49) { LessonPeriod("08:00", "08:30") },
            listOf(LessonPeriod("8:00", "08:30")), listOf(LessonPeriod("08:00 ", "08:30")),
            listOf(LessonPeriod("08:00:00", "08:30")), listOf(LessonPeriod("24:00", "24:30")),
            listOf(LessonPeriod("08:00", "08:00")), listOf(LessonPeriod("23:30", "00:30")),
            listOf(LessonPeriod("08:00", "09:00"), LessonPeriod("08:50", "09:30")),
            listOf(LessonPeriod("10:00", "11:00"), LessonPeriod("08:00", "09:00")),
        )
        invalid.forEach { assertFails { PeriodSchedule.validate(it) } }
        val contiguous = listOf(LessonPeriod("00:00", "00:30"), LessonPeriod("00:30", "01:00"))
        assertEquals(contiguous, PeriodSchedule.validate(contiguous))
    }

    @Test fun acceptsFortyEightOrderedPeriodsAndRejectsMalformedJson() {
        val periods = (0 until 48).map { index ->
            val start = LocalTime.MIDNIGHT.plusMinutes(index * 25L)
            LessonPeriod(start.toString(), start.plusMinutes(20).toString())
        }
        assertEquals(48, PeriodSchedule.decode(PeriodSchedule.encode(periods)).size)
        listOf("{}", "[]", "null", "[null]", "[{\"start\":800,\"end\":\"09:00\"}]",
            "[{\"start\":\"08:00\"}]", "[{\"start\":\"08:00\",\"end\":\"09:00\",\"extra\":1}]")
            .forEach { assertFails { PeriodSchedule.decode(it) } }
    }
}
