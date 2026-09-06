package com.kebiao.app.notifications

import java.time.LocalTime

/** The 12 standard lesson periods shared with the Web schedule. */
object PeriodSchedule {
    private val starts = listOf(
        LocalTime.of(8, 0), LocalTime.of(9, 0), LocalTime.of(10, 10), LocalTime.of(11, 10),
        LocalTime.of(13, 0), LocalTime.of(14, 0), LocalTime.of(15, 10), LocalTime.of(16, 10),
        LocalTime.of(17, 10), LocalTime.of(18, 40), LocalTime.of(19, 40), LocalTime.of(20, 40),
    )
    private val ends = listOf(
        LocalTime.of(8, 50), LocalTime.of(9, 50), LocalTime.of(11, 0), LocalTime.of(12, 0),
        LocalTime.of(13, 50), LocalTime.of(14, 50), LocalTime.of(16, 0), LocalTime.of(17, 0),
        LocalTime.of(18, 0), LocalTime.of(19, 30), LocalTime.of(20, 30), LocalTime.of(21, 30),
    )

    fun start(period: Int): LocalTime = starts.getOrElse(period - 1) {
        error("Unknown lesson period: $period")
    }

    fun end(period: Int): LocalTime = ends.getOrElse(period - 1) {
        error("Unknown lesson period: $period")
    }
}
