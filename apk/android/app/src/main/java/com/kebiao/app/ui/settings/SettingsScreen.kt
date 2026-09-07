package com.kebiao.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.kebiao.app.imports.OpenAiImageContract
import androidx.compose.ui.unit.dp
import com.kebiao.app.domain.model.ScheduleOverride
import com.kebiao.app.ui.AppViewModel
import com.kebiao.app.mcp.McpSettingsSection
import java.time.LocalDate

@Composable
fun SettingsScreen(viewModel: AppViewModel, padding: PaddingValues = PaddingValues()) {
    val state by viewModel.uiState.collectAsState()
    var semesterDate by remember(state.settings.semesterStartDate) { mutableStateOf(state.settings.semesterStartDate.orEmpty()) }
    var semesterError by remember { mutableStateOf<String?>(null) }
    var showOverrideEditor by remember { mutableStateOf(false) }
    var openAiKey by remember { mutableStateOf("") }
    var openAiEndpoint by remember(state.settings.openAiEndpoint) { mutableStateOf(state.settings.openAiEndpoint) }
    var openAiModel by remember(state.settings.openAiModel) { mutableStateOf(state.settings.openAiModel) }
    var openAiStatus by remember { mutableStateOf<String?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("设置") }
        item {
            Row(Modifier.fillMaxWidth()) {
                listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (value, label) ->
                    Column(Modifier.weight(1f)) {
                        RadioButton(state.settings.theme == value, { viewModel.updateSettings { it.copy(theme = value) } })
                        Text(label)
                    }
                }
            }
        }
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("单双周计算")
                    Text("以学期开始日期为第 1 周（单周）。之后每隔 7 天切换一次：第 1、3、5 周是单周，第 2、4、6 周是双周。未设置学期开始日期或日期早于开学时，单双周课程暂不列入当天课表。")
                    OutlinedTextField(
                        value = semesterDate,
                        onValueChange = { semesterDate = it },
                        label = { Text("学期开始日期 YYYY-MM-DD") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val date = runCatching { LocalDate.parse(semesterDate.trim()) }.getOrNull()
                            if (date != null) { viewModel.updateSemesterStartDate(date); semesterError = null }
                            else semesterError = "请输入有效日期，例如 2026-09-07"
                        }) { Text("保存日期") }
                        TextButton(onClick = { semesterDate = ""; viewModel.updateSemesterStartDate(null) }) { Text("清除") }
                    }
                    semesterError?.let { Text(it) }
                }
            }
        }
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("OpenAI 图片导入")
                    OutlinedTextField(openAiEndpoint, { openAiEndpoint = it }, label = { Text("API 地址") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(openAiModel, { openAiModel = it }, label = { Text("模型") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(openAiKey, { openAiKey = it }, label = { Text("API Key（本机加密保存）") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(onClick = {
                        openAiStatus = runCatching {
                            OpenAiImageContract.validateEndpoint(openAiEndpoint.trim())
                            require(openAiModel.isNotBlank()) { "请填写模型名称" }
                            viewModel.saveOpenAiKey(openAiKey)
                            viewModel.updateSettings { it.copy(openAiEndpoint = openAiEndpoint.trim(), openAiModel = openAiModel.trim()) }
                            openAiKey = ""
                            "配置已保存"
                        }.getOrElse { it.message ?: "保存失败" }
                    }) { Text("保存配置") }
                    openAiStatus?.let { Text(it) }
                }
            }
        }
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OcrModelSection(viewModel)
                }
            }
        }
        item {
            McpSettingsSection(state.settings, viewModel::updateSettings)
        }
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("课程提醒")
                    ReminderSettingsActions(state.settings.notificationsEnabled)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("启用通知")
                        Switch(
                            checked = state.settings.notificationsEnabled,
                            onCheckedChange = { value -> viewModel.updateSettings { it.copy(notificationsEnabled = value) } },
                        )
                    }
                    Text("提前 ${state.settings.reminderLeadMinutes} 分钟")
                    Slider(
                        value = state.settings.reminderLeadMinutes.toFloat(),
                        onValueChange = { value -> viewModel.updateSettings { it.copy(reminderLeadMinutes = value.toInt()) } },
                        valueRange = 0f..60f,
                        steps = 11,
                    )
                }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("调休规则")
                Button(onClick = { showOverrideEditor = true }) { Text("添加整天调休") }
            }
        }
        items(state.overrides, key = { it.date.toString() }) { override ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("${override.date} 按周${override.replacementWeekday}上课")
                        override.note?.takeIf { it.isNotBlank() }?.let { Text(it) }
                    }
                    TextButton(onClick = { viewModel.deleteOverride(override.date) }) { Text("删除") }
                }
            }
        }
    }

    if (showOverrideEditor) {
        OverrideEditorDialog(
            onDismiss = { showOverrideEditor = false },
            onSave = { viewModel.addOverride(it); showOverrideEditor = false },
        )
    }
}

@Composable
private fun OverrideEditorDialog(onDismiss: () -> Unit, onSave: (ScheduleOverride) -> Unit) {
    var date by remember { mutableStateOf("") }
    var weekday by remember { mutableStateOf("1") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加整天调休") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("指定日期当天，使用目标星期的整天课程。")
                OutlinedTextField(date, { date = it }, label = { Text("调休日期 YYYY-MM-DD") }, singleLine = true)
                OutlinedTextField(weekday, { weekday = it.filter(Char::isDigit) }, label = { Text("目标星期 1-7") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("备注") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsedDate = runCatching { LocalDate.parse(date.trim()) }.getOrNull()
                val parsedWeekday = weekday.toIntOrNull()?.takeIf { it in 1..7 }
                if (parsedDate != null && parsedWeekday != null) onSave(ScheduleOverride(parsedDate, parsedWeekday, note.trim().ifBlank { null }))
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
