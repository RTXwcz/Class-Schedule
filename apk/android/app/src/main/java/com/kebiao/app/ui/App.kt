package com.kebiao.app.ui

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kebiao.app.ui.calendar.ExamCalendarScreen
import com.kebiao.app.ui.importexport.ImportExportScreen
import com.kebiao.app.ui.settings.SettingsScreen
import com.kebiao.app.ui.timetable.TimetableScreen
import com.kebiao.app.mcp.McpApprovalDialog
import com.kebiao.app.ui.settings.OcrChoiceDialog
import com.kebiao.app.ui.components.LocalProductColorPalette
import com.kebiao.app.ui.components.productColorScheme
import com.kebiao.app.widget.WidgetLaunchRequest
import com.kebiao.app.widget.WidgetNavigation

@Composable
fun ScheduleApp(viewModel: AppViewModel, widgetRequest: WidgetLaunchRequest? = null, onWidgetLaunchHandled: () -> Unit = {}) {
    val pageState = rememberSaveableStateHolder()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var settingsSection by rememberSaveable { mutableStateOf("课表与提醒") }
    var entryRequestKey by rememberSaveable { mutableStateOf<String?>(null) }
    var deferOnboarding by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(widgetRequest?.id) {
        widgetRequest?.let { request ->
            if (request.destination == WidgetNavigation.ENTRY) {
                selectedTab = 2
                entryRequestKey = request.id
                deferOnboarding = true
            } else selectedTab = 0
            onWidgetLaunchHandled()
        }
    }
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message -> snackbar.showSnackbar(message); viewModel.clearError() }
    }
    val tabs = listOf("课表", "安排", "录入", "设置")
    val icons = listOf(Icons.Outlined.CalendarMonth, Icons.Outlined.EventNote, Icons.Outlined.AddCircleOutline, Icons.Outlined.Tune)
    val dark = state.settings.theme == "dark" || (state.settings.theme == "system" && isSystemInDarkTheme())
    val activity = LocalActivity.current as? ComponentActivity
    SideEffect {
        val bars = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        activity?.enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
    }
    CompositionLocalProvider(LocalProductColorPalette provides state.settings.colorPalette) {
        MaterialTheme(colorScheme = productColorScheme(state.settings.colorPalette, dark),
            shapes = Shapes(small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp), large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp))) {
            McpApprovalDialog()
            if (!deferOnboarding) OcrChoiceDialog(viewModel, onManual = { selectedTab = 2 }, onConfigure = { settingsSection = "图片导入"; selectedTab = 3 })
            Scaffold(containerColor = MaterialTheme.colorScheme.background, snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    // A centred pill keeps the bar narrower than the screen while giving every
                    // icon and label its own equal slot, including the gesture inset.
                    Box(
                        Modifier.fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp).testTag("app-navigation"),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            tonalElevation = 2.dp,
                            shadowElevation = 3.dp,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f)),
                        ) {
                            Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                tabs.forEachIndexed { index, label ->
                                    Column(
                                        Modifier.weight(1f).fillMaxHeight().clickable { selectedTab = index },
                                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Box(
                                            Modifier.width(46.dp).height(26.dp).background(
                                                if (selectedTab == index) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                                androidx.compose.foundation.shape.RoundedCornerShape(13.dp),
                                            ),
                                            contentAlignment = androidx.compose.ui.Alignment.Center,
                                        ) {
                                            Icon(
                                                icons[index],
                                                contentDescription = null,
                                                modifier = Modifier.width(20.dp).height(20.dp).testTag("navigation-icon-$index"),
                                                tint = if (selectedTab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Text(
                                            label,
                                            modifier = Modifier.testTag("navigation-label-$index"),
                                            fontSize = 11.sp,
                                            lineHeight = 14.sp,
                                            color = if (selectedTab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }) { padding ->
                pageState.SaveableStateProvider(selectedTab) { when (selectedTab) {
                    0 -> TimetableScreen(viewModel, padding)
                    1 -> ExamCalendarScreen(viewModel, padding)
                    2 -> ImportExportScreen(viewModel, padding, onConfigure = { settingsSection = "图片导入"; selectedTab = 3 }, entryRequestKey = entryRequestKey)
                    else -> SettingsScreen(viewModel, padding, initialSection = settingsSection, onSectionSelected = { settingsSection = it })
                } }
            }
        }
    }
}
