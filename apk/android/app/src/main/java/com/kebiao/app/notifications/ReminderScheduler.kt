package com.kebiao.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.kebiao.app.domain.model.Course
import com.kebiao.app.domain.model.ScheduleOverride
import java.time.LocalDate
import java.time.ZonedDateTime

class ReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(
        now: ZonedDateTime,
        semesterStart: LocalDate?,
        courses: List<Course>,
        overrides: List<ScheduleOverride>,
        leadMinutes: Long,
    ) {
        cancelAll()
        val plans = ReminderPlanner.plan(now, semesterStart, courses, overrides, leadMinutes)
        val saved = plans.map { it.key }.toMutableSet()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY_KEYS, saved)
            .apply()
        plans.forEach { plan ->
            val location = listOfNotNull(plan.course.course.building, plan.course.course.room).joinToString(" ")
            val intent = ReminderReceiver.intent(context, plan)
            prefsFor(plan.key, plan.course.course.name, plan.startsAt.toLocalTime().toString().take(5), location, plan.triggerAt.toInstant().toEpochMilli())
            val pending = PendingIntent.getBroadcast(
                context,
                requestCode(plan.key),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val trigger = plan.triggerAt.toInstant().toEpochMilli()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            }
        }
    }

    fun cancelAll() {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getStringSet(KEY_KEYS, emptySet()).orEmpty().forEach { key ->
            val pending = PendingIntent.getBroadcast(
                context,
                requestCode(key),
                Intent(context, ReminderReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            pending?.let(alarmManager::cancel)
            prefs.edit().remove(field(key, "name")).remove(field(key, "start")).remove(field(key, "location")).remove(field(key, "trigger")).apply()
        }
        prefs.edit().remove(KEY_KEYS).apply()
    }

    fun restore() {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val valid = mutableSetOf<String>()
        prefs.getStringSet(KEY_KEYS, emptySet()).orEmpty().forEach { key ->
            val trigger = prefs.getLong(field(key, "trigger"), 0L)
            if (trigger <= now) return@forEach
            val intent = ReminderReceiver.intent(
                context,
                key,
                prefs.getString(field(key, "name"), "课程") ?: "课程",
                prefs.getString(field(key, "start"), "") ?: "",
                prefs.getString(field(key, "location"), "") ?: "",
            )
            val pending = PendingIntent.getBroadcast(context, requestCode(key), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            }
            valid += key
        }
        prefs.edit().putStringSet(KEY_KEYS, valid).apply()
    }

    private fun prefsFor(key: String, name: String, start: String, location: String, trigger: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(field(key, "name"), name)
            .putString(field(key, "start"), start)
            .putString(field(key, "location"), location)
            .putLong(field(key, "trigger"), trigger)
            .apply()
    }

    private fun field(key: String, name: String) = "${key}_$name"

    companion object {
        private const val PREFS = "reminder_schedule"
        private const val KEY_KEYS = "keys"
        fun requestCode(key: String): Int = key.hashCode() and 0x7fffffff
    }
}
