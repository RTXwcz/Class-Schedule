package com.kebiao.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.room.Room
import androidx.lifecycle.lifecycleScope
import com.kebiao.app.data.ScheduleRepository
import com.kebiao.app.data.local.AppDatabase
import com.kebiao.app.data.settings.AppSettingsStore
import com.kebiao.app.notifications.ReminderCoordinator
import com.kebiao.app.ui.AppViewModel
import com.kebiao.app.ui.ScheduleApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
        }
        val database = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "schedule.db").build()
        val repository = ScheduleRepository(database)
        val settingsStore = AppSettingsStore(applicationContext)
        val viewModel = AppViewModel(repository, settingsStore)
        ReminderCoordinator(applicationContext, repository, settingsStore).start(lifecycleScope)
        setContent {
            ScheduleApp(viewModel)
        }
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST = 1001
    }
}
