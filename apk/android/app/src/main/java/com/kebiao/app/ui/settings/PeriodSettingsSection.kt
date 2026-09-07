package com.kebiao.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kebiao.app.notifications.LessonPeriod
import com.kebiao.app.notifications.PeriodSchedule
import com.kebiao.app.ui.AppViewModel

@Composable
fun PeriodSettingsSection(viewModel: AppViewModel) {
    val state by viewModel.uiState.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val periods = state.settings.periods
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("每日作息", style = MaterialTheme.typography.titleMedium)
            Text("每天 ${periods.size} 节 · ${periods.first().start}—${periods.last().end}",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("课表、上课提醒与桌面小组件共用这份时间表。", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { editing = true }) { Text("编辑作息") }
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起时间" else "查看时间") }
            }
            if (expanded) periods.forEachIndexed { index, period ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("第 ${index + 1} 节")
                    Text("${period.start}—${period.end}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (editing) PeriodEditorDialog(
        initial = periods,
        minimumPeriods = state.courses.maxOfOrNull { it.endPeriod } ?: 1,
        onDismiss = { editing = false },
        onSave = { viewModel.updatePeriodSchedule(it); editing = false },
    )
}

@Composable
private fun PeriodEditorDialog(
    initial: List<LessonPeriod>,
    minimumPeriods: Int,
    onDismiss: () -> Unit,
    onSave: (List<LessonPeriod>) -> Unit,
) {
    var drafts by remember { mutableStateOf(initial) }
    var count by remember { mutableStateOf(initial.size.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    fun applyCount(): Boolean {
        val desired = count.toIntOrNull()
        if (desired == null || desired !in 1..PeriodSchedule.MAX_PERIODS) {
            error = "每天节数须为 1 到 ${PeriodSchedule.MAX_PERIODS}"
            return false
        }
        if (desired < minimumPeriods) {
            error = "已有课程使用到第 $minimumPeriods 节，请先调整这些课程再减少节数"
            return false
        }
        drafts = List(desired) { index -> drafts.getOrNull(index) ?: LessonPeriod("", "") }
        error = null
        return true
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑每日作息") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("可设置 1—${PeriodSchedule.MAX_PERIODS} 节。时间使用 24 小时制，按先后排列且不能重叠。")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(count, { count = it; error = null }, label = { Text("每天节数") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    TextButton(onClick = { applyCount() }, modifier = Modifier.padding(top = 8.dp)) { Text("应用节数") }
                }
                if (count.toIntOrNull() != drafts.size) Text("点击“应用节数”后填写新增的时间。", style = MaterialTheme.typography.bodySmall)
                drafts.forEachIndexed { index, period ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("第 ${index + 1} 节", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(period.start, { text ->
                                drafts = drafts.mapIndexed { i, value -> if (i == index) value.copy(start = text) else value }; error = null
                            }, label = { Text("开始 HH:mm") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(period.end, { text ->
                                drafts = drafts.mapIndexed { i, value -> if (i == index) value.copy(end = text) else value }; error = null
                            }, label = { Text("结束 HH:mm") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Button(onClick = {
                    if (count.toIntOrNull() != drafts.size) {
                        if (applyCount()) error = "节数已应用，请检查每节时间后再次保存"
                    } else {
                        runCatching { PeriodSchedule.validate(drafts) }
                            .onSuccess(onSave)
                            .onFailure { error = it.message ?: "作息时间无效" }
                    }
                }) { Text("保存作息") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
