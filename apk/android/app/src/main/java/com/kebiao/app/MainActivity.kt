package com.kebiao.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.room.Room
import com.kebiao.app.data.ScheduleRepository
import com.kebiao.app.data.local.AppDatabase
import com.kebiao.app.data.settings.AppSettingsStore
import com.kebiao.app.ui.AppViewModel
import com.kebiao.app.ui.ScheduleApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "schedule.db").build()
        val viewModel = AppViewModel(ScheduleRepository(database), AppSettingsStore(applicationContext))
        setContent {
            ScheduleApp(viewModel)
        }
    }
}
