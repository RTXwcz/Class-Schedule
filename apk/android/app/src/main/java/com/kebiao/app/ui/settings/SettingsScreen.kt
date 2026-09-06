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
import androidx.compose.ui.unit.dp
import com.kebiao.app.domain.model.ScheduleOverride
import com.kebiao.app.ui.AppViewModel
import java.time.LocalDate

@Composable
fun SettingsScreen(viewModel: AppViewModel, padding: PaddingValues = PaddingValues()) {
    val state by viewModel.uiState.collectAsState()
    var semesterDate by remember(state.settings.semesterStartDate) { mutableStateOf(state.settings.semesterStartDate.orEmpty()) }
    var showOverrideEditor by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("设置") }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("单双周计算")
                    Text("以学期开始日期为第 1 周（单周）。之后每隔 7 天切换一次：第 1、3、5 周是单周，第 2、4、6 周是双周。未设置学期开始日期时，单双周课程不会被过滤。")
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
                            viewModel.updateSemesterStartDate(date)
                        }) { Text("保存日期") }
                        TextButton(onClick = { semesterDate = ""; viewModel.updateSemesterStartDate(null) }) { Text("清除") }
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("课程提醒")
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
