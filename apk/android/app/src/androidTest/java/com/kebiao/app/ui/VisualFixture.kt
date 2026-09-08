package com.kebiao.app.ui

import androidx.test.platform.app.InstrumentationRegistry
import com.kebiao.app.data.*
import com.kebiao.app.data.local.AppDatabase
import com.kebiao.app.data.settings.AppSettingsStore
import com.kebiao.app.notifications.ReminderCoordinator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Explicit visual QA fixture; normal instrumentation runs do not touch the user's dataset. */
class VisualFixture {
    @Test fun manageVisualDataset() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val action = args.getString("visualFixture") ?: return@runBlocking
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repo = ScheduleRepository(AppDatabase.getInstance(context))
        val settings = AppSettingsStore(context)
        val backup = File(context.filesDir, "visual-qa-schedule-backup.json")
        val preferences = context.getSharedPreferences("visual-qa-preferences", 0)
        if (action == "seed") {
            if (!backup.exists()) {
                val original = settings.settings.first()
                backup.writeText(JsonScheduleCodec.encode(repo.snapshotWithSettings(original)))
                preferences.edit().putString("theme", original.theme).putBoolean("notifications", original.notificationsEnabled).commit()
            }
            settings.update { it.copy(theme = "system", notificationsEnabled = false) }
            val day = LocalDate.now()
            repo.replaceAll(ScheduleRules.from(settings.settings.first()).copy(semesterStartDate = day.with(java.time.DayOfWeek.MONDAY).toString(), parityEnabled = false)
                .apply(ScheduleExport(courses = listOf(
                    ScheduleCourse("visual-math", "高等数学", day.dayOfWeek.value, 9, 10, building = "理科楼", room = "A301"),
                    ScheduleCourse("visual-english", "大学英语", day.dayOfWeek.value, 11, 12, building = "外语楼", room = "205"),
                    ScheduleCourse("visual-lab", "程序设计实验", day.plusDays(1).dayOfWeek.value, 1, 3, building = "实验楼", room = "B402"),
                    ScheduleCourse("visual-physics", "大学物理", day.minusDays(1).dayOfWeek.value, 3, 4, building = "理科楼", room = "208"),
                ), exams = listOf(ScheduleExam("visual-event", "阅读与分享", day.toString(), "19:00", "图书馆", "302", type = "EVENT", note = "带上本周的阅读笔记"),
                    ScheduleExam("visual-exam", "大学英语测验", day.plusDays(2).toString(), "09:00", "教学楼", "208")))))
        } else if (action == "empty") {
            check(backup.exists())
            repo.replaceAll(repo.snapshot().copy(courses = emptyList()))
        } else if (action == "restore") {
            if (backup.exists()) {
                repo.replaceAll(JsonScheduleCodec.decode(backup.readText()))
                settings.update { it.copy(theme = preferences.getString("theme", "system")!!, notificationsEnabled = preferences.getBoolean("notifications", true)) }
                backup.delete()
                preferences.edit().clear().commit()
            }
        } else error("Unknown visualFixture action")
        ReminderCoordinator.refresh(context)
    }
}
