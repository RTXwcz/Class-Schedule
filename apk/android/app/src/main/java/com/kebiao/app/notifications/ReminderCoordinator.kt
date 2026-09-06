package com.kebiao.app.notifications

import android.content.Context
import com.kebiao.app.data.ScheduleRepository
import com.kebiao.app.data.settings.AppSettingsStore
import com.kebiao.app.domain.model.ScheduleOverride
import com.kebiao.app.widget.WidgetSnapshotProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZonedDateTime

class ReminderCoordinator(
    context: Context,
    private val repository: ScheduleRepository,
    private val settingsStore: AppSettingsStore,
) {
    private val appContext = context.applicationContext
    private val scheduler = ReminderScheduler(appContext)

    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(repository.observeCourses(), repository.observeOverrides(), settingsStore.settings) { courses, records, settings ->
                Triple(courses, records.mapNotNull { record ->
                    runCatching { ScheduleOverride(LocalDate.parse(record.date), record.replacementWeekday, record.note) }.getOrNull()
                }, settings)
            }.collect { (courses, overrides, settings) ->
                if (!settings.notificationsEnabled) {
                    scheduler.cancelAll()
                    return@collect
                }
                scheduler.schedule(
                    now = ZonedDateTime.now(),
                    semesterStart = settings.semesterStartDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                    courses = courses,
                    overrides = overrides,
                    leadMinutes = settings.reminderLeadMinutes.toLong(),
                )
                val future = ReminderPlanner.plan(
                    now = ZonedDateTime.now(),
                    semesterStart = settings.semesterStartDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                    courses = courses,
                    overrides = overrides,
                    leadMinutes = 0,
                ).map { it.course }
                WidgetSnapshotProvider.update(appContext, future)
            }
        }
    }
}
