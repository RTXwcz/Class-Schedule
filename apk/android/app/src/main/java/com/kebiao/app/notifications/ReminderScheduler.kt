package com.kebiao.app.notifications

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.kebiao.app.domain.model.Course
import com.kebiao.app.domain.model.ScheduleOverride
import java.time.LocalDate
import java.time.ZonedDateTime

enum class ReminderStatus(val label: String) {
    DISABLED("课程提醒已关闭"),
    NOTIFICATIONS_BLOCKED("系统通知权限或课程提醒通知渠道已关闭"),
    EXACT("精确提醒已开启"),
    INEXACT("未获精确闹钟权限，将使用普通提醒，可能延迟"),
}

class ReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun status(notificationsEnabled: Boolean = true): ReminderStatus = when {
        !notificationsEnabled -> ReminderStatus.DISABLED
        !notificationsAllowed() -> ReminderStatus.NOTIFICATIONS_BLOCKED
        canScheduleExact() -> ReminderStatus.EXACT
        else -> ReminderStatus.INEXACT
    }

    fun notificationsAllowed(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.getSystemService(NotificationManager::class.java).getNotificationChannel(ReminderReceiver.CHANNEL_ID)
            ?.importance != NotificationManager.IMPORTANCE_NONE)

    fun schedule(
        now: ZonedDateTime,
        semesterStart: LocalDate?,
        courses: List<Course>,
        overrides: List<ScheduleOverride>,
        leadMinutes: Long = 10,
        parityEnabled: Boolean = true,
        periods: List<LessonPeriod> = PeriodSchedule.defaults,
    ) {
        cancelAll()
        if (!notificationsAllowed()) return
        val plans = ReminderPlanner.plan(now, semesterStart, courses, overrides, leadMinutes,
            includeDue = true, deliveredKeys = ReminderDeliveryStore.deliveredKeys(context, now.toLocalDate()) +
                ReminderDeliveryStore.deliveredKeys(context, now.toLocalDate().plusDays(1)), parityEnabled = parityEnabled, periods = periods)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY_KEYS, plans.map { it.key }.toSet()).apply()
        plans.forEach { plan ->
            val pending = PendingIntent.getBroadcast(context, requestCode(plan.key), ReminderReceiver.intent(context, plan),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val trigger = plan.triggerAt.toInstant().toEpochMilli()
                .coerceAtLeast(now.toInstant().toEpochMilli() + 1_000L)
            setAlarm(trigger, pending)
        }
    }

    fun cancelAll() {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getStringSet(KEY_KEYS, emptySet()).orEmpty().forEach { key ->
            // Cancel both current URI identities and alarms created by earlier app versions.
            listOf(ReminderReceiver.baseIntent(context, key), Intent(context, ReminderReceiver::class.java)).forEach { intent ->
                PendingIntent.getBroadcast(context, requestCode(key), intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
                    alarmManager.cancel(it)
                    it.cancel()
                }
            }
        }
        prefs.edit().clear().apply()
    }

    fun ensureDailyRefresh(now: ZonedDateTime = ZonedDateTime.now()) {
        val next = now.toLocalDate().plusDays(1).atStartOfDay(now.zone).plusMinutes(5)
        alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(),
            AlarmManager.INTERVAL_DAY, refreshIntent("daily"))
    }

    fun scheduleWidgetRefresh(nextStart: ZonedDateTime?) {
        val pending = refreshIntent("widget")
        alarmManager.cancel(pending)
        nextStart?.let { setAlarm(it.toInstant().toEpochMilli(), pending) }
    }

    private fun refreshIntent(kind: String): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, BootReceiver::class.java).setAction(ACTION_REFRESH).setData(Uri.parse("kebiao://refresh/$kind")),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun canScheduleExact() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun setAlarm(trigger: Long, pending: PendingIntent) {
        if (canScheduleExact()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
                return
            } catch (_: SecurityException) {
                // The exact-alarm grant can change between checking and scheduling.
            }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
    }

    companion object {
        const val ACTION_REFRESH = "com.kebiao.app.REFRESH_SCHEDULE"
        private const val PREFS = "reminder_schedule"
        private const val KEY_KEYS = "keys"
        fun requestCode(key: String): Int = key.hashCode() and 0x7fffffff
    }
}
