package com.kebiao.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.kebiao.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val key = intent.getStringExtra(EXTRA_KEY)
                if (key != null) {
                    val snapshot = ReminderCoordinator.snapshot(context)
                    val now = ZonedDateTime.now()
                    val plan = ReminderPlanner.currentReminder(key, intent.getLongExtra(EXTRA_TRIGGER, -1L), now,
                        snapshot.semesterStart, snapshot.courses, snapshot.overrides,
                        snapshot.settings.reminderLeadMinutes.toLong())
                    if (snapshot.settings.notificationsEnabled && plan != null && ReminderScheduler(context).notificationsAllowed()) {
                        notifyOnce(context, plan)
                    }
                }
                ReminderCoordinator.refresh(context.applicationContext)
            } catch (error: Exception) {
                Log.e("CourseReminder", "Could not deliver or refresh reminder", error)
            } finally {
                pending.finish()
            }
        }
    }

    private fun notifyOnce(context: Context, plan: PlannedReminder) = synchronized(deliveryLock) {
        val delivered = ReminderDeliveryStore.deliveredKeys(context, plan.course.date)
        if (plan.key in delivered) return@synchronized
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "课程提醒", NotificationManager.IMPORTANCE_HIGH))
        }
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val course = plan.course.course
        val location = listOfNotNull(course.building, course.room, course.locationNote)
            .filter(String::isNotBlank).joinToString(" ")
        val body = listOf("上课时间：${plan.startsAt.toLocalTime()}", location).filter(String::isNotBlank).joinToString(" · ")
        try {
            manager.notify(plan.key, 0, NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(com.kebiao.app.R.mipmap.ic_launcher)
                .setContentTitle("即将上课：${course.name}")
                .setContentText(body)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build())
            ReminderDeliveryStore.record(context, plan.course.date, plan.key)
        } catch (_: SecurityException) {
            // Notification permission may be revoked after the earlier check.
        }
    }

    companion object {
        const val CHANNEL_ID = "course_reminders"
        private const val EXTRA_KEY = "key"
        private const val EXTRA_TRIGGER = "triggerMillis"
        private val deliveryLock = Any()

        fun baseIntent(context: Context, key: String): Intent = Intent(context, ReminderReceiver::class.java)
            .setData(Uri.Builder().scheme("kebiao").authority("reminder").appendPath(key).build())

        fun intent(context: Context, plan: PlannedReminder): Intent = baseIntent(context, plan.key).apply {
            putExtra(EXTRA_KEY, plan.key)
            putExtra(EXTRA_TRIGGER, plan.triggerAt.toInstant().toEpochMilli())
        }
    }
}
