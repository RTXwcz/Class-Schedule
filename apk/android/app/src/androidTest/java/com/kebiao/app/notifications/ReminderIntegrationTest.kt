package com.kebiao.app.notifications

import android.app.PendingIntent
import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kebiao.app.data.ScheduleCourse
import com.kebiao.app.data.ScheduleExport
import com.kebiao.app.data.ScheduleRepository
import com.kebiao.app.data.local.AppDatabase
import com.kebiao.app.data.settings.AppSettingsStore
import com.kebiao.app.domain.model.Course
import com.kebiao.app.widget.WidgetSnapshotProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class ReminderIntegrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun disabledNotificationsStillRefreshDatabaseSnapshotAndKeepDailyRenewal() = runBlocking {
        val repository = ScheduleRepository(AppDatabase.getInstance(context))
        val settings = AppSettingsStore(context)
        val original = repository.snapshot()
        val originalSettings = settings.settings.first()
        try {
            repository.replaceAll(ScheduleExport(courses = listOf(
                ScheduleCourse("widget-test", "首次课程", LocalDate.now().dayOfWeek.value, 1, 2),
            )))
            settings.update { it.copy(notificationsEnabled = false) }
            ReminderCoordinator.refresh(context)
            assertEquals(ReminderStatus.DISABLED, ReminderScheduler(context).status(false))
            assertEquals(3, WidgetSnapshotProvider.load(context).size)
            repository.upsertCourse(ScheduleCourse("widget-test", "数据库新名称", LocalDate.now().dayOfWeek.value, 1, 2))
            assertEquals("数据库新名称", WidgetSnapshotProvider.load(context).first().course.name)
            assertTrue(context.getSharedPreferences("reminder_schedule", Context.MODE_PRIVATE).getStringSet("keys", emptySet()).orEmpty().isEmpty())
            val daily = PendingIntent.getBroadcast(context, 0,
                Intent(context, BootReceiver::class.java).setAction(ReminderScheduler.ACTION_REFRESH)
                    .setData(Uri.parse("kebiao://refresh/daily")),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            assertNotNull(daily)
        } finally {
            repository.replaceAll(original)
            settings.update { originalSettings }
            ReminderCoordinator.refresh(context)
        }
    }

    @Test
    fun collidingJavaHashCodesStillHaveDistinctAlarmIdentities() {
        val date = LocalDate.now()
        assertEquals(ReminderScheduler.requestCode("Aa@$date"), ReminderScheduler.requestCode("BB@$date"))
        assertNotEquals(ReminderReceiver.baseIntent(context, "Aa@$date").data,
            ReminderReceiver.baseIntent(context, "BB@$date").data)
    }

    @Test
    fun refreshRebuildsDueAlarmAndStopsRebuildingAfterDelivery() = runBlocking {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val scheduler = ReminderScheduler(context)
        assertTrue("课程提醒通知渠道需要开启", scheduler.notificationsAllowed())
        val prefs = context.getSharedPreferences("delivered_reminders", Context.MODE_PRIVATE)
        val previousDate = prefs.getString("date", null)
        val previousKeys = prefs.getStringSet("keys", emptySet()).orEmpty().toSet()
        // A future date keeps the test alarm from firing against real wall-clock time.
        val date = LocalDate.now().plusDays(7)
        val now = date.atTime(7, 51).atZone(ZoneId.systemDefault())
        val key = "catch-up-test@$date"
        val course = Course("catch-up-test", "待补发课程", date.dayOfWeek.value, 1, 2)
        fun pending() = PendingIntent.getBroadcast(context, ReminderScheduler.requestCode(key),
            ReminderReceiver.baseIntent(context, key), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        try {
            prefs.edit().clear().commit()
            scheduler.schedule(now.minusMinutes(2), date, listOf(course), emptyList())
            assertNotNull(pending())
            scheduler.schedule(now, date, listOf(course), emptyList())
            assertNotNull("7:51 刷新必须补排尚未投递的 7:50 提醒", pending())
            assertTrue(context.getSharedPreferences("reminder_schedule", Context.MODE_PRIVATE)
                .getStringSet("keys", emptySet()).orEmpty().contains(key))

            ReminderDeliveryStore.record(context, date, key)
            scheduler.schedule(now.plusSeconds(1), date, listOf(course), emptyList())
            assertNull("已投递提醒不得立即重排", pending())
            assertTrue(context.getSharedPreferences("reminder_schedule", Context.MODE_PRIVATE)
                .getStringSet("keys", emptySet()).orEmpty().none { it == key })
            scheduler.schedule(now.plusSeconds(2), date, listOf(course), emptyList())
            assertNull("重复刷新不得形成补排循环", pending())
            assertTrue(ReminderDeliveryStore.deliveredKeys(context, date.plusDays(1)).isEmpty())
        } finally {
            scheduler.cancelAll()
            prefs.edit().clear().putString("date", previousDate).putStringSet("keys", previousKeys).commit()
            ReminderCoordinator.refresh(context)
        }
    }
}
