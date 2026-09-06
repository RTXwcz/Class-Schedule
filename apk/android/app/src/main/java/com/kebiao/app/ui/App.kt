package com.kebiao.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kebiao.app.ui.calendar.ExamCalendarScreen
import com.kebiao.app.ui.importexport.ImportExportScreen
import com.kebiao.app.ui.settings.SettingsScreen
import com.kebiao.app.ui.timetable.TimetableScreen

@Composable
fun ScheduleApp(viewModel: AppViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("课表", "考试", "导入导出", "设置")
    val icons = listOf(Icons.Default.CalendarMonth, Icons.Default.Event, Icons.Default.ImportExport, Icons.Default.Settings)

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        tabs.forEachIndexed { index, label ->
                            NavigationBarItem(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                icon = { Icon(icons[index], contentDescription = label) },
                                label = { Text(label) },
                            )
                        }
                    }
                },
            ) { padding ->
                when (selectedTab) {
                    0 -> TimetableScreen(viewModel, padding)
                    1 -> ExamCalendarScreen(viewModel, padding)
                    2 -> ImportExportScreen(viewModel, padding)
                    else -> SettingsScreen(viewModel, padding)
                }
            }
        }
    }
}
