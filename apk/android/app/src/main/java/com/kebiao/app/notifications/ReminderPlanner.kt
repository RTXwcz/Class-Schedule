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

    fun currentReminder(
        key: String,
        expectedTriggerMillis: Long,
        now: ZonedDateTime,
        semesterStart: LocalDate?,
        courses: List<Course>,
        overrides: List<ScheduleOverride>,
        leadMinutes: Long = 10,
        parityEnabled: Boolean = true,
        periods: List<LessonPeriod> = PeriodSchedule.defaults,
    ): PlannedReminder? {
        // A class just after midnight can have its reminder on the previous date.
        val courseDate = key.substringAfterLast('@', "").let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        if (courseDate.isBefore(now.toLocalDate()) || courseDate.isAfter(now.plusMinutes(leadMinutes).toLocalDate())) return null
        return resolver.resolve(courseDate, semesterStart, courses, overrides, parityEnabled)
        .asSequence().filter { it.course.endPeriod <= periods.size }.map { effective ->
            val startsAt = effective.date.atTime(PeriodSchedule.start(effective.course.startPeriod, periods)).atZone(now.zone)
            PlannedReminder("${effective.course.id}@${effective.date}", effective, startsAt, startsAt.minusMinutes(leadMinutes))
        }.firstOrNull {
            it.key == key && it.triggerAt.toInstant().toEpochMilli() == expectedTriggerMillis &&
                !now.isBefore(it.triggerAt) && now.isBefore(if (leadMinutes == 0L) it.startsAt.plusMinutes(1) else it.startsAt)
        }
    }

    fun upcoming(
        now: ZonedDateTime,
        semesterStart: LocalDate?,
        courses: List<Course>,
        overrides: List<ScheduleOverride>,
        count: Int = 3,
        parityEnabled: Boolean = true,
        periods: List<LessonPeriod> = PeriodSchedule.defaults,
    ): List<PlannedReminder> {
        require(count >= 0)
        if (courses.isEmpty() || count == 0) return emptyList()
        // Beyond the final exception, three fortnightly occurrences take at most six weeks.
        val lastCourseWeek = semesterStart?.plusWeeks(courses.flatMap { it.weeks }.maxOrNull()?.toLong() ?: 0L)
        val lastException = listOfNotNull(now.toLocalDate(), semesterStart, lastCourseWeek, overrides.maxOfOrNull { it.date }).max()
        val end = lastException.plusWeeks(count.toLong() * 2)
        val start = if (courses.none { (!parityEnabled || it.weekRule == com.kebiao.app.domain.model.WeekRule.ALL) && it.weeks.isEmpty() }) {
            semesterStart?.coerceAtLeast(now.toLocalDate()) ?: return emptyList()
        } else now.toLocalDate()
        return generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }
            .flatMap { date ->
                resolver.resolve(date, semesterStart, courses, overrides, parityEnabled).asSequence().filter { it.course.endPeriod <= periods.size }.map { effective ->
                    val startsAt = date.atTime(PeriodSchedule.start(effective.course.startPeriod, periods)).atZone(now.zone)
                    PlannedReminder("${effective.course.id}@$date", effective, startsAt, startsAt)
                }
            }.filter { it.startsAt.isAfter(now) }.distinctBy { it.key }.take(count).toList()
    }

    fun plan(
        now: ZonedDateTime,
        semesterStart: LocalDate?,
        courses: List<Course>,
        overrides: List<ScheduleOverride>,
        leadMinutes: Long = 10,
        horizonDays: Int = 14,
        includeDue: Boolean = false,
        deliveredKeys: Set<String> = emptySet(),
        parityEnabled: Boolean = true,
        periods: List<LessonPeriod> = PeriodSchedule.defaults,
    ): List<PlannedReminder> {
        require(leadMinutes >= 0) { "Reminder lead time must not be negative" }
        require(horizonDays >= 0) { "Reminder horizon must not be negative" }
        val seen = HashSet<String>()
        return (0..horizonDays).asSequence()
            .map { now.toLocalDate().plusDays(it.toLong()) }
            .flatMap { date ->
                resolver.resolve(date, semesterStart, courses, overrides, parityEnabled).asSequence().filter { it.course.endPeriod <= periods.size }.mapNotNull { effective ->
                    val startsAt = date.atTime(PeriodSchedule.start(effective.course.startPeriod, periods)).atZone(now.zone)
                    val triggerAt = startsAt.minusMinutes(leadMinutes)
                    val key = "${effective.course.id}@$date"
                    val eligible = triggerAt.isAfter(now) || (includeDue && startsAt.isAfter(now))
                    if (eligible && key !in deliveredKeys && seen.add(key)) {
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
