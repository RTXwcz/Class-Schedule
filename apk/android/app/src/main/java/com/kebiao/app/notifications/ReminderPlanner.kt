package com.kebiao.app.notifications

import com.kebiao.app.domain.ScheduleResolver
import com.kebiao.app.domain.model.EffectiveCourse
import com.kebiao.app.domain.model.Course
import com.kebiao.app.domain.model.ScheduleOverride
import java.time.LocalDate
import java.time.ZonedDateTime

data class PlannedReminder(
    val key: String,
    val course: EffectiveCourse,
    val startsAt: ZonedDateTime,
    val triggerAt: ZonedDateTime,
)

/** Pure date/time planning used by both Android alarms and unit tests. */
object ReminderPlanner {
    private val resolver = ScheduleResolver()

    fun plan(
        now: ZonedDateTime,
        semesterStart: LocalDate?,
        courses: List<Course>,
        overrides: List<ScheduleOverride>,
        leadMinutes: Long = 10,
        horizonDays: Int = 14,
    ): List<PlannedReminder> {
        require(leadMinutes >= 0) { "Reminder lead time must not be negative" }
        require(horizonDays >= 0) { "Reminder horizon must not be negative" }
        val seen = HashSet<String>()
        return (0..horizonDays).asSequence()
            .map { now.toLocalDate().plusDays(it.toLong()) }
            .flatMap { date ->
                resolver.resolve(date, semesterStart, courses, overrides).asSequence().mapNotNull { effective ->
                    val startsAt = date.atTime(PeriodSchedule.start(effective.course.startPeriod)).atZone(now.zone)
                    val triggerAt = startsAt.minusMinutes(leadMinutes)
                    val key = "${effective.course.id}@$date"
                    if (triggerAt.isAfter(now) && seen.add(key)) {
                        PlannedReminder(key, effective, startsAt, triggerAt)
                    } else {
                        null
                    }
                }
            }
            .sortedBy(PlannedReminder::triggerAt)
            .toList()
    }
}
