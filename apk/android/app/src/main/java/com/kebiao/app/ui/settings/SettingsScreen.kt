package com.kebiao.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kebiao.app.domain.model.ScheduleOverride
import com.kebiao.app.imports.OpenAiImageContract
import com.kebiao.app.mcp.McpSettingsSection
import com.kebiao.app.ui.AppViewModel
import com.kebiao.app.ui.components.*
import java.time.LocalDate

@Composable
fun SettingsScreen(viewModel: AppViewModel, padding: PaddingValues = PaddingValues(), initialSection: String = "课表与提醒", onSectionSelected: (String) -> Unit = {}) {
    val state by viewModel.uiState.collectAsState()
    var section by rememberSaveable(initialSection) { mutableStateOf(initialSection) }
    LaunchedEffect(initialSection) { section = initialSection }
    var editingOverride by remember { mutableStateOf<ScheduleOverride?>(null) }
    var showOverride by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ScheduleOverride?>(null) }
    var openAiKey by remember { mutableStateOf("") }
    var endpoint by remember(state.settings.openAiEndpoint) { mutableStateOf(state.settings.openAiEndpoint) }
    var model by remember(state.settings.openAiModel) { mutableStateOf(state.settings.openAiModel) }
    var configStatus by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { ProductHeader("按你的节奏", "把课表调成适合自己的样子", "偏好设置") }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("课表与提醒", "图片导入", "AI 连接").forEach { label ->
                    FilterChip(section == label, { section = label; onSectionSelected(label) }, label = { Text(label) })
                }
            }
        }
        if (section == "课表与提醒") {
            item {
                SettingsGroup("外观") {
                    Text("配色", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("green" to "松林绿", "purple" to "鸢尾紫").forEach { (value, label) ->
                            FilterChip(state.settings.colorPalette == value, { viewModel.updateSettings { it.copy(colorPalette = value) } },
                                label = { Text(label) }, leadingIcon = {
                                    Box(Modifier.size(16.dp).background(productColorScheme(value, false).primary, CircleShape))
                                })
                        }
                    }
                    Text("显示模式", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (value, label) ->
                            FilterChip(state.settings.theme == value, { viewModel.updateSettings { it.copy(theme = value) } }, label = { Text(label) })
                        }
                    }
                }
            }
            item { PeriodSettingsSection(viewModel) }
            item {
                SettingsGroup("学期与周次", "设置开学日期后，周次范围和单双周课程才会按学期生效。") {
                    DateWheelField("学期开始日期", state.settings.semesterStartDate?.let(LocalDate::parse), viewModel::updateSemesterStartDate)
                    if (state.settings.semesterStartDate != null) TextButton(onClick = { viewModel.updateSemesterStartDate(null) }) { Text("清除学期日期") }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("启用单双周", style = MaterialTheme.typography.titleSmall)
                            Text(if (state.settings.parityEnabled) "第 1、3、5 周为单周，第 2、4、6 周为双周" else "关闭时按每周显示，保留原有单双周规则", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(state.settings.parityEnabled, { value -> viewModel.updateSettings { it.copy(parityEnabled = value) } })
                    }
                }
            }
            item {
                SettingsGroup("上课提醒") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("提前提醒我", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        Switch(state.settings.notificationsEnabled, { value -> viewModel.updateSettings { it.copy(notificationsEnabled = value) } })
                    }
                    if (state.settings.notificationsEnabled) {
                        Text("提前 ${state.settings.reminderLeadMinutes} 分钟", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        Slider(state.settings.reminderLeadMinutes.toFloat().coerceIn(0f, 60f), { value -> viewModel.updateSettings { it.copy(reminderLeadMinutes = value.toInt()) } }, valueRange = 0f..60f, steps = 11)
                        ReminderSettingsActions(true)
                    }
                }
            }
            item {
                SettingsGroup("调休与补课", "指定某一天，改上另一个星期的整天课程。") {
                    Button(onClick = { editingOverride = null; showOverride = true }) { Text("添加调休") }
                    if (state.overrides.isEmpty()) Text("还没有调休安排", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.overrides.sortedBy { it.date }.forEach { item ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { editingOverride = item; showOverride = true }, modifier = Modifier.weight(1f)) {
                                Text("${item.date} · 改上周${"一二三四五六日"[item.replacementWeekday - 1]}")
                            }
                            TextButton(onClick = { pendingDelete = item }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
        if (section == "图片导入") {
            item { SettingsGroup("离线识别", "主动下载后，课表图片可以在手机上识别。") { OcrModelSection(viewModel) } }
            item {
                SettingsGroup("使用 OpenAI 兼容接口", "选择云端识别时，图片会发送到你配置的 API 服务。") {
                    OutlinedTextField(endpoint, { endpoint = it }, label = { Text("API 地址") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(model, { model = it }, label = { Text("模型名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(openAiKey, { openAiKey = it }, label = { Text(if (viewModel.hasOpenAiKey()) "已保存密钥 · 输入可替换" else "API Key") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(onClick = {
                        configStatus = runCatching {
                            OpenAiImageContract.validateEndpoint(endpoint.trim())
                            require(model.isNotBlank()) { "请填写模型名称" }
                            viewModel.saveOpenAiKey(openAiKey)
                            viewModel.updateSettingsAndThen({ it.copy(openAiEndpoint = endpoint.trim(), openAiModel = model.trim()) }) { configStatus = "配置已保存" }
                            openAiKey = ""
                            "正在保存…"
                        }.getOrElse { it.message ?: "保存失败" }
                    }) { Text("保存配置") }
                    configStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        if (section == "AI 连接") item { McpSettingsSection(state.settings, viewModel::updateSettings) }
        item { LicenseSection() }
    }
    if (showOverride) OverrideEditorDialog(editingOverride, { showOverride = false }, { viewModel.saveOverride(editingOverride?.date, it); showOverride = false })
    pendingDelete?.let { item -> AlertDialog(onDismissRequest = { pendingDelete = null }, title = { Text("删除这条调休？") },
        text = { Text("${item.date} 将恢复按自然星期显示课程。") },
        confirmButton = { TextButton(onClick = { viewModel.deleteOverride(item.date); pendingDelete = null }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }) }
}

@Composable
private fun OverrideEditorDialog(initial: ScheduleOverride?, onDismiss: () -> Unit, onSave: (ScheduleOverride) -> Unit) {
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now()) }
    var weekday by remember { mutableIntStateOf(initial?.replacementWeekday ?: 1) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    var weekdayMoving by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial == null) "添加调休" else "编辑调休") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            DateWheelField("调休日期", date, { date = it })
            Text("这一天改上", style = MaterialTheme.typography.titleSmall)
            NumberWheel("星期", 1..7, weekday, { weekday = it }, Modifier.fillMaxWidth(), onScrolling = { weekdayMoving = it }) { "周${"一二三四五六日"[it - 1]}" }
            OutlinedTextField(note, { note = it }, label = { Text("备注（可选）") }, singleLine = true)
        }
    }, confirmButton = { Button(onClick = { onSave(ScheduleOverride(date, weekday, note.trim().ifBlank { null })) }, enabled = !weekdayMoving) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
