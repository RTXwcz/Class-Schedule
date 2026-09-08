package com.kebiao.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kebiao.app.notifications.LessonPeriod
import com.kebiao.app.notifications.PeriodSchedule
import com.kebiao.app.ui.AppViewModel
import com.kebiao.app.ui.components.*
import java.time.LocalTime

@Composable
fun PeriodSettingsSection(viewModel: AppViewModel) {
    val state by viewModel.uiState.collectAsState()
    var editing by remember { mutableStateOf(false) }
    val periods = state.settings.periods
    SettingsGroup("每日作息", "课程卡片、提醒和小组件使用同一份时间表。") {
        Text("${periods.size} 节课", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${periods.first().start} — ${periods.last().end}", style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = { editing = true }) { Text("编辑作息") }
        }
    }
    if (editing) PeriodEditorDialog(periods, state.courses.maxOfOrNull { it.endPeriod } ?: 1, { editing = false }, {
        viewModel.updatePeriodSchedule(it); editing = false
    })
}

@Composable
private fun PeriodEditorDialog(initial: List<LessonPeriod>, minimumPeriods: Int, onDismiss: () -> Unit, onSave: (List<LessonPeriod>) -> Unit) {
    var drafts by remember { mutableStateOf(initial) }
    var pickCount by remember { mutableStateOf(false) }
    var count by remember { mutableIntStateOf(initial.size) }
    var countMoving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    if (pickCount) AlertDialog(onDismissRequest = { pickCount = false }, title = { Text("每天多少节课") }, text = {
        Column {
            Text("已有课程使用到第 $minimumPeriods 节。", style = MaterialTheme.typography.bodySmall)
            NumberWheel("节数", minimumPeriods..PeriodSchedule.MAX_PERIODS, count, { count = it }, Modifier.fillMaxWidth(), onScrolling = { countMoving = it })
        }
    }, confirmButton = { Button(onClick = {
        drafts = List(count) { drafts.getOrNull(it) ?: LessonPeriod("", "") }; pickCount = false; error = null
    }, enabled = !countMoving) { Text("应用节数") } }, dismissButton = { TextButton(onClick = { pickCount = false }) { Text("取消") } })
    else AlertDialog(onDismissRequest = onDismiss, title = { Text("编辑每日作息") }, text = {
        LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("每天 ${drafts.size} 节", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { count = drafts.size; pickCount = true }) { Text("调整节数") }
                }
                Text("滚动选择起止时间。每节按先后排列，不能重叠或跨午夜。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            itemsIndexed(drafts) { index, period ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("第 ${index + 1} 节", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TimeWheelField("第${index + 1}节开始", period.start.takeIf(String::isNotEmpty)?.let(LocalTime::parse), { value ->
                            drafts = drafts.mapIndexed { i, p -> if (i == index) p.copy(start = value.toString()) else p }; error = null
                        }, Modifier.weight(1f))
                        TimeWheelField("第${index + 1}节结束", period.end.takeIf(String::isNotEmpty)?.let(LocalTime::parse), { value ->
                            drafts = drafts.mapIndexed { i, p -> if (i == index) p.copy(end = value.toString()) else p }; error = null
                        }, Modifier.weight(1f))
                    }
                }
            }
        }
    }, confirmButton = {
        Column {
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { runCatching { PeriodSchedule.validate(drafts) }.onSuccess(onSave).onFailure { error = it.message } }) { Text("保存作息") }
        }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
