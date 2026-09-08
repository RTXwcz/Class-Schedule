package com.kebiao.app.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kebiao.app.data.ScheduleExam
import com.kebiao.app.ui.AppViewModel
import com.kebiao.app.ui.components.DateWheelField
import com.kebiao.app.ui.components.TimeWheelField
import com.kebiao.app.ui.components.ProductHeader
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ExamCalendarScreen(
    viewModel: AppViewModel,
    padding: PaddingValues = PaddingValues(),
    initialType: String? = null,
    onInitialTypeConsumed: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    var filter by rememberSaveable { mutableStateOf("ALL") }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ScheduleExam?>(null) }
    var editorType by remember { mutableStateOf("EXAM") }
    var showEditor by remember { mutableStateOf(false) }
    val today = LocalDate.now().toString()
    val filtered = state.exams.filter { filter == "ALL" || it.type == filter }
    val past = filtered.filter { it.date < today }
    val datedItems = filtered.filter { it.date >= today }
        .sortedWith(compareBy<ScheduleExam> { it.date }.thenBy { it.time.orEmpty() }.thenBy { it.subject })
    LaunchedEffect(initialType) {
        if (initialType in setOf("EXAM", "EVENT")) {
            editing = null
            editorType = initialType!!
            showEditor = true
            onInitialTypeConsumed()
        }
    }

    Scaffold(
        modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = null; editorType = if (filter == "EVENT") "EVENT" else "EXAM"; showEditor = true },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("添加安排") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ProductHeader("重要的日子", "考试、活动和计划，按日期有序展开", "考试与日程")
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ALL" to "全部", "EXAM" to "考试", "EVENT" to "日程").forEach { (value, label) ->
                        FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(label) })
                    }
                }
            }
            if (datedItems.isEmpty()) item {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(if (past.isNotEmpty()) "近期没有安排" else if (filter == "ALL") "还没有安排" else if (filter == "EXAM") "还没有考试" else "还没有日程",
                            style = MaterialTheme.typography.titleLarge)
                        Text("添加考试、会议或个人计划，按日期集中查看。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            val groups = datedItems.groupBy { it.date }.toList() + listOf("history" to emptyList<ScheduleExam>()) +
                if (showHistory) past.sortedWith(compareByDescending<ScheduleExam> { it.date }.thenBy { it.time.orEmpty() }).groupBy { it.date }.toList() else emptyList()
            groups.forEach { (date, group) ->
                if (date == "history") {
                    if (past.isNotEmpty()) item(key = "history") {
                        TextButton(onClick = { showHistory = !showHistory }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (showHistory) "收起过去安排 · ${past.size}" else "查看过去安排 · ${past.size}")
                        }
                    }
                    return@forEach
                }
                item(key = "date:$date") {
                    Text(dateLabel(date), style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
                }
                items(group, key = { "item:${it.id}" }) { entry ->
                    Card(
                        onClick = { editing = entry; editorType = entry.type; showEditor = true },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                Icon(if (entry.type == "EVENT") Icons.Outlined.EventNote else Icons.Outlined.School,
                                    contentDescription = null, modifier = Modifier.padding(12.dp).size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(entry.subject, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("${if (entry.type == "EVENT") "日程" else "考试"} · ${entry.time ?: "全天"}",
                                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                listOfNotNull(entry.building, entry.room, entry.locationNote).filter { it.isNotBlank() }
                                    .joinToString(" · ").takeIf { it.isNotBlank() }?.let {
                                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                entry.note?.takeIf(String::isNotBlank)?.let {
                                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) ExamEditorDialog(
        initial = editing, initialType = editorType,
        onDismiss = { showEditor = false },
        onSave = { viewModel.addExam(it); showEditor = false },
        onDelete = { viewModel.deleteExam(it.id); showEditor = false },
    )
}

private fun dateLabel(value: String): String = runCatching {
    val date = LocalDate.parse(value)
    val relative = when (date) { LocalDate.now() -> "今天 · "; LocalDate.now().plusDays(1) -> "明天 · "; else -> "" }
    relative + date.format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.SIMPLIFIED_CHINESE))
}.getOrDefault(value)

@Composable
fun ExamEditorDialog(
    initial: ScheduleExam? = null,
    onDismiss: () -> Unit,
    onSave: (ScheduleExam) -> Unit,
    onDelete: (ScheduleExam) -> Unit,
    initialType: String = "EXAM",
) {
    var type by rememberSaveable(initial) { mutableStateOf(initial?.type ?: initialType) }
    var subject by rememberSaveable(initial) { mutableStateOf(initial?.subject.orEmpty()) }
    val originalDate = rememberSaveable(initial) { initial?.date ?: LocalDate.now().toString() }
    var date by rememberSaveable(initial) { mutableStateOf(originalDate) }
    var selectedTime by rememberSaveable(initial) { mutableStateOf(initial?.time ?: "09:00") }
    var allDay by rememberSaveable(initial) { mutableStateOf(initial?.time.isNullOrBlank()) }
    val time = if (allDay) "" else selectedTime
    var building by rememberSaveable(initial) { mutableStateOf(initial?.building.orEmpty()) }
    var room by rememberSaveable(initial) { mutableStateOf(initial?.room.orEmpty()) }
    var locationNote by rememberSaveable(initial) { mutableStateOf(initial?.locationNote.orEmpty()) }
    var note by rememberSaveable(initial) { mutableStateOf(initial?.note.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val validDate = date.trim().matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && runCatching { LocalDate.parse(date.trim()) }.isSuccess
    val validTime = time.isBlank() || (time.trim().matches(Regex("\\d{2}:\\d{2}")) && runCatching { LocalTime.parse(time.trim()) }.isSuccess)
    val typeLabel = if (type == "EVENT") "日程" else "考试"
    val hasChanges = type != (initial?.type ?: initialType) || subject != initial?.subject.orEmpty() ||
        date != originalDate || time != initial?.time.orEmpty() || building != initial?.building.orEmpty() ||
        room != initial?.room.orEmpty() || locationNote != initial?.locationNote.orEmpty() || note != initial?.note.orEmpty()
    AlertDialog(
        onDismissRequest = {
            if (!confirmDelete && !confirmDiscard) {
                if (hasChanges) confirmDiscard = true else onDismiss()
            }
        },
        title = { Text("${if (initial == null) "添加" else "编辑"}$typeLabel") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(type == "EXAM", { type = "EXAM" }, label = { Text("考试") })
                    FilterChip(type == "EVENT", { type = "EVENT" }, label = { Text("日程") })
                }
                OutlinedTextField(subject, { subject = it }, label = { Text(if (type == "EXAM") "科目" else "日程名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                DateWheelField("日期", runCatching { LocalDate.parse(date) }.getOrNull(), { date = it.toString() })
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("全天安排", Modifier.weight(1f))
                    androidx.compose.material3.Switch(allDay, { allDay = it })
                }
                if (!allDay) TimeWheelField("开始时间", LocalTime.parse(selectedTime), { selectedTime = it.toString() })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(building, { building = it }, label = { Text(if (type == "EXAM") "教学楼" else "地点 / 建筑") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(room, { room = it }, label = { Text(if (type == "EXAM") "教室" else "房间") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                OutlinedTextField(locationNote, { locationNote = it }, label = { Text("地点备注（可选）") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("备注（可选）") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(ScheduleExam(
                    id = initial?.id ?: AppViewModel.newExamId(), subject = subject.trim(), date = date.trim(),
                    time = time.trim().ifBlank { null }, building = building.trim().ifBlank { null }, room = room.trim().ifBlank { null },
                    locationNote = locationNote.trim().ifBlank { null }, type = type, note = note.trim().ifBlank { null },
                    source = initial?.source ?: "MANUAL", createdAtEpochMillis = initial?.createdAtEpochMillis ?: System.currentTimeMillis(),
                    updatedAtEpochMillis = System.currentTimeMillis(),
                ))
            }, enabled = subject.isNotBlank() && validDate && validTime) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (initial != null) TextButton(onClick = { confirmDelete = true }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false }, title = { Text("放弃修改？") },
        text = { Text("这条安排的修改尚未保存，可以继续编辑。") },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; onDismiss() }) { Text("放弃修改", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("继续编辑") } },
    )
    if (confirmDelete && initial != null) AlertDialog(
        onDismissRequest = { confirmDelete = false }, title = { Text("删除这条安排？") },
        text = { Text(initial.subject) },
        confirmButton = { TextButton(onClick = { onDelete(initial) }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
    )
}
