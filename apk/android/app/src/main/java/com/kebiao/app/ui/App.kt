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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.activity.compose.LocalActivity
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kebiao.app.ui.calendar.ExamCalendarScreen
import com.kebiao.app.ui.importexport.ImportExportScreen
import com.kebiao.app.ui.settings.SettingsScreen
import com.kebiao.app.ui.timetable.TimetableScreen
import com.kebiao.app.mcp.McpApprovalDialog
import com.kebiao.app.ui.settings.OcrChoiceDialog

@Composable
fun ScheduleApp(viewModel: AppViewModel) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            snackbar.showSnackbar(message)
            viewModel.clearError()
        }
    }
    val tabs = listOf("课表", "考试", "导入导出", "设置")
    val icons = listOf(Icons.Default.CalendarMonth, Icons.Default.Event, Icons.Default.ImportExport, Icons.Default.Settings)

    val dark = state.settings.theme == "dark" || (state.settings.theme == "system" && isSystemInDarkTheme())
    val activity = LocalActivity.current as? ComponentActivity
    SideEffect {
        val bars = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        activity?.enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
    }
    MaterialTheme(colorScheme = if (dark) darkColorScheme(
        primary = Color(0xFF8ED5C2), onPrimary = Color(0xFF00382C),
        primaryContainer = Color(0xFF145143), onPrimaryContainer = Color(0xFFB0EFDA),
        secondary = Color(0xFFE8B56F), onSecondary = Color(0xFF452B00),
        secondaryContainer = Color(0xFF55452B), onSecondaryContainer = Color(0xFFFFDEAC),
        background = Color(0xFF151718), onBackground = Color(0xFFE4E7E8),
        surface = Color(0xFF151718), onSurface = Color(0xFFE4E7E8),
        surfaceVariant = Color(0xFF343B3D), onSurfaceVariant = Color(0xFFBEC6C8),
        surfaceContainerLowest = Color(0xFF101213), surfaceContainerLow = Color(0xFF1D2021),
        surfaceContainer = Color(0xFF242728), surfaceContainerHigh = Color(0xFF2B2F30),
        surfaceContainerHighest = Color(0xFF343839), outline = Color(0xFF899295),
    ) else lightColorScheme(
        primary = Color(0xFF176B5B), onPrimary = Color.White,
        primaryContainer = Color(0xFFC0EBDC), onPrimaryContainer = Color(0xFF004D3E),
        secondary = Color(0xFF805D23), onSecondary = Color.White,
        secondaryContainer = Color(0xFFF7E0B6), onSecondaryContainer = Color(0xFF573E16),
        background = Color(0xFFFAFBFC), onBackground = Color(0xFF202527),
        surface = Color(0xFFFAFBFC), onSurface = Color(0xFF202527),
        surfaceVariant = Color(0xFFE4E9EA), onSurfaceVariant = Color(0xFF465154),
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF3F6F7),
        surfaceContainer = Color(0xFFEEF2F3), surfaceContainerHigh = Color(0xFFE8EDEF),
        surfaceContainerHighest = Color(0xFFE0E7E9), outline = Color(0xFF727D81),
    )) {
        McpApprovalDialog()
        OcrChoiceDialog(viewModel)
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
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
