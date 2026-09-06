package com.kebiao.app.ui.calendar

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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.kebiao.app.data.ScheduleExam
import com.kebiao.app.ui.AppViewModel

@Composable
fun ExamCalendarScreen(viewModel: AppViewModel, padding: PaddingValues = PaddingValues()) {
    val state by viewModel.uiState.collectAsState()
    val exams = state.exams.sortedWith(compareBy<ScheduleExam> { it.date }.thenBy { it.time.orEmpty() })
    var editing by remember { mutableStateOf<ScheduleExam?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.padding(padding),
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; showEditor = true }) { Text("+") }
        },
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            Text("考试日历", modifier = Modifier.padding(16.dp))
            if (exams.isEmpty()) {
                Text("还没有考试安排", modifier = Modifier.padding(16.dp))
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(exams, key = { it.id }) { exam ->
                        Card(modifier = Modifier.fillMaxWidth(), onClick = { editing = exam; showEditor = true }) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(exam.subject)
                                Text("${exam.date}${exam.time?.let { " $it" }.orEmpty()}")
                                val location = listOfNotNull(exam.building, exam.room).joinToString(" ")
                                if (location.isNotBlank()) Text(location)
                                exam.locationNote?.takeIf { it.isNotBlank() }?.let { Text(it) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        ExamEditorDialog(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = { viewModel.addExam(it); showEditor = false },
            onDelete = { viewModel.deleteExam(it.id); showEditor = false },
        )
    }
}

@Composable
private fun ExamEditorDialog(
    initial: ScheduleExam?,
    onDismiss: () -> Unit,
    onSave: (ScheduleExam) -> Unit,
    onDelete: (ScheduleExam) -> Unit,
) {
    var subject by remember(initial) { mutableStateOf(initial?.subject.orEmpty()) }
    var date by remember(initial) { mutableStateOf(initial?.date.orEmpty()) }
    var time by remember(initial) { mutableStateOf(initial?.time.orEmpty()) }
    var building by remember(initial) { mutableStateOf(initial?.building.orEmpty()) }
    var room by remember(initial) { mutableStateOf(initial?.room.orEmpty()) }
    var note by remember(initial) { mutableStateOf(initial?.locationNote.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加考试" else "编辑考试") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(subject, { subject = it }, label = { Text("科目") }, singleLine = true)
                OutlinedTextField(date, { date = it }, label = { Text("日期 YYYY-MM-DD") }, singleLine = true)
                OutlinedTextField(time, { time = it }, label = { Text("时间") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(building, { building = it }, label = { Text("教学楼") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(room, { room = it }, label = { Text("教室") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                OutlinedTextField(note, { note = it }, label = { Text("地点备注") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (subject.isNotBlank() && date.isNotBlank()) onSave(
                    ScheduleExam(
                        id = initial?.id ?: AppViewModel.newExamId(),
                        subject = subject.trim(),
                        date = date.trim(),
                        time = time.trim().ifBlank { null },
                        building = building.trim().ifBlank { null },
                        room = room.trim().ifBlank { null },
                        locationNote = note.trim().ifBlank { null },
                        source = initial?.source ?: "MANUAL",
                        createdAtEpochMillis = initial?.createdAtEpochMillis ?: System.currentTimeMillis(),
                        updatedAtEpochMillis = System.currentTimeMillis(),
                    ),
                )
            }) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (initial != null) TextButton(onClick = { onDelete(initial) }) { Text("删除") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}
