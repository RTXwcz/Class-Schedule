package com.kebiao.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.kebiao.app.MainActivity

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra(EXTRA_NAME) ?: return
        val start = intent.getStringExtra(EXTRA_START).orEmpty()
        val location = intent.getStringExtra(EXTRA_LOCATION).orEmpty()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "课程提醒", NotificationManager.IMPORTANCE_HIGH),
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = listOf("上课时间：$start", location.takeIf { it.isNotBlank() })
            .filterNotNull().joinToString(" · ")
        manager.notify(intent.getIntExtra(EXTRA_REQUEST_CODE, name.hashCode()), NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.kebiao.app.R.mipmap.ic_launcher)
            .setContentTitle("即将上课：$name")
            .setContentText(body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build())
    }

    companion object {
        private const val CHANNEL_ID = "course_reminders"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_START = "start"
        private const val EXTRA_LOCATION = "location"
        private const val EXTRA_REQUEST_CODE = "requestCode"

        fun intent(context: Context, plan: PlannedReminder): Intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_NAME, plan.course.course.name)
            putExtra(EXTRA_START, plan.startsAt.toLocalTime().toString().take(5))
            putExtra(EXTRA_LOCATION, listOfNotNull(plan.course.course.building, plan.course.course.room).joinToString(" "))
            putExtra(EXTRA_REQUEST_CODE, ReminderScheduler.requestCode(plan.key))
        }

        fun intent(context: Context, key: String, name: String, start: String, location: String): Intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_NAME, name)
            putExtra(EXTRA_START, start)
            putExtra(EXTRA_LOCATION, location)
            putExtra(EXTRA_REQUEST_CODE, ReminderScheduler.requestCode(key))
        }
    }
}
