package com.kebiao.app.widget

import android.content.Context
import android.content.ComponentName
import android.appwidget.AppWidgetManager
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.MutablePreferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.kebiao.app.domain.model.EffectiveCourse
import com.kebiao.app.notifications.PeriodSchedule
import com.kebiao.app.notifications.LessonPeriod
import com.kebiao.app.notifications.ReminderCoordinator
import com.kebiao.app.notifications.ReminderPlanner
import com.kebiao.app.notifications.ReminderSnapshot
import java.time.ZonedDateTime

object WidgetSnapshotProvider {
    fun upcoming(now: ZonedDateTime, snapshot: ReminderSnapshot) = ReminderPlanner.upcoming(
        now, snapshot.semesterStart, snapshot.courses, snapshot.overrides,
        parityEnabled = snapshot.settings.parityEnabled,
        periods = snapshot.settings.periods,
    )

    suspend fun load(context: Context): List<EffectiveCourse> =
        upcoming(ZonedDateTime.now(), ReminderCoordinator.snapshot(context)).map { it.course }

    fun format(courses: List<EffectiveCourse>, periods: List<LessonPeriod> = PeriodSchedule.defaults): String = courses.take(3).joinToString("\n") { effective ->
        val course = effective.course
        val location = listOfNotNull(course.building, course.room, course.locationNote).filter(String::isNotBlank).joinToString(" ")
        listOf("${effective.date.monthValue}/${effective.date.dayOfMonth} ${PeriodSchedule.start(course.startPeriod, periods)}",
            course.name, location).filter(String::isNotBlank).joinToString(" · ")
    }

    internal fun write(prefs: MutablePreferences, courses: List<EffectiveCourse>, periods: List<LessonPeriod> = PeriodSchedule.defaults, colorPalette: String = "green") {
        prefs[WidgetKeys.colorPalette] = if (colorPalette == "purple") "purple" else "green"
        val upcoming = courses.take(3)
        prefs[WidgetKeys.count] = upcoming.size
        upcoming.forEachIndexed { index, effective ->
            val course = effective.course
            prefs[WidgetKeys.time(index)] = "${effective.date.monthValue}/${effective.date.dayOfMonth} ${PeriodSchedule.start(course.startPeriod, periods)}"
            prefs[WidgetKeys.clock(index)] = PeriodSchedule.start(course.startPeriod, periods).toString()
            prefs[WidgetKeys.date(index)] = "${effective.date.monthValue}/${effective.date.dayOfMonth}"
            prefs[WidgetKeys.weekday(index)] = "周${"一二三四五六日"[effective.date.dayOfWeek.value - 1]}"
            prefs[WidgetKeys.name(index)] = course.name
            prefs[WidgetKeys.location(index)] = listOfNotNull(course.building, course.room, course.locationNote)
                .filter(String::isNotBlank).joinToString(" ").ifBlank { "地点未填写" }
        }
        for (index in upcoming.size until 3) {
            prefs.remove(WidgetKeys.time(index))
            prefs.remove(WidgetKeys.name(index))
            prefs.remove(WidgetKeys.location(index))
            prefs.remove(WidgetKeys.clock(index))
            prefs.remove(WidgetKeys.date(index))
            prefs.remove(WidgetKeys.weekday(index))
        }
        prefs.remove(stringPreferencesKey("schedule_lines"))
    }

    suspend fun update(context: Context, courses: List<EffectiveCourse>, periods: List<LessonPeriod> = PeriodSchedule.defaults, colorPalette: String = "green") {
        val manager = GlanceAppWidgetManager(context)
        val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, ScheduleWidgetReceiver::class.java))
        ids.forEach { appWidgetId ->
            val id = manager.getGlanceIdBy(appWidgetId)
            updateAppWidgetState(context, id) { write(it, courses, periods, colorPalette) }
            ScheduleWidget.update(context, id)
        }
    }
}

internal object WidgetKeys {
    val colorPalette = stringPreferencesKey("color_palette")
    val count = intPreferencesKey("course_count")
    fun time(index: Int) = stringPreferencesKey("course_${index}_time")
    fun name(index: Int) = stringPreferencesKey("course_${index}_name")
    fun location(index: Int) = stringPreferencesKey("course_${index}_location")
    fun clock(index: Int) = stringPreferencesKey("course_${index}_clock")
    fun date(index: Int) = stringPreferencesKey("course_${index}_date")
    fun weekday(index: Int) = stringPreferencesKey("course_${index}_weekday")
}
