package com.kebiao.app.notifications

import android.content.Context
import com.kebiao.app.data.ScheduleRepository
import com.kebiao.app.data.ScheduleRules
import com.kebiao.app.data.local.AppDatabase
import com.kebiao.app.data.settings.AppSettings
import com.kebiao.app.data.settings.AppSettingsStore
import com.kebiao.app.domain.model.Course
import com.kebiao.app.domain.model.ScheduleOverride
import com.kebiao.app.domain.model.WeekRule
import com.kebiao.app.widget.WidgetSnapshotProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZonedDateTime

data class ReminderSnapshot(
    val courses: List<Course>,
    val overrides: List<ScheduleOverride>,
    val settings: AppSettings,
) {
    val semesterStart: LocalDate? get() = settings.semesterStartDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}

class ReminderCoordinator(
    context: Context,
    private val repository: ScheduleRepository,
    private val settingsStore: AppSettingsStore,
) {
    private val appContext = context.applicationContext

    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(repository.observeSnapshot(), settingsStore.settings) { _, _ -> Unit }
                .collect { refresh(appContext) }
        }
    }

    companion object {
        private val refreshMutex = Mutex()

        suspend fun snapshot(context: Context): ReminderSnapshot {
            val preferences = AppSettingsStore(context.applicationContext).settings.first()
            val export = ScheduleRepository(AppDatabase.getInstance(context)).snapshotWithSettings(preferences)
            return ReminderSnapshot(
                courses = export.courses.mapNotNull { course -> runCatching {
                    Course(course.id, course.name, course.weekday, course.startPeriod, course.endPeriod,
                        WeekRule.valueOf(course.weekRule), course.building, course.room, course.locationNote,
                        course.source, course.createdAtEpochMillis, course.updatedAtEpochMillis,
                        course.teacher, course.weeks, course.courseNote)
                }.getOrNull() },
                overrides = export.overrides.mapNotNull { record -> runCatching {
                    ScheduleOverride(LocalDate.parse(record.date), record.replacementWeekday, record.note)
                }.getOrNull() },
                settings = ScheduleRules.read(export).apply(preferences),
            )
        }

        suspend fun refresh(context: Context) = refreshMutex.withLock {
            val scheduler = ReminderScheduler(context)
            scheduler.ensureDailyRefresh()
            val snapshot = snapshot(context)
            val now = ZonedDateTime.now()
            if (snapshot.settings.notificationsEnabled) {
                scheduler.schedule(now, snapshot.semesterStart, snapshot.courses, snapshot.overrides,
                    snapshot.settings.reminderLeadMinutes.toLong(), parityEnabled = snapshot.settings.parityEnabled, periods = snapshot.settings.periods)
            } else {
                scheduler.cancelAll()
            }
            val upcoming = WidgetSnapshotProvider.upcoming(now, snapshot)
            // Let a zero-minute reminder finish before rebuilding alarms at this course's start.
            scheduler.scheduleWidgetRefresh(upcoming.firstOrNull()?.startsAt?.plusMinutes(1))
            WidgetSnapshotProvider.update(context, upcoming.map { it.course }, snapshot.settings.periods, snapshot.settings.colorPalette)
        }
    }
}
